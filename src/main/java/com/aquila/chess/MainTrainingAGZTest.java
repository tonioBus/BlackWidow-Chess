package com.aquila.chess;

import com.aquila.chess.manager.GameManager;
import com.aquila.chess.manager.Record.Status;
import com.aquila.chess.manager.Sequence;
import com.aquila.chess.strategy.mcts.*;
import com.aquila.chess.strategy.mcts.nnImpls.NNDeep4j;
import com.aquila.chess.utils.Utils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
public class MainTrainingAGZTest {

    static private final String NN_REFERENCE = "../AGZ_NN/AGZ.reference";

    static private final String NN_OPPONENT = "../AGZ_NN/AGZ.partner";
    private static final int BATCH_SIZE = 50000;
    static public final int NB_STEP = 800;

    static public final int NB_THREADS = 1;

    /**
     * The learning rate was set to 0.2 and dropped to 0.02, 0.002,
     * and 0.0002 after 100, 300, and 500 thousand steps for chess
     */
    public static final UpdateLr updateLr = nbGames -> {
        if (nbGames > 5000) return 1e-6;
        if (nbGames > 3000) return 1e-5;
        if (nbGames > 1000) return 1e-4;
        return 1e-3;
    };

    private static final UpdateCpuct updateCpuct = nbStep -> {
        // return 2.5;
        if (nbStep <= 30) return 2.5;
        else return 0.0025;
        // return 2.0 * Math.exp(-0.01 * nbStep);
    };

    private static final Dirichlet dirichlet = nbStep -> true;

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(final String[] args) throws Exception {
        int lastSaveGame = Utils.maxGame("train/") + 1;
        log.info("START MainTrainingAGZ: game {}", lastSaveGame);
        GameManager gameManager = new GameManager("../AGZ_NN/sequences.csv", 40000, 55);
        MCTSStrategyConfig.DEFAULT_WHITE_INSTANCE.setDirichlet(true);
        MCTSStrategyConfig.DEFAULT_BLACK_INSTANCE.setDirichlet(true);
        INN nnWhite = new NNDeep4j(NN_REFERENCE, false);
        DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nnWhite, false);
        deepLearningWhite.setUpdateLr(updateLr, gameManager.getNbGames());
        INN nnBlack = new NNDeep4j(NN_OPPONENT, false);
        DeepLearningAGZ deepLearningBlack = new DeepLearningAGZ(nnBlack, false);
        deepLearningBlack = DeepLearningAGZ.initFile(deepLearningWhite, deepLearningBlack, gameManager.getNbGames(), updateLr);
        while (true) {
            final Board board = Board.createBoard("kh1,pg6", "pa4,kg3", Alliance.BLACK);
            final Game game = Game.builder().board(board).build();
            Sequence sequence = gameManager.createSequence();
            long seed1 = System.currentTimeMillis();
            log.info("SEED WHITE:{}", seed1);
            deepLearningWhite.clearAllCaches();
            deepLearningBlack.clearAllCaches();
            long seed2 = System.nanoTime();
            log.info("SEED BLACK:{}", seed2);
            final MCTSStrategy whiteStrategy = new MCTSStrategy(
                    game,
                    Alliance.WHITE,
                    deepLearningWhite,
                    seed1,
                    updateCpuct,
                    -1)
                    .withNbSearchCalls(NB_STEP)
                    .withNbThread(NB_THREADS)
                    .withDirichlet(dirichlet);
            final MCTSStrategy blackStrategy = new MCTSStrategy(
                    game,
                    Alliance.BLACK,
                    deepLearningBlack,
                    seed2,
                    updateCpuct,
                    -1)
                    .withNbSearchCalls(NB_STEP)
                    .withNbThread(NB_THREADS)
                    .withDirichlet(dirichlet);
            whiteStrategy.setPartnerStrategy(blackStrategy);
            game.setup(whiteStrategy, blackStrategy);
            Game.GameStatus gameStatus;
            do {
                gameStatus = game.play();
                sequence.play();
                Move move = game.getLastMove();
                log.warn("game:\n{}", game);
                if(sequence.nbStep > 20) {
                    gameStatus= Game.GameStatus.DRAW_300;
                }
            } while (gameStatus == Game.GameStatus.IN_PROGRESS);
            log.info("#########################################################################");
            log.info("END OF game [{}] :\n{}\n{}", gameManager.getNbGames(), gameStatus.toString(), game);
            log.info("#########################################################################");
            ResultGame resultGame = whiteStrategy.getResultGame(gameStatus);
            whiteStrategy.saveBatch(resultGame, lastSaveGame);
            if (lastSaveGame % BATCH_SIZE == 0 && lastSaveGame > 0) {
//                MainFitNN.trainGames("train", lastSaveGame - BATCH_SIZE + 1, lastSaveGame, updateLr, deepLearningWhite);
//                nnWhite.close();
//                nnWhite = new NNDeep4j(NN_REFERENCE, false);
//                deepLearningWhite = new DeepLearningAGZ(nnWhite, false);
//                deepLearningWhite.setUpdateLr(updateLr, gameManager.getNbGames());
            }
            lastSaveGame++;
            Status status = gameManager.endGame(game, deepLearningWhite.getScore(), gameStatus, sequence);
            if (status == Status.SWITCHING) {
                final Path reference = Paths.get(NN_REFERENCE);
                final Path opponent = Paths.get(NN_OPPONENT);
                SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy_hh-mm-ss");
                final Path backupOpponent = Paths.get(NN_OPPONENT + "_" + format.format(new Date()));
                log.info("BACKUP PARTNER {} -> {}", opponent, backupOpponent);
                if (opponent.toFile().canRead()) {
                    Files.copy(opponent, backupOpponent, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.copy(reference, opponent, StandardCopyOption.REPLACE_EXISTING);
                log.info("Switching DP {} <-> {}", reference, opponent);
                nnBlack.close();
                nnBlack = new NNDeep4j(NN_OPPONENT, false);
                deepLearningBlack = new DeepLearningAGZ(nnBlack, false);
                deepLearningBlack.setUpdateLr(updateLr, gameManager.getNbGames());
            }
        }
    }
}
