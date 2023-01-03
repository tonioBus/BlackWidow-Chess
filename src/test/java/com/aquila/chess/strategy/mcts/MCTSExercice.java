package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.Helper;
import com.aquila.chess.MCTSStrategyConfig;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
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
        MCTSStrategyConfig.DEFAULT_WHITE_INSTANCE.setDirichlet(false);
        MCTSStrategyConfig.DEFAULT_BLACK_INSTANCE.setDirichlet(false);
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
                .withNbThread(4)
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
                    log.info("GAME:\n{}\n", game.toPGN());
                    return;
                }
                log.error("{}", game);
                assertFalse(true, "End of game not expected:" + status);
            }
            if (move.getMovedPiece().getPieceAllegiance().isBlack()) {
                log.warn("GRAPH: {}", DotGenerator.toString(blackStrategy.getCurrentRoot(), 5));
            }
        }
        log.info("GAME:\n{}\n", game.toPGN());
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
                .withNbThread(4)
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
                log.info("GAME:\n{}\n", game.toPGN());
                return;
            }
            assertEquals(Game.GameStatus.IN_PROGRESS, status, "wrong status: only white-chessmate or in progress is allow");
            if (move.getMovedPiece().getPieceAllegiance().isBlack()) {
                log.warn("GRAPH: {}", DotGenerator.toString(blackStrategy.getCurrentRoot(), 5));
            }
        }
        log.info("GAME:\n{}\n", game.toPGN());
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
                .withNbThread(4)
                .withNbMaxSearchCalls(800);
        game.setup(whiteStrategy, blackStrategy);

        for (int i = 0; i < 2; i++) {
            Game.GameStatus status = game.play();
            Move move = game.getLastMove();
            log.warn("Status:{} [{}] move: {} class:{}", status, move.getMovedPiece().getPieceAllegiance(), move, move.getClass().getSimpleName());
            Helper.checkMCTSTree(blackStrategy);
            if (status == Game.GameStatus.CHESSMATE_WHITE) {
                log.info("GAME:\n{}\n", game.toPGN());
                return;
            }
            assertEquals(Game.GameStatus.IN_PROGRESS, status, "wrong status: only white-chessmate or in progress is allow");
            if (move.getMovedPiece().getPieceAllegiance().isBlack()) {
                log.warn("GRAPH: {}", DotGenerator.toString(blackStrategy.getCurrentRoot(), 5));
            }
        }
        log.warn("graph: {}", DotGenerator.toString(blackStrategy.getCurrentRoot(), 10, true));
        log.info("GAME:\n{}\n", game.toPGN());
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
                .withNbThread(4)
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
                log.info("GAME:\n{}\n", game.toPGN());
                return;
            }
            assertEquals(Game.GameStatus.IN_PROGRESS, status, "wrong status: only white-chessmate or in progress is allow");
        }
        log.warn("graph: {}", DotGenerator.toString(whiteStrategy.getCurrentRoot(), 10, true));
        log.info("GAME:\n{}\n", game.toPGN());
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
                .withNbThread(4)
                .withNbMaxSearchCalls(800);
        final DeepLearningAGZ deepLearningBlack = new DeepLearningAGZ(nnBlack, false, 10);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                BLACK,
                deepLearningBlack,
                1,
                updateCpuct,
                -1)
                .withNbThread(4)
                .withNbMaxSearchCalls(800);
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
        log.info("GAME:\n{}\n", game.toPGN());
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
                .withNbThread(4)
                .withNbMaxSearchCalls(800);
        final DeepLearningAGZ deepLearningBlack = new DeepLearningAGZ(nnBlack, false, 10);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                BLACK,
                deepLearningBlack,
                1,
                updateCpuct,
                -1)
                .withNbThread(4)
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
        log.info("GAME:\n{}\n", game.toPGN());
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  R-B --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- --- ---  3
     * 2  --- --- --- --- K-W --- K-B R-B  2
     * 1  --- --- --- --- --- --- --- ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @Test
    void testAvoidWhiteChessMate() throws Exception {
        final Board board = Board.createBoard("ke2", "ra8,kg2,rh2", WHITE);
        final Game game = Game.builder().board(board).build();
        final DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nnWhite, false, 10);
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                1,
                updateCpuct,
                -1)
                .withNbThread(4)
                .withNbMaxSearchCalls(800);
        final StaticStrategy blackStrategy = new StaticStrategy(BLACK, "G2-G3;A8-A1");
        game.setup(whiteStrategy, blackStrategy);
        for (int i = 0; i < 4; i++) {
            Game.GameStatus status = game.play();
            Move move = game.getLastMove();
            log.warn("Status:{} [{}] move: {} class:{}", status, move.getMovedPiece().getPieceAllegiance(), move, move.getClass().getSimpleName());
            switch (move.getMovedPiece().getPieceAllegiance()) {
                case WHITE:
                    Helper.checkMCTSTree(whiteStrategy);
                    List<MCTSNode> looses = whiteStrategy.getCurrentRoot().search(MCTSNode.State.LOOSE);
                    log.info("[BLACK]Loose Nodes:{}", looses.stream().map(node -> node.getMove().toString()).collect(Collectors.joining(",")));
                    break;
                case BLACK:
                    break;
            }
            assertNotEquals(Game.GameStatus.CHESSMATE_WHITE, status, "We should not have a white chessmate");
            assertEquals(Game.GameStatus.IN_PROGRESS, status, "wrong status: only white-chessmate or in progress is allow");
        }
        log.warn("graph: {}", DotGenerator.toString(whiteStrategy.getCurrentRoot(), 10, false));
        log.info("GAME:\n{}\n", game.toPGN());
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  R-B --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- --- ---  3
     * 2  --- --- --- --- K-W --- K-B R-B  2
     * 1  --- --- --- --- --- --- --- ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @Test
    void testMakeWhiteChessMate() {
//        final Board board = new Board();
//        final MCTSPlayer whitePlayer = new MCTSPlayer(deepLearningWhite, 1, updateCpuct, -1)
//                .withNbMaxSearchCalls(800).withDirichlet(dirichlet);
//        final MCTSPlayer blackPlayer = new MCTSPlayer(deepLearningBlack, 10000, updateCpuct, -1)
//                .withNbMaxSearchCalls(800).withDirichlet(dirichlet);
//        final Game game = new Game(board, whitePlayer, blackPlayer);
//
//        whitePlayer.addPieces("KE1");
//        blackPlayer.addPieces("RA8,KG2,RH2");
//
//        game.init();
//        game.setColorToPlay(Color.BLACK);
//        boolean good = false;
//        try {
//            for (int i = 0; i < 20; i++) {
//                final Move move = game.play();
//                if (i == 0 && move.getColor() == Color.BLACK) {
//                    log.warn("GRAPH: {}", DotGenerator.toString(blackPlayer.getRoot(), 10));
//                }
//                log.warn("[{}] move: {}", move.getColor(), move);
//            }
//        } catch (final EndOfGameException e) {
//            log.warn("digraph: {}", DotGenerator.toString(blackPlayer.getRoot(), 10));
//            assertTrue(e.getTypeOfEnding() == TypeOfEnding.CHESSMATE);
//            good = true;
//        }
//        assertTrue(good);
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  R-W --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- --- ---  3
     * 2  --- --- --- --- --- --- K-W R-W  2
     * 1  --- --- --- --- K-B --- --- ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @Test
    void testMakeBlackChessMate() {
//        final Board board = new Board();
//        final MCTSPlayer whitePlayer = new MCTSPlayer(deepLearningWhite, 1, updateCpuct, -1)
//                .withNbMaxSearchCalls(800).withDirichlet(dirichlet);
//        final MCTSPlayer blackPlayer = new MCTSPlayer(deepLearningBlack, 10000, updateCpuct, -1)
//                .withNbMaxSearchCalls(800).withDirichlet(dirichlet);
//
//        final Game game = new Game(board, whitePlayer, blackPlayer);
//
//        blackPlayer.addPieces("KE1");
//        whitePlayer.addPieces("RA8,KG2,RH2");
//
//        game.init();
//        game.setColorToPlay(Color.WHITE);
//        boolean good = false;
//        try {
//            for (int i = 0; i < 20; i++) {
//                final Move move = game.play();
//                if (move.getColor() == Color.WHITE) {
//                    log.warn("GRAPH: {}", DotGenerator.toString(whitePlayer.getRoot(), 4));
//                }
//                log.warn("[{}] move: {}", move.getColor(), move);
//            }
//        } catch (final EndOfGameException e) {
//            log.warn("digraph: {}", DotGenerator.toString(whitePlayer.getRoot(), 10));
//            assertTrue(e.getTypeOfEnding() == TypeOfEnding.CHESSMATE);
//            good = true;
//        }
//        assertTrue(good);
    }

    /**
     * * @formatter:off
     * <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- Q-W --- --- ---  7
     * 6  --- p-B --- --- p-B --- P-B K-B  6
     * 5  p-B --- --- --- --- --- N-W Q-B  5
     * 4  --- --- --- p-W p-B --- --- ---  4
     * 3  --- --- --- --- --- --- p-W ---  3
     * 2  p-W p-W --- --- --- p-W K-W ---  2
     * 1  --- --- --- --- --- --- --- ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     *
     * </pre>
     *
     * @formatter:on
     */
    @Test
    void testMCTSChessCheck2Move() {
//        final Board board = new Board();
//        final MCTSPlayer whitePlayer = new MCTSPlayer(deepLearningWhite, 1, updateCpuct, -1)
//                .withNbMaxSearchCalls(800).withDirichlet(dirichlet);
//        final ChessPlayer blackPlayer = new RandomPlayer(1);
//        final Game game = new Game(board, whitePlayer, blackPlayer);
//
//        whitePlayer.addPieces("PA2,PB2,PD4,QE7,PF2,KG2,PG3,NG5");
//        blackPlayer.addPieces("PA5,PB6,PE4,PE6,PG6,QH5,KH6");
//
//        game.init();
//
//        assertThrows(EndOfGameException.class, () -> {
//            for (int i = 0; i < 8; i++) {
//                // WHITE MOVE
//                Move move = game.play();
//                log.info("\n[{}] move:{} stats:{}", move.getColor(), move, whitePlayer.getStatistic());
//                log.warn("\nwhite graph: {}", DotGenerator.toString(whitePlayer.getRoot(), 10, true));
//                // BLACK MOVE
//                log.warn("\nmove black: {}", game.play());
//            }
//        });
//        log.info(game.toString());
    }

    /**
     * @throws IOException
     * @formatter:off [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- R-B --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- K-B ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- K-W ---  1
     * [a] [b] [c] [d] [e] [f] [g] [h]
     * <p>
     * PGN format to use with -> https://lichess.org/paste
     * @formatter:on
     */
    @Test
    void testMCTSChessMateBlack1Move() throws IOException, InterruptedException {
//        final Board board = new Board();
//        final ChessPlayer whitePlayer = new RandomPlayer(100);
//        final MCTSPlayer blackPlayer = new MCTSPlayer(deepLearningBlack, 1, updateCpuct, -1)
//                .withNbMaxSearchCalls(800).withDirichlet(dirichlet);
//        final Game game = new Game(board, whitePlayer, blackPlayer);
//
//        final King kW = new King(Color.WHITE, Location.get("G1"));
//        final King kB = new King(Color.BLACK, Location.get("G3"));
//        final Rook rB = new Rook(Color.BLACK, Location.get("E8"));
//
//        whitePlayer.addPieces(kW);
//        blackPlayer.addPieces(kB, rB);
//        game.init();
//        game.setColorToPlay(Color.BLACK);
//        log.info(game.toString());
//        log.info("### Black Possibles Moves:");
//        blackPlayer.getPossibleLegalMoves().forEach((move) -> {
//            System.out.print(move + " | ");
//        });
//
//
//        // BLACK MOVE
//        final EndOfGameException endOfGameException = assertThrows(EndOfGameException.class, () -> {
//            log.info("move black: {}", game.play(false));
//            log.info("move white: {}", game.play(false));
//        });
//        assertEquals(TypeOfEnding.CHESSMATE, endOfGameException.getTypeOfEnding());
//        log.info(game.toString());
    }

    /**
     * @throws IOException
     * @formatter:off [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- K-B ---  8
     * 7  R-W --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- K-W ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- --- ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- ---  1
     * [a] [b] [c] [d] [e] [f] [g] [h]
     * <p>
     * PGN format to use with -> https://lichess.org/paste
     * ------------ P G N -------------
     * [Event "Test Aquila Chess Player"]
     * [Site "MOUGINS 06250"]
     * [Date "2021.08.23"]
     * [Round "1"]
     * [White "MCTSPLAYER MCTS -> Move: null visit:2 win:0 parent:false childs:1 SubNodes:21"]
     * [Black "RandomPlayer:100"]
     * [Result "*"]
     * 1.Ra8
     * @formatter:on
     */
    @Test
    void testMCTSChessMateWhite1Move() throws IOException {
//        final Board board = new Board();
//        final MCTSPlayer whitePlayer = new MCTSPlayer(deepLearningWhite, 1, updateCpuct, -1)
//                .withNbMaxSearchCalls(100)
//                .withDirichlet(dirichlet);
//        final ChessPlayer blackPlayer = new RandomPlayer(100);
//        final Game game = new Game(board, whitePlayer, blackPlayer);
//
//        final Rook rW = new Rook(Color.WHITE, Location.get("A7"));
//        final King kW = new King(Color.WHITE, Location.get("G6"));
//        final King kB = new King(Color.BLACK, Location.get("G8"));
//
//        whitePlayer.addPieces(rW, kW);
//        blackPlayer.addPieces(kB);
//        game.init();
//        log.info(game.toString());
//        log.info("### Black Possibles Moves:");
//        final Moves moves = blackPlayer.getPossibleLegalMoves();
//        moves.forEach((move) -> log.info("### {}", move.getAlgebricNotation()));
//
//        // WHITE MOVE
//        assertThrows(EndOfGameException.class, () -> {
//            log.info("move white: {}", game.play());
//        });
    }

    @ParameterizedTest
    @ValueSource(ints = {1})
    @Disabled
    void testStopDoubleGame(final int seed) throws IOException {
//        Board board = new Board();
//        MCTSPlayer whitePlayer = new MCTSPlayer(deepLearningWhite, 1, updateCpuct, 200)
//                .withDirichlet(dirichlet);
//        ChessPlayer blackPlayer = new RandomPlayerFirstLevel(seed);
//        Game game = new Game(board, whitePlayer, blackPlayer);
//        game.initWithAllPieces();
//        log.info("BOARD before play:\n" + game);
//        try {
//            do {
//                game.play(false);
//            } while (true);
//        } catch (final EndOfGameException e) {
//            log.warn("END OF game:\n{}\n{}", e.getLocalizedMessage(), game);
//        }
//        log.warn("END OF game:\n{}", game);
//        whitePlayer = new MCTSPlayer(deepLearningWhite, 1, updateCpuct, 200);
//        blackPlayer = new RandomPlayer(seed);
//        board = new Board();
//        game = new Game(board, whitePlayer, blackPlayer);
//        game.initWithAllPieces();
//        log.warn("BOARD before play:\n" + game);
//        try {
//            do {
//                game.play(false);
//            } while (true);
//        } catch (final EndOfGameException e) {
//            log.warn("END OF game:\n{}\n{}", e.getLocalizedMessage(), game);
//        }
//        log.warn("END OF game:\n{}", game);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    @Disabled
    void testStopGame(final int seed) {
//        final Board board = new Board();
//        final MCTSPlayer whitePlayer = new MCTSPlayer(deepLearningWhite, 1, updateCpuct, 100).withDirichlet(dirichlet);
//        final ChessPlayer blackPlayer = new RandomPlayerFirstLevel(seed + 1000);
//        final Game game = new Game(board, whitePlayer, blackPlayer);
//        game.initWithAllPieces();
//        log.info("BOARD before play:\n" + game);
//        try {
//            do {
//                game.play(false);
//            } while (true);
//        } catch (final EndOfGameException e) {
//            log.info("END OF game:\n{}\n{}", e.getLocalizedMessage(), game);
//        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    public void testAvoidChessIn1(int nbThreads) {
//        INN nnWhite = new NNTest();
//        DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nnWhite, true, 20);
//        INN nnBlack = new NNTest();
//        DeepLearningAGZ deepLearningBlack = new DeepLearningAGZ(nnBlack, true, 20);
//
//        final Board board = new Board();
//        final MCTSPlayer whitePlayer = new MCTSPlayer(deepLearningWhite, 1, updateCpuct, -1)
//                .withNbMaxSearchCalls(100)
//                .withNbThread(nbThreads)
//                .withDirichlet(dirichlet);
//        final MCTSPlayer blackPlayer = new MCTSPlayer(deepLearningBlack, 1, updateCpuct, -1)
//                .withNbMaxSearchCalls(100)
//                .withNbThread(nbThreads)
//                .withDirichlet(dirichlet);
//        final Game game = new Game(board, whitePlayer, blackPlayer);
//        game.initWithAllPieces();
//        final String pgn = "1.e3 f5 2.Nh3"; // 3.g5 4.Qh5 ";
//        game.playPGN(pgn);
//        log.warn(game.toString());
//        assertEquals(Color.BLACK, game.getColorToPlay());
//        Move move;
//        move = game.play();
//        log.info("[{}] move:{} stats:{}", move.getColor(), move, blackPlayer.getStatistic());
//        move = game.play();
//        log.info("[{}] move:{} stats:{}", move.getColor(), move, whitePlayer.getStatistic());
//        move = game.play();
//        log.info("[{}] move:{} stats:{}", move.getColor(), move, blackPlayer.getStatistic());
//        move = game.play();
//        log.info("[{}] move:{} stats:{}", move.getColor(), move, whitePlayer.getStatistic());
    }

}
