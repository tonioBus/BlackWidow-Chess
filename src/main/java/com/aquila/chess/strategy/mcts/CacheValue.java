package com.aquila.chess.strategy.mcts;

import com.aquila.chess.config.MCTSConfig;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Slf4j
public class CacheValue extends OutputNN implements Serializable {

    public static final float NOT_INITIALIZED_VALUE = -1.0F;

    static final CacheValue getNotInitialized(final String label) {
        return new CacheValue(NOT_INITIALIZED_VALUE, label, new double[PolicyUtils.MAX_POLICY_INDEX]);
    }

    private double[] sourcePolicies = null;

    @Setter
    private boolean initialized = false;

    final private String label;

    final private List<MCTSNode> nodes = Collections.synchronizedList(new ArrayList<>());

    CacheValue(double value, String label, double[] policies) {
        super(value, policies);
        this.label = label;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("initialized=" + this.initialized);
        sb.append(" label=" + this.label);
        sb.append(" value=" + this.value);
        try {
            sb.append(" nodes=" + nodes.stream().map(node -> node.getMove().toString()).collect(Collectors.joining(",")));
        } catch(ConcurrentModificationException e) {
            sb.append(" nodes not available (sync)");
        }
        return sb.toString();
    }

    public synchronized void normalizePolicies() {
        if (nodes.size() == 0) {
            // log.warn("Can not normalize policies, not connected to any nodes: {}", this.label);
            return;
        }
        if (!this.isInitialized()) return;
        synchronized (nodes) {
            nodes.stream().filter(node -> !node.isDirichletDone()).forEach(node -> {
                int[] indexes = PolicyUtils.getIndexesFilteredPolicies(node.getChildMoves());
                boolean isDirichlet = node.getState() == MCTSNode.State.ROOT;
                isDirichlet = MCTSConfig.mctsConfig.isDirichlet(node.getMove()) && isDirichlet;
                if (isDirichlet) {
                    if (this.sourcePolicies == null) {
                        this.sourcePolicies = new double[policies.length];
                        System.arraycopy(policies, 0, sourcePolicies, 0, policies.length);
                    } else {
                        this.policies = new double[policies.length];
                        System.arraycopy(sourcePolicies, 0, policies, 0, sourcePolicies.length);
                    }
                    log.warn("NORMALIZED move.size:{} dirichlet:{} node:{}", node.getChildMoves().size(), node.isDirichletDone(), node);
                }
                PolicyUtils.toDistribution(policies, indexes, isDirichlet, node.getChildMoves());
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
    public OutputNN setInferenceValuesAndPolicies(final double value, final double[] policies) {
        this.value = value;
        this.policies = policies;
        if (log.isDebugEnabled())
            log.debug("setTrueValuesAndPolicies({},{} {} {} ..)", value, policies[0], policies[1], policies[2]);
        this.setInitialized(true);
        if (nodes != null) {
            setInferenceValuesAndPolicies();
        }
        return this;
    }

    public void setInferenceValuesAndPolicies() {
        if (initialized) {
            nodes.forEach(MCTSNode::syncSum);
            normalizePolicies();
        }
    }

    public void addNode(final MCTSNode node) {
        try {
            if (log.isDebugEnabled() && nodes.size() > 0 && !node.isLeaf()) {
                log.debug("############# adding node:{}", node.getMovesFromRootAsString());
                log.debug("nodes already inserted:");
                nodes.forEach(node1 -> {
                    log.debug(" - {}", node1);
                    log.debug(" - {}", node1.getMovesFromRootAsString());
                });
            }
            this.nodes.add(node);
        } catch (ArrayIndexOutOfBoundsException e) {
            log.error("Error when adding a node: {}", node);
            throw e;
        }
        setInferenceValuesAndPolicies();
    }

}
