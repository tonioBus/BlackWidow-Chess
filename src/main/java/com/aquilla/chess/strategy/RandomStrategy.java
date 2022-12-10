package com.aquilla.chess.strategy;

import com.aquilla.chess.Game;
import com.chess.engine.classic.board.Move;

import java.util.Collection;
import java.util.List;
import java.util.Random;

public class RandomStrategy implements Strategy {

    final Random rand;

    public RandomStrategy(int seed) {
        rand = new Random(seed);
    }

    @Override
    public Move play(final Game game, final Move opponentMove, final List<Move> moves) {
        long skip = moves.isEmpty() ? 0 : rand.nextInt(moves.size());
        Move move = moves.stream().skip(skip).findFirst().get();
        return move;
    }

    @Override
    public String getName() {
        return this.getClass().getName();
    }
}