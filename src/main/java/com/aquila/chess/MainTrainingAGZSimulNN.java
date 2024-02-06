package com.aquila.chess;

import com.aquila.chess.config.MCTSConfig;
import com.aquila.chess.manager.GameManager;
import com.aquila.chess.manager.Sequence;
import com.aquila.chess.strategy.mcts.*;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNSimul;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainTrainingAGZSimulNN {

    private static final UpdateCpuct updateCpuct = nbStep -> {
        if (nbStep <= 30) return 2.5;
        else return 0.00025;
    };

    private static final Dirichlet dirichlet = nbStep -> true;

    private static final String trainDir = "train-simul";

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(final String[] args) throws Exception {
        GameManager gameManager = new GameManager("../AGZ_NN/sequences-simul.csv", 40000, 55);
        INN nnWhite = new NNSimul(1);
        INN nnBlack = new NNSimul(2);
        while (!gameManager.stopDetected(true)) {
            InputsManager inputsManager = new Lc0InputsManagerImpl();
            DeepLearningAGZ deepLearningWhite = DeepLearningAGZ.builder()
                    .nn(nnWhite)
                    .inputsManager(inputsManager)
                    .batchSize(MCTSConfig.mctsConfig.getMctsWhiteStrategyConfig().getBatch())
                    .train(false)
                    .build();
            DeepLearningAGZ deepLearningBlack = DeepLearningAGZ.builder()
                    .nn(nnBlack)
                    .inputsManager(inputsManager)
                    .batchSize(MCTSConfig.mctsConfig.getMctsBlackStrategyConfig().getBatch())
                    .train(false)
                    .build();
            final Board board = Board.createStandardBoard();
            final Game game = Game.builder()
                    .inputsManager(inputsManager)
                    .board(board)
                    .build();
            final TrainGame trainGame = new TrainGame();
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
                    .withTrainGame(trainGame)
                    .withNbSearchCalls(MCTSConfig.mctsConfig.getMctsWhiteStrategyConfig().getSteps())
                    .withNbThread(MCTSConfig.mctsConfig.getMctsWhiteStrategyConfig().getThreads())
                    .withDirichlet((step) -> MCTSConfig.mctsConfig.getMctsWhiteStrategyConfig().isDirichlet());
            final MCTSStrategy blackStrategy = new MCTSStrategy(
                    game,
                    Alliance.BLACK,
                    deepLearningBlack,
                    seed2,
                    updateCpuct,
                    -1)
                    .withTrainGame(trainGame)
                    .withNbSearchCalls(MCTSConfig.mctsConfig.getMctsBlackStrategyConfig().getSteps())
                    .withNbThread(MCTSConfig.mctsConfig.getMctsBlackStrategyConfig().getThreads())
                    .withDirichlet((step) -> MCTSConfig.mctsConfig.getMctsBlackStrategyConfig().isDirichlet());
            game.setup(whiteStrategy, blackStrategy);
            Game.GameStatus gameStatus;
            try {
                do {
                    gameStatus = game.play();
                    sequence.play();
                    log.warn("game:\n{}", game);
                } while (!gameStatus.isTheEnd());
                game.end(game.getLastMove());
            } catch (RuntimeException e) {
                log.error("game canceled, restarting a new one", e);
                continue;
            }
            log.info("#########################################################################");
            log.info("END OF game [{}] :\n{}\n{}", gameManager.getNbGames(), gameStatus.toString(), game);
            log.info("#########################################################################");
            final String filename = trainGame.saveBatch(trainDir, gameStatus);
            gameManager.endGame(game, deepLearningWhite.getScore(), gameStatus, sequence, filename);
        }
    }
}
