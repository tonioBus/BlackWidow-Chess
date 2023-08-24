package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.aquila.chess.strategy.mcts.MCTSNode.State.ROOT;

@Slf4j
public class MCTSNode implements Serializable {

    static private int nbBuild = 0;

    @Getter
    @Setter
    private boolean isPrepared = false;

    @Getter
    public int nbReturn;

    @Getter
    private double virtualLoss = 0.0;

    @Getter
    public final boolean dirichlet;

    @Getter
    private final CacheValue cacheValue;

    @Getter
    private final long key;

    @Getter
    protected transient final Move move;

    @Getter
    private double sum;

    @Getter
    private final transient Thread creator;

    @Getter
    private int visits = 0;

    @Getter
    private transient Piece piece;

    @Getter
    @Setter
    private State state = State.INTERMEDIATE;

    @Getter
    private transient MCTSNode parent;

    @Getter
    @Setter
    public boolean looseOptimise = false;

    @Getter
    @Setter
    private boolean chessMate = false;

    @Getter
    @Setter
    private boolean sync = false;

    @Getter
    protected Alliance colorState;

    @Getter
    private final int buildOrder;

    @Getter
    private final List<PropragateValue> values = new ArrayList<>();

    @Setter
    @Getter
    private boolean propagated = false;

    @Setter
    @Getter
    private int nbPropagationsToExecute = 0;

    @Getter
    private final transient Map<Move, MCTSNode> childNodes = new HashMap<>();

    public static void resetBuildOrder() {
        nbBuild = 0;
    }

    public void incVirtualLoss() {
        this.virtualLoss++;
    }

    public void decVirtualLoss() {
        this.virtualLoss--;
    }

    /**
     * Standard constructor for new MCTSNode
     *
     * @param possibleMove
     * @param cacheValue
     * @return
     */
    public static MCTSNode createNode(Move possibleMove, CacheValue cacheValue) {
        return new MCTSNode(possibleMove, new ArrayList<>(), -1, cacheValue);
    }

    /**
     * Create a node. This method is not responsible to attach this node to his parent
     *
     * @param move       the move this node represent
     * @param rootBoard  the board
     * @param key
     * @param cacheValue
     * @return
     */
    public static MCTSNode createNode(final Board rootBoard, final Move move, final long key, final CacheValue cacheValue) {
        synchronized (cacheValue) {
            final Board selectBoard = move == null ? rootBoard : move.execute();
            final List<Move> childMoves = selectBoard.currentPlayer().getLegalMoves(Move.MoveStatus.DONE);
            return new MCTSNode(move, childMoves, key, cacheValue);
        }
    }

    public static MCTSNode createRootNode(final Board rootBoard, final Move move, final long key, final CacheValue cacheValue) {
        assert move != null;
        synchronized (cacheValue) {
            MCTSNode rootNode;
            Optional<MCTSNode> optRootNode = cacheValue.getNodes().stream().filter(node -> node.getState() == ROOT).findFirst();
            if (optRootNode.isPresent()) {
                rootNode = optRootNode.get();
            } else {
                final List<Move> childMoves = rootBoard.currentPlayer().getLegalMoves(Move.MoveStatus.DONE);
                // rootNode = new MCTSNode(rootBoard.currentPlayer().getAlliance().complementary(), move, childMoves, key, cacheValue);
                rootNode = new MCTSNode(move, childMoves, key, cacheValue);
                rootNode.setAsRoot();
                rootNode.parent = null;
            }
            return rootNode;
        }
    }

    /**
     * @param move
     * @param childMoves
     * @param key
     * @param cacheValue
     */
    private MCTSNode(final Move move, final Collection<Move> childMoves, final long key, final CacheValue cacheValue) {
        this.buildOrder = nbBuild++;
        log.debug("CREATE NODE MOVE:{} key:{}", move, key);
        this.piece = move == null ? null : move.getMovedPiece();
        childMoves.forEach(move1 -> childNodes.put(move1, null));
        this.dirichlet = false;
        this.creator = Thread.currentThread();
        this.key = key;
        this.cacheValue = cacheValue;
        this.cacheValue.addNode(this);
        if (move != null) {
            this.colorState = move.getAllegiance();
            this.move = move;
            this.piece = move.getMovedPiece();
        } else {
            this.move = null;
        }
        this.syncSum();
        if (log.isDebugEnabled())
            log.debug("CREATE NODE[key:{}] -> move:{} cacheValue:{}", key, move, this.getCacheValue());
    }

    /**
     * synchronized the node:
     * If {@link #cacheValue} is initialised
     * <ul>
     *     <li>set {@link #visits} to 0</li>
     *     <li>set {@link #sum} to {@link #getCacheValue()}.value</li>
     *     <li>set {@link #sync} to true</li>
     * </ul>
     *
     * @return true if we are syncing this node, false if already done or cacheValue not initialised yet
     */
    public void syncSum() {
        if (!getCacheValue().isInitialised() || this.isSync()) return;
        this.visits = 0;
        this.sum = 0;
        this.setSync(true);
    }

    /**
     * Update the {@link #sum}, increase the number of {@link #visits}, append this propragation
     * to {@link #values} (used for log and debug)
     *
     * @param value the value used to updateValueAndPolicies the reward
     */
    public int propagate(double value) {
        //FIXME only for display this.values.add(new PropragateValue(value, propragateSrc, buildOrder));
        int nbPropagation;
//        for (nbPropagation = 0; nbPropagation < this.nbPropagationsToExecute; nbPropagation++) {
        this.sum += value;
        this.incVisits();
//        }
        log.info("PROPAGATE DONE: {}", this);
        this.nbPropagationsToExecute = 0;
//        return nbPropagation;
        return 1;
    }

    public void unPropagate(double value, final PropragateSrc propragateSrc, int buildOrder) {
        this.values.add(new PropragateValue(-value, propragateSrc, buildOrder));
        this.sum -= value;
        this.nbPropagationsToExecute--;
        this.decVisits();
        log.info("UN-PROPAGATE DONE[BuildOrder:{}]: {} -> move:{} visits:", this.buildOrder, value, this.move, this.visits);
    }

    public void setAsRoot() {
        log.warn("[{}] SET AS ROOT:{} {}", this.getColorState(), getCacheValue().value, this);
        this.state = ROOT;
        if (this.parent != null) {
            this.parent.clearChildrens();
            this.parent = null;
        }
    }

    private void clearChildrens() {
        for (Move move : this.childNodes.keySet()) {
            MCTSNode oldNode = this.childNodes.replace(move, null);
            if (oldNode != null) oldNode.parent = null;
        }
    }

    private void traverse(Consumer<MCTSNode> consumer) {
        consumer.accept(this);
        this.getChildsAsCollection().forEach(child -> {
            if (child != null) child.traverse(consumer);
        });
    }

    public void incNbReturn() {
        this.nbReturn++;
    }

    /**
     * @return all childs and descendant of child until leaf
     */
    public List<MCTSNode> allChildNodes() {
        List<MCTSNode> allDescendances = new ArrayList<>();
        allChildNodes(this, allDescendances);
        return allDescendances;
    }

    private void allChildNodes(final MCTSNode node, final List<MCTSNode> retNodes) {
        node.getChildsAsCollection().forEach(child -> {
            if (child != null) {
                retNodes.add(child);
                allChildNodes(child, retNodes);
            }
        });
    }

    public int getNumberNodesUntil(final MCTSNode node2optimise) {
        if (this == node2optimise) return 0;
        return 1 + this.parent.getNumberNodesUntil(node2optimise);
    }

    public Collection<Move> getChildMoves() {
        return this.childNodes.keySet();
    }

    public int getNumberOfChilds() {
        return (int) this.childNodes.values().stream().filter(node -> node != null).count();
    }

    public String getMovesFromRootAsString() {
        return this.getMovesFromRoot().stream().map(move -> String.format("%s-%s", move.getAllegiance(), move)).collect(Collectors.joining(","));
    }

    public List<Move> getMovesFromRoot() {
        MCTSNode tmpNode = this;
        final List<Move> ret = new ArrayList<>();
        while (tmpNode != null && tmpNode.state != ROOT) {
            if (tmpNode.move != null) ret.add(tmpNode.move);
            tmpNode = tmpNode.parent;
        }
        Collections.reverse(ret);
        return ret;
    }

    public MCTSNode getRoot() {
        MCTSNode tmpNode = this;
        while (tmpNode.state != ROOT && tmpNode.getParent() != null) {
            tmpNode = tmpNode.parent;
        }
        return tmpNode;
    }

    public Game.GameStatus getStatus(Alliance color2play) {
        return switch (this.getState()) {
            case WIN ->
                    color2play == Alliance.WHITE ? Game.GameStatus.BLACK_CHESSMATE : Game.GameStatus.WHITE_CHESSMATE;
            case LOOSE ->
                    color2play == Alliance.WHITE ? Game.GameStatus.WHITE_CHESSMATE : Game.GameStatus.WHITE_CHESSMATE;
            case PAT -> Game.GameStatus.PAT;
            case NB_MOVES_300 -> Game.GameStatus.DRAW_300;
            case NOT_ENOUGH_PIECES -> Game.GameStatus.DRAW_NOT_ENOUGH_PIECES;
            case REPEAT_50 -> Game.GameStatus.DRAW_50;
            case REPETITION_X3 -> Game.GameStatus.DRAW_3;
            default -> Game.GameStatus.IN_PROGRESS;
        };
    }

    public void incNbPropationsToExecute() {
        this.nbPropagationsToExecute++;
    }

    static public enum PropragateSrc {
        SERVICE_NN("SE"), MCTS("MC"), SAVE_BATCH("SA"), CALL("CA"), UN_PROPAGATE("UP");

        @Getter
        private final String name;

        PropragateSrc(String name) {
            this.name = name;
        }
    }

    void resetExpectedReward(float value) {
        this.cacheValue.value = value;
        this.cacheValue.setInitialised(true);
        syncSum();
        if (log.isDebugEnabled()) log.debug("RESET EXPECTED REWARD DONE: {}", this);
    }

    @AllArgsConstructor
    @Getter
    static public class PropragateValue {
        double value;
        PropragateSrc propragateSrc;
        int buildOrder;
    }

    public double getExpectedReward(boolean withVirtualLoss) {
        syncSum();
        if (this.getVisits() == 0) return this.getCacheValue().value - (withVirtualLoss ? virtualLoss : 0);
        else return (sum - (withVirtualLoss ? virtualLoss : 0)) / this.getVisits();
    }

    public List<MCTSNode> search(final State... states) {
        List<MCTSNode> ret = new ArrayList<>();
        if (Arrays.stream(states).anyMatch(state1 -> state1 == this.state)) ret.add(this);
        this.getChildsAsCollection().forEach(child -> {
            if (child != null) ret.addAll(child.search(states));
        });
        return ret;
    }

    public List<MCTSNode> searchNot(final State... states) {
        List<MCTSNode> ret = new ArrayList<>();
        if (Arrays.stream(states).allMatch(state1 -> state1 != this.state)) ret.add(this);
        this.getChildsAsCollection().forEach(child -> {
            if (child != null) ret.addAll(child.search(states));
        });
        return ret;
    }

    public List<MCTSNode> getNonNullChildsAsCollection() {
        return this.childNodes.values().stream().filter(node -> node != null).collect(Collectors.toList());
    }

    public Collection<MCTSNode> getChildsAsCollection() {
        return this.childNodes.values();
    }

    public MCTSNode findChild(final Move move) {
        if (move == null)
            return null;
        return this.childNodes.get(move);
    }

    /**
     * Add a children node to this
     *
     * @param childNode
     */
    public void addChild(final MCTSNode childNode) {
        assert (childNode.move != null);
        assert (childNode != this);
        final MCTSNode oldChild = this.childNodes.put(childNode.move, childNode);
        if (oldChild != null) {
            log.error("Add a child to a node with already this child set:{}", this);
            log.error("OldChild:{}", oldChild);
            log.error("NewChild:{}", childNode);
            return;
            // throw new RuntimeException("Adding a child to a node that already have this child ");
        }
        if (childNode.parent != null) {
            log.warn("Child already builded:{}", childNode);
            // throw new RuntimeException("child has already a parent");
        }
        childNode.parent = this;
    }

    public static MCTSNode getFirstRoot(final MCTSNode node) {
        if (node.getState() == ROOT) return node;
        return getFirstRoot(node.getParent());
    }

    @Override
    public String toString() {
        return String.format("MCTSNode[%d] -> Move: %s visit:%d expectedReward:%e parent:%b childs:%d nbPropragate:%d state:%s virtual:%f", //
                this.key,
                this.move == null ? "Starting" : this.move, //
                this.visits, //
                this.getExpectedReward(false), //
                this.parent != null, //
                this.childNodes == null ? -1 : this.childNodes.size(), //
                this.nbPropagationsToExecute,
                this.getState(),
                this.getVirtualLoss());
    }

    /**
     * equals method working with MCTSNode and Move
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (obj instanceof Move)
            return equals((Move) obj);
        return equals((MCTSNode) obj);
    }

    /**
     *
     */
    public boolean equals(final Move move) {
        if (move == null) {
            return this.move == null;
        }
        if (this.move == null)
            return false;
        return this.move.equals(move);
    }

    public boolean equals(final MCTSNode mctsNode) {
        return mctsNode.key == key &&
                buildOrder == mctsNode.buildOrder &&
                move == null ? true : move.equals(mctsNode.move);
    }

    public int getNumberAllSubNodes() {
        int subNode = 1;
        for (final MCTSNode node : this.getChildsAsCollection()) {
            if (node != null) {
                subNode += node.getNumberAllSubNodes();
            }
        }
        return subNode;
    }

    public void decVisits() {
        this.visits--;
    }

    public void incVisits() {
        this.visits++;
    }

    public void createLeaf() {
        this.childNodes.clear();
        this.visits = 0;
        this.getCacheValue().setInitialised(true);
    }

    public double getValue() {
        return cacheValue.getValue();
    }

    public enum State {
        ROOT, INTERMEDIATE, WIN, LOOSE, PAT, REPETITION_X3, REPEAT_50, NOT_ENOUGH_PIECES, NB_MOVES_300
    }
}