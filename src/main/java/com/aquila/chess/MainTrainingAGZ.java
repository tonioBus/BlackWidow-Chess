package com.aquila.chess;

import com.aquila.chess.config.MCTSConfig;
import com.aquila.chess.manager.GameManager;
import com.aquila.chess.manager.Sequence;
import com.aquila.chess.strategy.mcts.*;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNDeep4j;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainTrainingAGZ {

    static private final String NN_REFERENCE = "../AGZ_NN/AGZ.reference";

    static private final String NN_OPPONENT = "../AGZ_NN/AGZ.partner";

    private static final UpdateCpuct updateCpuct = nbStep -> {
        if (nbStep <= 30) return 2.5;
        else return 0.0000025;
    };

    private static final Dirichlet dirichlet = nbStep -> true;

    private static final String trainDir = "train";

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(final String[] args) throws Exception {
        GameManager gameManager = new GameManager("../AGZ_NN/sequences.csv", 40000, 55);
        if (gameManager.stopDetected(true)) System.exit(-1);
        INN nnWhite = new NNDeep4j(NN_REFERENCE, false, Lc0InputsManagerImpl.FEATURES_PLANES, 20);
        INN nnBlack = new NNDeep4j(NN_OPPONENT, false, Lc0InputsManagerImpl.FEATURES_PLANES, 20);
        while (!gameManager.stopDetected(true)) {
            final InputsManager inputsManager = new Lc0InputsManagerImpl();
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
            deepLearningBlack = DeepLearningAGZ.initNNFile(inputsManager, deepLearningWhite, deepLearningBlack, gameManager.getNbGames(), null);
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
                } while (gameStatus == Game.GameStatus.IN_PROGRESS);
            } catch (RuntimeException e) {
                log.error("game canceled, restarting a new one", e);
                continue;
            }
            log.info("#########################################################################");
            log.info("END OF game [{}] :\n{}\n{}", gameManager.getNbGames(), gameStatus.toString(), game);
            log.info("#########################################################################");
            final String filename = trainGame.saveBatch(trainDir, gameStatus);
            gameManager.endGame(game, deepLearningWhite.getScore(), gameStatus, sequence, filename);
            if (!gameManager.stopDetected(false)) {
                System.gc();
                MCTSConfig.reload();
                log.info("Waiting for {} seconds (param: waitInSeconds)", MCTSConfig.mctsConfig.getWaitInSeconds());
                Thread.sleep(MCTSConfig.mctsConfig.getWaitInSeconds() * 1000L);
            }
        }
    }
}
