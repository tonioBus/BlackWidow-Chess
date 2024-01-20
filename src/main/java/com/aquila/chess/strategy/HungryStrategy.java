package com.aquila.chess.strategy;

import com.aquila.chess.Game;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import com.chess.engine.classic.player.Player;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class HungryStrategy implements Strategy {

    private final Player player;
    @Getter
    private final Alliance alliance;

    public HungryStrategy(final Alliance alliance, final Player player) {
        this.alliance = alliance;
        this.player = player;
    }

    @Override
    public Move evaluateNextMove(final Game game, final Move opponentMove, final List<Move> moves) {
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
        log.info("{}:{}:bestAttackMove -> {}  bestUnpositionMove:{}",
                this.getName(), alliance, bestAttackMove, bestUnpositionMove);
        if (bestAttackMove != null) return bestAttackMove;
        return bestUnpositionMove;
    }

    @Override
    public String getName() {
        return this.getClass().getName();
    }

    @Override
    public void end(Move move) {

    }
}