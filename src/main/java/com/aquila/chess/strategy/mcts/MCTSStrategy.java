package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.TrainGame;
import com.aquila.chess.config.MCTSConfig;
import com.aquila.chess.strategy.FixMCTSTreeStrategy;
import com.aquila.chess.strategy.mcts.inputs.OneStepRecord;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import com.aquila.chess.strategy.mcts.utils.Statistic;
import com.aquila.chess.utils.DotGenerator;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
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

    @Getter
    private MCTSGame mctsGame;
    // private int nbStep = 0;
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
    private Game.GameStatus currentGameStatus;

    @Getter
    private TrainGame trainGame;
    private double parentReward = MCTSConfig.mctsConfig.getNewNodeValue();

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

    public MCTSStrategy withTrainGame(final TrainGame trainGame) {
        this.trainGame = trainGame;
        return this;
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
    public Move evaluateNextMove(final Game game,
                                 final Move moveOpponent,
                                 final List<Move> possibleMoves) throws InterruptedException {
        createRootNode(originalGame, moveOpponent, parentReward, possibleMoves);
        assert (directRoot != null);
        final Move move = mctsStep(moveOpponent, possibleMoves);
        if (trainGame != null) {
            OneStepRecord lastOneStepRecord = createStepTraining(
                    moveOpponent,
                    moveOpponent.getAllegiance(),
                    this.directRoot
            );
            trainGame.add(lastOneStepRecord);
        }
        currentGameStatus = this.mctsGame.play(move);
        double cpuct = this.updateCpuct.update(this.mctsGame.getNbStep(), possibleMoves.size());
        // this.nbStep++;
        log.info("[{}] -------------------------------------------------------", this.getAlliance());
        int whiteValue = mctsGame.getPlayer(Alliance.WHITE).getActivePieces().stream().mapToInt(Piece::getPieceValue).sum();
        int blackValue = mctsGame.getPlayer(Alliance.BLACK).getActivePieces().stream().mapToInt(Piece::getPieceValue).sum();
        log.info("[{}] Using as FPU: {}   CPUCT:{}", this.getAlliance(), this.parentReward, cpuct);
        log.info("[{}] PIECES VALUES | WHITE:{} <-> {}:BLACK --> RATIO:{} ", this.getAlliance(), whiteValue, blackValue, mctsGame.ratioPlayer());
        log.info("[{}] Childs:{} nextPlay() -> {}", this.getAlliance(), directRoot != null ? directRoot.getNumberOfAllNodes() : 0, move);
        log.info("[{}] -------------------------------------------------------", this.getAlliance());
        this.parentReward = -directRoot.getExpectedReward(false) - MCTSConfig.mctsConfig.getFpuReduction();
        return move;
    }

    @Override
    public void end(final Move move) {
        if (trainGame != null) {
            OneStepRecord finalOneStepRecord = createStepTraining(
                    move,
                    move.getAllegiance(),
                    null
            );
            trainGame.add(finalOneStepRecord);
        }
    }

    /**
     * Modify the directRoot
     *
     * @param game
     * @param opponentMove
     * @param possibleMoves
     * @return
     */
    protected void createRootNode(final Game game, final Move opponentMove, final double parentReward, final List<Move> possibleMoves) {
        assert opponentMove != null;
        assert opponentMove.isInitMove() || opponentMove.getAllegiance() != this.alliance;
        log.info("[{}] opponentMove:{} directRoot:{}", this.alliance, opponentMove, directRoot);
        deepLearning.getServiceNN().clearAll();
        this.mctsGame = new MCTSGame(game);
        long key = deepLearning.addRootCacheValue(mctsGame, "STRATEGY-ROOT", parentReward, alliance.complementary(), statistic);
        CacheValue cacheValue = deepLearning.getCacheValues().get(key);
        cacheValue.verifyAlliance(alliance.complementary());
        this.directRoot = MCTSNode.createRootNode(possibleMoves, opponentMove, key, cacheValue);
    }

    protected Move mctsStep(final Move moveOpponent,
                            final List<Move> currentPossibleMoves)
            throws InterruptedException {
        statistic.clearEachStep();
        IMCTSSearch mctsSearchMultiThread = new MCTSSearchMultiThread(
                this.mctsGame.getNbStep(),
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
        final MCTSNode bestNode = findBestReward(directRoot, currentPossibleMoves);
        if (bestNode == null) {
            log.error("!!! no bestnodes found: return random move from the list{}", currentPossibleMoves);
            log.error("!!! MCTSTree nodes:{}",
                    directRoot.getChildsAsCollection().stream().filter(Objects::nonNull).map(MCTSNode::getMove));
            assert false;
        }
        PolicyUtils.logPolicies(bestNode.getParent(), currentPossibleMoves);
        log.warn("[{}] bestNode: {}", this.getAlliance(), bestNode);
        log.warn("[{}] CacheSize: {} STATS: {}", this.getAlliance(), this.deepLearning.getCacheSize(), statistic);
        statistic.incNodes(this.deepLearning.getCacheValues());
        log.info(String.format("| %8s | %8s | %8s | %8s |", "", "Win", "Lost", "Drawn"));
        log.info(String.format("| %8s | %8d | %8d | %8d |", "TOTAL",
                this.statistic.totalWinNodes,
                this.statistic.totalLostNodes,
                this.statistic.totalDrawnNodes));
        log.info(String.format("| %8s | %8d | %8d | %8d |", "INTER",
                this.deepLearning.getCacheValues().getWinCacheValue().getNbNodes(),
                this.deepLearning.getCacheValues().getLostCacheValue().getNbNodes(),
                this.deepLearning.getCacheValues().getDrawnCacheValue().getNbNodes()));
        this.deepLearning.getCacheValues().clearNodes();// clearCache();
        log.warn("[{}] nbSearch calls:{} - term:{} ms - speed:{} calls/s visitsRoot:{} | BESTNODES.visits:{} (Max policy:BESTNODES.value:{} BESTNODES.reward:{}", this.getAlliance(), nbNumberSearchCalls,
                length, speed, directRoot.getVisits(), bestNode.getVisits(), bestNode.getCacheValue().getValue(), bestNode.getExpectedReward(false));
        final Optional<Move> optionalMove = currentPossibleMoves.parallelStream().filter(move -> move.toString().equals(bestNode.getMove().toString())).findAny();
        if (optionalMove.isEmpty()) {
            log.warn(
                    "##########################################################################################################");
            log.warn("[{}] ALARM: currentMoves: {}", this.getAlliance(), currentPossibleMoves);
            log.warn("[{}] moveOpponent: {}", this.getAlliance(), moveOpponent);
            log.warn("[{}] bestNode: {}", this.getAlliance(), bestNode);
        }
        final Move move = bestNode.getMove();
        if (currentPossibleMoves.stream().filter(move1 -> move1.toString().equals(move.toString())).findFirst().isEmpty()) {
            throw new RuntimeException(String.format("move:%s not in possible move:%s", move, currentPossibleMoves));
        }
        return move;
    }

    public MCTSNode findBestReward(final MCTSNode opponentNode, final Collection<Move> currentPossibleMoves) {
        assert !currentPossibleMoves.isEmpty();
        log.warn("[{}] FINDBEST MCTS: {}", this.getAlliance(), opponentNode);
        final List<MCTSNode> bestNodes = new ArrayList<>();
        final List<MCTSNode> bestExpectedRewardsNodes = new ArrayList<>();
        final List<MCTSNode> initializeNodes = opponentNode.getChildsAsCollection().stream().filter(node -> node != null).collect(Collectors.toList());
        double maxExpectedReward = Double.NEGATIVE_INFINITY;
        int maxVisits = Integer.MIN_VALUE;
        for (MCTSNode mctsNode : initializeNodes) {
            final Move currentMove = mctsNode.getMove();
            if (currentPossibleMoves.stream().filter(move1 -> move1.toString().equals(currentMove.toString())).findFirst().isEmpty()) {
                log.error("move:{} not in possible.\n - Board moves:{}.\n - MCTSTree nodes:{}",
                        currentMove,
                        currentPossibleMoves,
                        initializeNodes.stream().map(node -> node.getMove()).collect(Collectors.toList()));
                assert false; // FIXME throw new RuntimeException(String.format("move:%s not in possible move:%s", currentMove, currentPossibleMoves));
            }
            if (mctsNode.getState() == MCTSNode.State.WIN) {
                bestNodes.clear();
                int maxSteps = switch (opponentNode.getColorState()) {
                    case WHITE -> MCTSConfig.mctsConfig.getMctsBlackStrategyConfig().getSteps();
                    case BLACK -> MCTSConfig.mctsConfig.getMctsWhiteStrategyConfig().getSteps();
                };
                mctsNode.setVisits(maxSteps + mctsNode.getVisits());
                bestNodes.add(mctsNode);
                break;
            }
            maxExpectedReward = retrieveBestNodesWithExpectedRewards(mctsNode, maxExpectedReward, bestExpectedRewardsNodes);
            maxVisits = retrieveBestNodesWithBestVisits(mctsNode, maxVisits, bestNodes);
        }
        int nbBests = bestNodes.size();
        MCTSNode ret;
        if (nbBests > 1) {
            log.error("[{}] !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!", getAlliance());
            log.error("[{}] WARNING in MCTS Best Search Move RANDOM from {} bests moves", getAlliance(), nbBests);
            log.error("[{}] Moves: {}", getAlliance(), bestNodes.stream().map(node -> node.getMove())
                    .collect(Collectors.toList()));
            log.error("[{}] This could happen when we have the choices between many way to loose or win", getAlliance());
            log.error("[{}] parent: {}", getAlliance(), DotGenerator.toString(opponentNode, 10));
            log.error("[{}] !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!", getAlliance());
            ret = getRandomNodes(bestNodes);
        } else if (nbBests == 0) {
            log.error("[{}] NO BEST NODES, opponentNode:{}", getAlliance(), opponentNode);
            log.error("[{}] !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!", getAlliance());
            log.error("[{}] parent: {}", getAlliance(), DotGenerator.toString(opponentNode, 10));
            log.error("[{}] !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!", getAlliance());
            return null;
        } else {
            ret = bestNodes.get(0);
        }
        String state = "MEDIUM";
        int nbChilds = opponentNode.getNumberOfChilds();
        if (nbBests == 1 && nbChilds >= 1)
            state = "GOOD";
        else if (nbBests == nbChilds)
            state = "BAD";
        double percentGood = (nbBests * 100.0) / nbChilds;
        log.warn(
                "[{}] State: {}:{}% Step:{} nbChilds:{} nbBests:{} | RetNode:{}",
                getAlliance(), state, percentGood, mctsGame.getNbStep(), nbChilds, nbBests, ret);
        log.warn("[{}] BestRewardsNodes: {}", getAlliance(), bestExpectedRewardsNodes.stream().map(node -> String.format("move:%s expectedRewards:%f value:%f visits:%d",
                        node.move.toString(),
                        node.getExpectedReward(false),
                        node.getCacheValue().getValue(),
                        node.getVisits()))
                .collect(Collectors.joining("\n")));
        return ret;
    }

    double retrieveBestNodesWithExpectedRewards(final MCTSNode mctsNode, double maxExpectedReward, final List<MCTSNode> bestNodes) {
        double expectedReward = mctsNode.getExpectedReward(false);
        if (expectedReward > maxExpectedReward) {
            maxExpectedReward = expectedReward;
            bestNodes.clear();
            bestNodes.add(mctsNode);
        } else if (expectedReward == maxExpectedReward) {
            bestNodes.add(mctsNode);
        }
        return maxExpectedReward;
    }

    int retrieveBestNodesWithBestVisits(final MCTSNode mctsNode, int maxVisits, final List<MCTSNode> bestNodes) {
        int currentVisits = mctsNode.getVisits();
        if (currentVisits > maxVisits) {
            maxVisits = currentVisits;
            bestNodes.clear();
            bestNodes.add(mctsNode);
        } else if (currentVisits == maxVisits) {
            bestNodes.add(mctsNode);
        }
        return maxVisits;
    }

    private Move getRandomMove(Collection<Move> currentPossibleMoves) {
        final int index = rand.nextInt(currentPossibleMoves.size());
        int i = 0;
        for (Move move : currentPossibleMoves) {
            if (i == index) return move;
        }
        return null;
    }

    public MCTSNode getRandomNodes(List<MCTSNode> nodes) {
        final int index = rand.nextInt(nodes.size());
        return nodes.get(index);
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
                directRoot != null ? directRoot.getNumberOfAllNodes() : 0);
    }

    public String mctsTree4log(boolean displayLastChilds, int maxDepth) {
        return String.format("[%s] graph:\n############################\n%s\n############################", this.getAlliance(), DotGenerator.toString(this.getDirectRoot(), maxDepth, displayLastChilds));
    }

    @Override
    public String toString() {
        return getName();
    }

    /**
     * @param stepNode
     * @param move     used only if stepNode == null, usefull for the last move of a game, when we do not need to retrieve the rootNode but just checkMate
     * @return
     */
    private static Map<Integer, Double> calculatePolicies(final MCTSNode stepNode, final Move move) {
        final Map<Integer, Double> probabilities = new HashMap<>();
        if (stepNode == null || stepNode.getVisits() == 0) {
            int index = PolicyUtils.indexFromMove(move);
            probabilities.put(index, 1.0);
            return probabilities;
        }
        if (!stepNode.isSync()) {
            String msg = String.format("calculatePolicies: root is not sync: %s", stepNode);
            log.error(msg);
            throw new RuntimeException(msg);
        }
        int nbChilds = stepNode.getChildNodes().size();
        stepNode.getChildNodes()
                .values()
                .stream()
                .filter(childNode -> childNode != null && childNode.node != null)
                .forEach(childNode -> {
                    int index = PolicyUtils.indexFromMove(childNode.node.getMove());
                    double probability = (double) childNode.node.getVisits() / (double) stepNode.getVisits();
                    if (probability == 0 && childNode.getNode().getState() == MCTSNode.State.WIN)
                        probability = 1.0 / nbChilds;
                    if (probability != 0) probabilities.put(index, probability);
                });
        return probabilities;
    }

    private static OneStepRecord createStepTraining(final Move move,
                                                    final Alliance alliance,
                                                    final MCTSNode directParent) {
        Map<Integer, Double> policies = calculatePolicies(directParent, move);
        OneStepRecord lastOneStepRecord = new OneStepRecord(
                null,
                move.toString(),
                alliance,
                policies);
        log.debug("CREATE STEP TRAINING -> Save inputs:{}", policies.size());
        return lastOneStepRecord;
    }

}
