package com.aquila.chess.strategy.mcts.utils;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.StaticStrategy;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static com.chess.engine.classic.Alliance.BLACK;
import static com.chess.engine.classic.Alliance.WHITE;

@Slf4j
class MovesUtilsTest {

    @Test
    void nbMovesRepeat() throws Exception {
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        final StaticStrategy whiteStrategy = new StaticStrategy(WHITE, "B1-A3;A3-B1;B1-A3;A3-B1;B1-A3;A3-B1;B1-A3;A3-B1;B1-A3;A3-B1;B1-A3;A3-B1;B1-A3;A3-B1;B1-A3;A3-B1;");
        final StaticStrategy blackStrategy = new StaticStrategy(BLACK, "B8-C6;C6-B8;B8-C6;C6-B8;B8-C6;C6-B8;B8-C6;C6-B8;B8-C6;C6-B8;B8-C6;C6-B8;B8-C6;C6-B8;B8-C6;C6-B8;");
        game.setup(whiteStrategy, blackStrategy);
        for (int i = 0; i < 20; i++) {
            Game.GameStatus status = game.play();
            Move move = game.getLastMove();
            log.warn("STEP:{} Status:{} [{}] move: {} class:{}", i, status, move.getAllegiance(), move, move.getClass().getSimpleName());
            log.warn("NB REPEAT:{}", MovesUtils.nbMovesRepeat(game.getMoves()));
        }
    }
}