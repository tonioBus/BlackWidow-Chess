package com.aquila.chess.strategy.mcts;

import com.aquila.chess.config.MCTSConfig;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.DoubleStream;

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

    @Getter
    final private Map<Long, MCTSNode> nodes = Collections.synchronizedMap(new HashMap<>());

    CacheValue(double value, String label, double[] policies) {
        this.value = value;
        this.policies = policies;
        this.label = label;
    }

    public String toString() {
        final StringBuffer sb = new StringBuffer();
        sb.append("initialized=" + this.initialized);
        sb.append("label=" + this.label);
        sb.append("value=" + this.value);
        try {
            nodes.entrySet().forEach(entry -> {
                sb.append(String.format("- %d -> %s", entry.getKey(), entry.getValue().getMovesFromRootAsString()));
            });
        } catch (ConcurrentModificationException e) {
            sb.append(" nodes not available (sync)");
        }
        return sb.toString();
    }

    public synchronized void normalizePolicies() {
        if (nodes.size() == 0) {
            log.warn("Can not normalize policies, not connected to any nodes: {}", this.label);
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
                        log.warn("NORMALIZED move.size:{} dirichlet:{} node:{}", node.getChildMoves().size(), node.isDirichletDone(), node);
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
        long keyNode = node.hash();
        try {
            if (log.isDebugEnabled() && nodes.size() > 0 && !node.isLeaf()) {
                log.error("############# adding node: {} -> {}", keyNode, node.getMovesFromRootAsString());
                log.error("nodes already inserted:");
                nodes.entrySet().forEach(entry -> {
                    log.error("  {} -> {}", entry.getKey(), entry.getValue().getMovesFromRootAsString());
                });
            }
            if (this.nodes.containsKey(keyNode)) {
                MCTSNode oldNode = this.nodes.get(keyNode);
                if (oldNode == null) {
                    log.error("oldNode null:{}", keyNode);
                    nodes.entrySet().forEach(entry -> {
                        log.error("  {} -> {}", entry.getKey(), entry.getValue().getMovesFromRootAsString());
                    });
                }
                oldNode.incNbPropationsToExecute();
            } else this.nodes.put(keyNode, node);
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
     * @return true if a connected nodes is
     */
    //FIXME
    public boolean isLeaf() {
        if (nodes.size() > 1) {
            log.debug("Cache value detected with more than 1 connected nodes:\n{}", this);
        }
        AtomicBoolean ret = new AtomicBoolean(false);
        nodes.entrySet().forEach(entry -> {
            MCTSNode node = entry.getValue();
            if (node.isLeaf()) {
                log.debug("  {} -> {}", entry.getKey(), entry.getValue().getMovesFromRootAsString());
                ret.set(true);
            }
            if (ret.get() && !node.isLeaf()) {
                log.error("CacheValue seems a leaf but not all connected MCTSNode(s) are leaf");
                log.error(this.toString());
                assert false;
            }
        });
        return ret.get();
    }

    public double sumPolicies() {
        return DoubleStream.of(policies).sum();
    }
}
