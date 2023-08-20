package com.aquila.chess.strategy.mcts;

import com.aquila.chess.MCTSStrategyConfig;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import com.aquila.chess.utils.Utils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@Slf4j
public class CacheValue extends OutputNN implements Serializable {

    private static final float NOT_INITIALIZED_VALUE = 0;

    static final CacheValue getNotInitialized(final String label) {
        return new CacheValue(NOT_INITIALIZED_VALUE, label, new double[PolicyUtils.MAX_POLICY_INDEX]);
    }

    private double[] sourcePolicies = null;

    @Setter
    private boolean initialised = false;

    final private String label;

    final private List<MCTSNode> nodes = new ArrayList<>();

    private CacheValueType type = CacheValueType.INTERMEDIATE;

    private CacheValue(double value, String label, double[] policies) {
        super(value, policies);
        this.label = label;
    }

    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }

    public void setAsRoot() {
        this.type = CacheValueType.ROOT;
    }

    public synchronized void normalizePolicies(double[] policies, boolean old) {
        this.sourcePolicies = policies;
        if (nodes.size() == 0) {
            log.debug("Can not normalize policies, not connected to any nodes: {}", this.label);
            return;
        }
        int[] indexes = PolicyUtils.getIndexesFilteredPolicies(nodes.get(0).getChildMoves(), old);
        log.debug("NORMALIZED type:{} move.size:{} dirichlet:{}", this.type, nodes.get(0).getChildMoves().size(), nodes.get(0).isDirichlet());
        boolean isDirichlet = nodes.get(0).getState() == MCTSNode.State.ROOT;
        isDirichlet = MCTSStrategyConfig.isDirichlet(nodes.get(0).getMove()) && isDirichlet;
        double[] normalizedPolicies = Utils.toDistribution(policies, indexes, isDirichlet, nodes.get(0).getChildMoves(), old);
        this.policies = normalizedPolicies;
    }

    public synchronized void reNormalizePolicies(boolean old) {
        if (sourcePolicies != null) {
            log.info("re-normalize node:{}", this.nodes);
            normalizePolicies(sourcePolicies, old);
        }
    }

    public void setAsLeaf() {
        this.type = CacheValueType.LEAF;
    }

    public OutputNN setTrueValuesAndPolicies(final double value, final double[] policies) {
        this.value = value;
        this.policies = policies;
        log.debug("setTrueValuesAndPolicies({},{} {} {} ..)", value, policies[0], policies[1], policies[2]);
        this.setInitialised(true);
        if (nodes != null) {
            setTrueValuesAndPolicies();
        }
        return this;
    }

    public void setTrueValuesAndPolicies() {
        if (initialised && type != CacheValueType.LEAF) {
            nodes.forEach(MCTSNode::syncSum);
            normalizePolicies(policies, false);
        }
    }

    public void addNode(final MCTSNode node) {
        nodes.forEach(previousNode -> {
            assert previousNode.getChildNodes().keySet().equals(node.getChildNodes().keySet());
        });
        this.nodes.add(node);
        setTrueValuesAndPolicies();
    }

    public enum CacheValueType {
        INTERMEDIATE, ROOT, LEAF
    }

}
