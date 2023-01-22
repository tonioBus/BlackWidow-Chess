/**
 *
 */
package com.aquila.chess.utils;


import com.aquila.chess.strategy.mcts.CacheValues;
import com.aquila.chess.strategy.mcts.MCTSNode;
import com.aquila.chess.strategy.mcts.MCTSStrategy;
import com.aquila.chess.strategy.mcts.PolicyUtils;
import com.chess.engine.classic.Alliance;
import info.leadinglight.jdot.Edge;
import info.leadinglight.jdot.Graph;
import info.leadinglight.jdot.Node;
import info.leadinglight.jdot.enums.Shape;
import info.leadinglight.jdot.enums.Style;

import java.util.List;

/**
 * @author bussa
 */
public class DotGenerator {

    private static boolean displayLeafNode = false;

    private DotGenerator() {
    }

    public static String toString(final MCTSNode node, final int depthMax) {
        return toString(node, depthMax, false);
    }

    public static String toString(final MCTSNode node, final int depthMax, final boolean displayLeafNode) {
        DotGenerator.displayLeafNode = displayLeafNode;
        final DotGenerator dotGenerator = new DotGenerator();
        final Graph graph = dotGenerator.generate(node, depthMax);
        return graph.toDot();
    }

    // digraph structs { node [shape=record] "1552326679" [label=" { ligne1 | ligne2
    // |{ <fils1> fils1|<fils2> fils2 }}" shape=record] }
    /*
     * digraph structs { node [shape=record] "400731411"
     * [label="{ BLACK | d7 - e8 | Visits: 3 | <1995054261> 0.094828704992930 | <1788132713> 0.2844861894845962 }"
     * ] node [shape=record] "1995054261" [label="{ WHITE | d3 - b5 | Visits: 2  }"
     * ] node [shape=record] "1788132713" [label="{ BLACK | d7 - d8 | Visits: 1 }" ]
     * 400731411 -> { 1995054261 } [ label="0.094828704992930" ]; 400731411 ->
     * 1788132713 [ label="0.094828704992930" ]; }
     */
    private Graph generate(final MCTSNode node, final int depthMax) {
        Graph g = new Graph("structs");
        // g.setRankDir(Rankdir.LR);
        generate(g, node, 0, depthMax);
        return g;
    }

    private String values(final MCTSNode node) {
        final StringBuffer sb = new StringBuffer();
        List<MCTSNode.PropragateValue> values = node.getValues();
        synchronized (values) {
            values.stream().limit(6).forEach(propragateValue -> {
                sb.append(String.format("%s:%s:%.6f",
                        propragateValue.getBuildOrder(),
                        propragateValue.getPropragateSrc().getName(),
                        propragateValue.getValue()));
                sb.append("\n");
            });
        }
        return sb.toString().trim();
    }

    private int generate(final Graph g, final MCTSNode node, final int depth, final int depthMax) {
        if (depth >= depthMax)
            return 0;
        String szMove = node.getMove() == null ? "ROOT" : String.valueOf(node.getMove());
        Alliance color = node.getColorState();
        String core = String.format("Parent:%s | key:%s | Init:%b | Propa:%b | moves:%d | %s | %s | %s | Value:%f | Reward:%f | V-Loss:%f | Visits:%d | childs:%d | %d:%s", //
                node.getParent() == null ? "null" : node.getParent().getMove() == null ? "ROOT" : String.valueOf(node.getParent().getMove()),
                node.getKey(),
                node.getCacheValue().isInitialised(),
                node.getCacheValue().isPropagated(),
                node.getChildMoves().size(),
                color == null ? "no color" : color.toString(), //
                szMove, //
                String.format("ret:%d Prop:%d", node.getRet(), node.getPropagate()),
                node.getValue(), //
                node.getExpectedReward(false), //
                node.getVirtualLoss(), //
                node.getVisits(),
                node.getNonNullChildsAsCollection().size(),
                node.getBuildOrder(),
                node.getCreator().getName());
        Shape shape = Shape.record;
        switch (node.getState()) {
            case INTERMEDIATE:
                if (node.getCacheValue().getType() == CacheValues.CacheValue.CacheValueType.ROOT) {
                    shape = Shape.box3d;
                } else {
                    shape = Shape.record;
                }
                break;
            case WIN:
                shape = Shape.tripleoctagon;
                core = String.format("%s | %s", core, node.getState());
                break;
            case LOOSE:
                shape = Shape.octagon;
                core = String.format("%s | %s", core, node.getState());
                break;
            case PAT:
            case REPEAT_50:
            case REPETITION_X3:
            case NOT_ENOUGH_PIECES:
            case NB_MOVES_300:
                shape = Shape.doubleoctagon;
                core = String.format("%s | %s", core, node.getState());
                break;
        }
        if (node.getNonNullChildsAsCollection().size() == 0) {
            g.addNodes(new Node("" + node.hashCode()).setShape(shape).setLabel(core).setStyle(Style.Node.rounded));
            return node.hashCode();
        }
        String nodeLabel = String.format("{ %s }", core);
        g.addNodes(new Node("" + node.hashCode()).setShape(shape).setLabel(nodeLabel).setStyle(Style.Node.rounded));
        node.getChildsAsCollection().forEach(child -> {
            if (child != null) {
                int visits = child.getVisits();
                double policy = node.getCacheValue().getPolicies()[PolicyUtils.indexFromMove(child.getMove())];
                if ((DotGenerator.displayLeafNode || visits > 0) || child.getState() != MCTSNode.State.INTERMEDIATE) {
                    int hashCode = generate(g, child, depth + 1, depthMax);
                    double exploitation = child.getExpectedReward(false);
                    double reward = MCTSStrategy.expectedReward(child);
                    // double exploration = MCTSSearchWalker.exploration(node, 0.5, node. )
                    g.addEdges(new Edge().addNode("" + node.hashCode(), "").addNode("" + hashCode, "")
                            .setLabel(String.format("R:%f V:%d P:%f", reward, visits, policy)));
                }
            }
        });
        return node.hashCode();
    }
}
