package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.config.MCTSConfig;
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
import java.util.stream.Collectors;

import static com.aquila.chess.strategy.mcts.MCTSNode.State.*;

@Slf4j
@Getter
public class MCTSSearchWalker implements Callable<Integer> {
    protected static final double WIN_VALUE = 1;
    protected static final double LOOSE_VALUE = -1;
    private static final double DRAWN_VALUE = 0;

    private final int numThread;

    private final int nbSubmit;

    protected final Statistic statistic;
    protected final DeepLearningAGZ deepLearning;
    protected final Alliance colorStrategy;
    protected final MCTSNode currentRoot;
    private final int nbStep;
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
        SearchResult searchResult = search(currentRoot, 0);
        if (searchResult == null) {
            log.debug("[{}] END SEARCH: NULL", nbStep);
        } else {
            log.debug("[{}] END SEARCH: {}", nbStep, searchResult.getLabel());
        }
        getStatistic().nbCalls++;
        log.debug("SEARCH RESULT:{}", searchResult);
        return searchResult.nbSearchCalls;
    }

    protected SearchResult search(final MCTSNode opponentNode, int depth) throws Exception {
        log.debug("MCTS SEARCH: depth:{} opponentNode:{}", depth, opponentNode);
        final Alliance colorOpponent = opponentNode.getColorState();
        final Alliance moveColor = colorOpponent.complementary();

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
                    map(MCTSNode::getMove).
                    collect(Collectors.toList());
            if (looseMoves.size() == opponentNode.getChildNodes().size())
                return new SearchResult("DETECTED LEAF NODES", 1);
            selectedMove = selection(opponentNode, depth, looseMoves);
            log.debug("SELECTION: {}", selectedMove);
            if (selectedMove == null) return new SearchResult("NO SELECTION POSSIBLE", 0);
            selectedNode = opponentNode.findChild(selectedMove);
            log.debug("MCTS SEARCH END synchronized 1.0 ({})", opponentNode);

            // expansion
            if (selectedNode == null) {
                key = mctsGame.hashCode(selectedMove);
                CacheValue cacheValue = deepLearning.getBatchedValue(key, selectedMove, statistic);
                log.debug("MCTS SEARCH EXPANSION KEY[{}] MOVE:{} CACHE VALUE:{}", key, selectedMove, cacheValue);
                log.debug("BEGIN synchronized 1.1 ({})", opponentNode);
                try {
                    MCTSNodePath path = new MCTSNodePath(opponentNode.getPathFromRoot(), selectedMove);
                    log.debug("EXPANSION MCTS CREATE NODE for PATH:{}", path);
                    selectedNode = cacheValue.getNode(path);
                    if (selectedNode == null) {
                        selectedNode = MCTSNode.createNode(mctsGame.getBoard(), null, selectedMove, key, cacheValue);
                        opponentNode.addChild(selectedNode);
                        selectedNode.updateCache();
                    }
                    assert (selectedNode != opponentNode);
                } catch (Exception e) {
                    log.error(String.format("[%s] [S:%d D:%d] Error during the creation of a new MCTSNode", this.colorStrategy, mctsGame.getNbStep(), depth), e);
                    throw e;
                }
                selectedNode.syncSum();
                log.debug("END synchronized 1.1 ({})", opponentNode);
                newNodeCreated = true;
            } else {
                log.debug("MCTS SEARCH found child:{} node:{}", selectedMove, selectedNode);
            }
        }
        // evaluate
        selectedNode.incVirtualLoss();
        log.debug("SIMULATE PLAY: {}", selectedMove);
        Game.GameStatus gameStatus = mctsGame.play(selectedMove);
        getStatistic().nbPlay++;
        if (gameStatus != Game.GameStatus.IN_PROGRESS) {
            deepLearning.removeState(mctsGame, moveColor, selectedMove);
            selectedNode.decVirtualLoss();
            return returnEndOfSimulatedGame(selectedNode, depth, moveColor, selectedMove, gameStatus);
        }
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
            SearchResult searchResult = search(selectedNode, depth + 1);
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
        if (opponentNode.getChildNodes().isEmpty()) return 0;
        if (opponentNode.allChildNodes().stream().filter(MCTSNode::isLeaf).count() == opponentNode.getChildNodes().size()) {
            log.warn("[{}] TERMINAL NODE: {}", this.colorStrategy, opponentNode);
            return -1;
        }
        synchronized (opponentNode) {
            final Collection<Move> moves = opponentNode.getChildMoves();
            assert moves.size() > 0;
            final List<Move> movesCreatingLeaf = new ArrayList<>();
            for (Move possibleMove : moves) {
                final Player childPlayer = possibleMove.execute().currentPlayer();
                final List<Move> currentAllLegalMoves = childPlayer.getLegalMoves(Move.MoveStatus.DONE);
                if (currentAllLegalMoves.isEmpty()) {
                    if (Utils.isDebuggerPresent()) {
                        log.info("[{}] prepareChilds:\n{}", this.colorStrategy, DotGenerator.toString(opponentNode.getRoot(), 5, true));
                    }
                    movesCreatingLeaf.add(possibleMove);
                }
            }
            if (movesCreatingLeaf.isEmpty()) return 0;
            // Loose
            boolean stop = false;
            for (Move move : movesCreatingLeaf) {
                if (opponentNode.getColorState() == this.colorStrategy && opponentNode.getState() != ROOT) {
                    log.warn("[{}] DETECT LOSS MOVE: {} last:{}", this.colorStrategy, opponentNode, move);
                    stop = true;
                    break;
                }
            }
            if (stop) {
                statistic.nbGoodSelection++;
                int nbRemovedChild = opponentNode.getNumberOfAllNodes();
                log.debug("[{}] CLEAN UP child:{}", this.colorStrategy, nbRemovedChild);
                createLooseNode(opponentNode);
                return -nbRemovedChild;
            }
            int nbCreatedNodes = 0;
            for (Move move : movesCreatingLeaf) {
                final Player childPlayer = move.execute().currentPlayer();
                if (!childPlayer.isInCheck()) {
                    // PAT
                    nbCreatedNodes++;
                    createDrawnNode(opponentNode, move);
                } else if (opponentNode.getColorState() != this.colorStrategy) {
                    // WIN
                    nbCreatedNodes++;
                    createWinNode(opponentNode, move);
                }
            }
            return nbCreatedNodes;
        }
    }

    protected MCTSNode createStopLeafChild(final MCTSNode opponentNode, final Move possibleMove, final MCTSNode.State state) {
        assert state != LOOSE;
        MCTSNode child = opponentNode.findChild(possibleMove);
        if (child == null) {
            switch (state) {
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
                    log.debug("[{}] CREATE NEW {} NODE path:{} :{}", this.colorStrategy, state, child.getMovesFromRootAsString(), child.getCacheValue().getValue());
                    opponentNode.addChild(child);
                }
            }
            child.updateCache();
            child.createLeaf(null);
        } else {
            throw new RuntimeException(String.format("Node can not change status:%s", child));
        }
        return child;
    }

    protected void createDrawnNode(final MCTSNode opponentNode, final Move possibleMove) {
        final MCTSNode child = createStopLeafChild(opponentNode, possibleMove, PAT);
        if (child.getState() != PAT) {
            log.info("[{}] DETECT DRAWN MOVE {} -> DRAWN-NODE:{} OLD_VALUE:{}", this.colorStrategy, opponentNode.getMovesFromRootAsString(), possibleMove, child.getCacheValue().getValue());
            child.setState(PAT);
            undoPropagation(child, child.move.getAllegiance(), child.getMove());
            child.setPropagated(false);
            this.deepLearning.addDefinedNodeToPropagate(child);
        }
    }

    protected void createWinNode(final MCTSNode opponentNode, final Move possibleMove) {
        final MCTSNode child = createStopLeafChild(opponentNode, possibleMove, WIN);
        if (child.getState() != WIN) {
            log.info("[{}] DETECT WIN MOVE: {} -> WIN-NODE {} OLD_VALUE:{}", this.colorStrategy, opponentNode.getMovesFromRootAsString(), possibleMove, child.getCacheValue().getValue());
            child.setState(WIN);
            undoPropagation(child, child.move.getAllegiance(), child.getMove());
            child.setPropagated(false);
            this.deepLearning.addDefinedNodeToPropagate(child);
        }
    }

    protected void createLooseNode(final MCTSNode opponentNode) {
        if (opponentNode.getState() != LOOSE) {
            log.info("[{}] STOP LOSS NODE {} LOOSE-NODE:{} OLD_VALUE:{}", this.colorStrategy, opponentNode.getMovesFromRootAsString(), opponentNode, opponentNode.getCacheValue().getValue());
            undoPropagation(opponentNode, opponentNode.getColorState(), opponentNode.getMove());
            opponentNode.createLeaf(deepLearning.getCacheValues().getLostCacheValue());
            opponentNode.setPropagated(false);
            opponentNode.setState(LOOSE);
            this.deepLearning.addDefinedNodeToPropagate(opponentNode);
        }
    }

    protected Move selection(final MCTSNode opponentNode, int depth, List<Move> looseMoves) {
        double maxUcb = Double.NEGATIVE_INFINITY;
        double ucb;
        double policy;
        double exploitation;
        double exploration;
        MCTSNode child;

        final Collection<Move> moves = opponentNode.getChildMoves();
        cpuct = updateCpuct.update(mctsGame.getMoves().size(), moves.size());
        if (log.isDebugEnabled()) {
            log.debug("MOVES:{}", moves.stream().map(move -> move.toString()).collect(Collectors.joining(",")));
            log.debug("graph:-----------------------------------\n{}\n-----------------------------------",
                    DotGenerator.toString(opponentNode.getRoot(), 10, true));
        }
        final List<Move> bestMoves = new ArrayList<>();

        String label;
        synchronized (moves) {
            for (final Move possibleMove : moves) {
                int childVisits = 0;
                final MCTSNode.ChildNode childNode = opponentNode.findChildNode(possibleMove);
                if (childNode == null || childNode.node == null) {
                    label = String.format("[S:%d|D:%d] PARENT:%s CHILD-SELECTION:%s", mctsGame.getNbStep(), depth, opponentNode.getMove(), possibleMove == null ? "BasicMove(null)" : possibleMove.toString());
                    MCTSNode parentOpponentNode = opponentNode.getParent();
                    double initValue = parentOpponentNode == null ?
                            opponentNode.getExpectedReward(false) :
                            parentOpponentNode.getExpectedReward(false);
                    initValue -= MCTSConfig.mctsConfig.getFpuReduction();
                    long key = deepLearning.addState(mctsGame, label, initValue, possibleMove, statistic);
                    CacheValue cacheValue = deepLearning.getCacheValues().get(key);
                    log.debug("GET CACHE VALUE[key:{}] possibleMove:{}", key, possibleMove);
                    exploitation = cacheValue.getValue();
                } else {
                    child = childNode.node;
                    if (child.getState() == LOOSE) continue;
                    exploitation = child.getExpectedReward(true);
                    childVisits = child.getVisits();
                }
                log.debug("exploitation({})={}", possibleMove, exploitation);
                policy = childNode == null ? 0 : childNode.policy;
                if (log.isDebugEnabled()) {
                    log.debug("BATCH deepLearning.getPolicy({})", possibleMove);
                    log.debug("policy:{}", policy);
                }
                exploration = exploration(opponentNode, cpuct, childVisits, policy);
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
            bestMove = getRandomMove(bestMoves, looseMoves);
        } else if (nbBestMoves == 0) {
            statistic.nbRandomSelection++;
            bestMove = getRandomMove(moves, looseMoves);
        }
        return bestMove;
    }

    /**
     * From https://colab.research.google.com/github/es2mac/SwiftDigger/blob/master/TetrisField.ipynb
     *
     * @param opponentNode
     * @param cpuct
     * @param childVisits
     * @param policy
     * @return
     */
    static public double exploration(final MCTSNode opponentNode, double cpuct, int childVisits, double policy) {
        return cpuct * policy * Math.sqrt(opponentNode.getVisits()) / (1 + childVisits);
    }

    public SearchResult returnEndOfSimulatedGame(final MCTSNode node,
                                                 int depth,
                                                 final Alliance simulatedPlayerColor,
                                                 final Move selectedMove,
                                                 final Game.GameStatus gameStatus) {
        switch (gameStatus) {
            case BLACK_CHESSMATE:
            case WHITE_CHESSMATE:
                if (simulatedPlayerColor == colorStrategy) {
                    if (node.getState() != WIN) {
                        if (log.isDebugEnabled()) {
                            String sequence = sequenceMoves(node);
                            log.debug("#{} [{} - {}] moves:{} CURRENT COLOR WIN V1", depth, colorStrategy, simulatedPlayerColor,
                                    sequence);
                        }
                        undoPropagation(node, simulatedPlayerColor, selectedMove);
                        node.createLeaf(this.deepLearning.getCacheValues().getWinCacheValue());
                        node.setState(WIN);
                        node.setPropagated(false);
                    } else node.incNbPropationsToExecute();
                    this.deepLearning.addDefinedNodeToPropagate(node);
                    return new SearchResult("RETURN WIN NODE", 1);
                } else {
                    if (node.getState() != LOOSE) {
                        if (log.isDebugEnabled()) {
                            String sequence = sequenceMoves(node);
                            log.debug("#{} [{} - {}] move:{} CURRENT COLOR LOOSE V1", depth, colorStrategy, simulatedPlayerColor,
                                    sequence);
                        }
                        undoPropagation(node, simulatedPlayerColor, selectedMove);
                        node.createLeaf(this.deepLearning.getCacheValues().getLostCacheValue());
                        node.setState(LOOSE);
                        node.setPropagated(false);
                    } else node.incNbPropationsToExecute();
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
            case DRAW_TOO_MUCH_STEPS:
                node.setState(MCTSNode.State.NB_MOVES_300);
                break;
            default:
        }
        if (node.getExpectedReward(false) != 0) {
            if (log.isDebugEnabled())
                log.debug("#{} [{} - {}] move:{} {} RETURN: 0", depth, colorStrategy, simulatedPlayerColor, selectedMove,
                        gameStatus);
            undoPropagation(node, simulatedPlayerColor, selectedMove);
            node.createLeaf(deepLearning.getCacheValues().getDrawnCacheValue());
            node.setPropagated(false);
        } else node.incNbPropationsToExecute();
        this.deepLearning.addDefinedNodeToPropagate(node);
        return new SearchResult("RETURN " + node.getState() + " NODE", 1);
    }

    private void undoPropagation(final MCTSNode node) {
        undoPropagation(node, node.getColorState(), node.getMove());
    }

    private void undoPropagation(final MCTSNode node, final Alliance simulatedPlayerColor, final Move selectedMove) {
        if (node.isPropagated()) {
            log.debug("[{}] removePropagation({}) ", this.colorStrategy, node);
            MCTSNode parent = node;
            double value = -node.getCacheValue().getValue();
            do {
                parent = parent.getParent();
                parent.incVirtualLoss();
                parent.unPropagate(value);
                value = -value;
                parent.decVirtualLoss();
            } while (parent.getState() != MCTSNode.State.ROOT);
        } else {
            log.debug("[{}] removeState({}) ", this.colorStrategy, node);
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
