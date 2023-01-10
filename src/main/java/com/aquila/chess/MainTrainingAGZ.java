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
public class MainTrainingAGZ {

    static private final String NN_REFERENCE = "../AGZ_NN/AGZ.reference";

    static private final String NN_OPPONENT = "../AGZ_NN/AGZ.partner";
    private static final int BATCH_SIZE = 1;
    static public final int NB_STEP = 800;

    /**
     * The learning rate was set to 0.2 and dropped to 0.02, 0.002,
     * and 0.0002 after 100, 300, and 500 thousand steps for chess
     */
    public static final UpdateLr updateLr = nbGames -> {
        if (nbGames > 500000) return 1e-7;
        if (nbGames > 300000) return 1e-6;
        if (nbGames > 100000) return 1e-5;
        return 1e-6;
    };

    private static final UpdateCpuct updateCpuct = nbStep -> {
        return 2.5;
        // if (nbStep <= 30) return 2.5;
        // else return 0.25;
        // return 2.0 * Math.exp(-0.01 * nbStep);
    };

    private static final Dirichlet dirichlet = nbStep -> true; // nbStep <= 30;

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(final String[] args) throws Exception {
        int lastSaveGame = Utils.maxGame("train/") + 1;
        log.info("START MainTrainingAGZ: game {}", lastSaveGame);
        GameManager gameManager = new GameManager("../AGZ_NN/sequences.csv", 40, 55);
        INN nnWhite = new NNDeep4j(NN_REFERENCE, true);
        DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nnWhite, true);
        deepLearningWhite.setUpdateLr(updateLr, gameManager.getNbGames());
        INN nnBlack = new NNDeep4j(NN_OPPONENT, false);
        DeepLearningAGZ deepLearningBlack = new DeepLearningAGZ(nnBlack, false);
        deepLearningBlack = DeepLearningAGZ.initFile(deepLearningWhite, deepLearningBlack, gameManager.getNbGames(), updateLr);
        while (true) {
            final Board board = Board.createStandardBoard();
            final Game game = Game.builder().board(board).build();
            Sequence sequence = gameManager.createSequence();
            long seed = System.nanoTime();
            final MCTSStrategy whiteStrategy = new MCTSStrategy(
                    game,
                    Alliance.WHITE,
                    deepLearningWhite,
                    seed,
                    updateCpuct,
                    -1)
                    .withNbSearchCalls(NB_STEP)
                    .withDirichlet(dirichlet)
                    .withNbThread(1);
            final MCTSStrategy blackStrategy = new MCTSStrategy(
                    game,
                    Alliance.BLACK,
                    deepLearningBlack,
                    seed,
                    updateCpuct,
                    -1)
                    .withNbSearchCalls(NB_STEP)
                    .withDirichlet(dirichlet)
                    .withNbThread(1);
            game.setup(whiteStrategy, blackStrategy);
            Game.GameStatus gameStatus;
            do {
                gameStatus = game.play();
                sequence.play();
                Move move = game.getLastMove();
                log.info("game:\n{}", game.toString());
            } while (gameStatus == Game.GameStatus.IN_PROGRESS);
            log.info("#########################################################################");
            log.info("END OF game [{}] :\n{}\n{}", gameManager.getNbGames(), gameStatus.toString(), game);
            log.info("#########################################################################");
            ResultGame resultGame = whiteStrategy.getResultGame(gameStatus);
            game.saveBatch(resultGame, lastSaveGame);
            if (lastSaveGame % BATCH_SIZE == 0 && lastSaveGame > 0) {
                MainFitNN.trainGames(lastSaveGame - BATCH_SIZE + 1, lastSaveGame, updateLr, deepLearningWhite);
                nnWhite.close();
                nnWhite = new NNDeep4j(NN_REFERENCE, true);
                deepLearningWhite = new DeepLearningAGZ(nnWhite, true);
                deepLearningWhite.setUpdateLr(updateLr, gameManager.getNbGames());
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
                deepLearningBlack = new DeepLearningAGZ(nnBlack, true);
                deepLearningBlack.setUpdateLr(updateLr, gameManager.getNbGames());
            }
        }
    }
}
