package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.RandomStrategy;
import com.aquila.chess.strategy.mcts.inputs.InputsNNFactory;
import com.aquila.chess.strategy.mcts.inputs.InputsOneNN;
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

    final INN nn = new NNTest();

    final DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nn, true);

    final UpdateCpuct updateCpuct = (nbStep) -> {
        return Math.exp(-0.04 * nbStep) / 2;
    };

    @Test
    void testPolicies() {
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        game.setup(new RandomStrategy(Alliance.WHITE, 1), new RandomStrategy(Alliance.BLACK, 1000));
        List<Move> legalMovesBlack = game.getPlayer(Alliance.BLACK).getLegalMoves(Move.MoveStatus.DONE);
        assertEquals(97, PolicyUtils.indexFromMove(BoardUtils.getMove("a7-a6", legalMovesBlack).get()));
        assertEquals(161, PolicyUtils.indexFromMove(BoardUtils.getMove("b7-b6", legalMovesBlack).get()));
        assertEquals(225, PolicyUtils.indexFromMove(BoardUtils.getMove("c7-c6", legalMovesBlack).get()));
        assertEquals(289, PolicyUtils.indexFromMove(BoardUtils.getMove("d7-d6", legalMovesBlack).get()));
        assertEquals(353, PolicyUtils.indexFromMove(BoardUtils.getMove("e7-e6", legalMovesBlack).get()));
    }

    /**
     * Test method for
     * {@link com.aquila.chess.strategy.mcts.DeepLearningAGZ#DeepLearningAGZ(java.lang.String, boolean, boolean)}.
     */
    @Test
    void testDeepLearningAGZ() throws Exception {
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        game.setup(new RandomStrategy(Alliance.WHITE, 1), new RandomStrategy(Alliance.BLACK, 1000));

        final int LOOP_SIZE = 50;

        long start = System.currentTimeMillis();
        for (int i = 0; i < LOOP_SIZE; i++) {
            Game.GameStatus status = game.play();
            if (status != Game.GameStatus.IN_PROGRESS) break;
            Move move = game.getMoves().get(0);
            MCTSGame mctsGame = new MCTSGame(game);
            InputsOneNN inputs = InputsNNFactory.createInputsForOnePosition(mctsGame.getLastBoard(), move);
            assertNotNull(inputs);
        }
        long end = System.currentTimeMillis();
        long delta = end - start;
        log.warn("time for {} time:{} speed:{} ", LOOP_SIZE, delta, delta / LOOP_SIZE);
    }

    @Test
    public void testCreateInputs() {
        final int seed = 1;
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
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
            final InputsOneNN inputs = InputsNNFactory.createInputsForOnePosition(mctsGame.getLastBoard(), move);
            log.info("{}\n{}", move == null ? "Na" : move, inputs);
        }
    }
}