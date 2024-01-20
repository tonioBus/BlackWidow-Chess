package com.chess.engine.classic.board;

import com.aquila.chess.AbstractGame;
import com.chess.engine.classic.Alliance;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Slf4j
class BoardTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void testRandomPlayer(int seed) {
        Board board = Board.createStandardBoard();
        Random rand = new Random(seed);
        int nbStep = 0;
        while (true) {
            if (nbStep % 10 == 0) System.out.printf(".");
            if (nbStep % 400 == 0) System.out.printf("\n");
            nbStep++;
            Collection<Move> whiteMoves = board.whitePlayer().getLegalMoves(Move.MoveStatus.DONE);
            long skip = whiteMoves.isEmpty() ? 0 : rand.nextInt(whiteMoves.size());
            Move moveWhite = whiteMoves.stream().skip(skip).findFirst().get();
            board = board.whitePlayer().executeMove(moveWhite);
            if (BoardUtils.isEndGame(board) || nbStep >= AbstractGame.NUMBER_OF_MAX_STEPS) break;
            nbStep++;
            Collection<Move> blackMoves = board.blackPlayer().getLegalMoves(Move.MoveStatus.DONE);
            skip = whiteMoves.isEmpty() ? 0 : rand.nextInt(blackMoves.size());
            Move moveBlack = blackMoves.stream().skip(skip).findFirst().get();
            board = board.blackPlayer().executeMove(moveBlack);
            if (BoardUtils.isEndGame(board) || nbStep >= AbstractGame.NUMBER_OF_MAX_STEPS) break;
        }
        log.info("nbStep:{}", nbStep);
        log.info("currentPlayer:{} inCheckMate:{} inStaleMate:{}",
                board.currentPlayer(),
                board.currentPlayer().isInCheckMate(),
                board.currentPlayer().isInStaleMate());
        log.info("End Board:\n{}", board);
    }

    @Test
    void testGenerateMoves() {
        Board board = Board.createStandardBoard();
        final long start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            board.whitePlayer().getLegalMoves(Move.MoveStatus.DONE);
        }
        final long end = System.currentTimeMillis();
        log.info("Time: {}", (end - start));
    }

    @Test
    public void test() {
        Board board = Board.createStandardBoard();
        board.whitePlayer().getLegalMoves().forEach(m -> {
            log.info("move:{}", m);
        });

        board = board.whitePlayer().executeMove("e2-e3");
        log.info("New Board1:\n{}", board);

        board = board.blackPlayer().executeMove("a7-a6");
        log.info("New Board2:\n{}", board);

        board = board.whitePlayer().executeMove("d1-h5");
        log.info("New Board3:\n{}", board);

        board = board.blackPlayer().executeMove("a6-a5");
        log.info("New Board4:\n{}", board);

        board = board.whitePlayer().executeMove("f1-c4");
        log.info("New Board5:\n{}", board);

        board = board.blackPlayer().executeMove("b7-b6");
        log.info("New Board6:\n{}", board);

        for (Move m : board.whitePlayer().getLegalMoves()) {
            MoveTransition moveTransition = board.whitePlayer().makeMove(m);
            log.info("move:{} moveTransition:{}", m, moveTransition.getMoveStatus());
        }

        board = board.whitePlayer().executeMove("h5-f7");
        log.info("New Board7:\n{}", board);

        for (Move m : board.blackPlayer().getLegalMoves()) {
            MoveTransition moveTransition = board.whitePlayer().makeMove(m);
            log.info("move:{} moveTransition:{}", m, moveTransition.getMoveStatus());
        }

        log.info("Board EndGame:{}", BoardUtils.isEndGame(board));
        log.info("blackPLayer.isCheckMate:{}", board.blackPlayer().isInCheckMate());
        assertTrue(BoardUtils.isEndGame(board));
        assertTrue(board.blackPlayer().isInCheckMate());
        assertFalse(board.whitePlayer().isInCheckMate());
    }

    @Test
    void testCreateBoard() {
        Board board = Board.createBoard("a2,pb3,ke1", "pa5,b6,ke8", Alliance.WHITE);
        log.info("board:\n{}", board);
    }
}