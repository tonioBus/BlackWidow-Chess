package com.chess.engine.classic.board;

import com.chess.engine.classic.Alliance;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Slf4j
class BoardTest {

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