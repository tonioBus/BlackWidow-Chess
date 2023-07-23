package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNSimul;
import com.aquila.chess.strategy.mcts.nnImpls.agz.DL4JAlphaGoZeroBuilder;
import com.aquila.chess.utils.Utils;
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
                .train(true)
                .build();
        deepLearningBlack = DeepLearningAGZ.builder()
                .nn(nnBlack)
                .inputsManager(inputsManager)
                .train(true)
                .build();

        nnBlack.clearIndexOffset();
    }

//    @Test
//    void testGameLastInputs() throws ChessPositionException, EndOfGameException {
//        final Board board = new Board();
//        final MCTSPlayer whitePlayer = new MCTSPlayer(deepLearningWhite, 1, updateCpuct, -1) //
//                .withNbMaxSearchCalls(50).withDirichlet(dirichlet);
//        final MCTSPlayer blackPlayer = new MCTSPlayer(deepLearningBlack, 1, updateCpuct, -1) //
//                .withNbMaxSearchCalls(50).withDirichlet(dirichlet);
//        final Game game = new Game(board, whitePlayer, blackPlayer);
//        double[][][] inputs = new double[DL4JAlphaGoZeroBuilder.FEATURES_PLANES][Board.NB_COL][Board.NB_COL];
//
//        game.initWithAllPieces();
//        log.warn("############################################################");
//        for (int i = 0; i < 4; i++) {
//            InputsNNFactory.createInputs(inputs, whitePlayer, Color.WHITE);
//            game.play();
//            game.savePolicies(inputs, Color.WHITE, whitePlayer.getCurrentRootNode());
//        }
//    }

    @Test
    void testSavedInputs() {
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
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


        double[][][] inputs = new double[DL4JAlphaGoZeroBuilder.FEATURES_PLANES][Board.NB_COL][Board.NB_COL];

//        log.warn("############################################################");
//        game.play();
//        InputsNNFactory.createInputs(inputs, whitePlayer, Color.WHITE);
//        game.savePolicies(inputs, Color.WHITE, whitePlayer.getCurrentRootNode());
//        log.warn("############################################################");
//        dumpInput(game, 0);
//        dumpInput(game, 1);
//        Utils.assertInputsFillWith(0.0, game.getTrainGame().getLc0OneStepRecordList().get(0).getInputs(), 1);
//
//        game.play();
//        InputsNNFactory.createInputs(inputs, blackPlayer, Color.BLACK);
//        game.savePolicies(inputs, Color.WHITE, whitePlayer.getCurrentRootNode());
//        log.warn("############################################################");
//        dumpInput(game, 0);
//        dumpInput(game, 1);
//        dumpInput(game, 2);
//        Utils.assertInputsFillWith(0.0, game.getTrainGame().getLc0OneStepRecordList().get(0).getInputs(), 2);
//
//        InputsNNFactory.createInputs(inputs, whitePlayer, Color.WHITE);
//        game.play();
//        game.savePolicies(inputs, Color.WHITE, whitePlayer.getCurrentRootNode());
//        log.warn("############################################################");
//        dumpInput(game, 0);
//        dumpInput(game, 1);
//        dumpInput(game, 2);
//        dumpInput(game, 3);
//        Utils.assertInputsFillWith(0.0, game.getTrainGame().getLc0OneStepRecordList().get(0).getInputs(), 3);
//
//        game.play();
//        game.savePolicies(inputs, Color.WHITE, whitePlayer.getCurrentRootNode());
//        log.warn("WHITE INPUTING(0) B&W: {}", Utils.displayBoard(inputs, 0));
//        log.warn("WHITE INPUTING(1) B&W: {}", Utils.displayBoard(inputs, 1));
//        log.warn("WHITE INPUTING(2) B&W: {}", Utils.displayBoard(inputs, 2));
//        log.warn("WHITE INPUTING(2) B&W: {}", Utils.displayBoard(inputs, 3));
    }

    private void dumpInput(final Game game, int nbInput) {
//        int lastIndex = game.getTrainGame().getLc0OneStepRecordList().size() - 1;
//        log.warn("\nINPUT({}):\n{}", nbInput, Utils.displayBoard(game.getTrainGame().getLc0OneStepRecordList().get(lastIndex).getInputs(), nbInput));
    }
}
