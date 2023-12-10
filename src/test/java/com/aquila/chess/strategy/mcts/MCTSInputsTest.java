package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.mcts.inputs.InputRecord;
import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNSimul;
import com.chess.engine.classic.board.Board;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.chess.engine.classic.Alliance.BLACK;
import static com.chess.engine.classic.Alliance.WHITE;

@SuppressWarnings("InfiniteLoopStatement")
@Slf4j
class MCTSInputsTest {

    final UpdateCpuct updateCpuct = (nbStep) -> {
        return 2.5; // Math.exp(-0.04 * nbStep) / 2;
    };

    private static final Dirichlet dirichlet = game -> false;

    DeepLearningAGZ deepLearningWhite;
    DeepLearningAGZ deepLearningBlack;
    NNSimul nnBlack;
    NNSimul nnWhite;

    @BeforeEach
    public void initMockDeepLearning() {
        nnWhite = new NNSimul(2);
        nnBlack = new NNSimul(1);
        Lc0InputsManagerImpl inputsManager = new Lc0InputsManagerImpl();
        deepLearningWhite = DeepLearningAGZ.builder()
                .nn(nnWhite)
                .inputsManager(inputsManager)
                .batchSize(128)
                .train(true)
                .build();
        deepLearningBlack = DeepLearningAGZ.builder()
                .nn(nnBlack)
                .inputsManager(inputsManager)
                .batchSize(128)
                .train(true)
                .build();

        nnBlack.clearIndexOffset();
    }

    @Test
    void testSavedLc0Inputs() throws Exception {
        InputsManager inputsManager = new Lc0InputsManagerImpl();
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).inputsManager(inputsManager).build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                1,
                updateCpuct,
                -1)
                .withNbThread(4)
                .withNbSearchCalls(50);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                BLACK,
                deepLearningBlack,
                1,
                updateCpuct,
                -1)
                .withNbThread(4)
                .withNbSearchCalls(50);
        game.setup(whiteStrategy, blackStrategy);


        double[][][] inputs = new double[inputsManager.getNbFeaturesPlanes()][Board.NB_COL][Board.NB_COL];

        log.warn("############################################################");
        game.play();
        InputRecord inputRecord = new InputRecord(game, board, game.getLastMove(), game.getMoves(), game.getCurrentPLayerColor());
        inputsManager.createInputs(inputRecord);
        log.warn("############################################################");
        dumpInput(game, 0);
        dumpInput(game, 1);
        game.play();
        inputRecord = new InputRecord(game, board, game.getLastMove(), game.getMoves(), game.getCurrentPLayerColor());
        inputsManager.createInputs(inputRecord);
        log.warn("############################################################");
        dumpInput(game, 0);
        dumpInput(game, 1);
        dumpInput(game, 2);
        game.play();
        inputRecord = new InputRecord(game, board, game.getLastMove(), game.getMoves(), game.getCurrentPLayerColor());
        InputsFullNN inputFullNN = inputsManager.createInputs(inputRecord);
        log.warn("############################################################");
        dumpInput(game, 0);
        dumpInput(game, 1);
        dumpInput(game, 2);
        dumpInput(game, 3);
        game.play();
        log.warn("WHITE INPUTING(0) B&W: {}", inputFullNN);
    }

    private void dumpInput(final Game game, int nbInput) {
//        int lastIndex = game.getTrainGame().getOneStepRecordList().size() - 1;
//        log.warn("\nINPUT({}):\n{}", nbInput, Utils.displayBoard(game.getTrainGame().getOneStepRecordList().get(lastIndex).getInputs(), nbInput));
    }
}
