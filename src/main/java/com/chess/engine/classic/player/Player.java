package com.chess.engine.classic.player;

import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.board.Move.MoveStatus;
import com.chess.engine.classic.board.MoveTransition;
import com.chess.engine.classic.pieces.King;
import com.chess.engine.classic.pieces.Piece;

import java.util.*;
import java.util.stream.Collectors;

import static com.chess.engine.classic.pieces.Piece.PieceType.KING;
import static java.util.stream.Collectors.collectingAndThen;

public abstract class Player {

    protected final Board board;
    protected final King playerKing;
    protected final List<Move> legalMoves;
    protected final boolean isInCheck;

    Player(final Board board,
           final List<Move> playerLegals,
           final List<Move> opponentLegals) {
        this.board = board;
        if(board.isCheckBoard()) {
            this.playerKing = establishKing();
            this.isInCheck = !calculateAttacksOnTile(this.playerKing.getPiecePosition(), opponentLegals).isEmpty();
            playerLegals.addAll(calculateKingCastles(playerLegals, opponentLegals));
            this.legalMoves = playerLegals;
        } else {
            playerKing = null;
            isInCheck = false;
            legalMoves = List.of();
        }
    }

    public boolean isInCheck() {
        return this.isInCheck;
    }

    public boolean isInCheckMate() {
        return this.isInCheck && !hasEscapeMoves();
    }

    public boolean isInStaleMate() {
        return !this.isInCheck && !hasEscapeMoves();
    }

    public boolean isCastled() {
        return this.playerKing.isCastled();
    }

    public boolean isKingSideCastleCapable() {
        return this.playerKing.isKingSideCastleCapable();
    }

    public boolean isQueenSideCastleCapable() {
        return this.playerKing.isQueenSideCastleCapable();
    }

    public King getPlayerKing() {
        return this.playerKing;
    }

    private King establishKing() {
        return (King) getActivePieces().stream()
                .filter(piece -> piece.getPieceType() == KING)
                .findAny()
                .orElseThrow(RuntimeException::new);
    }

    private boolean hasEscapeMoves() {
        return this.legalMoves.stream()
                .anyMatch(move -> makeMove(move)
                        .getMoveStatus().isDone());
    }

    public List<Move> getLegalMoves() {
        return this.legalMoves;
    }

    public List<Move> getLegalMoves(final MoveStatus moveStatus) {
        return this.
                getLegalMoves().
                stream().
                filter(m -> this.makeMove(m).getMoveStatus() == moveStatus)
                .collect(Collectors.toList());
    }

    public Optional<Move> getMove(final String moveSz) {
        return BoardUtils.INSTANCE.getMove(moveSz, this.getLegalMoves());
    }

    public Board executeMove(final String moveSz) {
        Optional<Move> move = this.getMove(moveSz);
        return executeMove(move.get());
    }

    public Board executeMove(final Move move) {
        MoveTransition moveTransition = this.makeMove(move);
        return moveTransition.getToBoard();
    }

    static Collection<Move> calculateAttacksOnTile(final int tile,
                                                   final Collection<Move> moves) {
        return moves.stream()
                .filter(move -> move.getDestinationCoordinate() == tile)
                .collect(collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    public MoveTransition makeMove(final Move move) {
        if (!this.legalMoves.contains(move)) {
            return new MoveTransition(this.board, this.board, move, MoveStatus.ILLEGAL_MOVE);
        }
        final Board transitionedBoard = move.execute();
        return transitionedBoard.currentPlayer().getOpponent().isInCheck() ?
                new MoveTransition(this.board, this.board, move, MoveStatus.LEAVES_PLAYER_IN_CHECK) :
                new MoveTransition(this.board, transitionedBoard, move, MoveStatus.DONE);
    }

    public MoveTransition unMakeMove(final Move move) {
        return new MoveTransition(this.board, move.undo(), move, MoveStatus.DONE);
    }

    public abstract Collection<Piece> getActivePieces();

    public abstract Alliance getAlliance();

    public abstract Player getOpponent();

    protected abstract Collection<Move> calculateKingCastles(Collection<Move> playerLegals,
                                                             Collection<Move> opponentLegals);

    protected boolean hasCastleOpportunities() {
        return !this.isInCheck && !this.playerKing.isCastled() &&
                (this.playerKing.isKingSideCastleCapable() || this.playerKing.isQueenSideCastleCapable());
    }

}
