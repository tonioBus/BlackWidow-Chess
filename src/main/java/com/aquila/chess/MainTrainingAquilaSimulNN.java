package com.aquila.chess;

import com.aquila.chess.manager.GameManager;
import com.aquila.chess.manager.Sequence;
import com.aquila.chess.strategy.check.GameChecker;
import com.aquila.chess.strategy.mcts.*;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.aquila.AquilaInputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNSimul;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainTrainingAquilaSimulNN {

    static public final int NB_STEP = 800;

    /**
     * The learning rate was set to 0.2 and dropped to 0.02, 0.002,
     * and 0.0002 after 100, 300, and 500 thousand steps for chess
     */
    public static final UpdateLr updateLr = nbGames -> {
        if (nbGames > 500000) return 1e-6F;
        if (nbGames > 300000) return 1e-5F;
        if (nbGames > 100000) return 1e-4F;
        return 1e-3;
    };

    private static final UpdateCpuct updateCpuct = nbStep -> {
        if (nbStep <= 30) return 2.0;
        else return 0.0025;
    };

    private static final Dirichlet dirichlet = nbStep -> true; // nbStep <= 30;

    private static final String trainDir = "train-todel";

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(final String[] args) throws Exception {
        GameManager gameManager = new GameManager("../AQUILA_NN/sequences-aquila.csv", 40, 55);
        INN nnWhite = new NNSimul(1);
        // deepLearningWhite.setUpdateLr(updateLr, gameManager.getNbGames());
        INN nnBlack = new NNSimul(2);
        InputsManager inputsManager = new AquilaInputsManagerImpl();
        DeepLearningAGZ deepLearningWhite = DeepLearningAGZ.builder()
                .nn(nnWhite)
                .inputsManager(inputsManager)
                .batchSize(256)
                .train(false)
                .build();
        DeepLearningAGZ deepLearningBlack = DeepLearningAGZ.builder()
                .nn(nnBlack)
                .inputsManager(inputsManager)
                .batchSize(256)
                .train(false)
                .build();
        while (true) {
            final Board board = Board.createStandardBoard();
            final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
            final TrainGame trainGame = new TrainGame();
            Sequence sequence = gameManager.createSequence();
            long seed = System.nanoTime();
            final MCTSStrategy whiteStrategy = new MCTSStrategy(
                    game,
                    Alliance.WHITE,
                    deepLearningWhite,
                    seed,
                    updateCpuct,
                    -1)
                    .withTrainGame(trainGame)
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
                    .withTrainGame(trainGame)
                    .withNbSearchCalls(NB_STEP)
                    .withDirichlet(dirichlet);
            // .withNbThread(1);
            game.setup(whiteStrategy, blackStrategy);
            GameChecker gameChecker = new GameChecker(inputsManager);
            Game.GameStatus gameStatus;
            do {
                gameStatus = game.play();
                sequence.play();
                Move move = game.getLastMove();
                gameChecker.play(move.toString());
                log.info("move:{} game:\n{}", move, game);
            } while (!gameStatus.isTheEnd());
            game.end(game.getLastMove());
            log.info("#########################################################################");
            log.info("END OF game [{}] :\n{}\n{}", gameManager.getNbGames(), gameStatus.toString(), game);
            log.info("#########################################################################");
            final String filename = trainGame.saveBatch(trainDir, gameStatus);
        }
    }

}
