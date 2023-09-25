package com.aquila.chess.strategy.mcts;

import com.aquila.chess.MCTSStrategyConfig;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import com.aquila.chess.utils.Utils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Slf4j
public class CacheValue extends OutputNN implements Serializable {

    private static final float NOT_INITIALIZED_VALUE = -1;

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
        sb.append(" nodes=" + nodes.stream().map(node -> node.getMove().toString()).collect(Collectors.joining(",")));
        return sb.toString();
    }

    public synchronized void normalizePolicies(double[] policies) {
        this.sourcePolicies = policies;
        if (nodes.size() == 0) {
            log.debug("Can not normalize policies, not connected to any nodes: {}", this.label);
            return;
        }
        int[] indexes = PolicyUtils.getIndexesFilteredPolicies(nodes.get(0).getChildMoves());
        log.debug("NORMALIZED move.size:{} dirichlet:{}", nodes.get(0).getChildMoves().size(), nodes.get(0).isDirichlet());
        boolean isDirichlet = nodes.get(0).getState() == MCTSNode.State.ROOT;
        isDirichlet = MCTSStrategyConfig.isDirichlet(nodes.get(0).getMove()) && isDirichlet;
        double[] normalizedPolicies = Utils.toDistribution(policies, indexes, isDirichlet, nodes.get(0).getChildMoves());
        this.policies = normalizedPolicies;
    }

    public synchronized void reNormalizePolicies() {
        if (sourcePolicies != null) {
            log.info("re-normalize node:{}", this.nodes);
            normalizePolicies(sourcePolicies);
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
            normalizePolicies(policies);
        }
    }

    public void addNode(final MCTSNode node) {
//        nodes.forEach(previousNode -> {
//            assert previousNode.getChildNodes().keySet().equals(node.getChildNodes().keySet());
//        });
        try {
            this.nodes.add(node);
        } catch(ArrayIndexOutOfBoundsException e) {
            log.error("Error when adding a node: {}", node);
            throw e;
        }
        setInferenceValuesAndPolicies();
    }

}
