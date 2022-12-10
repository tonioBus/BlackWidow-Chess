package com.aquilla.chess.strategy;

import com.aquilla.chess.Game;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import com.chess.engine.classic.player.Player;

import java.util.Collection;
import java.util.List;

public class HungryStrategy implements Strategy {

    private final Player player;

    public HungryStrategy(Player player) {
        this.player = player;
    }

    @Override
    public Move play(final Game game, final Move opponentMove, final List<Move> moves) {
        Move bestAttackMove = null;
        Move bestUnpositionMove = null;
        int bestPiece = 0;
        int bonus = Integer.MAX_VALUE;
        Piece piece;
        for (Move move : moves) {
            if (move.isAttack()) {
                piece = move.getAttackedPiece();
                if (piece.getPieceValue() > bestPiece) {
                    bestPiece = piece.getPieceValue();
                    bestAttackMove = move;
                }
            }
            piece = move.getMovedPiece();
            if (piece.locationBonus() < bonus) {
                bonus = piece.locationBonus();
                bestUnpositionMove = move;
            }
        }
        if (bestAttackMove != null) return bestAttackMove;
        return bestUnpositionMove;
    }

    @Override
    public String getName() {
        return this.getClass().getName();
    }
}