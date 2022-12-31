package com.aquila.chess;

import com.aquila.chess.strategy.mcts.MCTSNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
        checkMCTSTreeVisits(node, ret);
        assertTrue(ret.size() == 0, "\n" + ret.stream().collect(Collectors.joining("\n")));
    }

    static private void checkMCTSTreeVisits(final MCTSNode node, final List<String> ret) {
        int nbNodes = countNbNodes(node).getCount() - 1;
        int nbVisists = node.getVisits();
        if (nbNodes != nbVisists) {
            ret.add(String.format("Node with wrong number of visits (%d) vs nbChilds (%d): %s", nbVisists, nbNodes, node));
        }
        for (MCTSNode child : node.getChilds()) {
            checkMCTSTreeVisits(child, ret);
        }
    }

}
