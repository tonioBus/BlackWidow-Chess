package com.aquila.chess;

import com.aquila.chess.strategy.mcts.MCTSNode;
import com.aquila.chess.strategy.mcts.MCTSStrategy;
import com.aquila.chess.utils.DotGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Helper {

    static public CountNodes countNbNodes(final MCTSNode node) {
        int ret = 1;
        if (node.getState() == MCTSNode.State.WIN) return new CountNodes(ret, true);
        for (final MCTSNode child : node.getChildsAsCollection()) {
            if (child != null) {
                CountNodes countNodes = countNbNodes(child);
                if (countNodes.isWin()) return countNodes;
                else {
                    ret += countNodes.getCount();
                }
            }
        }
        return new CountNodes(ret, false);
    }

    @Data
    @AllArgsConstructor
    static class CountNodes {
        private int count;
        private boolean win;
    }

    static public void checkMCTSTree(final MCTSStrategy mctsStrategy) {
        final MCTSNode root = mctsStrategy.getDirectRoot();
        final List<String> ret = new ArrayList<>();
//        int numberAllSubNodes = root.getNumberOfAllNodes() - 1;
//        if (mctsStrategy.getNbSearchCalls() > 1 && numberAllSubNodes < mctsStrategy.getNbSearchCalls()) {
//            String msg = String.format("number of sub-nodes of ROOT node:%d should be == number of search:%d", numberAllSubNodes, mctsStrategy.getNbSearchCalls());
//            log.error(msg);
//            ret.add(msg);
//        }
        checkMCTSTreePoliciesAndValues(mctsStrategy.getDirectRoot(), ret);
        Map<Long, MCTSNode> notTestedNodes = new HashMap<>();
        addNodes2NotTest(root, notTestedNodes);
        checkMCTSTreeVisits(root, ret, notTestedNodes);
        if (ret.size() > 0) {
            log.error(DotGenerator.toString(mctsStrategy.getDirectRoot(), 5, true));
            // assertEquals(0, ret.size(), "\n" + ret.stream().collect(Collectors.joining("\n", "\n", "\n")));
        }
    }

    static private void checkMCTSTreePoliciesAndValues(final MCTSNode node, final List<String> ret) {
        if (node.getState() != MCTSNode.State.INTERMEDIATE) return;
        double sumPolicies = node.getCacheValue().sumPolicies();
        if (node.getNonNullChildsAsCollection().size() > 0 && sumPolicies < 0.9 || sumPolicies > 1.1) {
            ret.add(String.format("Helper.checkMCTSTreePoliciesAndValues: Node:%s sum of policies should be ~= 1. (sum:%f)", node, sumPolicies));
        }
        if (!node.getCacheValue().isInitialized()) {
            ret.add(String.format("Helper.checkMCTSTreePoliciesAndValues: node %s should be initialized", node));
        }
        for (MCTSNode child : node.getChildsAsCollection()) {
            if (child == null) continue;
            checkMCTSTreePoliciesAndValues(child, ret);
        }
    }

    private static boolean addNodes2NotTest(final MCTSNode node, final Map<Long, MCTSNode> notTestedNodes) {
        if (node.getChildsAsCollection().size() == 0) {
            switch (node.getState()) {
                case WIN:
                case PAT:
                case LOOSE:
                case NOT_ENOUGH_PIECES:
                case NB_MOVES_300:
                case REPEAT_50:
                case REPETITION_X3:
                    notTestedNodes.put(node.getKey(), node);
                    return true;
            }
            return false;
        }
        boolean ret = false;
        for (MCTSNode child : node.getChildsAsCollection()) {
            if (child == null) continue;
            if (addNodes2NotTest(child, notTestedNodes)) {
                notTestedNodes.put(child.getKey(), child);
                ret = true;
            }
        }
        if (ret) notTestedNodes.put(node.getKey(), node);
        return ret;
    }

    static private void checkMCTSTreeVisits(final MCTSNode node, final List<String> ret, final Map<Long, MCTSNode> notTestedNodes) {
        int nbAllChilds = countNbNodes(node).getCount() - 1;
        int nbVisits = node.getVisits();
        if (notTestedNodes.containsKey(node.getKey())) {
            if (nbAllChilds > nbVisits)
                ret.add(String.format("Node with number of visits (%d) 'not <=' number all childs (%d): %s", nbVisits, nbAllChilds, node));
        } else if (nbAllChilds != nbVisits) {
            ret.add(String.format("Node with wrong number of visits (%d) vs number all childs (%d): %s", nbVisits, nbAllChilds, node));
        }
        for (MCTSNode child : node.getChildsAsCollection()) {
            if (child != null) {
                checkMCTSTreeVisits(child, ret, notTestedNodes);
            }
        }
    }

}
