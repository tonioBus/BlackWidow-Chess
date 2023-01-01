/**
 *
 */
package com.aquila.chess.strategy;

import com.aquila.chess.Game;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class StaticStrategy implements Strategy {

    static private final Logger logger = LoggerFactory.getLogger(StaticStrategy.class);

    final private List<String> moveSzs = new ArrayList<>();
    private final Alliance alliance;
    private int sequenceNumber = 0;

    /**
     * @param alliance    color of the player
     * @param sequence algebraic moves (e2-e4) separated by semi-colon
     */
    public StaticStrategy(final Alliance alliance, final String sequence) {
        this.alliance = alliance;
        Stream.of(sequence.toLowerCase().split(";")).forEach(moveSz -> {
            moveSzs.add(moveSz);
        });
    }

    @Override
    public Move play(Game game, Move moveOpponent, List<Move> moves) throws Exception {
        String nextMoveSz = moveSzs.get(sequenceNumber++);
        Optional<Move> nextMove = BoardUtils.getMove(nextMoveSz, moves);
        if (nextMove.isEmpty()) throw new RuntimeException(String.format("Incorrect move:%s", nextMoveSz));
        return nextMove.get();
    }

    @Override
    public String getName() {
        return toString();
    }

    @Override
    public Alliance getAlliance() {
        return this.alliance;
    }

    @Override
    public String toString() {
        return String.format("%s{nextMoveSz=%s,alliance=%s}",
                this.getClass().getSimpleName(),
                moveSzs.get(sequenceNumber),
                alliance);
    }
}
