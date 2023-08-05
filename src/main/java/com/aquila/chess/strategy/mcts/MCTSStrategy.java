package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.TrainGame;
import com.aquila.chess.strategy.FixMCTSTreeStrategy;
import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.OneStepRecord;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import com.aquila.chess.strategy.mcts.utils.Statistic;
import com.aquila.chess.utils.DotGenerator;
import com.aquila.chess.utils.Utils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.nd4j.shade.protobuf.common.io.PatternFilenameFilter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class MCTSStrategy extends FixMCTSTreeStrategy {

    private final Game originalGame;

    @Getter
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
    final private Statistic statistic = new Statistic();

    @Getter
    private MCTSNode directRoot = null;

    @Getter
    final private TrainGame trainGame = new TrainGame();

    @Setter
    private MCTSStrategy partnerStrategy = null;

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
                     final List<Move> possibleMoves) throws InterruptedException {
        if (isTraining() && this.partnerStrategy.getDirectRoot() != null && this.mctsGame != null) {
            OneStepRecord lastOneStepRecord = createStepTraining(
                    this.mctsGame,
                    moveOpponent,
                    this.alliance.complementary(),
                    this.partnerStrategy.getDirectRoot()
            );
            trainGame.add(lastOneStepRecord);
        }
        // this.root = null;
        this.directRoot = null;
        createRootNode(originalGame, moveOpponent);
        assert (directRoot != null);
        final Move move = mctsStep(moveOpponent, possibleMoves);
        log.info("[{}] {} nextPlay() -> {}", this.nbStep, this, move);
        this.nbStep++;

        if (isTraining()) {
            OneStepRecord lastOneStepRecord = createStepTraining(
                    this.mctsGame,
                    move,
                    this.alliance,
                    this.directRoot
            );
            trainGame.add(lastOneStepRecord);
        }
        this.mctsGame.play(this.directRoot, move);
        return move;
    }

    public boolean isTraining() {
        return this.partnerStrategy != null;
    }

    /**
     * Modify the directRoot
     * @param game
     * @param opponentMove
     * @return
     */
    protected void createRootNode(final Game game, final Move opponentMove) {
        assert (opponentMove != null);
        assert (opponentMove.getAllegiance() != this.alliance);
        deepLearning.getServiceNN().clearAll();
        this.mctsGame = new MCTSGame(game);
        if (this.directRoot == null) {
            long key = deepLearning.addRootState(mctsGame, "STRATEGY-ROOT", alliance.complementary(), statistic);
            this.directRoot = MCTSNode.createRootNode(mctsGame.getBoard(), opponentMove, key, deepLearning.getCacheValues().get(key));
            return;
        }
        MCTSNode childNode = this.directRoot.findChild(opponentMove);
        if (childNode == null) {
            long key = deepLearning.addState(mctsGame, "ROOT-1", opponentMove, statistic);
            this.directRoot = MCTSNode.createNode(mctsGame.getBoard(), opponentMove, key, deepLearning.getCacheValues().get(key));
        } else {
            directRoot = childNode;
            directRoot.setAsRoot();
        }
    }

    protected Move mctsStep(final Move moveOpponent,
                            final List<Move> currentMoves)
            throws InterruptedException {
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
        final MCTSNode bestNode = findBestReward(directRoot, false);
        log.warn("bestNode: {}", bestNode);
        log.warn("[{}] CacheSize: {} STATS: {}", this.getAlliance(), this.deepLearning.getCacheSize(), statistic);
        log.warn("[{}] nbSearch calls:{} - term:{} ms - speed:{} calls/s value:{} reward:{}", this.getAlliance(), nbNumberSearchCalls,
                length, speed, bestNode.getCacheValue().value, bestNode.getExpectedReward(false));
        final Optional<Move> optionalMove = currentMoves.parallelStream().filter(move -> move.equals(bestNode.getMove())).findAny();
        if (optionalMove.isEmpty()) {
            log.warn(
                    "##########################################################################################################");
            log.warn("[{}] ALARM: currentMoves: {}", this.getAlliance(), currentMoves);
            log.warn("[{}] moveOpponent: {}", this.getAlliance(), moveOpponent);
            log.warn("[{}] bestNode: {}", this.getAlliance(), bestNode);
        }
        return bestNode.getMove();
//
//        final Optional<Move> optionalMove = currentMoves.parallelStream().filter(move -> move.equals(bestNode.getMove())).findAny();
//        Move ret;
//        if (optionalMove.isEmpty()) {
//            log.warn(
//                    "##########################################################################################################");
//            log.warn("[{}] ALARM: currentMoves: {}", this.getAlliance(), currentMoves);
//            log.warn("[{}] moveOpponent: {}", this.getAlliance(), moveOpponent);
//            log.warn("[{}] bestNode: {}", this.getAlliance(), bestNode);
//            // log.warn(DotGenerator.toString(directRoot, 10));
//            // log.warn("[{}] Game:\n{}", this.getAlliance(), mctsGame.toPGN());
//            Collections.shuffle(currentMoves, rand);
//            ret = currentMoves.get(0);
//            long key = deepLearning.addState(mctsGame, "ALARM:" + directRoot.getMove().toString(), ret, statistic);
//            MCTSNode childNode = MCTSNode.createNode(ret, this.mctsGame.getBoard(), key, deepLearning.getCacheValues().get(key));
//            directRoot.addChild(childNode);
//            log.warn("[{}] choosing randomly: {}", this.getAlliance(), ret);
//            log.warn(
//                    "##########################################################################################################");
//        } else {
//            ret = optionalMove.get();
//        }
//        this.directRoot = directRoot.findChild(ret);
//        return ret;
    }

    public static double rewardWithLogVisit(final MCTSNode mctsNode) {
        return mctsNode.getExpectedReward(false) + Math.log(1 + Math.sqrt(mctsNode.getVisits()));
    }

    public MCTSNode findBestReward(final MCTSNode opponentNode, boolean withLogVisit) {
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
            double rewardsLogVisits = withLogVisit ? rewardWithLogVisit(mctsNode) : mctsNode.getExpectedReward(false);
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
            log.error("[{}] WARNING in MCTS Best Search Move RANDOM from {} bests moves", getAlliance(), nbBests);
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
        if (nbThreads > 0) {
            this.nbThreads = nbThreads;
            log.warn("change nb threads: {}", nbThreads);
        }
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

    public String mctsTree4log(boolean displayLastChilds, int maxDepth) {
        return String.format("[%s] graph:\n############################\n%s\n############################", this.getAlliance(), DotGenerator.toString(this.getDirectRoot(), maxDepth, displayLastChilds));
    }

    @Override
    public String toString() {
        return getName();
    }

    public ResultGame getResultGame(final Game.GameStatus gameStatus) {
        return switch (gameStatus) {
            case PAT, DRAW_3, DRAW_50, DRAW_300, DRAW_NOT_ENOUGH_PIECES -> new ResultGame(1, 1);
            case WHITE_CHESSMATE -> new ResultGame(0, 1);
            case BLACK_CHESSMATE -> new ResultGame(1, 0);
            default -> null;
        };
    }

    private static Map<Integer, Double> calculatePolicies(final MCTSNode stepNode) {
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

    private static OneStepRecord createStepTraining(final MCTSGame mctsGame, final Move move, final Alliance alliance, final MCTSNode directParent) {
        InputsFullNN inputs = mctsGame.getInputsManager().createInputs(mctsGame.getLastBoard(), move, alliance);
        Map<Integer, Double> policies = calculatePolicies(directParent);
        OneStepRecord lastOneStepRecord = new OneStepRecord(
                inputs,
                move.toString(),
                alliance,
                policies);
        log.debug("CREATE STEP TRAINING -> Save inputs:{}", policies.size());
        log.debug("CREATE STEP TRAINING ->[{}] INPUTS:\n{}", alliance, inputs);
        return lastOneStepRecord;
    }

    public String saveBatch(String trainDir, ResultGame resultGame) throws IOException {
        final int numGames = maxGame(trainDir + "/") + 1;
        log.info("SAVING Batch (game number: {}) ... (do not stop the jvm)", numGames);
        log.info("Result: {}   Game size: {} inputsList(s)", resultGame.reward, trainGame.getOneStepRecordList().size());
        final String filename = trainGame.save(trainDir, numGames, resultGame);
        log.info("SAVE DONE in {}", filename);
        clearTrainGame();
        return filename;
    }

    private int maxGame(String path) {
        File dataDirectory = new File(path);
        int max = 0;
        if (dataDirectory.canRead()) {
            for (File file : dataDirectory.listFiles(new PatternFilenameFilter("[0-9]+"))) {
                int currentNumber = Integer.valueOf(file.getName()).intValue();
                if (currentNumber > max) max = currentNumber;
            }
        }
        return max;
    }

    public void clearTrainGame() {
        this.trainGame.clear();
    }

}
