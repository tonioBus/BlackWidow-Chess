package com.aquila.chess.strategy;

import com.aquila.chess.Game;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Random;

@Slf4j
public class RandomStrategy implements Strategy {

    final Random rand;
    private final int seed;
    @Getter
    private final Alliance alliance;
    private int nbStep;


    public RandomStrategy(final Alliance alliance, int seed) {
        this.alliance = alliance;
        this.seed = seed;
        rand = new Random(seed);
    }

    @Override
    public Move play(final Game game, final Move opponentMove, final List<Move> moves) {
        long skip = moves.isEmpty() ? 0 : rand.nextInt(moves.size());
        Move move = moves.stream().skip(skip).findFirst().get();
        log.info("\n{}\n[{}] {} nextPlay() -> {}", "####################################################", this.nbStep, this, move);
        this.nbStep++;
        return move;
    }

    @Override
    public String getName() {
        return String.format("%s{%s, seed:%d}",
                this.getClass().getSimpleName(),
                this.alliance,
                this.seed);
    }

    @Override
    public String toString() {
        return getName();
    }
}