package com.aquilla.chess.strategy;

import com.aquilla.chess.Game;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;

import java.util.Collection;
import java.util.List;

public interface Strategy {

    Move play(final Game game, final Move moveOpponent, final List<Move> moves) throws Exception ;

    String getName();

    Alliance getAlliance();
}
