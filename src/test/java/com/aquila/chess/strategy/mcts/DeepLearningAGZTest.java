package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.RandomStrategy;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsOneNN;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
class DeepLearningAGZTest {

    final INN nn = new Lc0NNTest();

    final InputsManager inputsManager = new Lc0InputsManagerImpl();
    final DeepLearningAGZ deepLearningWhite = DeepLearningAGZ.builder()
            .nn(nn)
            .inputsManager(inputsManager)
            .batchSize(128)
            .train(true)
            .build();

    final UpdateCpuct updateCpuct = (nbStep) -> {
        return Math.exp(-0.04 * nbStep) / 2;
    };

    @Test
    void testPolicies() {
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        game.setup(new RandomStrategy(Alliance.WHITE, 1), new RandomStrategy(Alliance.BLACK, 1000));
        List<Move> legalMovesBlack = game.getPlayer(Alliance.BLACK).getLegalMoves(Move.MoveStatus.DONE);
        assertEquals(944, PolicyUtils.indexFromMove(BoardUtils.getMove("a7-a6", legalMovesBlack).get()));
        assertEquals(1456, PolicyUtils.indexFromMove(BoardUtils.getMove("a7-a5", legalMovesBlack).get()));
        assertEquals(945, PolicyUtils.indexFromMove(BoardUtils.getMove("b7-b6", legalMovesBlack).get()));
        assertEquals(946, PolicyUtils.indexFromMove(BoardUtils.getMove("c7-c6", legalMovesBlack).get()));
        assertEquals(947, PolicyUtils.indexFromMove(BoardUtils.getMove("d7-d6", legalMovesBlack).get()));
        assertEquals(948, PolicyUtils.indexFromMove(BoardUtils.getMove("e7-e6", legalMovesBlack).get()));
        assertEquals(949, PolicyUtils.indexFromMove(BoardUtils.getMove("f7-f6", legalMovesBlack).get()));
        assertEquals(950, PolicyUtils.indexFromMove(BoardUtils.getMove("g7-g6", legalMovesBlack).get()));
        assertEquals(951, PolicyUtils.indexFromMove(BoardUtils.getMove("h7-h6", legalMovesBlack).get()));
    }

    /**
     * Test method for
     * {@link com.aquila.chess.strategy.mcts.DeepLearningAGZ#DeepLearningAGZ(java.lang.String, boolean, boolean)}.
     */
    @Test
    void testDeepLearningAGZ() throws Exception {
        final Board board = Board.createStandardBoard();
        Lc0InputsManagerImpl inputsManager = new Lc0InputsManagerImpl();
        final Game game = Game.builder().board(board).inputsManager(inputsManager).build();
        game.setup(new RandomStrategy(Alliance.WHITE, 1), new RandomStrategy(Alliance.BLACK, 1000));

        final int LOOP_SIZE = 50;

        long start = System.currentTimeMillis();
        for (int i = 0; i < LOOP_SIZE; i++) {
            Game.GameStatus status = game.play();
            if (status != Game.GameStatus.IN_PROGRESS) break;
            Move move = game.getMoves().get(0);
            MCTSGame mctsGame = new MCTSGame(game);
            Lc0InputsOneNN inputs = inputsManager.createInputsForOnePosition(mctsGame.getLastBoard(), move);
            assertNotNull(inputs);
        }
        long end = System.currentTimeMillis();
        long delta = end - start;
        log.warn("time for {} time:{} speed:{} ", LOOP_SIZE, delta, delta / LOOP_SIZE);
    }

    @Test
    public void testCreateInputsLc0() {
        final int seed = 1;
        final Board board = Board.createStandardBoard();
        Lc0InputsManagerImpl inputsManager = new Lc0InputsManagerImpl();
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final MCTSStrategy whitePlayer = new MCTSStrategy(
                game,
                Alliance.WHITE,
                deepLearningWhite,
                seed,
                updateCpuct,
                2000);
        game.setup(whitePlayer, new RandomStrategy(Alliance.BLACK, 1000));
        final List<Move> moves = game.getPlayer(Alliance.WHITE).getLegalMoves(Move.MoveStatus.DONE);
        MCTSGame mctsGame = new MCTSGame(game);
        for (final Move move : moves) {
            final Lc0InputsOneNN inputs = inputsManager.createInputsForOnePosition(mctsGame.getLastBoard(), move);
            log.info("{}\n{}", move == null ? "Na" : move, inputs);
        }
    }
}