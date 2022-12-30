package com.aquilla.chess.strategy.mcts;

import com.aquilla.chess.utils.DotGenerator;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
public class MCTSNode implements Serializable {

    static private int nbBuild = 0;

    @Getter
    private double virtualLoss = 0.0;

    @Getter
    private final CacheValues.CacheValue cacheValue;

    @Getter
    private final long key;

    @Getter
    protected transient final Move move;

    @Getter
    protected double sum;
    @Getter
    protected transient Thread creator;

    @Getter
    protected final transient Map<Integer, MCTSNode> childNodes = new ConcurrentHashMap<>();

    @Getter
    private int visits = 0;

    @Getter
    protected transient Piece piece;

    @Getter
    @Setter
    protected State state = State.INTERMEDIATE;

    @Getter
    private transient MCTSNode parent;

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

    public static void resetBuildOrder() {
        nbBuild = 0;
    }

    public synchronized void incVirtualLoss() {
        this.virtualLoss++;
    }

    public synchronized void decVirtualLoss() {
        this.virtualLoss--;
    }

    private MCTSNode(final MCTSNode parent, final Move move, final long key, final CacheValues.CacheValue cacheValue) {
        this.buildOrder = nbBuild++;
        this.parent = parent;
        this.creator = Thread.currentThread();
        if (cacheValue.getNode() != null) {
            // log.error("CONNECTION PROBLEM: graph:{}", DotGenerator.toString(MCTSNode.getPreviousRoot(parent), 10, true));
            throw new RuntimeException(String.format("CONNECTION PROBLEM [%s] ! cacheValue:%s already connected to node:%s",
                    move, cacheValue, cacheValue.getNode()));
        }
        this.key = key;
        this.cacheValue = cacheValue;
        this.cacheValue.setNode(this);
        if (move != null) {
            this.colorState = move.getMovedPiece().getPieceAllegiance();
            this.move = move; //FIXME ? new Move(move);
            this.piece = move.getMovedPiece();
        } else {
            this.move = null;
        }
        this.syncSum();
        if (log.isDebugEnabled())
            log.debug("CREATE NODE[key:{}] -> move:{} cacheValue:{}", key, move, this.getCacheValue());
    }

    public static MCTSNode createNode(final MCTSNode parent, final Move move, final long key, final CacheValues.CacheValue cacheValue) {
        synchronized (cacheValue) {
            MCTSNode ret = cacheValue.getNode();
            if (ret != null) {
                if (log.isDebugEnabled())
                    log.debug("RETURN MADE NODE[key:{}] -> move: {} cacheValue.Move:{}", key, move, ret.getMove());
                if (!ret.getMove().equals(move)) {
                    log.error("[CTX ERROR] createNode(\nparent=[{}],\nmove=[{}],\nkey={},\ncacheValue={})", parent, move, key, cacheValue);
                    log.error("[CTX ERROR] move:{} != cacheValue.move:{}", move.toString(), ret.getMove());
                    log.error(DotGenerator.toString(MCTSNode.getPreviousRoot(parent), 10, true));
                    throw new RuntimeException(String.format("[key:%s] CONNECTION PROBLEM [%s]\n! cacheValue:%s already connected to node:%s",
                            key, move, cacheValue, cacheValue.getNode()));
                }
                return ret;
            }
            return new MCTSNode(parent, move, key, cacheValue);
        }
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
     * @param value         the value used to updateValueAndPolicies the reward
     * @param propragateSrc
     */
    public void propagate(double value, PropragateSrc propragateSrc, int buildOrder) {
        //FIXME this.values.add(new PropragateValue(value, propragateSrc, buildOrder));
        this.sum += value;
        this.incVisits();
        if (log.isDebugEnabled())
            log.debug("PROPAGATE DONE[BuildOrder:{}]: {} -> move:{} visits:", this.buildOrder, value, this.move, this.visits);
    }

    public void unPropagate(double value, PropragateSrc propragateSrc, int buildOrder) {
        this.values.add(new PropragateValue(-value, propragateSrc, buildOrder));
        this.sum -= value;
        this.decVisits();
        if (log.isDebugEnabled())
            log.debug("UN-PROPAGATE DONE[BuildOrder:{}]: {} -> move:{} visits:", this.buildOrder, value, this.move, this.visits);
    }

    private static MCTSNode getPreviousRoot(final MCTSNode nodeP) {
        MCTSNode node = nodeP;
        while (node.getParent() != null) {
            node = node.getParent();
            if (node.getCacheValue() != null && node.getCacheValue().getType() == CacheValues.CacheValue.CacheValueType.ROOT)
                break;
        }
        return node;
    }

    public void setAsRoot() {
        log.warn("[{}] SET AS ROOT:{}", this.getColorState(), this);
        getCacheValue().setAsRoot();
        this.state = State.ROOT;
        if (this.parent != null) {
            this.parent.getChilds().clear();
            this.parent = null;
        }
    }

    private void traverse(Consumer<MCTSNode> consumer) {
        consumer.accept(this);
        this.getChilds().forEach(child -> {
            child.traverse(consumer);
        });
    }

    static public enum PropragateSrc {
        SERVICE_NN("SE"), MCTS("MC"), SAVE_BATCH("SA"), CALL("CA"), UN_PROPAGATE("UP");

        @Getter
        private final String name;

        PropragateSrc(String name) {
            this.name = name;
        }
    }

    void resetExpectedReward(double value) {
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

    public List<MCTSNode> search(final State state) {
        List<MCTSNode> ret = new ArrayList<>();
        if (this.state == state) ret.add(this);
        this.getChilds().forEach(child -> {
            log.info("child:{} state:{}", child.move, child.state);
            ret.addAll(child.search(state));
        });
        return ret;
    }

    /**
     * @return the childNodes
     */
    public List<MCTSNode> getChilds() {
        return new ArrayList<>(childNodes.values());
    }

    public MCTSNode findChild(final Move move) {
        if (move == null)
            return null;
        return this.childNodes.get(move.hashCode());
    }

    public void addChild(final MCTSNode node) {
        if (node.getParent() != this) {
            log.warn("node.parent: {}", DotGenerator.toString(node.getParent(), 10, true));
            log.warn("this: {}", DotGenerator.toString(this, 10, true));
            String msg = String.format("NODE-PARENT:%s <>\nCURRENT-PARENT: %s", node.getParent(), this);
            log.error(msg);
            throw new RuntimeException(msg);
        }
        this.childNodes.put(node.move.hashCode(), node);
        node.parent = this;
    }

    @Override
    public String toString() {
        return String.format("MCTSNode[%d] -> Move: %s visit:%d expectedReward:%e parent:%b childs:%d state:%s virtual:%f", //
                this.key,
                this.move == null ? "Starting" : this.move, //
                this.visits, //
                this.getExpectedReward(false), //
                this.parent != null, //
                this.childNodes == null ? -1 : this.childNodes.size(), //
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
        if (move == null) {
            return mctsNode.move == null;
        }
        return move.equals(mctsNode.move);
    }

    public int getNumberAllSubNodes() {
        int subNode = 1;
        for (final MCTSNode node : this.childNodes.values()) {
            subNode += node.getNumberAllSubNodes();
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
        this.getCacheValue().setAsLeaf();
        this.getCacheValue().setInitialised(true);
    }

    public double getValue() {
        return cacheValue.getValue();
    }

    public double getPolicy() {
        return cacheValue.getPolicies()[PolicyUtils.indexFromMove(this.move, this.piece)];
    }

    public enum State {
        ROOT, INTERMEDIATE, WIN, LOOSE, PAT, REPETITION_X3, REPEAT_50, NOT_ENOUGH_PIECES, NB_MOVES_300
    }
}