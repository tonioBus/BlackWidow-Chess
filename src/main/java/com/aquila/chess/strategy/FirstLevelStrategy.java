package com.aquila.chess.strategy;

import com.aquila.chess.Game;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Random;

@Slf4j
public class FirstLevelStrategy implements Strategy {

    final Random rand;
    private final int seed;
    @Getter
    private final Alliance alliance;
    private int nbStep;


    public FirstLevelStrategy(final Alliance alliance, int seed) {
        this.alliance = alliance;
        this.seed = seed;
        rand = new Random(seed);
    }

    @Override
    public Move evaluateNextMove(final Game game, final Move opponentMove, final List<Move> moves) {
		Piece attackedPiece=null;
		Move attackingMove=null;
        for(Move move:moves) {
			if (move.execute().isCheckBoard()) {
				if (move.execute().getAllLegalMoves().size() == 0) return move;
			}
		}
		for(Move move:moves) {
            if(move.isCastlingMove()) return move;
            if(move.isAttack()) {
				Piece currentAttackedPiece = move.getAttackedPiece();
				if(attackedPiece == null || currentAttackedPiece.getPieceValue()>attackedPiece.getPieceValue()) {
					attackedPiece = currentAttackedPiece;
					attackingMove = move;
				}
            }
			if(attackingMove!=null) return attackingMove;
			if (move.execute().isCheckBoard()) {
				return move;
			}
        }
        long skip = moves.isEmpty() ? 0 : rand.nextInt(moves.size());
        Move move = moves.stream().skip(skip).findFirst().get();
        log.info("[{}] {} nextPlay() -> {}", this.nbStep, this, move);
        this.nbStep++;
        return move;
    }

    /**
    @Override
	public Move nextPlay(final Move moveOpponent, final Moves moves) throws EndOfGameException, ChessPositionException {
		final Move moveCastling = moves.getCastlingMove();
		if (moveCastling != null)
			return moveCastling;
		Move moveRet = null;
		int scoreMax = 0;
		// chessmate
		for (final Move move : moves) {
			if (move.isCreateChessMate()) {
				return move;
			}
		}
		// promotion
		for (final Move move : moves) {
			if (move.isGettingPromoted()) {
				return move;
			}
		}
		for (final Move move : moves) {
			int score = 0;
			if (move.isCreateCheck() && move.getCanBeCaptured() == null) {
				score = 10;
				if (score > scoreMax) {
					moveRet = move;
					scoreMax = score;
				}
			}
			if (move.getCapturePiece() != null && move.getCanBeCaptured() == null
					&& score < move.getCapturePiece().getPower()) {
				score += move.getCapturePiece().getPower();
				if (score > scoreMax) {
					moveRet = move;
					scoreMax = score;
				}
			}
			if (move.getPiece().getClass() == Pawn.class && move.getCanBeCaptured() == null) {
				final int y = move.getEndLocation().getY();
				switch (this.getColor()) {
				case WHITE:
					if (y > 5)
						score += y;
					break;
				case BLACK:
					if (y < 3)
						score += y;
					break;
				}
				if (score > scoreMax) {
					moveRet = move;
					scoreMax = score;
				}
			}
		}
		if (scoreMax > 0 && moveRet != null)
			return moveRet;
		int step = 5;
		Move move = null;
		do {
			move = moves.getPossibleMoveWithout(rand, King.class, Rook.class);
			if (move.getCanBeCaptured() == null)
				return move;
		} while (step-- != 0);
		// logger.info("## Move: " + move.getPiece() + " -> " +
		// move.getAlgebricNotation());
		return move;
	}
     */

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