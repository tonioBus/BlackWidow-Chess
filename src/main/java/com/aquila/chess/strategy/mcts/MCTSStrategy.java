package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
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

    private MCTSNode directRoot = null;

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
        Move move = mctsStep(moveOpponent, moves);
        log.info("[{}] {} nextPlay() -> {}", this.nbStep, this, move);
        this.nbStep++;
        return move;
    }

    /**
     * @param game
     * @param opponentMove
     * @return
     */
    protected MCTSNode setDirectRoot(final Game game, final Move opponentMove) {
        assert (opponentMove == null || opponentMove.getMovedPiece().getPieceAllegiance() != this.alliance);
        deepLearning.clearAllCaches();
        this.mctsGame = new MCTSGame(game);
        // long key = deepLearning.addState(mctsGame, "PLAYER-ROOT", alliance.complementary(), opponentMove, true, true, statistic);
        if (this.directRoot != null) {
            MCTSNode childNode = this.directRoot.findChild(opponentMove);
            if (childNode == null) {
                long key = deepLearning.addState(mctsGame, "PLAYER-ROOT", alliance.complementary(), opponentMove, true, true, statistic);
                this.directRoot = MCTSNode.createNode(directRoot, opponentMove, mctsGame.getBoard(), true, key, deepLearning.getCacheValues().get(key));
            } else directRoot = childNode;
        } else {
            long key = deepLearning.addState(mctsGame, "ROOT", alliance.complementary(), opponentMove, true, true, statistic);
            this.directRoot = MCTSNode.createNode(null, opponentMove, mctsGame.getBoard(), true, key, deepLearning.getCacheValues().get(key));
        }
        this.directRoot.setAsRoot();
        return directRoot;
    }

    protected Move mctsStep(final Move opponentMove,
                            final List<Move> currentMoves)
            throws InterruptedException {
        setDirectRoot(originalGame, opponentMove);
        statistic.clear();
        IMCTSSearch mctsSearchMultiThread = new MCTSSearchMultiThread(
                nbStep,
                nbThreads,
                this.timeMillisPerStep,
                this.nbSearchCalls,
                statistic,
                this.deepLearning,
                directRoot,
                mctsGame,
                this.alliance,
                this.updateCpuct,
                this.dirichlet,
                rand);
        final long startTime = System.currentTimeMillis();
        long nbNumberSearchCalls = mctsSearchMultiThread.search();
        final long endTime = System.currentTimeMillis();
        final long length = endTime > startTime ? endTime - startTime : Long.MIN_VALUE;
        final long speed = (nbNumberSearchCalls * 1000) / length;
        MCTSNode bestNode = findBestRewardsWithLogVisits(directRoot);
        if (this.mctsGame.isLogBoard()) {
            log.warn("[{}] CacheSize: {} STATS: {}", this.getAlliance(), this.deepLearning.getCacheSize(), statistic.toString());
            log.warn("[{}] nbSearch calls:{} - term:{} ms - speed:{} calls/s", this.getAlliance(), nbNumberSearchCalls,
                    length, speed);
        }
        Optional<Move> optionalMove = currentMoves.stream().filter(move -> move.equals(bestNode.getMove())).findFirst();
        Move ret;
        if (optionalMove.isEmpty()) {
            log.warn(
                    "##########################################################################################################");
            log.warn("[{}] ALARM: currentMoves: {}", this.getAlliance(), currentMoves);
            log.warn("[{}] opponentMove: {}", this.getAlliance(), opponentMove);
            log.warn("[{}] bestNode: {}", this.getAlliance(), bestNode);
            log.warn(DotGenerator.toString(directRoot, 10));
            log.warn("[{}] Game:\n{}", this.getAlliance(), mctsGame.toPGN());
            Collections.shuffle(currentMoves, rand);
            ret = currentMoves.get(0);
            long key = deepLearning.addState(mctsGame, "ALARM:" + directRoot.getMove().toString(), alliance, ret, false, false, statistic);
            directRoot.addChild(MCTSNode.createNode(directRoot, ret, this.mctsGame.getBoard(), false, key, deepLearning.getCacheValues().get(key)));
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
        if (this.mctsGame.isLogBoard()) {
            log.warn("[{}] FINDBEST MCTS: {}", this.getAlliance(), opponentNode);
        }
        double maxExpectedReward = Double.NEGATIVE_INFINITY;
        List<MCTSNode> bestNodes = new ArrayList<>();
        for (MCTSNode mctsNode : opponentNode.getChildNodes().values()) {
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
        int nbChilds = opponentNode.getChildNodes().size();
        if (nbBests == 1 && nbChilds >= 1)
            state = "GOOD";
        else if (nbBests == nbChilds)
            state = "BAD";
        float percentGood = (nbBests * 100) / nbChilds;
        if (this.mctsGame.isLogBoard()) {
            log.warn(
                    "[{}] State: {}:{}% Step:{} nbChilds:{} nbBests:{} | RetNode:{}",
                    getAlliance(), state, percentGood, nbStep, nbChilds, nbBests, ret);
        }
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

    /**
     * @return the root
     */
//    public MCTSNode getRoot() {
//        return root;
//    }
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

    @Override
    public String toString() {
        return getName();
    }

}
