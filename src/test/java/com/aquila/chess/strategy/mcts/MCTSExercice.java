package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.Helper;
import com.aquila.chess.strategy.RandomStrategy;
import com.aquila.chess.strategy.StaticStrategy;
import com.aquila.chess.strategy.Strategy;
import com.aquila.chess.utils.DotGenerator;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

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
        deepLearningWhite = new DeepLearningAGZ(nnWhite, false, 50);
        nnBlack = new NNSimul(1);
        deepLearningBlack = new DeepLearningAGZ(nnBlack, false, 50);
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
                .withNbMaxSearchCalls(800);
        game.setup(whiteStrategy, blackStrategy);
        Piece pawn = board.getPiece(BoardUtils.INSTANCE.getCoordinateAtPosition("a3"));
        int index1 = PolicyUtils.indexFromMove(0, 2, 0, 1, pawn);
        int index2 = PolicyUtils.indexFromMove(0, 1, 0, 0, pawn);
        nnBlack.addIndexOffset(0.5, index1, index2);
        for (int i = 0; i < 5; i++) {
            Game.GameStatus status = game.play();
            Move move = game.getLastMove();
            log.warn("[{}] move: {} class:{}", move.getMovedPiece().getPieceAllegiance(), move, move.getClass().getSimpleName());
            Helper.checkMCTSTree(blackStrategy);
            if (status != Game.GameStatus.IN_PROGRESS) {
                if (move instanceof Move.PawnPromotion) {
                    return;
                }
                log.error("{}", game);
                assertFalse(true, "End of game not expected:" + status);
            }
            if (move.getMovedPiece().getPieceAllegiance().isBlack()) {
                log.warn("GRAPH: {}", DotGenerator.toString(blackStrategy.getCurrentRoot(), 5));
            }
        }
        assertTrue(false, "We should have get promoted");
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  P-B --- --- --- --- --- K-B ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- K-W  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @Test
    void testEndWithBlackPromotion() throws Exception {
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
                .withNbMaxSearchCalls(800);
        game.setup(whiteStrategy, blackStrategy);
        Piece pawn = board.getPiece(BoardUtils.INSTANCE.getCoordinateAtPosition("a3"));
        int index1 = PolicyUtils.indexFromMove(0, 2, 0, 1, pawn);
        int index2 = PolicyUtils.indexFromMove(0, 1, 0, 0, pawn);
        nnBlack.addIndexOffset(0.5, index1, index2);
        for (int i = 0; i < 4; i++) {
            Game.GameStatus status = game.play();
            Move move = game.getLastMove();
            log.warn("Status:{} [{}] move: {} class:{}", status, move.getMovedPiece().getPieceAllegiance(), move, move.getClass().getSimpleName());
            Helper.checkMCTSTree(blackStrategy);
            if (status == Game.GameStatus.CHESSMATE_WHITE) {
                return;
            }
            assertEquals(Game.GameStatus.IN_PROGRESS, status, "wrong status: only white-chessmate or in progress is allow");
            if (move.getMovedPiece().getPieceAllegiance().isBlack()) {
                log.warn("GRAPH: {}", DotGenerator.toString(blackStrategy.getCurrentRoot(), 5));
            }
        }
        assertTrue(false, "We should have got a black chessmate");
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- K-B ---  3
     * 2  P-B --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- K-W  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @Test
    void testOneShotBlackChessMate() throws Exception {
        final Board board = Board.createBoard("kh1", "pa2,kg3", BLACK);
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
                .withNbMaxSearchCalls(800);
        game.setup(whiteStrategy, blackStrategy);

        for (int i = 0; i < 2; i++) {
            Game.GameStatus status = game.play();
            Move move = game.getLastMove();
            log.warn("Status:{} [{}] move: {} class:{}", status, move.getMovedPiece().getPieceAllegiance(), move, move.getClass().getSimpleName());
            Helper.checkMCTSTree(blackStrategy);
            if (status == Game.GameStatus.CHESSMATE_WHITE) {
                return;
            }
            assertEquals(Game.GameStatus.IN_PROGRESS, status, "wrong status: only white-chessmate or in progress is allow");
            if (move.getMovedPiece().getPieceAllegiance().isBlack()) {
                log.warn("GRAPH: {}", DotGenerator.toString(blackStrategy.getCurrentRoot(), 5));
            }
        }
        log.warn("graph: {}", DotGenerator.toString(blackStrategy.getCurrentRoot(), 10, true));
        assertTrue(false, "We should have got a white chessmate");
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- K-B  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  P-W --- --- --- --- --- K-W ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- --- ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @Test
    void testEndWithWhitePromotion() throws Exception {
        final Board board = Board.createBoard("pa6,kg6", "kh8", WHITE);
        final Game game = Game.builder().board(board).build();
        final DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nnWhite, false, 10);
        final Strategy blackStrategy = new RandomStrategy(BLACK, 1);
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                1,
                updateCpuct,
                -1)
                .withNbMaxSearchCalls(800);
        game.setup(whiteStrategy, blackStrategy);
        Piece pawn = board.getPiece(BoardUtils.INSTANCE.getCoordinateAtPosition("a6"));
        int index1 = PolicyUtils.indexFromMove(0, 2, 0, 1, pawn);
        int index2 = PolicyUtils.indexFromMove(0, 1, 0, 0, pawn);
        nnBlack.addIndexOffset(0.5, index1, index2);
        boolean good = false;
        for (int i = 0; i < 3; i++) {
            Game.GameStatus status = game.play();
            Move move = game.getLastMove();
            log.warn("Status:{} [{}] move: {} class:{}", status, move.getMovedPiece().getPieceAllegiance(), move, move.getClass().getSimpleName());
            Helper.checkMCTSTree(whiteStrategy);
            if (status == Game.GameStatus.CHESSMATE_BLACK) {
                return;
            }
            assertEquals(Game.GameStatus.IN_PROGRESS, status, "wrong status: only white-chessmate or in progress is allow");
        }
        log.warn("graph: {}", DotGenerator.toString(whiteStrategy.getCurrentRoot(), 10, true));
        assertTrue(false, "We should have a chessmate");
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- R-B --- K-B --- ---  3
     * 2  P-B --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- K-W --- ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @Test
    void testAvoidEndWithBlackPromotion() throws Exception {
        final Board board = Board.createBoard("kf1", "pa2,rd3,kf3", WHITE);
        final Game game = Game.builder().board(board).build();
        final DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nnWhite, false, 10);
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                1,
                updateCpuct,
                -1)
                .withDirichlet((nbStep)->false)
                .withNbMaxSearchCalls(100);
        final DeepLearningAGZ deepLearningBlack = new DeepLearningAGZ(nnBlack, false, 10);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                BLACK,
                deepLearningBlack,
                1,
                updateCpuct,
                -1)
                .withNbMaxSearchCalls(100);
        game.setup(whiteStrategy, blackStrategy);
        for (int i = 0; i < 4; i++) {
            Game.GameStatus status = game.play();
            Move move = game.getLastMove();
            log.warn("Status:{} [{}] move: {} class:{}", status, move.getMovedPiece().getPieceAllegiance(), move, move.getClass().getSimpleName());
            switch (move.getMovedPiece().getPieceAllegiance()) {
                case WHITE:
                    log.warn("graph: {}", DotGenerator.toString(whiteStrategy.getCurrentRoot(), 10, false));
                    Helper.checkMCTSTree(whiteStrategy);
                    List<MCTSNode> looses = whiteStrategy.getCurrentRoot().search(MCTSNode.State.LOOSE);
                    log.info("[WHITE] Looses Nodes:{}", looses.stream().map(node -> node.getMove().toString()).collect(Collectors.joining(",")));
                    assertTrue(looses.size() > 0);
                    break;
                case BLACK:
                    Helper.checkMCTSTree(blackStrategy);
                    List<MCTSNode> wins = blackStrategy.getCurrentRoot().search(MCTSNode.State.WIN);
                    log.info("[BLACK] Win Nodes:{}", wins.stream().map(node -> node.getMove().toString()).collect(Collectors.joining(",")));
                    assertTrue(wins.size() > 0);
                    break;
            }
            assertNotEquals(Game.GameStatus.CHESSMATE_WHITE, status, "We should not have a white chessmate");
            assertEquals(Game.GameStatus.IN_PROGRESS, status, "wrong status: only white-chessmate or in progress is allow");
        }
        log.warn("graph: {}", DotGenerator.toString(blackStrategy.getCurrentRoot(), 10, false));
    }

    /**
     * @throws ChessPositionException
     * @throws EndOfGameException
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- K-B --- ---  8
     * 7  P-W --- --- --- --- --- --- ---  7
     * 6  --- --- --- R-W --- K-W --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- --- ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @Test
    void testAvoidEndWithWhitePromotion() throws Exception {
        final Board board = Board.createBoard("pa7,rd6,kf6", "kf8", BLACK);
        final Game game = Game.builder().board(board).build();
        final DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nnWhite, false, 10);
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                1,
                updateCpuct,
                -1)
                .withNbMaxSearchCalls(800);
        final DeepLearningAGZ deepLearningBlack = new DeepLearningAGZ(nnBlack, false, 10);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                BLACK,
                deepLearningBlack,
                1,
                updateCpuct,
                -1)
                .withNbMaxSearchCalls(800);
        game.setup(whiteStrategy, blackStrategy);
        for (int i = 0; i < 4; i++) {
            Game.GameStatus status = game.play();
            Move move = game.getLastMove();
            log.warn("Status:{} [{}] move: {} class:{}", status, move.getMovedPiece().getPieceAllegiance(), move, move.getClass().getSimpleName());
            switch (move.getMovedPiece().getPieceAllegiance()) {
                case WHITE:
                    Helper.checkMCTSTree(whiteStrategy);
                    List<MCTSNode> wins = whiteStrategy.getCurrentRoot().search(MCTSNode.State.WIN);
                    log.info("[WHITE] Wins Nodes:{}", wins.stream().map(node -> node.getMove().toString()).collect(Collectors.joining(",")));
                    assertTrue(wins.size() > 0);
                    break;
                case BLACK:
                    log.warn("graph: {}", DotGenerator.toString(blackStrategy.getCurrentRoot(), 10, false));
                    Helper.checkMCTSTree(blackStrategy);
                    List<MCTSNode> looses = blackStrategy.getCurrentRoot().search(MCTSNode.State.LOOSE);
                    log.info("[BLACK]Loose Nodes:{}", looses.stream().map(node -> node.getMove().toString()).collect(Collectors.joining(",")));
                    assertTrue(looses.size() > 0);
                    break;
            }
            assertNotEquals(Game.GameStatus.CHESSMATE_BLACK, status, "We should not have a black chessmate");
            assertEquals(Game.GameStatus.IN_PROGRESS, status, "wrong status: only black-chessmate or in progress is allow");
        }
        log.warn("graph: {}", DotGenerator.toString(blackStrategy.getCurrentRoot(), 10, false));
    }

}
