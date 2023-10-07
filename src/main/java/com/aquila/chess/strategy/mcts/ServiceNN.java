package com.aquila.chess.strategy.mcts;

import com.aquila.chess.strategy.mcts.inputs.ServiceNNInputsJobs;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
class ServiceNN {

    @Getter
    private final Map<Long, ServiceNNInputsJobs> batchJobs2Commit = new LinkedHashMap<>();

    @Getter
    private final Map<Long, MCTSNode> nodesToPropagate = new LinkedHashMap<>();

    private final DeepLearningAGZ deepLearningAGZ;

    @Setter
    private int batchSize;

    private int nbFeaturesPlanes;

    @Builder
    public ServiceNN(final DeepLearningAGZ deepLearningAGZ, int nbFeaturesPlanes, int batchSize) {
        assert (nbFeaturesPlanes > 0);
        assert (batchSize > 0);
        this.deepLearningAGZ = deepLearningAGZ;
        this.nbFeaturesPlanes = nbFeaturesPlanes;
        this.batchSize = batchSize;
    }

    /**
     * Add a node to the propagation list, it will be taken in account at the next commit of Job ({@link #executeJobs})
     * Internally only the cacheValue is store for a propagation
     */
    public void addNodeToPropagate(final MCTSNode... nodes) {
        addNodeToPropagate(List.of(nodes));
    }

    private synchronized void addNodeToPropagate(Collection<MCTSNode> nodes) {
        nodes.stream().filter(node -> node.getState() != MCTSNode.State.ROOT && !node.isPropagated()).forEach(node -> {
            node.incNbPropationsToExecute();
            log.debug("ADD NODE TO PROPAGATE:{}", node);
            this.nodesToPropagate.put(node.getKey(), node);
        });
    }

    /**
     * Remove node from propagation, internally this is removing the cacheValue
     *
     * @param node
     */
    public synchronized void removeNodeToPropagate(final MCTSNode node) {
        long key = node.getKey();
        synchronized (this.nodesToPropagate) {
            this.nodesToPropagate.remove(key);
        }
        this.removeJob(key);
    }

    public synchronized void removeJob(long key) {
        batchJobs2Commit.remove(key);
    }

    public boolean containsJob(long key) {
        return batchJobs2Commit.containsKey(key);
    }

    public synchronized void clearAll() {
        this.batchJobs2Commit.clear();
        this.getNodesToPropagate().clear();
    }

    /**
     * Execute all stored calls to the NN, updateValueAndPolicies corresponding CacheValue(s) and propragate the found value to parents until the ROOT
     *
     * @param force
     */
    public synchronized void executeJobs(boolean force) {
        boolean submit2NN = force || batchJobs2Commit.size() >= batchSize;
        log.debug("BEGIN executeJobs({})", submit2NN);
        initValueAndPolicies(submit2NN);
        log.debug("END executeJobs({})", submit2NN);
    }

    /**
     * <strong>INFERENCE</strong> of the current batched inputs
     *
     * @param length
     */
    private void inferNN(int length) {
        log.debug("RETRIEVE VALUES & POLICIES: BATCH-SIZE:{} <- CURRENT-SIZE:{}", batchSize, length);
        final var nbIn = new double[length][nbFeaturesPlanes][BoardUtils.NUM_TILES_PER_ROW][BoardUtils.NUM_TILES_PER_ROW];
        createInputs(nbIn);
        System.out.print("#");
        final List<OutputNN> outputsNN = this.deepLearningAGZ.nn.outputs(nbIn, length);
        System.out.printf("%d&", length);
        int nbPropagatedNodes = updateCacheValuesAndPoliciesWithInference(outputsNN);
        System.out.printf("%d|", nbPropagatedNodes);
    }

    private void propagateValues(boolean submit2NN, int length) {
        if (nodesToPropagate.isEmpty()) return;
        int nbPropagate = 0;
        List<Long> deleteCaches = new ArrayList<>();
        synchronized (nodesToPropagate) {
            log.debug("PROPAGATE VALUES LIST:\n{}",
                    nodesToPropagate.values().stream().map(node -> node.getMove().toString()).collect(Collectors.joining(",")));
            for (Map.Entry<Long, MCTSNode> entry : nodesToPropagate.entrySet()) {
                long key = entry.getKey();
                MCTSNode node = entry.getValue();
                CacheValue cacheValue = node.getCacheValue();
                node.syncSum();
                if (!node.isSync()) {
                    log.debug("POSTPONED PROPAGATE [key:{}] -> node not synchronised:{}", key, cacheValue);
                    continue;
                }
                List<MCTSNode> propagationListUntilRoot = createPropragationList(node.getParent(), key);
                if (propagationListUntilRoot != null) {
                    deleteCaches.add(key);
                    double value2propagate = node.getCacheValue().getValue();
                    int nbPropagation2Apply = node.getNbPropagationsToExecute();
                    if (log.isDebugEnabled()) log.debug("PROPAGATE NODE:{} VALUE:{} PATH:[ {} ] NB of TIMES:{}",
                            node.getMove(),
                            value2propagate,
                            propagationListUntilRoot.stream().map(node1 -> node1.getMove().toString()).collect(Collectors.joining(" / ")),
                            node.getNbPropagationsToExecute());
                    for (MCTSNode node2propagate : propagationListUntilRoot) {
                        value2propagate = -value2propagate;
                        for (int nbPropragation = 0; nbPropragation < nbPropagation2Apply; nbPropragation++) {
                            nbPropagate += node2propagate.propagate(value2propagate);
                        }
                    }
                }
                node.setPropagated(true);
            }
            if (submit2NN && length > 0) System.out.printf("%d#", nbPropagate);
            for (long key : deleteCaches) {
                log.debug("DELETE KEY TO PROPAGATE: {}", key);
                nodesToPropagate.remove(key);
            }
        }
    }

    private void initValueAndPolicies(boolean submit2NN) {
        int length = batchJobs2Commit.size();
        if (submit2NN && length > 0) {
            inferNN(length);
            batchJobs2Commit.clear();
        }
        propagateValues(submit2NN, length);
    }

    private List<MCTSNode> createPropragationList(final MCTSNode child, long key) {
        if (child == null) return null; // || child.getState() == MCTSNode.State.ROOT) return null;
        List<MCTSNode> nodes2propagate = new ArrayList<>();
        MCTSNode node = child;
        do {
            if (!node.isSync()) {
                log.debug("POSTPONED PROPAGATE(node not sync): key:{} node:{}", key, node);
                return null;
            }
            log.debug("CREATING PROPAGATION LIST: add:{}", node);
            nodes2propagate.add(node);
            node = node.getParent();
        } while (node != null && node.getState() != MCTSNode.State.ROOT);
        if (node != null) nodes2propagate.add(node);
        return nodes2propagate.size() == 0 ? null : nodes2propagate;
    }

    private void createInputs(double[][][][] nbIn) {
        int indexNbIn = 0;
        for (Map.Entry<Long, ServiceNNInputsJobs> entry : this.batchJobs2Commit.entrySet()) {
            System.arraycopy(entry.getValue().inputs().inputs(), 0, nbIn[indexNbIn], 0, nbFeaturesPlanes);
            indexNbIn++;
        }
    }

    /**
     * @param outputsNN
     * @return propragated nodes
     */
    private int updateCacheValuesAndPoliciesWithInference(final List<OutputNN> outputsNN) {
        int index = 0;
        for (Map.Entry<Long, ServiceNNInputsJobs> entry : this.batchJobs2Commit.entrySet()) {
            Move move = entry.getValue().move();
            Alliance color2play = entry.getValue().color2play();
            long key = entry.getKey();
            double value = outputsNN.get(index).getValue();
            double[] policies = outputsNN.get(index).getPolicies();
            CacheValue cacheValue = this.deepLearningAGZ.getCacheValues().updateValueAndPolicies(key, value, policies);
            synchronized (nodesToPropagate) {
                if (nodesToPropagate.containsKey(key)) {
                    MCTSNode propagationNode = nodesToPropagate.get(key);
                    CacheValue oldCacheValue = propagationNode.getCacheValue();
                    if (oldCacheValue.hashCode() != cacheValue.hashCode() &&
                            propagationNode.getState() != MCTSNode.State.ROOT ||
                            propagationNode.getState() != MCTSNode.State.INTERMEDIATE) {
                        log.error("oldCacheValue[{}]:{}", oldCacheValue.hashCode(), ToStringBuilder.reflectionToString(oldCacheValue, ToStringStyle.JSON_STYLE));
                        log.error("newCacheValue[{}]:{}", cacheValue.hashCode(), ToStringBuilder.reflectionToString(cacheValue, ToStringStyle.JSON_STYLE));
                    }
                    log.debug("CacheValue [{}/{}] already stored on tmpCacheValues", key, move);
                } else {
                    addNodeToPropagate(cacheValue.getNodes());
                    log.debug("[{}] RETRIEVE value for key:{} -> move:{} value:{}  policies:{},{},{}", color2play, key, move == null ? "null" : move, value, policies[0], policies[1], policies[2]);
                }
                index++;
            }
        }
        return index;
    }

    /**
     * Submit a NN Job that will be committed later
     *
     * @param key          - the key of the CacheValue
     * @param possibleMove - the concern move
     * @param color2play   - the color playing
     * @param gameCopy
     * @param isDirichlet
     * @param isRootNode
     */
    protected synchronized void submit(final long key,
                                       final Move possibleMove,
                                       final Alliance color2play,
                                       final MCTSGame gameCopy,
                                       final boolean isDirichlet,
                                       final boolean isRootNode) {
        if (batchJobs2Commit.containsKey(key)) return;
        synchronized (nodesToPropagate) {
            if (nodesToPropagate.containsKey(key)) return;
        }
        if (possibleMove != null) {
            Alliance possibleMoveColor = possibleMove.getAllegiance();
            if (possibleMoveColor != color2play) {
                log.error("Not identical color: move:{} color2play:{}", possibleMove, color2play);
                throw new RuntimeException(String.format("Color not Identical, move:%s color:%s",
                        possibleMove, color2play.toString()));
            }
        }
        // log.info("ServiceNNInputsJobs(move:{}) key:{}", possibleMove, key);
        batchJobs2Commit.put(key, new ServiceNNInputsJobs(
                possibleMove,
                color2play,
                gameCopy,
                isDirichlet,
                isRootNode));
    }


    public String toString() {
        synchronized (nodesToPropagate) {
            return String.format("cacheValues.size():%d batchJobs.size:%d", this.nodesToPropagate.size(), this.batchJobs2Commit.size());
        }
    }
}
