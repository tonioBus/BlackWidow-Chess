package com.aquila.chess.strategy.mcts;

import com.aquila.chess.OneStepRecord;
import com.aquila.chess.TrainGame;
import com.aquila.chess.strategy.FixMCTSTreeStrategy;
import com.aquila.chess.strategy.mcts.inputs.lc0.BatchInputsNN;
import com.aquila.chess.strategy.mcts.nnImpls.NNDeep4j;
import com.aquila.chess.strategy.mcts.utils.ConvertValueOutput;
import com.aquila.chess.strategy.mcts.utils.Statistic;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
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

    static final int BATCH_SIZE = 128;

    static final int CACHE_VALUES_SIZE = 80000;

    private final boolean train;

    @Getter
    final INN nn;

    @Getter
    private final CacheValues cacheValues = new CacheValues(CACHE_VALUES_SIZE);

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
            return addRootState(mctsGame, label, node.getColorState().complementary(), statistic);
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
        Alliance color2play = possibleMove.getMovedPiece().getPieceAllegiance();
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
    public synchronized long addRootState(final MCTSGame mctsGame, final String label, final Alliance color2play, final Statistic statistic) {
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

    public CacheValues.CacheValue getBatchedValue(long key, final Move possibleMove, final Statistic statistic) {
        if (!cacheValues.containsKey(key)) {
            String msg = String.format("KEY:%d SHOULD HAVE CREATED MOVE:%s", key, possibleMove);
            log.error(msg);
            log.error("- {}",
                    cacheValues.getValues()
                            .stream()
                            .map(v -> v.getNode() != null ? String.valueOf(v.getNode().getMove()) : "v.getNode:null")
                            .collect(Collectors.joining("\n")));
            throw new RuntimeException(msg);
        }
        CacheValues.CacheValue cacheValue = cacheValues.get(key);
        statistic.nbRetrieveNNValues++;
        return cacheValue;
    }

    public double[] getBatchedPolicies(long key, final Collection<Move> moves, boolean withDirichlet, final Statistic statistic) {
        CacheValues.CacheValue output = cacheValues.get(key);
        if (output != null) {
            if (log.isDebugEnabled())
                log.debug("getBatchedPolicies(): key:{} type:{}", key, output.getType());
            statistic.nbRetrieveNNCachedPolicies++;
            return output.getPolicies();
        } else {
            String msg = String.format("KEY:%d SHOULD HAVE BEEN CREATED", key);
            log.error(msg);
            throw new RuntimeException(msg);
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

    public void train(final TrainGame trainGame) throws IOException {
        if (!train) throw new RuntimeException("DeepLearningAGZ not in train mode");
        this.nn.train(true);
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

    private void trainChunk(final int indexChunk, final int chunkSize, final TrainGame trainGame) {
        final BatchInputsNN inputsForNN = new BatchInputsNN(chunkSize);
        final var policiesForNN = new double[chunkSize][BoardUtils.NUM_TILES_PER_ROW * BoardUtils.NUM_TILES_PER_ROW * 73];
        final var valuesForNN = new double[chunkSize][1];
        final AtomicInteger atomicInteger = new AtomicInteger();
        final double value = trainGame.getValue();
        List<OneStepRecord> inputsList = trainGame.getOneStepRecordList();
        for (int chunkNumber = 0; chunkNumber < chunkSize; chunkNumber++) {
            atomicInteger.set(chunkNumber);
            int gameRound = indexChunk * chunkSize + chunkNumber;
            OneStepRecord oneStepRecord = inputsList.get(gameRound);
            inputsForNN.add(oneStepRecord);
            Map<Integer, Double> policies = oneStepRecord.policies();
            // actual reward for current state (inputs), so color complement color2play
            // if color2play is WHITE, the current node is BLACK, so -reward
            Alliance playedColor = oneStepRecord.color2play();
            double actualRewards = getActualRewards(value, playedColor);
            // we train policy when rewards=+1 and color2play=WHITE OR rewards=1 and color2play is BLACK
            double trainPolicy = -actualRewards;
            valuesForNN[chunkNumber][0] = ConvertValueOutput.convertTrainValueToSigmoid(actualRewards); // CHOICES
            // valuesForNN[chunkNumber][0] = oneStepRecord.getExpectedReward(); // CHOICES
            if (policies != null) {
                policies.forEach((indexFromMove, previousPolicies) -> {
                    policiesForNN[atomicInteger.get()][indexFromMove] = previousPolicies;
                });
            }
        }
        log.info("NETWORK FIT[{}]: {}", chunkSize, value);
        nn.fit(inputsForNN.getInputs(), policiesForNN, valuesForNN);
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
