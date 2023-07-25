package com.aquila.chess;

import com.aquila.chess.manager.GameManager;
import com.aquila.chess.manager.Sequence;
import com.aquila.chess.strategy.mcts.*;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.aquila.AquilaInputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNDeep4j;
import com.aquila.chess.utils.Utils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainTrainingAquila {

    static private final String NN_REFERENCE = "../AGZ_NN/AquilaNN.reference";

    static private final String NN_OPPONENT = "../AGZ_NN/AquilaNN.partner";
    static public final int NB_STEP = 800;

    private static final UpdateCpuct updateCpuct = nbStep -> {
        // return 2.5;
        if (nbStep <= 30) return 2.5;
        else return 0.0025;
        // return 2.0 * Math.exp(-0.01 * nbStep);
    };

    private static final Dirichlet dirichlet = nbStep -> true;

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(final String[] args) throws Exception {
        int lastSaveGame = Utils.maxGame("train-aquila/") + 1;
        log.info("START MainTrainingAquila: game {}", lastSaveGame);
        GameManager gameManager = new GameManager("../AQUILA_NN/sequences.csv", 40000, 55);
        MCTSStrategyConfig.DEFAULT_WHITE_INSTANCE.setDirichlet(true);
        MCTSStrategyConfig.DEFAULT_BLACK_INSTANCE.setDirichlet(true);
        INN nnWhite = new NNDeep4j(NN_REFERENCE, false);
        INN nnBlack = new NNDeep4j(NN_OPPONENT, false);
        final InputsManager inputsManager = new AquilaInputsManagerImpl();
        DeepLearningAGZ deepLearningWhite = DeepLearningAGZ.builder()
                .nn(nnWhite)
                .inputsManager(inputsManager)
                .train(false)
                .build();
        DeepLearningAGZ deepLearningBlack = DeepLearningAGZ.builder()
                .nn(nnBlack)
                .inputsManager(inputsManager)
                .train(false)
                .build();
        deepLearningBlack = DeepLearningAGZ.initNNFile(deepLearningWhite, deepLearningBlack, gameManager.getNbGames(), null);
        while (true) {
            final Board board = Board.createStandardBoard();
            final Game game = Game.builder()
                    .inputsManager(inputsManager)
                    .board(board)
                    .build();
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
                    // .withNbThread(NB_THREADS)
                    .withDirichlet(dirichlet);
            final MCTSStrategy blackStrategy = new MCTSStrategy(
                    game,
                    Alliance.BLACK,
                    deepLearningBlack,
                    seed2,
                    updateCpuct,
                    -1)
                    .withNbSearchCalls(NB_STEP)
                    // .withNbThread(NB_THREADS)
                    .withDirichlet(dirichlet);
            whiteStrategy.setPartnerStrategy(blackStrategy);
            game.setup(whiteStrategy, blackStrategy);
            Game.GameStatus gameStatus;
            do {
                gameStatus = game.play();
                sequence.play();
                Move move = game.getLastMove();
                log.warn("game:\n{}", game);
            } while (gameStatus == Game.GameStatus.IN_PROGRESS);
            log.info("#########################################################################");
            log.info("END OF game [{}] :\n{}\n{}", gameManager.getNbGames(), gameStatus.toString(), game);
            log.info("#########################################################################");
            ResultGame resultGame = whiteStrategy.getResultGame(gameStatus);
            whiteStrategy.saveBatch(resultGame, lastSaveGame);
            lastSaveGame++;
            gameManager.endGame(game, deepLearningWhite.getScore(), gameStatus, sequence);
        }
    }
}
