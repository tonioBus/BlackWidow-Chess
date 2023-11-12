package com.aquila.chess.strategy.mcts;

import com.aquila.chess.config.MCTSConfig;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.*;
import java.util.stream.DoubleStream;

import static com.aquila.chess.strategy.mcts.MCTSNode.State.ROOT;

@Slf4j
public class CacheValue implements Serializable {

    public static final float NOT_INITIALIZED_VALUE = -1.0F;

    static final CacheValue getNotInitialized(final String label) {
        return new CacheValue(NOT_INITIALIZED_VALUE, label, new double[PolicyUtils.MAX_POLICY_INDEX]);
    }

    @Getter
    @Setter
    private double value;

    private double[] policies;

    @Setter
    @Getter
    private boolean initialized = false;

    final private String label;

    final private Map<MCTSNodePath, MCTSNode> nodes = Collections.synchronizedMap(new HashMap<>());

    CacheValue(double value, String label, double[] policies) {
        this.value = value;
        this.policies = policies;
        this.label = label;
    }

    public String toString() {
        final StringBuffer sb = new StringBuffer();
        sb.append(String.format("  initialized:%b\n", this.initialized));
        sb.append(String.format("  label=%s\n", this.label));
        sb.append(String.format("  value=%f\n", this.value));
        try {
            nodes.entrySet().forEach(entry -> {
                sb.append(String.format("  - node %s -> %s (isLeaf:%b propagated:%b sync:%b)\n",
                        entry.getKey(),
                        entry.getValue().getPathFromRoot(),
                        entry.getValue().isLeaf(),
                        entry.getValue().isPropagated(),
                        entry.getValue().isSync()
                ));
            });
        } catch (ConcurrentModificationException e) {
            sb.append(" nodes not available (sync)");
        }
        return sb.toString();
    }

    public synchronized void normalizePolicies() {
        if (nodes.size() == 0) {
            log.debug("Can not normalize policies, not connected to any nodes: {}", this.label);
            return;
        }
        if (!this.isInitialized()) return;
        synchronized (nodes) {
            nodes.values()
                    .stream()
                    .filter(node -> !node.isDirichletDone())
                    .forEach(node -> {
                        boolean isDirichlet = node.getState() == MCTSNode.State.ROOT;
                        isDirichlet = MCTSConfig.mctsConfig.isDirichlet(node.getMove()) && isDirichlet;
                        log.debug("NORMALIZED move.size:{} dirichlet:{} node:{}", node.getChildMoves().size(), node.isDirichletDone(), node);
                        node.updatePolicies(policies, isDirichlet);
                        node.dirichletDone = true;
                    });
        }
    }

    /**
     * Method call when an inference is done on this CacheValue
     *
     * @param value
     * @param policies
     * @return
     */
    public void setInferenceValuesAndPolicies(final double value, final double[] policies) {
        this.value = value;
        this.policies = policies;
        if (log.isDebugEnabled())
            log.debug("setTrueValuesAndPolicies({} : {} {} {} ..)", value, policies[0], policies[1], policies[2]);
        this.setInitialized(true);
        if (nodes != null) {
            setInferenceValuesAndPolicies();
        }
    }

    public void setInferenceValuesAndPolicies() {
        if (initialized) {
            nodes.values().forEach(MCTSNode::syncSum);
            normalizePolicies();
        }
    }


    public void addNode(final MCTSNode node) {
        assert node != null;
        MCTSNodePath pathFromRoot = node.getPathFromRoot();
        log.debug("addNode({}) currentNodesSize:{}", pathFromRoot, nodes.size());
        try {
            if (log.isDebugEnabled() && nodes.size() > 0 && !node.isLeaf()) {
                log.error("############# adding node: {} -> {}", pathFromRoot, node.getMovesFromRootAsString());
                log.error("nodes already inserted:");
                nodes.entrySet().forEach(entry -> {
                    log.error("  {} -> {}", entry.getKey(), entry.getValue().getMovesFromRootAsString());
                });
            }
            MCTSNode oldNode = this.nodes.get(pathFromRoot);
            if (oldNode != null) {
                oldNode.incNbPropationsToExecute();
            } else {
                log.debug("nodes.put({},{})", pathFromRoot, node.move);
                this.nodes.put(pathFromRoot, node);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            log.error("Error when adding a node: {}", node);
            throw e;
        }
        setInferenceValuesAndPolicies();
    }

    public void clearNodes() {
        this.nodes.clear();
    }

    /**
     * @return true if a connected nodes is a leaf
     */
    //FIXME
    public boolean isLeaf() {
        if (nodes.size() > 1) {
            log.debug("Cache value detected with more than 1 connected nodes:\n{}", this);
        }
        boolean ret = false;
        for (Map.Entry<MCTSNodePath, MCTSNode> entry : nodes.entrySet()) {
            MCTSNode node = entry.getValue();
            if (node.isLeaf()) ret = true;
            if (ret && !node.isLeaf()) {
                log.error("CacheValue seems a leaf but not all connected MCTSNode(s) are leaf");
                log.error(this.toString());
            }
        }
        return ret;
    }

    public double sumPolicies() {
        return DoubleStream.of(policies).sum();
    }

    public boolean isNodesEmpty() {
        return nodes.isEmpty();
    }

    public void verifyAlliance(final Alliance alliance) {
        if (!this.nodes.isEmpty()) {
            nodes.values().stream().forEach(node -> {
                assert (node.getMove().getAllegiance() == alliance);
            });
        }
    }

    public Optional<MCTSNode> getFirstNode() {
        return nodes.values().stream().findFirst();
    }

    public Optional<MCTSNode> getRootNode() {
        return nodes.values().stream().filter(node -> node.getState() == ROOT).findFirst();
    }

    public MCTSNode getNode(final MCTSNodePath path, Move selectedMove) {
        MCTSNode ret = nodes.get(new MCTSNodePath(path, selectedMove));
        log.debug("getNode({},{}", path, ret == null ? ret : ret.move);
        return ret;
    }

    public MCTSNode getNode(final MCTSNodePath path) {
        MCTSNode ret = nodes.get(path);
        log.debug("getNode({},{}", path, ret == null ? ret : ret.move);
        return ret;
    }

    public Collection<MCTSNode> getAllMCTSNodes() {
        return nodes.values();
    }

    public int getNbNodes() {
        return nodes.size();
    }

    public Collection<MCTSNode> getNodes() {
        return this.nodes.values();
    }
}
