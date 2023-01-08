package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.FixMCTSTreeStrategy;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

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
        Thread.currentThread().setName(String.format("Worker-%d", numThread));
        // gameOriginal.isInitialPosition();
        final List<Move> selectNodesMoves = this.getPossibleMoves(mctsGame);
        cpuct = updateCpuct.update(mctsGame.getTransitions().size());
        SearchResult searchResult = search(currentRoot, selectNodesMoves, 0, true);
        if (searchResult == null) {
            if (log.isDebugEnabled()) log.debug("[{}] END SEARCH: NULL", nbStep);
        } else {
            if (log.isDebugEnabled())
                log.debug("[{}] END SEARCH: {} <- {}", nbStep, searchResult.getValue(), searchResult.getSource());
        }
        getStatistic().nbCalls++;
        return numThread;
    }

    protected SearchResult search(final MCTSNode opponentNode, final List<Move> moves, int depth, final boolean isRootNode) throws Exception {
        try {
            deepLearning.flushJob(false);
        } catch (ExecutionException e) {
            throw new RuntimeException("Error during last flushJobs", e);
        }
        final FixMCTSTreeStrategy strategy2use = (FixMCTSTreeStrategy) mctsGame.getNextStrategy();
        final Alliance color2play = strategy2use.getAlliance();

        MCTSNode selectedNode;
        Move selectedMove;
        synchronized (opponentNode) {
            if (log.isDebugEnabled()) log.debug("BEGIN synchronized 1.0 ({})", opponentNode);
            selectedMove = selection(strategy2use, opponentNode, moves, isRootNode, depth);
            selectedNode = opponentNode.findChild(selectedMove);
            if (log.isDebugEnabled()) log.debug("END synchronized 1.0 ({})", opponentNode);
            // expansion
            if (selectedNode == null) {
                long key = mctsGame.hashCode(selectedMove.getMovedPiece().getPieceAllegiance(), selectedMove);
                CacheValues.CacheValue cacheValue = deepLearning.getBatchedValue(key, selectedMove, statistic);
                if (log.isDebugEnabled())
                    log.debug("EXPANSION KEY[{}] MOVE:{} CACHEVALUE:{}", key, selectedMove, cacheValue);
                if (log.isDebugEnabled()) log.debug("BEGIN synchronized 1.1 ({})", opponentNode);
                try {
                    selectedNode = MCTSNode.createNode(opponentNode, selectedMove, mctsGame.getBoard(), false, key, cacheValue);
                } catch (Exception e) {
                    log.error(String.format("[S:%d D:%d] Error during the creation of a new MCTSNode", mctsGame.getNbStep(), depth), e);
                    throw e;
                }
                opponentNode.addChild(selectedNode);
                selectedNode.syncSum();
                if (log.isDebugEnabled()) log.debug("END synchronized 1.1 ({})", opponentNode);
                // opponentNode.decVirtualLoss();
                if (!selectedNode.isSync()) {
                    if (log.isDebugEnabled()) log.debug("NOT ADD TO PROPAGATE: selectedNode:{}", selectedNode);
                    if (log.isDebugEnabled()) log.debug("\tparent:{}", opponentNode);
                    this.deepLearning.getServiceNN().addNodeToPropagate(key, selectedNode);
                }
                return null;
            }
            // opponentNode.decVirtualLoss();
        }
        // evaluate
        selectedNode.incVirtualLoss();
        strategy2use.setNextMove(selectedMove);
        Game.GameStatus gameStatus = mctsGame.play();
//            log.info("GAME.PLAY:{}  NB-STEP:{}", selectedMove, game.getNbStep());
        getStatistic().nbPlay++;
        if (gameStatus != Game.GameStatus.IN_PROGRESS) {
            deepLearning.removeState(mctsGame, color2play, selectedMove);
            selectedNode.decVirtualLoss();
            return returnEndOfSimulatedGame(selectedNode, depth, color2play, selectedMove, gameStatus).negate();
            // returnEndOfSimulatedGame(selectedNode, depth, color2play, selectedMove, gameStatus).negate();
        }
        // recursive calls
        // List<Move> selectNodesMoves = this.getPossibleMoves(mctsGame);
        List<Move> selectNodesMoves = selectedNode.getChildMoves();
        SearchResult searchResult = search(selectedNode, selectNodesMoves, depth + 1, false);
        // retro-propagate done in ServiceNN
        selectedNode.decVirtualLoss();
        return null;
    }

    protected Move selection(final FixMCTSTreeStrategy fixMCTSTreeStrategy, final MCTSNode opponentNode, final List<Move> moves, final boolean isRootNode, int depth) throws InterruptedException {
        double maxUcb = Double.NEGATIVE_INFINITY;
        double ucb;
        double policy;
        double exploitation;
        double exploration = 0.0;
        MCTSNode child;
        List<Move> bestMoves = new ArrayList<>();
        int sumVisits = opponentNode.getVisits();

        String label = String.format("[S:%d|D:%d] ROOT-SELECTION:%s", mctsGame.getNbStep(), depth, opponentNode == null ? "ROOT" : opponentNode.getMove() == null ? "Move(null)" : opponentNode.getMove().toString());
        boolean withDirichlet = this.updateDirichlet.update(mctsGame.getNbStep());
        long key;
        key = deepLearning.addState(mctsGame, label, fixMCTSTreeStrategy.getAlliance().complementary()
                , null, isRootNode, isRootNode & withDirichlet, statistic);
        double policies[] = deepLearning.getBatchedPolicies(fixMCTSTreeStrategy.getAlliance().complementary(), key, moves, isRootNode & withDirichlet, statistic);
        Collections.shuffle(moves, rand);
        for (final Move possibleMove : moves) {
            int visits = 0;
            child = opponentNode.findChild(possibleMove);
            if (log.isDebugEnabled())
                log.debug("[{}]  FINDCHILD({}): child={}", nbSubmit, possibleMove, child);
            if (child == null) {
                label = String.format("[S:%d|D:%d] PARENT:%s CHILD-SELECTION:%s", mctsGame.getNbStep(), depth, opponentNode.getMove(), possibleMove == null ? "BasicMove(null)" : possibleMove.toString());
                key = deepLearning.addState(mctsGame, label, possibleMove.getMovedPiece().getPieceAllegiance(), possibleMove, false, false, statistic);
                CacheValues.CacheValue cacheValue = deepLearning.getCacheValues().get(key);
                if (log.isDebugEnabled())
                    log.debug("GET CACHE VALUE[key:{}] possibleMove:{} CACHEVALUE:{}", key, possibleMove, cacheValue);
                exploitation = cacheValue.getValue();
            } else {
                exploitation = child.getExpectedReward(true);
                visits = child.getVisits();
            }
            log.debug("exploitation({})={}", possibleMove, exploitation);
            if (sumVisits > 0) {
                policy = policies[PolicyUtils.indexFromMove(possibleMove)];
                if (log.isDebugEnabled()) log.debug("BATCH deepLearning.getPolicy({})", possibleMove);
                if (log.isDebugEnabled()) log.debug("policy:{}", policy);
                exploration = exploration(opponentNode, cpuct, sumVisits, visits, policy);
            }
            ucb = exploitation + exploration;
            if (ucb > maxUcb) {
                maxUcb = ucb;
                bestMoves.clear();
                bestMoves.add(possibleMove);
            } else if (ucb == maxUcb) {
                bestMoves.add(possibleMove);
            }
        }
        int nbBestMoves = bestMoves.size();
        Move bestMove = null;
        if (nbBestMoves == 1) {
            bestMove = bestMoves.get(0);
            statistic.nbGoodSelection++;
        } else if (nbBestMoves > 1) {
            System.out.print("|" + nbBestMoves);
            statistic.nbRandomSelection++;
            statistic.nbRandomSelectionBestMoves += nbBestMoves;
            if (nbBestMoves > statistic.maxRandomSelectionBestMoves)
                statistic.maxRandomSelectionBestMoves = nbBestMoves;
            if (nbBestMoves < statistic.minRandomSelectionBestMoves)
                statistic.minRandomSelectionBestMoves = nbBestMoves;
            bestMove = getRandomMove(bestMoves);
        } else if (nbBestMoves == 0) {
            bestMove = getRandomMove(moves);
        }
        if (log.isInfoEnabled()) {
            // log.info("SELECTION ret:{} opponent:{} max-ucb:{}", bestMove.toBasicNotation(), opponentNode == null ? "NULL" : opponentNode.getBasicMove(), maxUcb);
        }
        return bestMove;
    }

    static public double exploration(final MCTSNode opponentNode, double cpuct, int sumVisits, int visits, double policy) {
        double cpuctBase = 19652.0;
        double currentCpuct = cpuct + Math.log((opponentNode.getChilds().stream().mapToDouble(child1 -> child1.getVisits()).sum() + 1 + cpuctBase) / cpuctBase);
        return policy * currentCpuct * Math.sqrt(sumVisits) / (1 + visits);
    }

    public SearchResult returnEndOfSimulatedGame(final MCTSNode node, int depth, final Alliance simulatedPlayerColor, final Move selectedMove, final Game.GameStatus gameStatus) {
        switch (gameStatus) {
            case BLACK_CHESSMATE:
            case WHITE_CHESSMATE:
                if (simulatedPlayerColor == colorStrategy) {
                    if (node.getState() != MCTSNode.State.WIN) {
                        String sequence = sequenceMoves(node);
                        if (log.isDebugEnabled())
                            log.debug("#{} [{} - {}] moves:{} CURRENT COLOR WIN V1", depth, colorStrategy, simulatedPlayerColor,
                                    sequence);
                        removePropagation(node, simulatedPlayerColor, selectedMove);
                        node.createLeaf();
                        node.setState(MCTSNode.State.WIN);
                        node.resetExpectedReward(WIN_VALUE);
                        node.getCacheValue().setPropagated(false);
                    }
                    long key = mctsGame.hashCode(simulatedPlayerColor);
                    this.deepLearning.addTerminalNodeToPropagate(key, node);
                    node.incRet();
                    return new SearchResult(node, WIN_VALUE);
                } else {
                    if (node.getState() != MCTSNode.State.LOOSE) {
                        String sequence = sequenceMoves(node);
                        if (log.isDebugEnabled())
                            log.debug("#{} [{} - {}] move:{} CURRENT COLOR LOOSE V1", depth, colorStrategy, simulatedPlayerColor,
                                    sequence);
                        removePropagation(node, simulatedPlayerColor, selectedMove);
                        node.createLeaf();
                        node.setState(MCTSNode.State.LOOSE);
                        node.resetExpectedReward(-LOOSE_VALUE);
                        node.getCacheValue().setPropagated(false);
                    }
                    long key = mctsGame.hashCode(simulatedPlayerColor);
                    this.deepLearning.addTerminalNodeToPropagate(key, node);
                    node.incRet();
                    return new SearchResult(node, -LOOSE_VALUE);
                }
            case PAT:
                node.setState(MCTSNode.State.PAT);
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
            removePropagation(node, simulatedPlayerColor, selectedMove);
            node.resetExpectedReward(DRAWN_VALUE);
            node.createLeaf();
            node.getCacheValue().setPropagated(false);
        }
        long key = mctsGame.hashCode(simulatedPlayerColor);
        this.deepLearning.addTerminalNodeToPropagate(key, node);
        node.incRet();
        return new SearchResult(node, 0);
    }

    private void removePropagation(final MCTSNode node, final Alliance simulatedPlayerColor, final Move selectedMove) {
        MCTSNode parent = node.getParent();
        if (node.getCacheValue().isPropagated()) {
            double value = -node.getCacheValue().getValue();
            while (parent.getCacheValue().getType() != CacheValues.CacheValue.CacheValueType.ROOT) {
                synchronized (parent) {
                    parent.incVirtualLoss();
                    parent.unPropagate(value, MCTSNode.PropragateSrc.UN_PROPAGATE, node.getBuildOrder());
                    value = -value;
                    parent.decVirtualLoss();
                    parent = parent.getParent();
                }
            }
            synchronized (parent) {
                parent.incVirtualLoss();
                parent.unPropagate(value, MCTSNode.PropragateSrc.UN_PROPAGATE, node.getBuildOrder());
                parent.decVirtualLoss();
            }
        } else {
            // parent.incVisits();
            deepLearning.removeState(mctsGame, simulatedPlayerColor, selectedMove);
            deepLearning.getServiceNN().removeNodeToPropagate(node);
        }
    }

    @AllArgsConstructor
    @Getter
    static public class SearchResult {
        private final MCTSNode source;
        private double value;

        public SearchResult negate() {
            this.value = -value;
            return this;
        }
    }

    protected List<Move> getPossibleMoves(final Game game) {
        List<Move> moves = game.getNextPlayer().getLegalMoves(Move.MoveStatus.DONE);
        getStatistic().nbPossibleMoves += moves.size();
        return moves;
    }

    protected String sequenceMoves(MCTSNode node) {
        if (node == this.currentRoot)
            return "";
        return sequenceMoves(node.getParent()) + " -> " + node.toString();
    }

    public Move getRandomMove(final List<Move> bestNodes) {
        Collections.shuffle(bestNodes, rand);
        return bestNodes.get(0);
    }

    public Statistic getStatistic() {
        return statistic;
    }

}
