package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.OneStepRecord;
import com.aquila.chess.TrainGame;
import com.aquila.chess.strategy.FixMCTSTreeStrategy;
import com.aquila.chess.utils.DotGenerator;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class MCTSStrategy extends FixMCTSTreeStrategy {

    private final Game originalGame;
    private MCTSGame mctsGame;
    private int nbStep = 0;
    @Setter
    protected int nbThreads;
    private final long timeMillisPerStep;
    @Getter
    private long nbSearchCalls = -1;
    private Dirichlet dirichlet = nbStep1 -> false;

    private final Random rand;

    @Getter
    private final DeepLearningAGZ deepLearning;

    private final UpdateCpuct updateCpuct;

    @Getter
    private Statistic statistic = new Statistic();

    private MCTSNode root = null;

    @Getter
    private MCTSNode directRoot = null;

    @Getter
    private TrainGame trainGame = new TrainGame();

    public MCTSNode getCurrentRoot() {
        return directRoot.getParent();
    }

    public MCTSStrategy(
            final Game originalGame,
            final Alliance alliance,
            @NonNull final DeepLearningAGZ deepLearning,
            final long seed,
            @NonNull UpdateCpuct updateCpuct,
            final long timeMillisPerStep) {
        super(alliance);
        nbThreads = Runtime.getRuntime().availableProcessors() - 4;
        if (nbThreads < 1) nbThreads = 1;
        log.warn("USED PROCESSORS FOR MCTS SEARCH: {}", nbThreads);
        if (seed == 0)
            rand = new Random();
        else
            rand = new Random(seed);
        this.deepLearning = deepLearning;
        this.deepLearning.setFixMCTSTreeStrategy(this);
        this.timeMillisPerStep = timeMillisPerStep;
        this.updateCpuct = updateCpuct;
        this.originalGame = originalGame;
    }

    public MCTSStrategy withNbSearchCalls(long nbSearchCalls) {
        this.nbSearchCalls = nbSearchCalls;
        return this;
    }

    public MCTSStrategy withDirichlet(Dirichlet dirichlet) {
        this.dirichlet = dirichlet;
        return this;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public void save() throws IOException {
        this.deepLearning.save();
        System.gc();
    }

    @Override
    public Move play(final Game game,
                     final Move moveOpponent,
                     final List<Move> moves) throws InterruptedException {
        this.root = null;
        this.directRoot = null;
        final MCTSGame currentMctsGame = new MCTSGame(game);
        final Move move = mctsStep(moveOpponent, moves);
        log.info("[{}] {} nextPlay() -> {}", this.nbStep, this, move);
        this.nbStep++;
        this.saveTraining(currentMctsGame);
        currentMctsGame.play(this.directRoot, move);
        return move;
    }

    /**
     * @param game
     * @param opponentMove
     * @return
     */
    protected void createRootNode(final Game game, final Move opponentMove) {
        deepLearning.clearAllCaches();
        this.mctsGame = new MCTSGame(game);
        if (this.root == null) {
            // assert (opponentMove == null);
            long key = deepLearning.addRootState(mctsGame, "STRATEGY-ROOT", alliance.complementary(), statistic);
            this.root = MCTSNode.createRootNode(mctsGame.getBoard(), key, deepLearning.getCacheValues().get(key));
            this.directRoot = this.root;
            return;
        }
        assert (opponentMove != null);
        if (this.directRoot != null) {
            assert (opponentMove != null && opponentMove.getMovedPiece().getPieceAllegiance() != this.alliance);
            MCTSNode childNode = this.directRoot.findChild(opponentMove);
            if (childNode == null) {
                long key = deepLearning.addState(mctsGame, "ROOT", opponentMove, statistic);
                this.directRoot = MCTSNode.createNode(directRoot, opponentMove, mctsGame.getBoard(), key, deepLearning.getCacheValues().get(key));
            } else {
                directRoot = childNode;
                directRoot.setAsRoot();
            }
        } else {
            long key = deepLearning.addState(mctsGame, "ROOT", opponentMove, statistic);
            this.directRoot = MCTSNode.createRootNode(mctsGame.getBoard(), key, deepLearning.getCacheValues().get(key));
        }
    }

    protected Move mctsStep(final Move opponentMove,
                            final List<Move> currentMoves)
            throws InterruptedException {
        createRootNode(originalGame, opponentMove);
        statistic.clear();
        IMCTSSearch mctsSearchMultiThread = new MCTSSearchMultiThread(
                this.nbStep,
                this.nbThreads,
                this.timeMillisPerStep,
                this.nbSearchCalls,
                this.statistic,
                this.deepLearning,
                this.directRoot,
                this.mctsGame,
                this.alliance,
                this.updateCpuct,
                this.dirichlet,
                this.rand);
        final long startTime = System.currentTimeMillis();
        long nbNumberSearchCalls = mctsSearchMultiThread.search();
        final long endTime = System.currentTimeMillis();
        final long length = endTime > startTime ? endTime - startTime : Long.MIN_VALUE;
        final long speed = (nbNumberSearchCalls * 1000) / length;
        final MCTSNode bestNode = findBestRewardsWithLogVisits(directRoot);
        log.warn("[{}] CacheSize: {} STATS: {}", this.getAlliance(), this.deepLearning.getCacheSize(), statistic.toString());
        log.warn("[{}] nbSearch calls:{} - term:{} ms - speed:{} calls/s BestReward:{}", this.getAlliance(), nbNumberSearchCalls,
                length, speed, bestNode.getCacheValue().value);
        final Optional<Move> optionalMove = currentMoves.stream().filter(move -> move.equals(bestNode.getMove())).findFirst();
        Move ret;
        if (optionalMove.isEmpty()) {
            log.warn(
                    "##########################################################################################################");
            log.warn("[{}] ALARM: currentMoves: {}", this.getAlliance(), currentMoves);
            log.warn("[{}] opponentMove: {}", this.getAlliance(), opponentMove);
            log.warn("[{}] bestNode: {}", this.getAlliance(), bestNode);
            log.warn(DotGenerator.toString(directRoot, 10));
            // log.warn("[{}] Game:\n{}", this.getAlliance(), mctsGame.toPGN());
            Collections.shuffle(currentMoves, rand);
            ret = currentMoves.get(0);
            long key = deepLearning.addState(mctsGame, "ALARM:" + directRoot.getMove().toString(), ret, statistic);
            MCTSNode childNode = MCTSNode.createNode(directRoot, ret, this.mctsGame.getBoard(), key, deepLearning.getCacheValues().get(key));
            directRoot.addChild(childNode);
            log.warn("[{}] choosing randomly: {}", this.getAlliance(), ret);
            log.warn(
                    "##########################################################################################################");
        } else {
            ret = optionalMove.get();
        }
        this.directRoot = directRoot.findChild(ret);
        return ret;
    }

    public static double expectedReward(final MCTSNode mctsNode) {
        return mctsNode.getExpectedReward(false) + Math.log(1 + Math.sqrt(mctsNode.getVisits()));
    }

    public MCTSNode findBestRewardsWithLogVisits(final MCTSNode opponentNode) {
        log.warn("[{}] FINDBEST MCTS: {}", this.getAlliance(), opponentNode);
        double maxExpectedReward = Double.NEGATIVE_INFINITY;
        List<MCTSNode> bestNodes = new ArrayList<>();
        for (MCTSNode mctsNode : opponentNode.getChildsAsCollection()) {
            if (mctsNode == null) continue;
            if (mctsNode.getState() == MCTSNode.State.WIN) {
                bestNodes.clear();
                bestNodes.add(mctsNode);
                break;
            }
            log.debug("FINDBEST: expectedReward:{} visitsDelta:{} node:{}", mctsNode.getExpectedReward(false), Math.log(1 + Math.sqrt(mctsNode.getVisits())), mctsNode);
            double rewardsLogVisits = expectedReward(mctsNode);
            if (rewardsLogVisits > maxExpectedReward) {
                maxExpectedReward = rewardsLogVisits;
                bestNodes.clear();
                bestNodes.add(mctsNode);
            } else if (rewardsLogVisits == maxExpectedReward) {
                bestNodes.add(mctsNode);
            }
        }
        int nbBests = bestNodes.size();
        MCTSNode ret;
        if (nbBests > 1) {
            log.error("[{}] !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!", getAlliance());
            log.error("[{}] WARNING in MCTS Best Search Move RANDOM from {} bests moves", nbBests);
            log.error("[{}] Moves: {}", getAlliance(), bestNodes.stream().map(move -> move.getPiece() + "->" + move.getMove().toString())
                    .collect(Collectors.joining(" | ")));
            log.error("[{}] This could happen when we have the choices between many way to loose or win", getAlliance());
            log.error("[{}] parent: {}", getAlliance(), DotGenerator.toString(opponentNode, 10));
            log.error("[{}] !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!", getAlliance());
            ret = getRandomNodes(bestNodes);
        } else if (nbBests == 0) {
            log.error("NO BEST NODES, opponentNode:{}", opponentNode.toString());
            throw new RuntimeException("NO BEST NODES");
        } else {
            ret = bestNodes.get(0);
        }
        String state = "MEDIUM";
        int nbChilds = opponentNode.getNumberOfChilds();
        if (nbBests == 1 && nbChilds >= 1)
            state = "GOOD";
        else if (nbBests == nbChilds)
            state = "BAD";
        float percentGood = (nbBests * 100) / nbChilds;
        log.warn(
                "[{}] State: {}:{}% Step:{} nbChilds:{} nbBests:{} | RetNode:{}",
                getAlliance(), state, percentGood, nbStep, nbChilds, nbBests, ret);
        return ret;
    }

    public MCTSNode getRandomNodes(List<MCTSNode> nodes) {
        Collections.shuffle(nodes, rand);
        return nodes.get(0);
    }

    @SuppressWarnings("unused")
    private MCTSNode getRandom(final List<MCTSNode> nodes, final Random rand) {
        final int index = rand.nextInt(nodes.size());
        return nodes.get(index);
    }

    public MCTSStrategy withNbThread(int nbThreads) {
        this.nbThreads = nbThreads;
        log.warn("change nb threads: {}", nbThreads);
        return this;
    }

    @Override
    public String getName() {
        return String.format("%s{%s file:%s Childs:%d}",
                this.getClass().getSimpleName(),
                alliance,
                this.deepLearning.getFilename(),
                directRoot != null ? directRoot.getNumberAllSubNodes() : 0);
    }

    String mctsTree4log(boolean displayLastChilds, int maxDepth) {
        return String.format("[%s] graph:\n############################\n%s\n############################", this.getAlliance(), DotGenerator.toString(this.getCurrentRoot(), maxDepth, displayLastChilds));
    }

    @Override
    public String toString() {
        return getName();
    }

    public ResultGame getResultGame(final Game.GameStatus gameStatus) {
        ResultGame resultGame = null;

        switch (gameStatus) {
            case PAT:
            case DRAW_3:
            case DRAW_50:
            case DRAW_300:
            case DRAW_NOT_ENOUGH_PIECES:
                resultGame = new ResultGame(1, 1);
                break;
            case WHITE_CHESSMATE:
                resultGame = new ResultGame(0, 1);
                break;
            case BLACK_CHESSMATE:
                resultGame = new ResultGame(1, 0);
                break;
        }
        return resultGame;

    }

    private Map<Integer, Double> calculatePolicies(final MCTSNode stepNode) {
        if (!stepNode.isSync()) {
            String msg = String.format("calculatePolicies: root is not sync: %s", stepNode);
            log.error(msg);
            throw new RuntimeException(msg);
        }
        final Map<Integer, Double> probabilities = new HashMap<>();
        stepNode.getChildNodes().values().stream().filter(child -> child != null).forEach(child -> {
            int index = PolicyUtils.indexFromMove(child.getMove());
            double probability = (double) child.getVisits() / (double) stepNode.getVisits();
            probabilities.put(index, probability);
        });
        return probabilities;
    }

    private void saveTraining(final MCTSGame mctsGame) {
        double[][][] inputs = InputsNNFactory.createInput(mctsGame, this.alliance);
        Map<Integer, Double> policies = calculatePolicies(this.directRoot.getParent());
        OneStepRecord lastOneStepRecord = new OneStepRecord(
                inputs,
                this.alliance,
                policies);
        trainGame.add(lastOneStepRecord);
    }

    public void saveBatch(ResultGame resultGame, int numGames) throws IOException {
        log.info("SAVING Batch (game number: {}) ... (do not stop the jvm)", numGames);
        log.info("Result: {}   Game size: {} inputsList(s)", resultGame.reward, trainGame.getOneStepRecordList().size());
        trainGame.save(numGames, resultGame);
        log.info("SAVE DONE");
        clearTrainGame();
    }

    public void clearTrainGame() {
        this.trainGame.clear();
    }
}
