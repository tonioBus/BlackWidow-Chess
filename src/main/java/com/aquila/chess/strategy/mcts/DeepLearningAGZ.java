package com.aquila.chess.strategy.mcts;

import com.aquila.chess.TrainGame;
import com.aquila.chess.strategy.FixMCTSTreeStrategy;
import com.aquila.chess.strategy.check.GameChecker;
import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.OneStepRecord;
import com.aquila.chess.strategy.mcts.inputs.TrainInputs;
import com.aquila.chess.strategy.mcts.nnImpls.NNDeep4j;
import com.aquila.chess.strategy.mcts.utils.ConvertValueOutput;
import com.aquila.chess.strategy.mcts.utils.Statistic;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * <h2>Network Input</h2>
 * <p>The input encoding follows the approach taken for AlphaZero.
 * The main difference is that the move count is no longer encoded — it is technically not required since it’s just some superfluous extra-information. We should
 * also mention that Leela Chess Zero is an ongoing project, and naturally improvements and code changes happen. The input format was subject to such changes
 * as well, for example to cope with chess variants such as Chess960 or Armageddon, or simply to experiment with encodings. The encoding described here is
 * the classic encoding, referred to in source code as INPUT_CLASSICAL_112_PLANE.
 * For those who want to look up things in code, the relevant source files are
 * lc0/src/neural/encoder.cc and lc0/src/neural/encoder_test.cc.
 * The input consists of 112 planes of size 8 × 8. Information w.r.t. the placement
 * of pieces is encoded from the perspective of the player whose current turn it
 * is. Assume that we take that player’s perspective.
 * <p>The first plane encodes
 * the position of our own pawns. </p>
 * <p>The second plane encodes the position of our
 * knights, then our bishops, rooks, queens and finally the king. Starting from
 * plane 6 we encode the position of the enemy’s pawns, then knights, bishops,
 * rooks, queens and the enemy’s king. Plane 12 is set to all ones if one or more
 * repetitions occurred.</p>
 * <p>These 12 planes are repeated to encode not only the current position, but also
 * the seven previous ones.
 * <p>Planes 104 to 107 are set to 1 if White can castle
 * queenside, White can castle kingside, Black can castle queenside and Black can</p>
 * 176 4. MODERN AI APPROACHES - A DEEP DIVE
 * castle kingside (in that order). Plane 108 is set to all ones if it is Black’s turn and
 * to 0 otherwise. Plane 109 encodes the number of moves where no capture has
 * been made and no pawn has been moved, i.e. the 50 moves rule. Plane 110 used
 * to be a move counter, but is simply set to always 0 in current generations of Lc0.
 * Last, plane 111 is set to all ones. This is, as previously mentioned, to help the
 * network detect the edge of the board when using convolutional filters.</p>
 */
@Slf4j
public class DeepLearningAGZ {

    @Getter
    final private ServiceNN serviceNN;

    static final int FIT_CHUNK = 20;

    static final int CACHE_VALUES_SIZE = 40000;

    @Getter
    private int batchSize;

    @Getter
    private final int nbFeaturesPlanes;

    @Getter
    private boolean train = false;

    @Getter
    final INN nn;

    @Getter
    private final CacheValues cacheValues = new CacheValues(CACHE_VALUES_SIZE);

    @Setter
    @Getter
    private FixMCTSTreeStrategy fixMCTSTreeStrategy;

    @Builder
    public DeepLearningAGZ(final INN nn, boolean train, int batchSize, final InputsManager inputsManager) {
        this(nn, train, batchSize, inputsManager.getNbFeaturesPlanes());
    }

    public DeepLearningAGZ(final INN nn, DeepLearningAGZ deepLearningAGZ) {
        this(nn, deepLearningAGZ.isTrain(), deepLearningAGZ.getBatchSize(), deepLearningAGZ.getNbFeaturesPlanes());
    }

    private DeepLearningAGZ(final INN nn, boolean train, int batchSize, int nbFeaturesPlanes) {
        if (batchSize <= 0) throw new RuntimeException("BatchSize should be > 0");
        this.nn = nn;
        this.train = train;
        this.batchSize = batchSize;
        this.nbFeaturesPlanes = nbFeaturesPlanes;
        this.serviceNN = ServiceNN.builder()
                .deepLearningAGZ(this)
                .nbFeaturesPlanes(nbFeaturesPlanes)
                .batchSize(batchSize)
                .build();
    }

    public static DeepLearningAGZ initNNFile(InputsManager inputsManager, final DeepLearningAGZ deepLearningWhite, final DeepLearningAGZ deepLearningBlack, int nbGames, UpdateLr updateLr) throws IOException {
        File nnWhiteFile = new File(deepLearningWhite.getFilename());
        File nnBlackFile = new File(deepLearningBlack.getFilename());
        if (!nnWhiteFile.isFile()) deepLearningWhite.nn.save();
        final DeepLearningAGZ retDeepLearningBlack;
        if (!nnBlackFile.isFile()) {
            Files.copy(nnWhiteFile.toPath(), nnBlackFile.toPath());
            NNDeep4j nnBlack = new NNDeep4j(deepLearningBlack.getFilename(), false, inputsManager.getNbFeaturesPlanes(),
                    ((NNDeep4j) deepLearningBlack.nn).numberResidualBlocks);
            retDeepLearningBlack = new DeepLearningAGZ(nnBlack, deepLearningWhite);
            if (updateLr != null) deepLearningBlack.setUpdateLr(updateLr, nbGames);
        } else {
            retDeepLearningBlack = deepLearningBlack;
        }
        return retDeepLearningBlack;
    }

    public void clearAllCaches() {
        serviceNN.clearAll();
        cacheValues.clearCache();
    }

    public Object getNetwork() {
        return nn.getNetwork();
    }

    public void setUpdateLr(UpdateLr updateLr, int nbGames) {
        nn.setUpdateLr(updateLr, nbGames);
    }

    /**
     * @param mctsGame
     * @param label
     * @param node
     * @param statistic
     * @return
     */
    public long addState(final MCTSGame mctsGame, final String label, final MCTSNode node, final Statistic statistic) {
        if (node.getMove() == null)
            return addRootCacheValue(mctsGame, label, node.getColorState().complementary(), statistic);
        final Move possibleMove = node.getMove();
        return addState(mctsGame, label, possibleMove, statistic);
    }

    /**
     * Add a new state for the next NN submission
     *
     * @param mctsGame     the game entity
     * @param label        label kept on cacheValue for debugging
     * @param possibleMove the move for this state
     * @return the key used to store the job and the related cacheValue
     */
    public synchronized long addState(final MCTSGame mctsGame, final String label, final Move possibleMove, final Statistic statistic) {
        if (log.isDebugEnabled()) log.debug("[{}] BEGIN addState", Thread.currentThread().getName());
        Alliance color2play = possibleMove.getAllegiance();
        long key = mctsGame.hashCode(possibleMove);
        if (!cacheValues.containsKey(key)) {
            if (log.isDebugEnabled())
                log.debug("CREATE CACHE VALUE:{} move:{} label:{}", key, possibleMove, label);
            String lastMoves = mctsGame.getMoves().stream().map(
                            move -> move == null ? "-" : move.toString()).
                    collect(Collectors.joining(":"));
            final String labelCacheValue = String.format("Label:%s lastMoves:%s possibleMove:%s", label, lastMoves, possibleMove == null ? "ROOT" : possibleMove);
            cacheValues.create(key, labelCacheValue);
            if (!serviceNN.containsJob(key)) statistic.nbSubmitJobs++;
            serviceNN.submit(key, possibleMove, color2play, mctsGame, false, false);
        } else {
            statistic.nbRetrieveNNCachedValues++;
        }
        if (log.isDebugEnabled()) log.debug("[{}] END addState", Thread.currentThread().getName());
        return key;
    }

    /**
     * @param mctsGame
     * @param label
     * @param color2play
     * @param statistic
     * @return
     */
    public synchronized long addRootCacheValue(final MCTSGame mctsGame, final String label, final Alliance color2play, final Statistic statistic) {
        if (log.isDebugEnabled()) log.debug("[{}] BEGIN addRootState", Thread.currentThread().getName());
        long key = mctsGame.hashCode(color2play);
        if (!cacheValues.containsKey(key)) {
            if (log.isDebugEnabled())
                log.debug("[{}] CREATE ROOT CACHE VALUE:{} move:root label:{}", color2play, key, label);
            String lastMoves = mctsGame.getMoves().stream().map(
                            move -> move == null ? "-" : move.toString()).
                    collect(Collectors.joining(":"));
            final String labelCacheValue = String.format("Label:%s lastMoves:%s possibleMove:%s", label, lastMoves, "ROOT");
            cacheValues.create(key, labelCacheValue);
            if (!serviceNN.containsJob(key)) statistic.nbSubmitJobs++;
            serviceNN.submit(key, null, color2play, mctsGame, true, true);
        } else {
            statistic.nbRetrieveNNCachedValues++;
        }
        if (log.isDebugEnabled()) log.debug("[{}] END addRootState", Thread.currentThread().getName());
        return key;
    }

    public synchronized long removeState(final MCTSGame gameCopy, final Alliance color2play, final Move possibleMove) {
        if (log.isDebugEnabled()) log.debug("[{}] BEGIN removeState", Thread.currentThread().getName());
        long key = gameCopy.hashCode(color2play, possibleMove);
        if (serviceNN.containsJob(key)) {
            if (log.isDebugEnabled()) log.debug("[{}] DELETE KEY:{} move:{}", color2play, key, possibleMove);
            serviceNN.removeJob(key);
        } else {
            if (log.isDebugEnabled()) log.debug("[{}] CAN NOT DELETE KEY:{} move:{}", color2play, key, possibleMove);
        }
        if (log.isDebugEnabled()) log.debug("[{}] END removeState", Thread.currentThread().getName());
        return key;
    }

    public CacheValue getBatchedValue(long key, final Move possibleMove, final Statistic statistic) {
        if (!cacheValues.containsKey(key)) {
            String msg = String.format("KEY:%d SHOULD HAVE CREATED MOVE:%s", key, possibleMove);
            log.error(msg);
            log.error("- {}", cacheValues
                    .getValues()
                    .stream()
                    .map(cacheValue -> cacheValue.toString())
                    .collect(Collectors.joining("\n")));
            throw new RuntimeException(msg);
        }
        CacheValue cacheValue = cacheValues.get(key);
        statistic.nbRetrieveNNValues++;
        return cacheValue;
    }

//    @Deprecated
//    public double[] getBatchedPolicies(long key, final Statistic statistic) {
//        CacheValue output = cacheValues.get(key);
//        if (output != null) {
//            if (log.isDebugEnabled())
//                log.debug("getBatchedPolicies(): key:{}", key);
//            statistic.nbRetrieveNNCachedPolicies++;
//            return output.getPolicies();
//        } else {
//            String msg = String.format("KEY:%d SHOULD HAVE BEEN CREATED", key);
//            log.error(msg);
//            throw new RuntimeException(msg);
//        }
//    }

    public double getScore() {
        return nn.getScore();
    }

    public void save() throws IOException {
        log.info("SAVING NNDeep4j ... (do not stop the jvm)");
        nn.save();
        log.info("SAVE DONE");
    }

    public void flushJob(boolean force) throws ExecutionException {
        log.debug("FLUSH JOB");
        this.serviceNN.executeJobs(force);
    }

    public int getCacheSize() {
        return this.cacheValues.size();
    }

    public String ToStringInputs(final double[][] nbIn) {
        final StringBuffer sb = new StringBuffer();
        for (int y = BoardUtils.NUM_TILES_PER_ROW - 1; y >= 0; y--) {
            sb.append(String.format("[%3d] [%3d] [%3d] [%3d] [%3d] [%3d] [%3d] [%3d]\n", //
                    (int) nbIn[0][y], //
                    (int) nbIn[1][y], //
                    (int) nbIn[2][y], //
                    (int) nbIn[3][y], //
                    (int) nbIn[4][y], //
                    (int) nbIn[5][y], //
                    (int) nbIn[6][y], //
                    (int) nbIn[7][y])); //
        }
        return sb.toString();
    }

    private LinkedList<OneStepRecord> checkGame(final TrainGame trainGame) {
        log.info("Check training size: {}", trainGame.getOneStepRecordList().size());
        final GameChecker gameChecker = new GameChecker();
        LinkedList<OneStepRecord> ret = new LinkedList<>();
        try {
            for (OneStepRecord oneStepRecord : trainGame.getOneStepRecordList()) {
                final Collection<Move> currentMoves = gameChecker.getCurrentLegalMoves();
                String moveSz = oneStepRecord.move();
                InputsFullNN inputsNN = gameChecker.play(moveSz);
                OneStepRecord newOneStepRecord = new OneStepRecord(
                        inputsNN,
                        oneStepRecord.move(),
                        oneStepRecord.color2play(),
                        oneStepRecord.policies());
                ret.add(newOneStepRecord);
            }
        } catch (RuntimeException e) {
            log.error("Bad saved game", e);
            log.error("!! Error during loading of game: only using {} steps", ret.size());
            // return ret;
            throw e;
        }
        log.info("Check done: {}", ret.size());
        return ret;
    }

    /**
     * Train the NN by sending batches of FIT_CHUNK size
     *
     * @param trainGame
     * @throws IOException
     */
    public void train(final TrainGame trainGame) throws IOException {
        if (!train) throw new RuntimeException("DeepLearningAGZ not in train mode");
        LinkedList<OneStepRecord> correctStepRecord = checkGame(trainGame);
        int nbCorrectStep = correctStepRecord.size();
        final int nbStep = trainGame.getOneStepRecordList().size();
        log.info("Current Check:{} <-> {}:Correct Step", nbCorrectStep, nbStep);
        trainGame.setOneStepRecordList(correctStepRecord);
        this.nn.train(true);
        log.info("NETWORK TO FIT[{}]: {}", nbCorrectStep, trainGame.getValue());
        int nbChunk = nbCorrectStep / FIT_CHUNK;
        int restChunk = nbCorrectStep % FIT_CHUNK;
        for (int indexChunk = 0; indexChunk < nbChunk; indexChunk++) {
            trainChunk(indexChunk, FIT_CHUNK, trainGame);
        }
        if (restChunk > 0) {
            trainChunk(nbChunk, restChunk, trainGame);
        }
    }

    private void trainChunk(final int indexChunk, final int chunkSize, final TrainGame trainGame) {
        final TrainInputs inputsForNN = new TrainInputs(chunkSize);
        final var policiesForNN = new double[chunkSize][BoardUtils.NUM_TILES_PER_ROW * BoardUtils.NUM_TILES_PER_ROW * 73];
        final var valuesForNN = new double[chunkSize][1];
        final AtomicInteger atomicInteger = new AtomicInteger();
        final double value = trainGame.getValue();
        List<OneStepRecord> inputsList = trainGame.getOneStepRecordList();

        for (int stepInChunk = 0; stepInChunk < chunkSize; stepInChunk++) {
            atomicInteger.set(stepInChunk);
            int gameRound = indexChunk * chunkSize + stepInChunk;
            OneStepRecord oneStepRecord = inputsList.get(gameRound);
            inputsForNN.add(oneStepRecord);
            Map<Integer, Double> policies = inputsList.get(gameRound).policies();
            normalize(policies);
            Alliance playedColor = oneStepRecord.color2play();
            double actualRewards = getActualRewards(value, playedColor);
            valuesForNN[stepInChunk][0] = ConvertValueOutput.convertTrainValueToSigmoid(actualRewards);
            if (policies != null) {
                policies.forEach((indexFromMove, previousPolicies) -> {
                    policiesForNN[atomicInteger.get()][indexFromMove] = previousPolicies;
                });
            }
        }
        log.info("NETWORK FIT[{}]: {}", chunkSize, value);
        nn.fit(inputsForNN.getInputs(), policiesForNN, valuesForNN);
        double score = nn.getScore();
        log.info("NETWORK score: {}", score);
        if ("NaN".equals(score + "")) {
            log.error("NN score not defined (0 / 0 ?), the saving is canceled :(");
            System.exit(-1);
        }
    }

    private void normalize(final Map<Integer, Double> policyMap) {
        double sum = 0;
        for (Map.Entry<Integer, Double> policyEntry : policyMap.entrySet()) {
            double policy = policyEntry.getValue();
            if (Double.isNaN(policy)) {
                policy = 1.0;
                policyEntry.setValue(policy);
            }
            sum += policy;
        }
        if (policyMap.size() > 0 && sum == 0) {
            log.warn("toDistribution(): sum of policies(nb:{})==0", policyMap.size());
            return;
        }
        for (Map.Entry<Integer, Double> policyEntry : policyMap.entrySet()) {
            double policy = policyEntry.getValue();
            policy = policy / sum;
            if (policy < 0.0) policy = 0.0;
            if (policy > 1.0) policy = 1.0;
            policyEntry.setValue(policy);
        }
    }


    /**
     * @param value
     * @param color2play
     * @return
     */
    public static double getActualRewards(final double value, final Alliance color2play) {
        int sign = 0;
        if (color2play.isWhite()) {
            sign = 1;
        } else {
            sign = -1;
        }
        return sign * value;
    }

    public String getFilename() {
        return nn.getFilename();
    }

    /**
     * this will propagate only node not propagated (node.propagated)
     *
     * @param node
     */
    public void addDefinedNodeToPropagate(final MCTSNode node) {
        node.incNbPropationsToExecute();
        this.serviceNN.addNodeToPropagate(node);
    }

}
