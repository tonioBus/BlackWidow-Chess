package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.StaticStrategy;
import com.aquila.chess.utils.DotGenerator;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.chess.engine.classic.Alliance.BLACK;
import static com.chess.engine.classic.Alliance.WHITE;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class MCTSExercice {

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
        deepLearningWhite = new DeepLearningAGZ(nnWhite, true, 50);
        nnBlack = new NNSimul(1);
        deepLearningBlack = new DeepLearningAGZ(nnBlack, true, 50);
        nnBlack.clearIndexOffset();
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  p-B --- --- --- --- --- K-B ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- K-W  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @Test
    void testSimulationDetectPossibleBlackPromotion() throws Exception {
        final Board board = Board.createBoard("kh1", "pa3,kg3", BLACK);
        final Game game = Game.builder().board(board).build();
        final DeepLearningAGZ deepLearningBlack = new DeepLearningAGZ(nnBlack, false, 10);
        final StaticStrategy whiteStrategy = new StaticStrategy(WHITE, "H1-G1;G1-H1;H1-G1;G1-H1");
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                BLACK,
                deepLearningBlack,
                1,
                updateCpuct,
                -1)
                .withNbThread(1)
                .withNbMaxSearchCalls(800);
        game.setup(whiteStrategy, blackStrategy);
        blackStrategy.setDirectRoot(game, null);
        Piece pawn = board.getPiece(BoardUtils.INSTANCE.getCoordinateAtPosition("a3"));
        int index1 = PolicyUtils.indexFromMove(0, 2, 0, 1, pawn);
        int index2 = PolicyUtils.indexFromMove(0, 1, 0, 0, pawn);
        nnBlack.addIndexOffset(0.5, index1, index2);
        for (int i = 0; i < 5; i++) {
            Game.GameStatus status = game.play();
            Move move = game.getLastMove();
            log.warn("[{}] move: {} class:{}", move.getMovedPiece().getPieceAllegiance(), move, move.getClass().getSimpleName());
            if(status != Game.GameStatus.IN_PROGRESS) {
                if (move instanceof Move.PawnPromotion) {
                    return;
                }
                log.error("{}", game);
                assertFalse(true, "End of game not expected:" + status);
            }
            if (move.getMovedPiece().getPieceAllegiance().isBlack()) {
                log.warn("GRAPH: {}", DotGenerator.toString(blackStrategy.getDirectRoot().getParent(), 5));
            }
        }
        assertTrue(false, "We should have get promoted");
    }

}
