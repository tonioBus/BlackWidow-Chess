package com.aquila.chess.strategy.mcts;

import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.*;

@Slf4j
class ServiceNN {

    @Getter
    private final Map<Long, InputForBatchJobs> batchJobs2Commit = new HashMap<>();

    @Getter
    private final Map<Long, CacheValues.CacheValue> propagationValues = new LinkedHashMap<>();

    private final DeepLearningAGZ deepLearningAGZ;

    private int batchSize;

    public ServiceNN(final DeepLearningAGZ deepLearningAGZ, int batchSize) {
        this.deepLearningAGZ = deepLearningAGZ;
        this.batchSize = batchSize;
    }

    /**
     * Add a node to the propagation list, it will be taken in account at the next commit of Job ({@link #executeJobs})
     * Internally only the cacheValue is store for a propagation
     *
     * @param key
     * @param node
     */
    public synchronized void addNodeToPropagate(long key, final MCTSNode node) {
        CacheValues.CacheValue cacheValue = node.getCacheValue();
        if (cacheValue.isInitialised() == true) {
            log.debug("adding a node already initialized: {} cacheValue:{}", node, cacheValue);
            return;
        }
        this.addValueToPropagate(key, cacheValue);
    }

    /**
     * Add a node to the propagation list, it will be taken in account at the next commit of Job ({@link #executeJobs})
     *
     * @param key
     * @param cacheValue
     */
    public synchronized void addValueToPropagate(long key, final CacheValues.CacheValue cacheValue) {
        this.propagationValues.put(key, cacheValue);
    }

    /**
     * Remove node from propagation, internally this is removing the cacheValue
     *
     * @param node
     */
    public synchronized void removeNodeToPropagate(final MCTSNode node) {
        long key = node.getKey();
        this.propagationValues.remove(key);
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
        this.getPropagationValues().clear();
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

    private void retrieveValuesPoliciesFromNN(int length) {
        log.debug("RETRIEVE VALUES & POLICIES: BATCH-SIZE:{} <- CURRENT-SIZE:{}", batchSize, length);
        final double[][][][] nbIn = new double[length][INN.FEATURES_PLANES][BoardUtils.NUM_TILES_PER_ROW][BoardUtils.NUM_TILES_PER_ROW];
        createInputs(nbIn);
        System.out.print("#");
        List<OutputNN> outputsNN = this.deepLearningAGZ.nn.outputs(nbIn, length);
        System.out.printf("%d&", length);
        updateCacheValuesAndPolicies(outputsNN);
    }

    private void propagateValues(boolean submit2NN, int length) {
        int nbPropagate = 0;
        List<Long> deleteCaches = new ArrayList<>();
        for (Map.Entry<Long, CacheValues.CacheValue> entry : propagationValues.entrySet()) {
            long key = entry.getKey();
            CacheValues.CacheValue cacheValue = entry.getValue();
            MCTSNode node = cacheValue.getNode();
            if (node == null) {
                log.debug("DELETING1[key:{}]  CACHE FROM PROPAGATE (cacheValue not connected to a Node):{}", key, cacheValue);
                continue;
            }
            node.syncSum();
            if (!node.isSync()) {
                log.debug("DELETING2[key:{}] CACHE FROM PROPAGATE (node not synchronised):{}", key, cacheValue);
                continue;
            }
            List<MCTSNode> nodes2propagate = createPropragationList(node.getParent(), key);
            if (nodes2propagate != null) {
                deleteCaches.add(key);
                double value = node.getValue();
                node.getCacheValue().setPropagated(true);
                log.debug("PROPAGATE VALUE:{} CHILD:{} NB of TIMES:{}", value, node, node.getCacheValue().getNbPropagate());
                for (MCTSNode node2propagate : nodes2propagate) {
                    value = -value;
                    for (int i = 0; i < node.getCacheValue().getNbPropagate(); i++) {
                        node2propagate.propagate(value);
                    }
                    nbPropagate += node.getCacheValue().getNbPropagate();
                }
                node.getCacheValue().resetNbPropragate();
            }
        }
        if (submit2NN && length > 0) System.out.printf("%d#", nbPropagate);
        for (long key : deleteCaches) {
            propagationValues.remove(key);
        }
    }

    private void initValueAndPolicies(boolean submit2NN) {
        int length = batchJobs2Commit.size();
        if (submit2NN && length > 0) {
            retrieveValuesPoliciesFromNN(length);
            batchJobs2Commit.clear();
        }
        propagateValues(submit2NN, length);
    }

    private List<MCTSNode> createPropragationList(final MCTSNode child, long key) {
        if (child == null) return null;
        List<MCTSNode> nodes2propagate = new ArrayList<>();
        MCTSNode node = child;
        while (node.getCacheValue().getType() != CacheValues.CacheValue.CacheValueType.ROOT) {
            if (!node.isSync()) {
                if (log.isDebugEnabled()) log.debug("POSTPONED PROPAGATE(node not sync): key:{} node:{}", key, node);
                return null;
            }
            if (log.isDebugEnabled()) log.debug("CREATE PROPRAGATION LIST: add:{}", node);
            nodes2propagate.add(node);
            node = node.getParent();
            if (node == null) {
                throw new RuntimeException("Node null !! Child:" + child);
            }
        }
        nodes2propagate.add(node);
        return nodes2propagate.size() == 0 ? null : nodes2propagate;
    }

    private void createInputs(double[][][][] nbIn) {
        int indexNbIn = 0;
        for (Map.Entry<Long, InputForBatchJobs> entry : this.batchJobs2Commit.entrySet()) {
            System.arraycopy(entry.getValue().getInputs(), 0, nbIn[indexNbIn], 0, INN.FEATURES_PLANES);
            indexNbIn++;
        }
    }

    private int updateCacheValuesAndPolicies(final List<OutputNN> outputsNN) {
        int index = 0;
        for (Map.Entry<Long, InputForBatchJobs> entry : this.batchJobs2Commit.entrySet()) {
            Move move = entry.getValue().getMove();
            Alliance color2play = entry.getValue().getColor2play();
            long key = entry.getKey();
            double value = outputsNN.get(index).getValue();
            double[] policies = outputsNN.get(index).getPolicies();
            if (Arrays.stream(policies).sum() == 0) {
                log.error("ERROR, no policy > 0");
                throw new Error("ERROR, no policy > 0");
            }
            CacheValues.CacheValue cacheValue = this.deepLearningAGZ.getCacheValues().updateValueAndPolicies(key, value, policies);
            if (propagationValues.containsKey(key)) {
                CacheValues.CacheValue oldCacheValue = propagationValues.get(key);
                if (oldCacheValue.hashCode() != cacheValue.hashCode() &&
                        oldCacheValue.getType() != CacheValues.CacheValue.CacheValueType.LEAF) {
                    log.error("oldCacheValue[{}]:{}", oldCacheValue.hashCode(), ToStringBuilder.reflectionToString(oldCacheValue, ToStringStyle.JSON_STYLE));
                    log.error("newCacheValue[{}]:{}", cacheValue.hashCode(), ToStringBuilder.reflectionToString(cacheValue, ToStringStyle.JSON_STYLE));
                    throw new Error("OldCacheValue != current cacheValue");
                }
                if (log.isDebugEnabled()) log.debug("CacheValue [{}/{}] already stored on tmpCacheValues", key, move);
                // throw new Error("CacheValue [" + key + "/"+move+"] already stored on tmpCacheValues");
            } else {
                propagationValues.put(key, cacheValue);
                if (log.isDebugEnabled())
                    log.debug("[{}] RETRIEVE value for key:{} -> move:{} value:{}  policies:{},{},{}", color2play, key, move == null ? "null" : move, value, policies[0], policies[1], policies[2]);
            }
            index++;
        }
        return index;
    }

    /**
     * @param nodeP
     * @return
     */
    @Deprecated
    public boolean isOnUpdateList(final MCTSNode nodeP) {
        MCTSNode node = nodeP;
        while (node.getCacheValue().getType() != CacheValues.CacheValue.CacheValueType.ROOT) {
            CacheValues.CacheValue cacheValue = propagationValues.get(node.getKey());
            if (cacheValue == null || cacheValue.isInitialised()) return true;
            // if (cacheValues.containsKey(node.getKey())) return true;
            // if (!node.getCacheValue().isInitialised()) return true;
            node = node.getParent();
        }
        return false;
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
        if (propagationValues.containsKey(key)) return;
        if (possibleMove != null) {
            Alliance possibleMoveColor = possibleMove.getMovedPiece().getPieceAllegiance();
            if (possibleMoveColor != color2play) {
                log.error("Not identical color: move:{} color2play:{}", possibleMove, color2play);
                throw new RuntimeException(String.format("Color not Identical, move:%s color:%s",
                        possibleMove, color2play.toString()));
            }
        }
        batchJobs2Commit.put(key, new InputForBatchJobs(
                possibleMove,
                color2play,
                gameCopy,
                isDirichlet,
                isRootNode));
    }


    public String toString() {
        return String.format("cacheValues.size():%d batchJobs.size:%d", this.propagationValues.size(), this.batchJobs2Commit.size());
    }
}
