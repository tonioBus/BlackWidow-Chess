package com.aquila.chess;

import com.aquila.chess.manager.GameManager;
import com.aquila.chess.manager.Record.Status;
import com.aquila.chess.manager.Sequence;
import com.aquila.chess.strategy.mcts.*;
import com.aquila.chess.strategy.mcts.nnImpls.NNDeep4j;
import com.aquila.chess.strategy.mcts.nnImpls.NNSimul;
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
public class MainTrainingSimulNN {

    static public final int NB_STEP = 800;

    /**
     * The learning rate was set to 0.2 and dropped to 0.02, 0.002,
     * and 0.0002 after 100, 300, and 500 thousand steps for chess
     */
    public static final UpdateLr updateLr = nbGames -> {
        if (nbGames > 500000) return 1e-6;
        if (nbGames > 300000) return 1e-5;
        if (nbGames > 100000) return 1e-4;
        return 1e-3;
    };

    private static final UpdateCpuct updateCpuct = nbStep -> {
        if (nbStep <= 30) return 2.5;
        else return 0.0025;
    };

    private static final Dirichlet dirichlet = nbStep -> true; // nbStep <= 30;

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(final String[] args) throws Exception {
        int lastSaveGame = Utils.maxGame("train/") + 1;
        log.info("START MainTrainingAGZ: game {}", lastSaveGame);
        GameManager gameManager = new GameManager("../AGZ_NN/sequences.csv", 40, 55);
        INN nnWhite = new NNSimul(1);
        DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nnWhite, true);
        // deepLearningWhite.setUpdateLr(updateLr, gameManager.getNbGames());
        INN nnBlack = new NNSimul(2);
        DeepLearningAGZ deepLearningBlack = new DeepLearningAGZ(nnBlack, false);
        // deepLearningBlack = DeepLearningAGZ.initFile(deepLearningWhite, deepLearningBlack, gameManager.getNbGames(), updateLr);
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
                    .withDirichlet(dirichlet);
                    // .withNbThread(1);
            final MCTSStrategy blackStrategy = new MCTSStrategy(
                    game,
                    Alliance.BLACK,
                    deepLearningBlack,
                    seed,
                    updateCpuct,
                    -1)
                    .withNbSearchCalls(NB_STEP)
                    .withDirichlet(dirichlet);
                    // .withNbThread(1);
            whiteStrategy.setPartnerStrategy(blackStrategy);
            game.setup(whiteStrategy, blackStrategy);
            Game.GameStatus gameStatus;
            do {
                gameStatus = game.play();
                sequence.play();
                Move move = game.getLastMove();
                log.info("move:{} game:\n{}", move, game);
            } while (gameStatus == Game.GameStatus.IN_PROGRESS);
            log.info("#########################################################################");
            log.info("END OF game [{}] :\n{}\n{}", gameManager.getNbGames(), gameStatus.toString(), game);
            log.info("#########################################################################");
            ResultGame resultGame = whiteStrategy.getResultGame(gameStatus);
            whiteStrategy.saveBatch(resultGame, lastSaveGame);
        }
    }
}
