package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.AbstractGame;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;

import java.util.List;

public record InputRecord(AbstractGame abstractGame,
                          List<Move> moves,
                          Move move,
                          Alliance moveColor) {
}
