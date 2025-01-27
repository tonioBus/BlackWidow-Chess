package com.aquila.chess.strategy.mcts;

import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.aquila.chess.strategy.mcts.MCTSNode.State.ROOT;

@Slf4j
public class MCTSNode {

    static private int nbBuild = 0;

    @Getter
    @Setter
    private boolean leaf = false;

    @Getter
    @Setter
    private boolean containsChildleaf = false;

    @Getter
    private double virtualLoss = 0.0;

    @Getter
    public boolean dirichletDone;

    @Getter
    private CacheValue cacheValue;

    @Getter
    private final long key;

    @Getter
    protected transient final Move move;

    @Getter
    private double sum;

    @Getter
    private final transient Thread creator;

    @Getter
    @Setter
    private int visits = 0;

    @Getter
    @Setter
    private State state = State.INTERMEDIATE;

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

    @Setter
    @Getter
    private boolean propagated = false;

    @Setter
    @Getter
    private int nbPropagationsToExecute = 0;

    @Getter
    private final transient Map<Move, ChildNode> childNodes = new HashMap<>();

    @Getter
    static public class ChildNode {
        MCTSNode node = null;
        double policy = 0.0;

        public ChildNode() {
        }

        public ChildNode(MCTSNode node) {
            this.policy = 0.314;
            this.node = node;
        }
    }

    public static void resetBuildOrder() {
        nbBuild = 0;
    }

    // synchronized
    public void incVirtualLoss() {
        this.virtualLoss++;
    }

    // synchronized
    public void decVirtualLoss() {
        this.virtualLoss--;
    }

    /**
     * Create a node. This method is not responsible to attach this node to his parent
     *
     * @param rootBoard     the board
     * @param possibleMoves
     * @param move          the move this node represent
     * @param key
     * @param cacheValue
     * @return
     */
    public static MCTSNode createNode(final Board rootBoard, final List<Move> possibleMoves, final Move move, final long key, final CacheValue cacheValue) {
        synchronized (cacheValue) {
            if (possibleMoves == null) {
                final Board selectBoard = move == null ? rootBoard : move.execute();
                final List<Move> childMoves = selectBoard.currentPlayer().getLegalMoves(Move.MoveStatus.DONE);
                return new MCTSNode(move, childMoves, key, cacheValue);
            } else
                return new MCTSNode(move, possibleMoves, key, cacheValue);
        }
    }

    public static MCTSNode createRootNode(final List<Move> childMoves, final Move move, final long boardKey, final CacheValue cacheValue) {
        assert move != null;
        log.debug("createRootNode move:{} cacheValue:{}", move, cacheValue);
        synchronized (cacheValue) {
            MCTSNode rootNode;
            Optional<MCTSNode> optRootNode = cacheValue.getRootNode();
            if (optRootNode.isPresent()) {
                rootNode = optRootNode.get();
            } else {
                if (cacheValue.isNodesEmpty()) {
                    rootNode = new MCTSNode(move, childMoves, boardKey, cacheValue);
                } else {
                    MCTSNode cacheValueNode;
                    List<MCTSNode> cacheValueNodes = cacheValue.getAllMCTSNodes().stream().filter(node -> node.getMove().equals(move)).collect(Collectors.toList());
                    switch (cacheValueNodes.size()) {
                        case 0 -> {
                            cacheValueNode = cacheValue.getFirstNode().get();
                            log.error("no node with move:{} found in:{}", move, cacheValueNode);
                        }
                        case 1 -> {
                            cacheValueNode = cacheValueNodes.get(0);
                        }
                        default -> {
                            log.error("{} nodes has the move:{} in cacheValue:{}", move, cacheValue);
                            cacheValueNode = cacheValueNodes.get(0);
                        }
                    }
                    if (!cacheValueNode.getMove().equals(move)) {
                        log.error("cacheValueNode:{} != move:{}", cacheValueNode, move);
                    }
                    rootNode = cacheValueNode;
                }
                log.info("CreateRootNode: rootNode:{} cacheValue.nbNodes:{} cacheValue:{}", rootNode, cacheValue.getNbNodes(), cacheValue);
                rootNode.setAsRoot();
                rootNode.parent = null;
            }
            return rootNode;
        }
    }

    /**
     * To call only if cacheValue does not already contains child
     *
     * @param move
     * @param childMoves
     * @param key
     * @param cacheValue
     */
    MCTSNode(final Move move, final Collection<Move> childMoves, final long key, final CacheValue cacheValue) {
        this.buildOrder = nbBuild++;
        log.debug("CREATE NODE MOVE:{} key:{}", move, key);
        childMoves.forEach(move1 -> childNodes.put(move1, null));
        this.dirichletDone = false;
        this.creator = Thread.currentThread();
        this.key = key;
        this.cacheValue = cacheValue;
        if (move != null) {
            this.colorState = move.getAllegiance();
            this.move = move;
        } else {
            this.move = null;
        }
        this.syncSum();
        log.debug("CREATE NODE[key:{}] -> move:{} cacheValue:{}", key, move, this.getCacheValue());
    }

    public void updateCache() {
        this.cacheValue.addNode(this);
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
        if (!getCacheValue().isInitialized() || this.isSync()) return;
        this.visits = 0;
        this.sum = getCacheValue().getValue();
        this.setSync(true);
    }

    /**
     * Update the {@link #sum}, increase the number of {@link #visits}, append this propragation
     * to {@link #values} (used for log and debug)
     *
     * @param value the value used to updateValueAndPolicies the reward
     */
    public int propagateOneTime(double value) {
        if (this.visits == 0) this.sum = value;
        else this.sum += value;
        this.incVisits();
        log.debug("PROPAGATE ({}) DONE: {}", this.nbPropagationsToExecute, this);
        this.nbPropagationsToExecute = 0;
        return 1;
    }

    public void unPropagate(double value) {
        this.sum -= value;
        this.nbPropagationsToExecute--;
        this.decVisits();
        log.debug("UN-PROPAGATE DONE[BuildOrder:{}]: {} -> move:{} visits:", this.buildOrder, value, this.move, this.visits);
    }

    public void setAsRoot() {
        log.warn("[{}] SET AS ROOT:{} {}", this.getColorState(), getCacheValue().getValue(), this);
        this.state = ROOT;
        if (this.parent != null) {
            this.parent.clearChildrens();
            this.parent = null;
        }
        if (this.isSync()) {
            log.warn("nodes already sync :{}", this);
            this.getCacheValue().normalizePolicies();
        }
    }

    private void clearChildrens() {
        for (Move move : this.childNodes.keySet()) {
            ChildNode chilNode = this.childNodes.replace(move, null);
            MCTSNode oldNode = chilNode.node;
            if (oldNode != null) oldNode.parent = null;
        }
    }

    private void traverse(Consumer<MCTSNode> consumer) {
        consumer.accept(this);
        this.getChildsAsCollection().forEach(child -> {
            if (child != null) child.traverse(consumer);
        });
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

    public MCTSNodePath getPathFromRoot() {
        return new MCTSNodePath(getMovesFromRoot());
    }

    public String getMovesFromRootAsString() {
        return getPathFromRoot().toString();
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

    public void incNbPropationsToExecute() {
        if (isLeaf()) {
            this.nbPropagationsToExecute++;
        } else {
            this.nbPropagationsToExecute = 1;
        }
    }


    public enum PropragateSrc {
        SERVICE_NN("SE"), MCTS("MC"), SAVE_BATCH("SA"), CALL("CA"), UN_PROPAGATE("UP");

        @Getter
        private final String name;

        PropragateSrc(String name) {
            this.name = name;
        }
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
        if (this.getVisits() == 0) return this.getCacheValue().getValue() - (withVirtualLoss ? virtualLoss : 0);
        else return (sum - (withVirtualLoss ? virtualLoss : 0)) / (this.getVisits());
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
        synchronized (childNodes) {
            return this.childNodes
                    .values()
                    .stream()
                    .filter(childNode -> childNode != null)
                    .map(childNode -> childNode.node)
                    .filter(node -> node != null)
                    .collect(Collectors.toList());
        }
    }

    public Collection<MCTSNode> getChildsAsCollection() {
        synchronized (childNodes) {
            return this.childNodes
                    .values()
                    .stream()
                    .filter(childNode -> childNode != null)
                    .map(childNode -> childNode.node)
                    .collect(Collectors.toList());
        }
    }

    public ChildNode findChildNode(final Move move) {
        if (move == null)
            return null;
        return this.childNodes.get(move);
    }

    public MCTSNode findChild(final Move move) {
        ChildNode childNode = this.findChildNode(move);
        if (childNode == null) return null;
        return childNode.node;
    }

    /**
     * Add a children node to the current node
     *
     * @param node
     */
    public void addChild(final MCTSNode node) {
        assert (node.move != null);
        assert (node != this);
        ChildNode oldNode = this.childNodes.get(node.move);
        if (oldNode != null) {
            log.debug("addChild() NEW node.move:{} hashCode:{}\nnode:{}", node.move, node.move.hashCode(), node);
            if (oldNode.node == null) oldNode.node = node;
            else throw new RuntimeException("Should not happen");
        } else {
            ChildNode newChildNode = new ChildNode(node);
            ChildNode oldChildNode = this.childNodes.put(node.move, newChildNode);
            if (oldChildNode != null && oldChildNode.node != null) {
                log.error("Add a child to a node with already this child set:{}", this);
                log.error("OldChild:{}", oldChildNode.node);
                log.error("NewChild:{}", node);
                // return;
            }
        }
        if (node.parent != null) {
            log.warn("Child already builded:{}", node);
        }
        node.parent = this;
    }

    public static MCTSNode getFirstRoot(final MCTSNode node) {
        if (node.getState() == ROOT) return node;
        return getFirstRoot(node.getParent());
    }

    @Override
    public String toString() {
        return String.format("MCTSNode[%d] -> Path:%s Move:%s Color:%s leaf:%b visit:%d expectedReward:%e value:%e parent:%b childs:%d nbPropragate:%d state:%s virtual:%f", //
                this.key,
                this.getMovesFromRootAsString(),
                this.move == null ? "Starting" : this.move, //
                this.move == null ? "N / A" : this.move.getAllegiance(),
                this.leaf,
                this.visits, //
                this.getExpectedReward(true), //
                this.getCacheValue() == null ? -1 : this.getCacheValue().getValue(),
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
        return this.move.toString().equals(move.toString());
    }

    public boolean equals(final MCTSNode mctsNode) {
        boolean ret = mctsNode.key == key &&
                buildOrder == mctsNode.buildOrder;
        if (move == null || mctsNode.move == null) {
            return ret && move == mctsNode.move;
        }
        return ret && move.toString().equals(mctsNode.move.toString());
    }

    /**
     * @return the number of sub nodes including the current node
     */
    public int getNumberOfAllNodes() {
        int subNode = 1;
        for (final MCTSNode node : this.getChildsAsCollection()) {
            if (node != null) {
                subNode += node.getNumberOfAllNodes();
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

    /**
     * Create a leaf using one the the 3 possible LEAF cache value: WIN, LOST, DRAWN
     * @return the nodes to <strong>NOT</strong> propagate as they will be removed
     */
    public void createLeaf(final CacheValue cacheValue) {
        this.childNodes.clear();
        this.visits = 0;
        this.setLeaf(true);
        this.sync = true;
        if (cacheValue != null) {
            if (this.cacheValue != cacheValue) {
                this.cacheValue.clearNodes();
                log.info("RESET EXPECTED REWARD DONE: oldValue:{} -> {}:newValue", this.cacheValue.getValue(), cacheValue.getValue());
                this.cacheValue = cacheValue;
            }
            this.cacheValue.addNode(this);
        }
        this.sum = this.cacheValue.getValue();
    }

    public enum State {
        ROOT, INTERMEDIATE, WIN, LOOSE, PAT, REPETITION_X3, REPEAT_50, NOT_ENOUGH_PIECES, NB_MOVES_300
    }

    public void updatePolicies(final double[] policies, boolean isDirichlet) {
        Collection<Move> childMoves = getChildMoves();
        int[] indexes = PolicyUtils.getIndexesFilteredPolicies(childMoves);
        Map<Move, Double> subPolicies = PolicyUtils.toDistribution(policies, indexes, isDirichlet, childMoves);
        subPolicies.entrySet().forEach(entry -> {
            Move childMove = entry.getKey();
            Double policy = entry.getValue();
            ChildNode childNode = this.childNodes.get(childMove);
            if (childNode == null) {
                childNode = new ChildNode();
                this.childNodes.put(childMove, childNode);
            }
            childNode.policy = policy;
        });
    }

}