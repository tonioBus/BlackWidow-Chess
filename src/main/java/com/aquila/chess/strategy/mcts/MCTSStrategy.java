package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.TrainGame;
import com.aquila.chess.strategy.FixMCTSTreeStrategy;
import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.OneStepRecord;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import com.aquila.chess.strategy.mcts.utils.Statistic;
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
    private TrainGame trainGame;

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
    public Move play(final Game game,
                     final Move moveOpponent,
                     final List<Move> possibleMoves) throws InterruptedException {
        this.directRoot = null;
        createRootNode(originalGame, moveOpponent, possibleMoves);
        assert (directRoot != null);
        final Move move = mctsStep(moveOpponent, possibleMoves);
        log.info("[{}] -------------------------------------------------------", this.getAlliance());
        log.info("[{}] {} nextPlay() -> {}", this.getAlliance(), this, move);
        log.info("[{}] -------------------------------------------------------", this.getAlliance());
        this.nbStep++;

        if (trainGame != null) {
            OneStepRecord lastOneStepRecord = createStepTraining(
                    this.mctsGame,
                    moveOpponent,
                    moveOpponent.getAllegiance(),
                    this.directRoot
            );
            trainGame.add(lastOneStepRecord);
        }
        Game.GameStatus gameStatus = this.mctsGame.play(move);
        if (trainGame != null && gameStatus != Game.GameStatus.IN_PROGRESS) {
            OneStepRecord finalOneStepRecord = createStepTraining(
                    this.mctsGame,
                    move,
                    move.getAllegiance(),
                    null
            );
            trainGame.add(finalOneStepRecord);
        }
        this.directRoot.setState(MCTSNode.State.INTERMEDIATE);
        return move;
    }

    public boolean isTraining() {
        return this.partnerStrategy != null;
    }

    /**
     * Modify the directRoot
     *
     * @param game
     * @param opponentMove
     * @param possibleMoves
     * @return
     */
    protected void createRootNode(final Game game, final Move opponentMove, final List<Move> possibleMoves) {
        assert opponentMove != null;
        assert opponentMove.isInitMove() || opponentMove.getAllegiance() != this.alliance;
        log.info("[{}] opponentMove:{} directRoot:{}", this.alliance, opponentMove, directRoot);
        deepLearning.getServiceNN().clearAll();
        this.mctsGame = new MCTSGame(game);
        if (this.directRoot == null) {
            long key = deepLearning.addRootCacheValue(mctsGame, "STRATEGY-ROOT", alliance.complementary(), statistic);
            CacheValue cacheValue = deepLearning.getCacheValues().get(key);
            cacheValue.verifyAlliance(alliance.complementary());
            this.directRoot = MCTSNode.createRootNode(possibleMoves, opponentMove, key, cacheValue);
            log.info("[{}] this.directRoot == null:directRoot:{}", this.alliance, directRoot);
            return;
        }
        log.warn("!!! trying to find a child:{}", opponentMove);
        MCTSNode childNode = this.directRoot.findChild(opponentMove);
        if (childNode == null) {
            long key = deepLearning.addState(mctsGame, "ROOT-1", opponentMove, statistic);
            this.directRoot = MCTSNode.createNode(mctsGame.getBoard(), possibleMoves, opponentMove, key, deepLearning.getCacheValues().get(key));
            log.info("childNode == null:directRoot:{}", directRoot);
        } else {
            log.info("NOT(childNode == null):childNode{}", childNode);
            directRoot = childNode;
            directRoot.setAsRoot();
        }
    }

    protected Move mctsStep(final Move moveOpponent,
                            final List<Move> currentPossibleMoves)
            throws InterruptedException {
        statistic.clear();
        this.deepLearning.getCacheValues().clearNodes(false);
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
        final MCTSNode bestNode = findBestReward(directRoot, currentPossibleMoves, false);
        if (bestNode == null) {
            log.error("!!! no bestnodes found: return random move from the list{}", currentPossibleMoves);
            log.error("!!! MCTSTree nodes:{}",
                    directRoot.getChildsAsCollection().stream().filter(node -> node != null).map(MCTSNode::getMove));
            assert false;
        }
        log.warn("[{}] bestNode: {}", this.getAlliance(), bestNode);
        log.warn("[{}] CacheSize: {} STATS: {}", this.getAlliance(), this.deepLearning.getCacheSize(), statistic);
        log.warn("[{}] WinNodes:{} LooseNodes:{} DrawnNodes:{}",
                this.getAlliance(),
                this.deepLearning.getCacheValues().getWinCacheValue().getNbNodes(),
                this.deepLearning.getCacheValues().getLostCacheValue().getNbNodes(),
                this.deepLearning.getCacheValues().getDrawnCacheValue().getNbNodes());
        log.warn("[{}] nbSearch calls:{} - term:{} ms - speed:{} calls/s visitsRoot:{} visits:{} value:{} reward:{}", this.getAlliance(), nbNumberSearchCalls,
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

    public static double rewardWithLogVisit(final MCTSNode mctsNode) {
        return mctsNode.getExpectedReward(false) + Math.log(1 + Math.sqrt(mctsNode.getVisits()));
    }

    public MCTSNode findBestReward(final MCTSNode opponentNode, final Collection<Move> currentPossibleMoves, boolean withLogVisit) {
        assert !currentPossibleMoves.isEmpty();
        log.warn("[{}] FINDBEST MCTS: {}", this.getAlliance(), opponentNode);
        double maxExpectedReward = Double.NEGATIVE_INFINITY;
        List<MCTSNode> bestNodes = new ArrayList<>();
        List<MCTSNode> initializeNodes = opponentNode.getChildsAsCollection().stream().filter(node -> node != null).collect(Collectors.toList());
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
        float percentGood = (nbBests * 100) / nbChilds;
        log.warn(
                "[{}] State: {}:{}% Step:{} nbChilds:{} nbBests:{} | RetNode:{}",
                getAlliance(), state, percentGood, nbStep, nbChilds, nbBests, ret);
        return ret;
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

    private static Map<Integer, Double> calculatePolicies(final MCTSNode stepNode) {
        final Map<Integer, Double> probabilities = new HashMap<>();
        if (stepNode == null) {
            return probabilities;
        }
        if (!stepNode.isSync()) {
            String msg = String.format("calculatePolicies: root is not sync: %s", stepNode);
            log.error(msg);
            throw new RuntimeException(msg);
        }
        stepNode.getChildNodes()
                .values()
                .stream()
                .filter(childNode -> childNode != null && childNode.node != null)
                .forEach(childNode -> {
                    int index = PolicyUtils.indexFromMove(childNode.node.getMove());
                    double probability = (double) childNode.node.getVisits() / (double) stepNode.getVisits();
                    probabilities.put(index, probability);
                });
        return probabilities;
    }

    private static OneStepRecord createStepTraining(final MCTSGame mctsGame, final Move move,
                                                    final Alliance alliance, final MCTSNode directParent) {
        final InputsFullNN inputs = null; //mctsGame.getInputsManager().createInputs(mctsGame.getLastBoard(), move, alliance);
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

}
