package com.aquilla.chess.strategy.mcts;

import lombok.Getter;

import static com.chess.engine.classic.board.BoardUtils.NUM_TILES_PER_ROW;

@Getter
public class Coordinate2D {
    int x, y;

    public Coordinate2D(int coordinate1D) {
        this.x = coordinate1D % NUM_TILES_PER_ROW;
        this.y = NUM_TILES_PER_ROW - 1 - coordinate1D / NUM_TILES_PER_ROW;
    }
}
