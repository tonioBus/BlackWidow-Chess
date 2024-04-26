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

    static private final String NN_REFERENCE = MCTSConfig.mctsConfig.getMctsWhiteStrategyConfig().getNnReference();

    static private final String NN_OPPONENT = MCTSConfig.mctsConfig.getMctsBlackStrategyConfig().getNnReference();

    private static final UpdateCpuct updateCpuctWhite = (nbStep, nbLegalMoves) -> {
        if (nbStep <= 30 || nbLegalMoves > MCTSConfig.mctsConfig.getMctsWhiteStrategyConfig().getCpuAlgoNumberOfMoves()) {
            return MCTSConfig.mctsConfig.getMctsWhiteStrategyConfig().getMaxCpuct();
        } else return 0.0000025;
    };

    private static final UpdateCpuct updateCpuctBlack = (nbStep, nbLegalMoves) -> {
        if (nbStep <= 30 || nbLegalMoves > MCTSConfig.mctsConfig.getMctsBlackStrategyConfig().getCpuAlgoNumberOfMoves()) {
            return MCTSConfig.mctsConfig.getMctsBlackStrategyConfig().getMaxCpuct();
        } else return 0.0000025;
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
            log.info("WHITE SEED:{}", seed1);
            deepLearningWhite.clearAllCaches();
            log.info("WHITE NN:{}", deepLearningWhite.getNn().getFilename());
            deepLearningBlack.clearAllCaches();
            long seed2 = System.nanoTime();
            log.info("BLACK SEED:{}", seed2 * 2);
            log.info("BLACK NN:{}", deepLearningBlack.getNn().getFilename());
            log.info("WHITE isCpuAlgoNumberOfMoves:{}", MCTSConfig.mctsConfig.getMctsWhiteStrategyConfig().getCpuAlgoNumberOfMoves());
            log.info("BLACK isCpuAlgoNumberOfMoves:{}", MCTSConfig.mctsConfig.getMctsBlackStrategyConfig().getCpuAlgoNumberOfMoves());
            final MCTSStrategy whiteStrategy = new MCTSStrategy(
                    game,
                    Alliance.WHITE,
                    deepLearningWhite,
                    seed1,
                    updateCpuctWhite,
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
                    updateCpuctBlack,
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
            if (!gameManager.stopDetected(false)) {
                System.gc();
                MCTSConfig.reload();
                log.info("Waiting for {} seconds (param: waitInSeconds)", MCTSConfig.mctsConfig.getWaitInSeconds());
                Thread.sleep(MCTSConfig.mctsConfig.getWaitInSeconds() * 1000L);
            }
        }
    }
}
