package com.aquila.chess.strategy.mcts;

import com.aquila.chess.strategy.mcts.inputs.ServiceNNInputsJobs;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ServiceNN {

    @Getter
    private final Map<Long, ServiceNNInputsJobs> batchJobs2Commit = Collections.synchronizedMap(new LinkedHashMap<>());

    @Getter
    private final Map<Long, MCTSNode> nodesToPropagate = Collections.synchronizedMap(new LinkedHashMap<>());

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

    /**
     * this will propagate only node not propagated (node.propagated)
     *
     * @param nodes
     */
    private void addNodeToPropagate(Collection<MCTSNode> nodes) {
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
    public void removeNodeToPropagate(final MCTSNode node) {
        long key = node.getKey();
        synchronized (this.nodesToPropagate) {
            this.nodesToPropagate.remove(key);
        }
        this.removeJob(key);
    }

    public void removeJob(long key) {
        batchJobs2Commit.remove(key);
    }

    public boolean containsJob(long key) {
        return batchJobs2Commit.containsKey(key);
    }

    public void clearAll() {
        this.batchJobs2Commit.clear();
        this.getNodesToPropagate().clear();
    }

    /**
     * Execute all stored calls to the NN, updateValueAndPolicies corresponding CacheValue(s) and propragate the found value to parents until the ROOT
     *
     * @param force
     */
    public void executeJobs(boolean force) {
        synchronized (batchJobs2Commit) {
            int batchJobs2CommitSize = batchJobs2Commit.size();
            boolean submit2NN = force || batchJobs2CommitSize >= batchSize;
            log.debug("ServiceNN.executeJobs() batchJobs2Commit:{}", batchJobs2CommitSize);
            log.debug("BEGIN executeJobs({})", submit2NN);
            initValueAndPolicies(submit2NN, batchJobs2CommitSize);
            log.debug("END executeJobs({})", submit2NN);
        }
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
        outputsNN.stream().forEach(outputNN -> {
            double sumPolicies = Arrays.stream(outputNN.policies).sum();
            if (sumPolicies == 0) {
                log.error("POLICIES SET TO NULL ");
            }
        });
        updateCacheValuesAndPoliciesWithInference(outputsNN);
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
                            nbPropagate += node2propagate.propagateOneTime(value2propagate);
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

    private void initValueAndPolicies(boolean submit2NN, int batchJobs2CommitSize) {
        if (submit2NN && batchJobs2CommitSize > 0) {
            inferNN(batchJobs2CommitSize);
            batchJobs2Commit.clear();
        }
        propagateValues(submit2NN, batchJobs2CommitSize);
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
        synchronized (batchJobs2Commit) {
            for (Map.Entry<Long, ServiceNNInputsJobs> entry : this.batchJobs2Commit.entrySet()) {
                System.arraycopy(entry.getValue().inputs().inputs(), 0, nbIn[indexNbIn], 0, nbFeaturesPlanes);
                indexNbIn++;
            }
        }
    }

    /**
     * @param outputsNN
     * @return propragated nodes
     */
    private int updateCacheValuesAndPoliciesWithInference(final List<OutputNN> outputsNN) {
        int index = 0;
        synchronized (batchJobs2Commit) {
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
                            log.warn("oldCacheValue[{}]:{}", oldCacheValue.hashCode(), oldCacheValue);
                            log.warn("newCacheValue[{}]:{}", cacheValue.hashCode(), cacheValue);
                            log.warn("NO PROPAGATION -> Keeping oldCacheValue");
                        }
                        log.debug("CacheValue [{}/{}] already stored on tmpCacheValues", key, move);
                        addNodeToPropagate(oldCacheValue.getAllMCTSNodes());
                    } else {
                        addNodeToPropagate(cacheValue.getAllMCTSNodes());
                        log.debug("[{}] RETRIEVE value for key:{} -> move:{} value:{}  policies:{},{},{}", color2play, key, move == null ? "null" : move, value, policies[0], policies[1], policies[2]);
                    }
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
    protected void submit(final long key,
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
        log.debug("SERVICENN.submit() batchJobs2Commit:{}", batchJobs2Commit.size());
    }


    public String toString() {
        synchronized (nodesToPropagate) {
            return String.format("cacheValues.size():%d batchJobs.size:%d", this.nodesToPropagate.size(), this.batchJobs2Commit.size());
        }
    }
}
