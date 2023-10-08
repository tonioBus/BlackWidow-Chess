package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import com.aquila.chess.strategy.mcts.utils.Statistic;
import com.aquila.chess.utils.DotGenerator;
import com.aquila.chess.utils.Utils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.player.Player;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.aquila.chess.strategy.mcts.MCTSNode.State.*;

@Slf4j
@Getter
public class MCTSSearchWalker implements Callable<Integer> {
    protected static final float WIN_VALUE = 1;
    protected static final float LOOSE_VALUE = -1;
    private static final float DRAWN_VALUE = 0;

    private final int numThread;

    private final int nbSubmit;

    protected final Statistic statistic;
    protected final DeepLearningAGZ deepLearning;
    // protected Game gameOriginal;
    protected final Alliance colorStrategy;
    // private final Game gameOriginal;
    protected final MCTSNode currentRoot;
    private final int nbStep;
    // protected FixMCTSTreeStrategy whiteStrategy;
    // protected FixMCTSTreeStrategy blackStrategy;
    protected final UpdateCpuct updateCpuct;
    protected final Dirichlet updateDirichlet;
    protected final Random rand;
    protected final MCTSGame mctsGame;
    /**
     * this hyperparameter control the exploration inside the system. 1 means no
     * exploration
     */
    protected double cpuct;
    protected boolean isDirichlet;

    public MCTSSearchWalker(
            final int nbStep,
            final int numThread,
            final int nbSubmit,
            final Statistic statistic,
            final DeepLearningAGZ deepLearning,
            final MCTSNode currentRoot,
            final MCTSGame gameRoot,
            final Alliance colorStrategy,
            final UpdateCpuct updateCpuct,
            final Dirichlet updateDirichlet,
            final Random rand) {
        this.nbStep = nbStep;
        this.numThread = numThread;
        this.nbSubmit = nbSubmit;
        this.statistic = statistic;
        this.currentRoot = currentRoot;
        this.deepLearning = deepLearning;
        this.colorStrategy = colorStrategy;
        this.updateCpuct = updateCpuct;
        this.updateDirichlet = updateDirichlet;
        this.rand = rand;
        mctsGame = new MCTSGame(gameRoot);
    }

    @Override
    public Integer call() throws Exception {
        Thread.currentThread().setName(String.format("Worker:%d Submit:%d", numThread, nbSubmit));
        cpuct = updateCpuct.update(mctsGame.getMoves().size());
        SearchResult searchResult = search(currentRoot, 0, true);
        if (searchResult == null) {
            log.debug("[{}] END SEARCH: NULL", nbStep);
        } else {
            log.debug("[{}] END SEARCH: {}", nbStep, searchResult.getLabel());
        }
        getStatistic().nbCalls++;
        log.debug("SEARCH RESULT:{}", searchResult);
        return searchResult.nbSearchCalls;
    }

    protected SearchResult search(final MCTSNode opponentNode, int depth, final boolean isRootNode) throws Exception {
        log.debug("MCTS SEARCH: depth:{} opponentNode:{}", depth, opponentNode);
        final Alliance colorOpponent = opponentNode.getColorState();
        final Alliance color2play = colorOpponent.complementary();

        MCTSNode selectedNode;
        Move selectedMove;
        long key = 0;
        boolean newNodeCreated = false;
        synchronized (opponentNode) {
            if (opponentNode.isLeaf()) {
                log.debug("OPPONENT NODE IS A LEAF: {}", opponentNode);
                return new SearchResult("OPPONENT NODE IS A LEAF NODE", 0);
            }
            log.debug("detectAndCreateLeaf({})", opponentNode);
            int nbCreatedLeafNodes = detectAndCreateLeaf(opponentNode);
            if (nbCreatedLeafNodes < 0) {
                log.debug("DETECTED {} LOOSE LEAF NODES", nbCreatedLeafNodes);
                return new SearchResult("DETECTED LEAF NODES", -(nbCreatedLeafNodes - 1));
            }
            List<Move> looseMoves = opponentNode.getChildsAsCollection().stream().
                    filter(node -> node != null && node.getState() == LOOSE).
                    map(node -> node.getMove()).
                    collect(Collectors.toList());
            if (looseMoves.size() == opponentNode.getChildNodes().size())
                return new SearchResult("DETECTED LEAF NODES", 1);
            selectedMove = selection(opponentNode, isRootNode, depth, looseMoves);
            log.debug("SELECTION: {}", selectedMove);
            if (selectedMove == null) return new SearchResult("NO SELECTION POSSIBLE", 0);
            selectedNode = opponentNode.findChild(selectedMove);
            log.debug("MCTS SEARCH END synchronized 1.0 ({})", opponentNode);

            // expansion
            if (selectedNode == null) {
                key = mctsGame.hashCode(selectedMove.getAllegiance(), selectedMove);
                CacheValue cacheValue = deepLearning.getBatchedValue(key, selectedMove, statistic);
                log.debug("MCTS SEARCH EXPANSION KEY[{}] MOVE:{} CACHE VALUE:{}", key, selectedMove, cacheValue);
                log.debug("BEGIN synchronized 1.1 ({})", opponentNode);
                try {
                    log.debug("MCTS CREATE NODE for MOVE:{}", selectedMove);
                    selectedNode = MCTSNode.createNode(mctsGame.getBoard(), selectedMove, key, cacheValue);
                    opponentNode.addChild(selectedNode);
                    // log.debug("CACHE VALUE ALREADY DEFINED: ADDING NODE TO PROPAGATE:\n{}", selectedNode);
                    // if (cacheValue.isInitialized()) deepLearning.getServiceNN().addNodeToPropagate(selectedNode);
                    assert (selectedNode != opponentNode);
                } catch (Exception e) {
                    log.error(String.format("[%s] [S:%d D:%d] Error during the creation of a new MCTSNode", this.colorStrategy, mctsGame.getNbStep(), depth), e);
                    throw e;
                }
                selectedNode.syncSum();
                log.debug("END synchronized 1.1 ({})", opponentNode);
//                if(selectedNode.isSync()) {
//                    deepLearning.addDefinedNodeToPropagate(selectedNode);
//                }
                newNodeCreated = true;
                // return new SearchResult("CREATED NODE", 1);
            } else {
                log.debug("MCTS SEARCH found child:{} node:{}", selectedMove, selectedNode);
            }
        }
        // evaluate
        selectedNode.incVirtualLoss();
        log.debug("SIMULATE PLAY: {}", selectedMove);
        Game.GameStatus gameStatus = mctsGame.play(opponentNode, selectedMove);
        getStatistic().nbPlay++;
        if (gameStatus != Game.GameStatus.IN_PROGRESS) {
            deepLearning.removeState(mctsGame, color2play, selectedMove);
            selectedNode.decVirtualLoss();
            return returnEndOfSimulatedGame(selectedNode, depth, color2play, selectedMove, gameStatus);
        }
        // if (key != 0 && selectedNode.isSync()) {
        log.debug("ADD NODE TO PROPAGATE: selectedNode:{}", selectedNode);
        log.debug("\tparent:{}", opponentNode);
        this.deepLearning.getServiceNN().addNodeToPropagate(selectedNode);
        // }
        if (newNodeCreated) {
            selectedNode.decVirtualLoss();
            return new SearchResult("CREATED NODE", 1);
        } else if (selectedNode.isLeaf()) {
            selectedNode.decVirtualLoss();
            return new SearchResult("LEAF NODE", 1);
        } else {
            // recursive calls
            SearchResult searchResult = search(selectedNode, depth + 1, false);
            // retro-propagate done in ServiceNN
            selectedNode.decVirtualLoss();
            log.debug("RETRO-PROPAGATION: {}", selectedNode);
            return searchResult;
        }
    }

    /**
     * Create possible WIN / DRAWN / LOST node found as child of the current opponentNode
     *
     * @param opponentNode
     * @return number of detected leaf nodes:
     * <ul>
     *     <li>negative -> loss nodes</li>
     *     <li>0 no -> leaf detected from opponentNode</li>
     *     <li>positive -> WIN or DRAWN nodes</li>
     * </ul>
     */
    private int detectAndCreateLeaf(final MCTSNode opponentNode) {
        if (opponentNode.isContainsChildleaf()) return 0;
        opponentNode.setContainsChildleaf(true);
        if (opponentNode.getChildNodes().size() == 0) return 0;
        if (opponentNode.allChildNodes().stream().filter(node -> node.isLeaf()).count() == opponentNode.getChildNodes().size()) {
            log.warn("[{}] TERMINAL NODE: {}", this.colorStrategy, opponentNode);
            return -1;
        }
        synchronized (opponentNode) {
            final Collection<Move> moves = opponentNode.getChildMoves();
            assert moves.size() > 0;
            final List<Move> movesCreatingLeaf = new ArrayList<>();
            moves.stream().forEach(possibleMove -> {
                final Player childPlayer = possibleMove.execute().currentPlayer();
                final List<Move> currentAllLegalMoves = childPlayer.getLegalMoves(Move.MoveStatus.DONE);
                if (currentAllLegalMoves.isEmpty()) {
                    if (Utils.isDebuggerPresent()) {
                        log.info("[{}] prepareChilds:\n{}", this.colorStrategy, DotGenerator.toString(opponentNode.getRoot(), 5, true));
                    }
                    movesCreatingLeaf.add(possibleMove);
                }
            });
            if (movesCreatingLeaf.isEmpty()) return 0;
            // Loose
            AtomicBoolean stop = new AtomicBoolean(false);
            movesCreatingLeaf.parallelStream().forEach(move -> {
                if (opponentNode.getColorState() == this.colorStrategy && opponentNode.getState() != ROOT) {
                    log.warn("[{}] DETECT LOSS MOVE: {} last:{}", this.colorStrategy, opponentNode, move);
                    stop.set(true);
                }
            });
            if (stop.get()) {
                statistic.nbGoodSelection++;
                int nbRemovedChild = opponentNode.getNumberOfAllNodes();
                log.debug("[{}] CLEAN UP child:{}", this.colorStrategy, nbRemovedChild);
                createLooseNode(opponentNode);
                return -nbRemovedChild;
            }
            final AtomicInteger nbCreatedNodes = new AtomicInteger(0);
            movesCreatingLeaf.parallelStream().forEach(move -> {
                final Player childPlayer = move.execute().currentPlayer();
                if (!childPlayer.isInCheck()) {
                    // PAT
                    nbCreatedNodes.incrementAndGet();
                    createDrawnNode(opponentNode, move, childPlayer);
                } else if (opponentNode.getColorState() != this.colorStrategy) {
                    // WIN
                    nbCreatedNodes.incrementAndGet();
                    createWinNode(opponentNode, move, childPlayer);
                }
            });
            return nbCreatedNodes.get();
        }
    }

    protected MCTSNode createChild(final MCTSNode opponentNode, final Move possibleMove, final MCTSNode.State state) {
        MCTSNode child = opponentNode.findChild(possibleMove);
        if (child == null) {
            switch (state) {
                case LOOSE -> {
                    final CacheValue cacheValue = deepLearning.getCacheValues().getLostCacheValue();
                    child = new MCTSNode(possibleMove, new ArrayList<>(), -1, cacheValue);
                }
                case WIN -> {
                    final CacheValue cacheValue = deepLearning.getCacheValues().getWinCacheValue();
                    child = new MCTSNode(possibleMove, new ArrayList<>(), 1, cacheValue);
                }
                case PAT, REPETITION_X3, REPEAT_50, NOT_ENOUGH_PIECES, NB_MOVES_300 -> {
                    final CacheValue cacheValue = deepLearning.getCacheValues().getDrawnCacheValue();
                    child = new MCTSNode(possibleMove, new ArrayList<>(), 0, cacheValue);
                }
            }
            synchronized (opponentNode.getChildNodes()) {
                if (opponentNode.findChild(possibleMove) == null) {
                    log.debug("[{}] CREATE NEW {} NODE path:{} :{}", this.colorStrategy, state, child.getMovesFromRootAsString(), child.getCacheValue().value);
                    opponentNode.addChild(child);
                }
            }
        } else {
            throw new RuntimeException(String.format("Node can not change status:%s", child));
        }
        return child;
    }

    protected void createDrawnNode(final MCTSNode opponentNode, final Move possibleMove, Player childPlayer) {
        final MCTSNode child = createChild(opponentNode, possibleMove, PAT);
        if (child.getState() != PAT) {
            log.warn("[{}] DETECT DRAWN MOVE {} -> DRAWN-NODE:{}", this.colorStrategy, opponentNode.getMovesFromRootAsString(), possibleMove);
            child.createLeaf(this.deepLearning.getCacheValues().getDrawnCacheValue());
            child.resetExpectedReward(DRAWN_VALUE);
            undoPropagation(child, childPlayer.getAlliance(), child.getMove());
            child.setPropagated(false);
            this.deepLearning.addDefinedNodeToPropagate(child);
        }
    }

    protected void createWinNode(final MCTSNode opponentNode, final Move possibleMove, Player childPlayer) {
        final MCTSNode child = createChild(opponentNode, possibleMove, WIN);
        if (child.getState() != WIN) {
            log.warn("[{}] DETECT WIN MOVE: {} -> WIN-NODE {}", this.colorStrategy, opponentNode.getMovesFromRootAsString(), possibleMove);
            child.createLeaf(this.deepLearning.getCacheValues().getWinCacheValue());
            child.setState(WIN);
            child.resetExpectedReward(WIN_VALUE);
            undoPropagation(child, childPlayer.getAlliance(), child.getMove());
            child.setPropagated(false);
            this.deepLearning.addDefinedNodeToPropagate(child);
        }
    }

    protected void createLooseNode(final MCTSNode opponentNode) {
        if (opponentNode.getState() != LOOSE) {
            log.info("[{}] STOP LOSS NODE {} LOOSE-NODE:{}", this.colorStrategy, opponentNode.getMovesFromRootAsString(), opponentNode);
            opponentNode.createLeaf(this.deepLearning.getCacheValues().getLostCacheValue());
            opponentNode.setPropagated(false);
            opponentNode.setState(LOOSE);
        }
    }

    protected Move selection(final MCTSNode opponentNode, final boolean isRootNode, int depth, List<Move> looseMoves) {
        double maxUcb = Double.NEGATIVE_INFINITY;
        double ucb;
        double policy;
        double exploitation;
        double exploration = 0.0;
        MCTSNode child;

        final Collection<Move> moves = opponentNode.getChildMoves();
        if (log.isDebugEnabled()) {
            log.debug("MOVES:{}", moves.stream().map(move -> move.toString()).collect(Collectors.joining(",")));
            log.debug("graph:-----------------------------------\n{}\n-----------------------------------",
                    DotGenerator.toString(opponentNode.getRoot(), 10, true));
        }
        final List<Move> bestMoves = new ArrayList<>();
        int sumVisits = opponentNode.getVisits();

        String label = String.format("[S:%d|D:%d] ROOT-SELECTION:%s",
                mctsGame.getNbStep(),
                depth,
                opponentNode.getMove() == null ? "Move(null)" : opponentNode.getMove().toString());
        boolean withDirichlet = this.updateDirichlet.update(mctsGame.getNbStep());
        long key = deepLearning.addState(mctsGame,
                label,
                opponentNode,
                statistic);
        final double[] policies = deepLearning.getBatchedPolicies(
                key,
                moves,
                isRootNode & withDirichlet,
                statistic);
        synchronized (moves) {
            for (final Move possibleMove : moves) {
                int childVisits = 0;
                child = opponentNode.findChild(possibleMove);
                if (child == null) {
                    label = String.format("[S:%d|D:%d] PARENT:%s CHILD-SELECTION:%s", mctsGame.getNbStep(), depth, opponentNode.getMove(), possibleMove == null ? "BasicMove(null)" : possibleMove.toString());
                    key = deepLearning.addState(mctsGame, label, possibleMove, statistic);
                    CacheValue cacheValue = deepLearning.getCacheValues().get(key);
                    log.debug("GET CACHE VALUE[key:{}] possibleMove:{}", key, possibleMove);
                    exploitation = cacheValue.getValue();
                } else {
                    if (child.getState() == LOOSE) continue;
                    exploitation = child.getExpectedReward(true);
                    childVisits = child.getVisits();
                }
                log.debug("exploitation({})={}", possibleMove, exploitation);
                // if (sumVisits > 0) {
                policy = policies[PolicyUtils.indexFromMove(possibleMove)];
                if (log.isDebugEnabled()) {
                    log.debug("BATCH deepLearning.getPolicy({})", possibleMove);
                    log.debug("policy:{}", policy);
                }
                exploration = exploration(opponentNode, cpuct, childVisits, policy);
                // }
                ucb = exploitation + exploration;
                if (ucb > maxUcb) {
                    maxUcb = ucb;
                    bestMoves.clear();
                    bestMoves.add(possibleMove);
                } else if (ucb == maxUcb) {
                    bestMoves.add(possibleMove);
                }
            }
        }
        int nbBestMoves = bestMoves.size();
        Move bestMove = null;
        if (nbBestMoves == 1) {
            bestMove = bestMoves.get(0);
            statistic.nbGoodSelection++;
        } else if (nbBestMoves > 1) {

            statistic.nbRandomSelection++;
            statistic.nbRandomSelectionBestMoves += nbBestMoves;
            if (nbBestMoves > statistic.maxRandomSelectionBestMoves)
                statistic.maxRandomSelectionBestMoves = nbBestMoves;
            if (nbBestMoves < statistic.minRandomSelectionBestMoves)
                statistic.minRandomSelectionBestMoves = nbBestMoves;
            //////////////////////////////////////////////////////////////
            bestMove = getRandomMove(bestMoves, looseMoves);
        } else if (nbBestMoves == 0) {
            statistic.nbRandomSelection++;
            bestMove = getRandomMove(moves, looseMoves);
        }
        return bestMove;
    }

    /**
     * From https://colab.research.google.com/github/es2mac/SwiftDigger/blob/master/TetrisField.ipynb
     * @param opponentNode
     * @param cpuct
     * @param visits
     * @param policy
     * @return
     */
    static public double exploration(final MCTSNode opponentNode, double puctConstant, int childVisits, double policy) {
        return policy * puctConstant * Math.sqrt((puctConstant * Math.log(opponentNode.getVisits())) / (1 + childVisits));
//        double cBase = 19652.0;
//        double cInitial = 1.25;
//        double totalN = opponentNode.getNonNullChildsAsCollection().stream().mapToDouble(child1 -> child1.getVisits()).sum();
//        double adjustedTotalN = Double.max(1, totalN - 1);
//        double c = cInitial + Math.log((1 + totalN + cBase) / cBase);
//        return puctConstant * c * policy * Math.sqrt(adjustedTotalN) / (1 + visits);
    }

    public SearchResult returnEndOfSimulatedGame(final MCTSNode node, int depth, final Alliance simulatedPlayerColor, final Move selectedMove, final Game.GameStatus gameStatus) {
        switch (gameStatus) {
            case BLACK_CHESSMATE:
            case WHITE_CHESSMATE:
                if (simulatedPlayerColor == colorStrategy) {
                    if (node.getState() != WIN) {
                        String sequence = sequenceMoves(node);
                        if (log.isDebugEnabled())
                            log.debug("#{} [{} - {}] moves:{} CURRENT COLOR WIN V1", depth, colorStrategy, simulatedPlayerColor,
                                    sequence);
                        undoPropagation(node, simulatedPlayerColor, selectedMove);
                        node.createLeaf(this.deepLearning.getCacheValues().getWinCacheValue());
                        node.setState(WIN);
                        node.resetExpectedReward(WIN_VALUE);
                        node.setPropagated(false);
                    }
                    long key = mctsGame.hashCode(simulatedPlayerColor);
                    this.deepLearning.addDefinedNodeToPropagate(node);
                    return new SearchResult("RETURN WIN NODE", 1);
                } else {
                    if (node.getState() != LOOSE) {
                        String sequence = sequenceMoves(node);
                        if (log.isDebugEnabled())
                            log.debug("#{} [{} - {}] move:{} CURRENT COLOR LOOSE V1", depth, colorStrategy, simulatedPlayerColor,
                                    sequence);
                        undoPropagation(node, simulatedPlayerColor, selectedMove);
                        node.createLeaf(this.deepLearning.getCacheValues().getLostCacheValue());
                        node.setState(LOOSE);
                        node.resetExpectedReward(LOOSE_VALUE);
                        node.setPropagated(false);
                    }
                    long key = mctsGame.hashCode(simulatedPlayerColor);
                    this.deepLearning.addDefinedNodeToPropagate(node);
                    return new SearchResult("RETURN LOOSE NODE", 1);
                }
            case PAT:
                node.setState(PAT);
                break;
            case DRAW_3:
                node.setState(MCTSNode.State.REPETITION_X3);
                break;
            case DRAW_50:
                node.setState(MCTSNode.State.REPEAT_50);
                break;
            case DRAW_NOT_ENOUGH_PIECES:
                node.setState(MCTSNode.State.NOT_ENOUGH_PIECES);
                break;
            case DRAW_300:
                node.setState(MCTSNode.State.NB_MOVES_300);
                break;
            default:
        }
        if (node.getExpectedReward(false) != 0) {
            if (log.isDebugEnabled())
                log.debug("#{} [{} - {}] move:{} {} RETURN: 0", depth, colorStrategy, simulatedPlayerColor, selectedMove,
                        gameStatus);
            undoPropagation(node, simulatedPlayerColor, selectedMove);
            node.resetExpectedReward(DRAWN_VALUE);
            node.createLeaf(deepLearning.getCacheValues().getDrawnCacheValue());
            node.setPropagated(false);
        }
        long key = mctsGame.hashCode(simulatedPlayerColor);
        this.deepLearning.addDefinedNodeToPropagate(node);
        return new SearchResult("RETURN " + node.getState() + " NODE", 1);
    }

    private void undoPropagation(final MCTSNode node) {
        undoPropagation(node, node.getColorState(), node.getMove());
    }

    private void undoPropagation(final MCTSNode node, final Alliance simulatedPlayerColor, final Move selectedMove) {
        if (node.isPropagated()) {
            log.warn("[{}] removePropagation({}) ", this.colorStrategy, node);
            MCTSNode parent = node;
            double value = -node.getCacheValue().getValue();
            do {
                parent = parent.getParent();
                parent.incVirtualLoss();
                parent.unPropagate(value, MCTSNode.PropragateSrc.UN_PROPAGATE, node.getBuildOrder());
                value = -value;
                parent.decVirtualLoss();
            } while (parent.getState() != MCTSNode.State.ROOT);
        } else {
            log.warn("[{}] removeState({}) ", this.colorStrategy, node);
            deepLearning.removeState(mctsGame, simulatedPlayerColor, selectedMove);
            deepLearning.getServiceNN().removeNodeToPropagate(node);
        }
    }

    @AllArgsConstructor
    @Getter
    @ToString
    static public class SearchResult {
        private String label;
        private int nbSearchCalls;
    }

    protected String sequenceMoves(MCTSNode node) {
        if (node == this.currentRoot)
            return "";
        return sequenceMoves(node.getParent()) + " -> " + node;
    }

    /**
     * Return a random move from the given collection of Moves and by removing
     *
     * @param moves
     * @param looseMoves
     * @return
     */
    public Move getRandomMove(final Collection<Move> moves, List<Move> looseMoves) {
        if (looseMoves.size() == 0 || moves.size() <= looseMoves.size()) {
            int size = moves.size();
            if (size == 1) return moves.stream().findFirst().get();
            return moves.stream().skip(rand.nextInt(size - 1)).findFirst().get();
        }
        List<Move> possibleMoves = moves.stream().filter(move -> !looseMoves.contains(move)).collect(Collectors.toList());
        int size = possibleMoves.size();
        if (size == 1) return possibleMoves.stream().findFirst().get();
        return possibleMoves.stream().skip(rand.nextInt(size - 1)).findFirst().get();
    }

    public Statistic getStatistic() {
        return statistic;
    }

}
