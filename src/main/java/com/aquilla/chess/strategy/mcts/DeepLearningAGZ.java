package com.aquilla.chess.strategy.mcts;

import com.aquilla.chess.Game;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * <p>Network Input</p>
 * The input encoding follows the approach taken for AlphaZero.
 * The main difference is that the move count is no longer encoded — it is technically not required since it’s just some superfluous extra-information. We should
 * also mention that Leela Chess Zero is an ongoing project, and naturally improvements and code changes happen. The input format was subject to such changes
 * as well, for example to cope with chess variants such as Chess960 or Armageddon, or simply to experiment with encodings. The encoding described here is
 * the classic encoding, referred to in source code as INPUT_CLASSICAL_112_PLANE.
 * For those who want to look up things in code, the relevant source files are
 * lc0/src/neural/encoder.cc and lc0/src/neural/encoder_test.cc.
 * The input consists of 112 planes of size 8 × 8. Information w.r.t. the placement
 * of pieces is encoded from the perspective of the player whose current turn it
 * is. Assume that we take that player’s perspective. The first plane encodes
 * the position of our own pawns. The second plane encodes the position of our
 * knights, then our bishops, rooks, queens and finally the king. Starting from
 * plane 6 we encode the position of the enemy’s pawns, then knights, bishops,
 * rooks, queens and the enemy’s king. Plane 12 is set to all ones if one or more
 * repetitions occurred.
 * These 12 planes are repeated to encode not only the current position, but also
 * the seven previous ones. Planes 104 to 107 are set to 1 if White can castle
 * queenside, White can castle kingside, Black can castle queenside and Black can
 * 176 4. MODERN AI APPROACHES - A DEEP DIVE
 * castle kingside (in that order). Plane 108 is set to all ones if it is Black’s turn and
 * to 0 otherwise. Plane 109 encodes the number of moves where no capture has
 * been made and no pawn has been moved, i.e. the 50 moves rule. Plane 110 used
 * to be a move counter, but is simply set to always 0 in current generations of Lc0.
 * Last, plane 111 is set to all ones. This is, as previously mentioned, to help the
 * network detect the edge of the board when using convolutional filters.
 */
@Slf4j
public class DeepLearningAGZ {

    @Getter
    final private ServiceNN serviceNN;

    static final int FIT_CHUNK = 50;

    static final int BATCH_SIZE = 250;

    private final boolean train;

    final INN nn;

    @Getter
    private final CacheValues cacheValues = new CacheValues(20000);

    @Setter
    @Getter
    private FixMCTSTreeStrategy fixMCTSTreeStrategy;

    public DeepLearningAGZ(final INN nn) {
        this(nn, false, BATCH_SIZE);
    }

    public DeepLearningAGZ(final INN nn, boolean train) {
        this(nn, train, BATCH_SIZE);
    }

    public DeepLearningAGZ(final INN nn, boolean train, int batchSize) {
        this.nn = nn;
        this.train = train;
        this.serviceNN = new ServiceNN(this, batchSize);
    }

    public static DeepLearningAGZ initFile(DeepLearningAGZ deepLearningWhite, DeepLearningAGZ deepLearningBlack, int nbGames, UpdateLr updateLr) throws IOException {
        File nnWhiteFile = new File(deepLearningWhite.getFilename());
        File nnBlackFile = new File(deepLearningBlack.getFilename());
        if (!nnWhiteFile.isFile()) deepLearningWhite.nn.save();
        if (!nnBlackFile.isFile()) {
            Files.copy(nnWhiteFile.toPath(), nnBlackFile.toPath());
            NNDeep4j nnBlack = new NNDeep4j(deepLearningBlack.getFilename(), false);
            deepLearningBlack = new DeepLearningAGZ(nnBlack, true);
            deepLearningBlack.setUpdateLr(updateLr, nbGames);
        }
        return deepLearningBlack;
    }

    public void clearAllCaches() {
        serviceNN.clearAll();
        cacheValues.clearCache();
    }

    public Object getNetwork() {
        return null; // FIXME nn.getNetwork();
    }

    public void setUpdateLr(UpdateLr updateLr, int nbGames) {
        nn.setUpdateLr(updateLr, nbGames);
    }

    /**
     * Add a new state for the next NN submission
     *
     * @param gameCopy     the game entity
     * @param label        label kept on cacheValue for debugging
     * @param color2play   color that will play
     * @param possibleMove the move for this state
     * @param isRootNode   true if this is the root node
     * @param isDirichlet  true is we need to apply dirichlet to the root node
     * @return the key used to store the job and the related cacheValue
     */
    public synchronized long addState(final Game gameCopy, final String label, final Alliance color2play, final Move possibleMove, boolean isRootNode, boolean isDirichlet, final Statistic statistic) {
        if (log.isDebugEnabled()) log.debug("[{}] BEGIN addState", Thread.currentThread().getName());
        long key = gameCopy.hashCode(color2play, possibleMove);
        if (!cacheValues.containsKey(key)) {
            if (log.isDebugEnabled())
                log.debug("[{}] CREATE CACHE VALUE:{} move:{} label:{}", color2play, key, possibleMove, label);
            String lastMoves = gameCopy.getLastMoves().stream().map(move -> {
                return move == null ? "-" : move.toString();
            }).collect(Collectors.joining(":"));
            final String labelCacheValue = String.format("Label:%s lastMoves:%s possibleMove:%s", label, lastMoves, possibleMove == null ? "ROOT" : possibleMove);
            cacheValues.create(key, labelCacheValue);
            if (!serviceNN.containsJob(key)) statistic.nbSubmitJobs++;
            serviceNN.submit(key, possibleMove, color2play, gameCopy, isDirichlet, isRootNode);
        } else {
            statistic.nbRetrieveNNCachedValues++;
        }
        if (log.isDebugEnabled()) log.debug("[{}] END addState", Thread.currentThread().getName());
        return key;
    }

    public synchronized long removeState(final Game gameCopy, final Alliance color2play, final Move possibleMove) {
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

    public CacheValues.CacheValue getBatchedValue(long key, Move possibleMove, final Statistic statistic) {
        if (!cacheValues.containsKey(key)) {
            String msg = String.format("KEY:%d SHOULD HAVE BEEN CREATED MOVE:%s", key, possibleMove);
            log.error(msg);
            log.error("- {}",
                    cacheValues.getValues()
                            .stream()
                            .map(v -> v.getNode().getMove().toString())
                            .collect(Collectors.joining("\n")));
            throw new RuntimeException(msg);
        }
        CacheValues.CacheValue cacheValue = cacheValues.get(key);
        return cacheValue;
    }

    public double[] getBatchedPolicies(final Game gameCopy, final Alliance currentColor, long key, final List<Move> moves, boolean withDirichlet, final Statistic statistic) {
        CacheValues.CacheValue output = cacheValues.get(key);
        if (output != null) {
            if (log.isDebugEnabled())
                log.debug("getBatchedPolicies(): key:{} type:{} color:{}", key, output.getType(), currentColor);
            statistic.nbRetrieveNNCachedPolicies++;
            if (!output.isInitialised()) return output.getPolicies();
            output.normalize(moves, withDirichlet);
            return output.getPolicies();
        } else {
            String msg = String.format("KEY:%d SHOULD HAVE BEEN CREATED", key);
            log.error(msg);
            throw new RuntimeException(msg);
            // return new double[PolicyUtils.MAX_POLICY_INDEX];
        }
    }

    public double getScore() {
        return nn.getScore();
    }

    public void save() throws IOException {
        log.info("SAVING NNDeep4j ... (do not stop the jvm)");
        nn.save();
        log.info("SAVE DONE");
    }

    public void flushJob(boolean force, final Statistic statistic) throws ExecutionException {
        log.debug("FLUSH JOB");
        this.serviceNN.executeJobs(force, statistic);
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

    public void save(final ResultGame resultGame, int numGame) throws IOException {
        // FIXME TrainGame trainGame = new TrainGame();
        // FIXME trainGame.save(numGame, resultGame);
    }

    /**
     * FIXME
     public void train(final TrainGame trainGame) throws IOException {
     if (!train) throw new RuntimeException("DeepLearningAGZ nbot in train mode");
     final int nbStep = trainGame.getOneStepRecordList().size();
     log.info("NETWORK TO FIT[{}]: {}", nbStep, trainGame.getValue());
     int nbChunk = nbStep / FIT_CHUNK;
     int restChunk = nbStep % FIT_CHUNK;
     for (int indexChunk = 0; indexChunk < nbChunk; indexChunk++) {
     trainChunk(indexChunk, FIT_CHUNK, trainGame);
     }
     if (restChunk > 0) {
     trainChunk(nbChunk, restChunk, trainGame);
     }
     double score = nn.getScore();
     log.info("NETWORK score: {}", score);
     if ("NaN".equals(score + "")) throw new IOException("NN score not defined (0 / 0 ?), the saving will not work");
     }
     */


    /**
     * @param value
     * @param color2play
     * @return
     */
    public static double getActualRewards(final double value, final Alliance color2play) {
        int sign = 0;
        if (color2play.isWhite()) {
            sign = -1;
        } else {
            sign = 1;
        }
        return sign * value;
    }

    /**
     *
     * @param indexChunk
     * @param chunkSize
     * @param trainGame
     */
    /**
     * FIXME
     * private void trainChunk(final int indexChunk, final int chunkSize, final TrainGame trainGame) {
     * double[][][][] inputsForNN = new double[chunkSize][DL4JAlphaGoZeroBuilder.FEATURES_PLANES][Board.NB_COL][Board.NB_COL];
     * double[][] policiesForNN = new double[chunkSize][Board.NB_COL * Board.NB_COL * 73];
     * double[][] valuesForNN = new double[chunkSize][1];
     * final AtomicInteger atomicInteger = new AtomicInteger();
     * Double value = trainGame.getValue();
     * List<OneStepRecord> inputsList = trainGame.getOneStepRecordList();
     * for (int chunkNumber = 0; chunkNumber < chunkSize; chunkNumber++) {
     * atomicInteger.set(chunkNumber);
     * int gameRound = indexChunk * chunkSize + chunkNumber;
     * OneStepRecord oneStepRecord = inputsList.get(gameRound);
     * inputsForNN[chunkNumber] = oneStepRecord.getInputs();
     * Map<Integer, Double> policies = oneStepRecord.getPolicies();
     * // actual reward for current state (inputs), so color complement color2play
     * double actualRewards = getActualRewards(value, oneStepRecord.getColor2play());
     * // we train policy when rewards=+1 and color2play=WHITE OR rewards=1 and color2play is BLACK
     * double trainPolicy = -actualRewards;
     * valuesForNN[chunkNumber][0] = actualRewards; // CHOICES
     * // valuesForNN[chunkNumber][0] = oneStepRecord.getExpectedReward(); // CHOICES
     * if (policies != null) {
     * // we train the policy only when we will move from the loosing player
     * if (trainPolicy > 0) {
     * policies.forEach((indexFromMove, previousPolicies) -> {
     * policiesForNN[atomicInteger.get()][indexFromMove] = previousPolicies;
     * });
     * }
     * }
     * }
     * log.info("NETWORK FIT[{}]: {}", chunkSize, value);
     * nn.fit(inputsForNN, policiesForNN, valuesForNN);
     * }
     */


    public String getFilename() {
        return nn.getFilename();
    }

    public void addTerminalNodeToPropagate(long key, final MCTSNode node) {
        CacheValues.CacheValue cacheValue = node.getCacheValue();
        if (cacheValue != null) {
            cacheValue.incPropagate();
            this.serviceNN.addValueToPropagate(key, cacheValue);
        } else {
            this.serviceNN.addValueToPropagate(key, node.getCacheValue());
        }
    }

}
