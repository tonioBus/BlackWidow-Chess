package com.aquila.chess.strategy.mcts;

import com.aquila.chess.TrainGame;
import com.aquila.chess.strategy.FixMCTSTreeStrategy;
import com.aquila.chess.strategy.check.GameChecker;
import com.aquila.chess.strategy.mcts.inputs.*;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ExecutionException;
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

    static final int CACHE_VALUES_SIZE = 40000;

    @Getter
    private int batchSize;

    @Getter
    private final int nbFeaturesPlanes;

    @Getter
    private boolean train;

    @Getter
    final INN nn;

    @Getter
    private final CacheValues cacheValues = new CacheValues(CACHE_VALUES_SIZE);

    @Setter
    @Getter
    private FixMCTSTreeStrategy fixMCTSTreeStrategy;

    private final InputsManager inputsManager;

    @Builder
    public DeepLearningAGZ(final INN nn, boolean train, int batchSize, final InputsManager inputsManager) {
        this(nn, train, batchSize, inputsManager, inputsManager.getNbFeaturesPlanes());
    }

    public DeepLearningAGZ(final INN nn, DeepLearningAGZ deepLearningAGZ) {
        this(nn, deepLearningAGZ.isTrain(), deepLearningAGZ.getBatchSize(), deepLearningAGZ.inputsManager, deepLearningAGZ.getNbFeaturesPlanes());
    }

    private DeepLearningAGZ(final INN nn, boolean train, int batchSize, InputsManager inputsManager, int nbFeaturesPlanes) {
        if (batchSize <= 0) throw new RuntimeException("BatchSize should be > 0");
        this.nn = nn;
        this.train = train;
        this.batchSize = batchSize;
        this.nbFeaturesPlanes = nbFeaturesPlanes;
        this.inputsManager = inputsManager;
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
     * Add a new state for the next NN submission
     *
     * @param mctsGame     the game entity
     * @param label        label kept on cacheValue for debugging
     * @param possibleMove the move for this state
     * @return the key used to store the job and the related cacheValue
     */
    public synchronized long addState(final MCTSGame mctsGame, final String label, final double initValue, final Move possibleMove, final Statistic statistic) {
        if (log.isDebugEnabled()) log.debug("[{}] BEGIN addState", Thread.currentThread().getName());
        Alliance moveColor = possibleMove.getAllegiance();
        long key = mctsGame.hashCode(possibleMove);
        if (!cacheValues.containsKey(key)) {
            if (log.isDebugEnabled())
                log.debug("CREATE CACHE VALUE:{} move:{} label:{}", key, possibleMove, label);
            final String labelCacheValue = String.format("Label:%s possibleMove:%s", label, possibleMove == null ? "ROOT" : possibleMove);
            cacheValues.create(key, labelCacheValue, initValue);
            if (!serviceNN.containsJob(key)) statistic.nbSubmitJobs++;
            serviceNN.submit(key, possibleMove, moveColor, mctsGame, false, false);
        } else {
            statistic.nbRetrieveNNCachedValues++;
        }
        if (log.isDebugEnabled()) log.debug("[{}] END addState", Thread.currentThread().getName());
        return key;
    }

    /**
     * @param mctsGame
     * @param label
     * @param moveColor
     * @param statistic
     * @return
     */
    public synchronized long addRootCacheValue(final MCTSGame mctsGame, final String label, final double initValue, final Alliance moveColor, final Statistic statistic) {
        log.debug("[{}] BEGIN addRootState:{}", Thread.currentThread().getName(), label);
        long key = mctsGame.hashCode(moveColor);
        if (!cacheValues.containsKey(key)) {
            if (log.isDebugEnabled())
                log.debug("[{}] CREATE ROOT CACHE VALUE:{} move:root label:{}", moveColor, key, label);
            final String labelCacheValue = String.format("Label:%s possibleMove:%s", label, "ROOT");
            cacheValues.create(key, labelCacheValue, initValue);
            if (!serviceNN.containsJob(key)) statistic.nbSubmitJobs++;
            serviceNN.submit(key, null, moveColor, mctsGame, true, true);
        } else {
            statistic.nbRetrieveNNCachedValues++;
        }
        log.debug("[{}] END addRootState:{}", Thread.currentThread().getName(), label);
        return key;
    }

    public synchronized long removeState(final MCTSGame gameCopy, final Alliance moveColor, final Move possibleMove) {
        if (log.isDebugEnabled()) log.debug("[{}] BEGIN removeState", Thread.currentThread().getName());
        long key = gameCopy.hashCode(moveColor, possibleMove);
        if (serviceNN.containsJob(key)) {
            if (log.isDebugEnabled()) log.debug("[{}] DELETE KEY:{} move:{}", moveColor, key, possibleMove);
            serviceNN.removeJob(key);
        } else {
            if (log.isDebugEnabled()) log.debug("[{}] CAN NOT DELETE KEY:{} move:{}", moveColor, key, possibleMove);
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

    private LinkedList<OneStepRecord> checkGame(final TrainGame trainGame, final String label) throws TrainException {
        log.info("Check training size: {}", trainGame.getOneStepRecordList().size(), label);
        final GameChecker gameChecker = new GameChecker(inputsManager, label);
        LinkedList<OneStepRecord> ret = new LinkedList<>();
        try {
            for (OneStepRecord oneStepRecord : trainGame.getOneStepRecordList()) {
                String moveSz = oneStepRecord.move();
                gameChecker.play(moveSz);
                InputRecord inputRecord = new InputRecord(gameChecker, gameChecker.getMoves(), gameChecker.getLastMove(), gameChecker.getCurrentPLayerColor().complementary());
                InputsFullNN inputsFullNN = inputsManager.createInputs(inputRecord);
                OneStepRecord newOneStepRecord = new OneStepRecord(
                        inputsFullNN,
                        oneStepRecord.move(),
                        oneStepRecord.moveColor(),
                        oneStepRecord.policies());
                ret.add(newOneStepRecord);
            }
        } catch (TrainException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Bad saved game", e);
            log.error("!! Error during loading of game: only using {} steps", ret.size());
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        log.info("Check done: {}", ret.size());
        return ret;
    }

    /**
     * Train the NN by sending batches of fitChunk size
     * @param trainGame
     * @param fitChunk
     * @param statisticsFit
     * @throws IOException
     * @throws TrainException
     */
    public void train(final TrainGame trainGame, final int fitChunk, final StatisticsFit statisticsFit) throws IOException, TrainException {
        if (!train) throw new RuntimeException("DeepLearningAGZ not in train mode");
        LinkedList<OneStepRecord> correctStepRecord = checkGame(trainGame, String.valueOf(trainGame.getNum()));
        int nbCorrectStep = correctStepRecord.size();
        final int nbStep = trainGame.getOneStepRecordList().size();
        log.info("Current Check:{} <-> {}:Correct Step", nbCorrectStep, nbStep);
        trainGame.setOneStepRecordList(correctStepRecord);
        this.nn.train(true);
        log.info("NETWORK TO FIT[{}]: {}", nbCorrectStep, trainGame.getValue());
        int nbChunk = nbCorrectStep / fitChunk;
        int restChunk = nbCorrectStep % fitChunk;
        switch (trainGame.getValue().intValue()) {
            case 1 -> {
                statisticsFit.nbWin++;
            }
            case -1 -> {
                statisticsFit.nbLost++;
            }
            case 0 -> {
                statisticsFit.nbDrawn++;
            }
        }
        for (int indexChunk = 0; indexChunk < nbChunk; indexChunk++) {
            trainChunk(indexChunk, fitChunk, trainGame, statisticsFit);
        }
        if (restChunk > 0) {
            trainChunk(nbChunk, restChunk, trainGame, statisticsFit);
        }
        statisticsFit.nbTrainGame++;
    }

    private void trainChunk(final int indexChunk, final int chunkSize, final TrainGame trainGame, final StatisticsFit statisticsFit) throws TrainException {
        final TrainInputs inputsForNN = new TrainInputs(chunkSize);
        final var policiesForNN = new double[chunkSize][BoardUtils.NUM_TILES_PER_ROW * BoardUtils.NUM_TILES_PER_ROW * 73];
        final var valuesForNN = new double[chunkSize][1];
        final double value = trainGame.getValue();
        List<OneStepRecord> inputsList = trainGame.getOneStepRecordList();

        for (int stepInChunk = 0; stepInChunk < chunkSize; stepInChunk++) {
            int gameRound = indexChunk * chunkSize + stepInChunk;
            OneStepRecord oneStepRecord = inputsList.get(gameRound);
            inputsForNN.add(oneStepRecord);
            Map<Integer, Double> policies = inputsList.get(gameRound).policies();
            OptionalDouble maxPolicy = policies.values().stream().mapToDouble(policy -> policy).max();
            if (maxPolicy.isPresent()) {
                policies.keySet().stream().filter(key -> policies.get(key) == maxPolicy.getAsDouble()).forEach(key -> {
                    log.info("MAX POLICY[{}]={}", key, maxPolicy);
                });
            } else log.error("max policies not present");
            //FIXME
            // policies.keySet().stream().filter(key -> policies.get(key) != 1.0).forEach(key -> policies.put(key, 0.1));
            //END FIXME
            normalize(policies);
            Alliance moveColor = oneStepRecord.moveColor();
            double actualRewards = getActualRewards(value, moveColor);
            valuesForNN[stepInChunk][0] = ConvertValueOutput.convertTrainValueToSigmoid(actualRewards);
            for (Map.Entry<Integer, Double> entry : policies.entrySet()) {
                Integer indexFromMove = entry.getKey();
                Double previousPolicies = entry.getValue();
                policiesForNN[stepInChunk][indexFromMove] = previousPolicies;
            }
        }
        log.info("NETWORK FIT[{}]: {}", chunkSize, value);
        nn.fit(inputsForNN.getInputs(), policiesForNN, valuesForNN);
        statisticsFit.nbInputsFit += chunkSize;
        double score = nn.getScore();
        if (score < statisticsFit.scoreMin) statisticsFit.scoreMin = score;
        if (score > statisticsFit.scoreMax) statisticsFit.scoreMax = score;
        log.info("NETWORK score: {}", score);
        if ("NaN".equals(score + "")) {
            log.error("NN score not defined (0 / 0 ?), the saving is canceled :(");
            throw new TrainException("NN score not defined (0 / 0 ?)", String.valueOf(trainGame.getNum()));
        }
    }

    private void normalize(final Map<Integer, Double> policyMap) {
        double sum = 0;
        for (Map.Entry<Integer, Double> policyEntry : policyMap.entrySet()) {
            double policy = policyEntry.getValue();
            if (Double.isNaN(policy) || Double.isInfinite(policy)) {
                policy = 1.0;
                policyEntry.setValue(policy);
            }
            sum += policy;
        }
        if (policyMap.size() > 0 && sum == 0) {
            final double policy = 0.0;
            log.warn("toDistribution(): sum of policies(nb:{})==0 correction:{}", policyMap.size(), policy);
            for (Map.Entry<Integer, Double> policyEntry : policyMap.entrySet()) {
                policyEntry.setValue(policy);
            }
        } else {
            for (Map.Entry<Integer, Double> policyEntry : policyMap.entrySet()) {
                double policy = policyEntry.getValue();
                policy = policy / sum;
                if (policy < 0.0) policy = 0.0;
                if (policy > 1.0) policy = 1.0;
                policyEntry.setValue(policy);
            }
        }
    }


    /**
     * <ul>
     * <li>WHITE WIN: value:+1 -> moveColor: WHITE:+1 BLACK:-1</li>
     * <li>BLACK WIN: value:-1 -> moveColor: WHITE:-1 BLACK:+1</li>
     * </ul>
     * @param value
     * @param moveColor
     * @return
     */
    public static double getActualRewards(final double value, final Alliance moveColor) {
        int sign = 0;
        if (moveColor.isWhite()) {
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
