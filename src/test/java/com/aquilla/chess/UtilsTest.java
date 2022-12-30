package com.aquilla.chess;

import com.aquilla.chess.strategy.mcts.Coordinate2D;
import com.chess.engine.classic.board.BoardUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UtilsTest {

    /**
     * y=7   "a8", "b8", "c8", "d8", "e8", "f8", "g8", "h8",
     * y=6   "a7", "b7", "c7", "d7", "e7", "f7", "g7", "h7",
     * y=5   "a6", "b6", "c6", "d6", "e6", "f6", "g6", "h6",
     * y=4   "a5", "b5", "c5", "d5", "e5", "f5", "g5", "h5",
     * y=3   "a4", "b4", "c4", "d4", "e4", "f4", "g4", "h4",
     * y=2   "a3", "b3", "c3", "d3", "e3", "f3", "g3", "h3",
     * y=1   "a2", "b2", "c2", "d2", "e2", "f2", "g2", "h2",
     * y=0   "a1", "b1", "c1", "d1", "e1", "f1", "g1", "h1"
     * x=     0     1     2     3     4     5     6     7
     */
    @Test
    public void testConvertCoordinate() {
        int coordinate1D;
        Coordinate2D coordinate2D;
        coordinate1D = BoardUtils.INSTANCE.getCoordinateAtPosition("a8");
        coordinate2D = new Coordinate2D(coordinate1D);
        assertEquals(0, coordinate2D.getX());
        assertEquals(7, coordinate2D.getY());

        coordinate1D = BoardUtils.INSTANCE.getCoordinateAtPosition("c7");
        coordinate2D = new Coordinate2D(coordinate1D);
        assertEquals(2, coordinate2D.getX());
        assertEquals(6, coordinate2D.getY());

    }
}
