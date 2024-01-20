package com.aquila.chess.strategy;

import com.aquila.chess.Game;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;

import java.util.List;

public interface Strategy {

    Move evaluateNextMove(final Game game, final Move moveOpponent, final List<Move> moves) throws Exception ;

    String getName();

    Alliance getAlliance();

    void end(Move move);
}
