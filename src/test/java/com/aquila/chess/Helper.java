package com.aquila.chess;

import com.aquila.chess.strategy.mcts.MCTSNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class Helper {

    static public CountNodes countNbNodes(final MCTSNode node) {
        int ret = 1;
        if (node.getState() == MCTSNode.State.WIN) return new CountNodes(ret, true);
        for (final MCTSNode child : node.getChilds()) {
            CountNodes countNodes = countNbNodes(child);
            if (countNodes.isWin()) return countNodes;
            else {
                ret += countNodes.getCount();
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

    static public void checkMCTSTree(final MCTSNode node) {
        List<String> ret = new ArrayList<>();
        Map<Long, MCTSNode> notTestedNodes = new HashMap<>();
        addNodes2NotTest(node, notTestedNodes);
        checkMCTSTreeVisits(node, ret, notTestedNodes);
        assertEquals(0, ret.size(), "\n" + ret.stream().collect(Collectors.joining("\n")));
    }

    private static boolean addNodes2NotTest(final MCTSNode node, final Map<Long, MCTSNode> notTestedNodes) {
        if (node.getChilds().size() == 0) {
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
        for (MCTSNode child : node.getChilds()) {
            if (addNodes2NotTest(child, notTestedNodes)) {
                notTestedNodes.put(child.getKey(), child);
                ret = true;
            }
        }
        if(ret) notTestedNodes.put(node.getKey(), node);
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
        for (MCTSNode child : node.getChilds()) {
            checkMCTSTreeVisits(child, ret, notTestedNodes);
        }
    }

}
