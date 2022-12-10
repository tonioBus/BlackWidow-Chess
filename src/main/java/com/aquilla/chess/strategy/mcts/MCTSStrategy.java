package com.aquilla.chess.strategy.mcts;

import com.aquilla.chess.Game;
import com.aquilla.chess.strategy.FixStrategy;
import com.aquilla.chess.utils.DotGenerator;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class MCTSStrategy extends FixMCTSTreeStrategy {

    private final Game game;
    private int nbStep = 0;

    protected int nbThreads;

    @Getter
    private MCTSNode currentRootNode = null;

    private final long timeMillisPerStep;
    private long nbMaxSearchCalls = -1;
    private Dirichlet dirichlet = nbStep1 -> false;

    private boolean smartRandomChoice = false;

    private final Random rand;

    @Getter
    private final DeepLearningAGZ deepLearning;

    private final UpdateCpuct updateCpuct;

    @Getter
    private Statistic statistic = new Statistic();

    private MCTSNode root;

    public MCTSStrategy(
            final Game game,
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
        this.game = game;
    }

    public MCTSStrategy withNbMaxSearchCalls(long nbMaxSearchCalls) {
        this.nbMaxSearchCalls = nbMaxSearchCalls;
        return this;
    }

    public MCTSStrategy withDirichlet(Dirichlet dirichlet) {
        this.dirichlet = dirichlet;
        return this;
    }

    public MCTSStrategy withSmartRandomChoice(boolean smartRandomChoice) {
        this.smartRandomChoice = smartRandomChoice;
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

    public void init() {
        currentRootNode = null;
    }

    @Override
    public Move play(final Game game,
                     final Move moveOpponent,
                     final List<Move> moves) throws InterruptedException {
        Move move = mctsStep(moveOpponent, moves);
        this.nbStep++;
        log.info("{} nextPlay() -> {}", this, move);
        pushNNInput(game, move);
        return move;
    }

    @Override
    public String getName() {
        return String.format("[%s %S DL:%s Nodes:%d] ", alliance, this.getClass().getSimpleName(),
                this.deepLearning.getFilename(), currentRootNode != null ? currentRootNode.getNumberAllSubNodes() : 0);
    }

    private MCTSNode setCurrentRootNode(final Move opponentMove) {
        deepLearning.clearAllCaches();
        if (currentRootNode == null) {
            long key;
            key = deepLearning.addState(game, "PLAYER-ROOT", alliance.complementary(), opponentMove, true, true, statistic);
            this.currentRootNode = MCTSNode.createNode(currentRootNode, opponentMove, key, deepLearning.getCacheValues().get(key));
            this.currentRootNode.setAsRoot();
            this.root = currentRootNode;
            return currentRootNode;
        }
        MCTSNode opponentNode = this.currentRootNode.findChild(opponentMove);
        if (opponentNode == null) {
            long key;
            key = deepLearning.addState(game, "PLAY:" + currentRootNode.getMove().toString(), alliance.complementary(), opponentMove, true, true, statistic);
            opponentNode = MCTSNode.createNode(currentRootNode, opponentMove, key, deepLearning.getCacheValues().get(key));
        }
        this.currentRootNode = opponentNode;
        this.currentRootNode.setAsRoot();
        return currentRootNode;
    }

    public void initCurrentRootNode() {
        setCurrentRootNode(null);
    }

    private Move mctsStep(final Move opponentMove,
                          final List<Move> currentMoves)
            throws InterruptedException {
        setCurrentRootNode(opponentMove);
        statistic.clear();
        IMCTSSearch mctsSearchMultiThread = new MCTSSearchMultiThread(
                nbThreads,
                this.timeMillisPerStep,
                this.nbMaxSearchCalls,
                statistic,
                this.deepLearning,
                currentRootNode,
                game,
                this.alliance,
                this.updateCpuct,
                this.dirichlet,
                rand);
        final long startTime = System.currentTimeMillis();
        long nbNumberSearchCalls = mctsSearchMultiThread.search();
        final long endTime = System.currentTimeMillis();
        final long length = endTime > startTime ? endTime - startTime : Long.MIN_VALUE;
        final long speed = (nbNumberSearchCalls * 1000) / length;
        MCTSNode bestNode = findBestRewardsWithLogVisits(currentRootNode);
        if (this.game.isLogBoard()) {
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
            log.warn(DotGenerator.toString(currentRootNode, 10));
            log.warn("[{}] Game:\n{}", this.getAlliance(), game.toPGN());
            Collections.shuffle(currentMoves, rand);
            ret = currentMoves.get(0);
            long key = deepLearning.addState(game, "ALARM:" + currentRootNode.getMove().toString(), alliance, ret, false, false, statistic);
            currentRootNode.addChild(MCTSNode.createNode(currentRootNode, ret, key, deepLearning.getCacheValues().get(key)));
            log.warn("[{}] choosing randomly: {}", this.getAlliance(), ret);
            log.warn(
                    "##########################################################################################################");
        } else {
            ret = optionalMove.get();
        }
        this.currentRootNode = currentRootNode.findChild(ret);
        return ret;
    }

    public MCTSNode findBestRewardsWithLogVisits(final MCTSNode opponentNode) {
        if (this.game.isLogBoard()) {
            log.warn("[{}] FINDBEST MCTS: {}", this.getAlliance(), opponentNode);
            log.warn("[{}] FINDBEST: {}", this.getAlliance(), DotGenerator.toString(opponentNode, 5));
        }

        double maxExpectedReward = Double.NEGATIVE_INFINITY;
        List<MCTSNode> bestNodes = new ArrayList<>();
        for (MCTSNode mctsNode : opponentNode.childNodes.values()) {
            if (mctsNode.getState() == MCTSNode.State.WIN) {
                bestNodes.clear();
                bestNodes.add(mctsNode);
                break;
            }
            log.debug("FINDBEST: expectedReward:{} visitsDelta:{} node:{}", mctsNode.getExpectedReward(false), Math.log(1 + Math.sqrt(mctsNode.getVisits())), mctsNode);
            double rewardsLogVisits = mctsNode.getExpectedReward(false) + Math.log(1 + Math.sqrt(mctsNode.getVisits()));
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
        } else
            ret = bestNodes.get(0);
        String state = "MEDIUM";
        int nbChilds = opponentNode.childNodes.size();
        if (nbBests == 1 && nbChilds >= 1)
            state = "GOOD";
        else if (nbBests == nbChilds)
            state = "BAD";
        float percentGood = (nbBests * 100) / nbChilds;
        if (this.game.isLogBoard()) {
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

    @Override
    public String toString() {
        return getName();
    }

    /**
     * @return the root
     */
    public MCTSNode getRoot() {
        return root;
    }


    public MCTSStrategy withNbThread(int nbThreads) {
        this.nbThreads = nbThreads;
        log.warn("change nb threads: {}", nbThreads);
        return this;
    }
}
