package com.aquila.chess;

import com.aquila.chess.strategy.mcts.inputs.InputRecord;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.strategy.mcts.utils.MovesUtils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import com.chess.engine.classic.player.Player;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public abstract class AbstractGame {

    @Getter
    protected final InputsManager inputsManager;

    @Getter
    protected final List<Move> moves = new ArrayList<>(127);

    @Getter
    protected int nbMoveNoAttackAndNoPawn = 0;

    @Getter
    protected Game.GameStatus status;

    @Getter
    protected Board board;

    public AbstractGame(InputsManager inputsManager, Board board) {
        this.board = board;
        if (inputsManager == null) {
            log.warn("USING DEFAULT INPUT-MANAGER -> LC0");
            this.inputsManager = new Lc0InputsManagerImpl();
        } else {
            this.inputsManager = inputsManager;
        }
    }

    public Alliance getCurrentPLayerColor() {
        return this.board.currentPlayer().getAlliance();
    }

    public boolean isInitialPosition() {
        return this.getNbStep() == 0;
    }

    public int getNbStep() {
        return this.getMoves().size();
    }

    public Board getLastBoard() {
        int size = moves.size();
        if (size == 0 || this.moves.get(size - 1).isInitMove()) return this.getBoard();
        return this.moves.get(size - 1).execute();
    }

    public Move getLastMove() {
        return this.getMoves().get(this.getMoves().size() - 1);
    }

    public Player getNextPlayer() {
        return this.board.currentPlayer();
    }

    public Player getPlayer(final Alliance alliance) {
        return alliance.choosePlayerByAlliance(this.board.whitePlayer(), this.board.blackPlayer());
    }

    /**
     * @return the game hashcode
     */
    public long hashCode(final Alliance alliance) {
        final InputRecord inputRecord = new InputRecord(this, getLastBoard(), null, moves, alliance);
        return inputsManager.hashCode(inputRecord);
    }

    public synchronized long hashCode(@NonNull final Move move) {
        final Alliance moveColor = move.getAllegiance();
        final InputRecord inputRecord = new InputRecord(this, getLastBoard(), move, moves, moveColor);
        return inputsManager.hashCode(inputRecord);
    }

    public long hashCode(final Alliance pieceAllegiance, final Move selectedMove) {
        final InputRecord inputRecord = new InputRecord(this, this.getLastBoard(), selectedMove, moves, pieceAllegiance);
        return this.inputsManager.hashCode(inputRecord);
    }

    public Game.GameStatus calculateStatus(final Board board) {
        if (board.whitePlayer().isInCheckMate()) return Game.GameStatus.WHITE_CHESSMATE;
        if (board.blackPlayer().isInCheckMate()) return Game.GameStatus.BLACK_CHESSMATE;
        if (board.currentPlayer().isInStaleMate()) return Game.GameStatus.PAT;
        if (moves.size() >= 300) return Game.GameStatus.DRAW_300;
        if (MovesUtils.is3MovesRepeat(moves)) return Game.GameStatus.DRAW_3;
        if (this.nbMoveNoAttackAndNoPawn >= 50) return Game.GameStatus.DRAW_50;
        if (!isThereEnoughMaterials(board)) return Game.GameStatus.DRAW_NOT_ENOUGH_PIECES;
        return Game.GameStatus.IN_PROGRESS;
    }

    private boolean isThereEnoughMaterials(final Board board) {
        long nbWhitePawn = board.whitePlayer().getActivePieces().stream().filter(piece -> piece.getPieceType() == Piece.PieceType.PAWN).count();
        long nbBlackPawn = board.blackPlayer().getActivePieces().stream().filter(piece -> piece.getPieceType() == Piece.PieceType.PAWN).count();
        if (nbWhitePawn + nbBlackPawn > 0) return true;
        long nbWhitePieces = board.whitePlayer().getActivePieces().size();
        long nbBlackPieces = board.blackPlayer().getActivePieces().size();
        long nbWhiteKnight = board.whitePlayer().getActivePieces().stream().filter(piece -> piece.getPieceType() == Piece.PieceType.KNIGHT).count();
        long nbBlackKnight = board.blackPlayer().getActivePieces().stream().filter(piece -> piece.getPieceType() == Piece.PieceType.KNIGHT).count();
        long nbWhiteBishop = board.whitePlayer().getActivePieces().stream().filter(piece -> piece.getPieceType() == Piece.PieceType.BISHOP).count();
        long nbBlackBishop = board.blackPlayer().getActivePieces().stream().filter(piece -> piece.getPieceType() == Piece.PieceType.BISHOP).count();

        final boolean whiteKingAlone = nbWhitePieces == 1;
        final boolean whiteHasOnly2knights = nbWhitePieces == 3 && nbWhiteKnight == 2;
        final boolean whiteHasOnly1knight = nbWhitePieces == 2 && nbWhiteKnight == 1;
        final boolean whiteHasOnly1Bishop = nbWhitePieces == 2 && nbWhiteBishop == 1;

        final boolean blackKingAlone = board.blackPlayer().getActivePieces().size() == 1;
        final boolean blackHasOnly2knights = board.blackPlayer().getActivePieces().size() == 3 && board.blackPlayer().getActivePieces().stream().filter(piece -> piece.getPieceType() == Piece.PieceType.KNIGHT).count() == 2;
        final boolean blackHasOnly1knight = nbBlackPieces == 2 && nbBlackKnight == 1;
        final boolean blackHasOnly1Bishop = nbBlackPieces == 2 && nbBlackBishop == 1;

        if ((whiteKingAlone || whiteHasOnly2knights || whiteHasOnly1knight || whiteHasOnly1Bishop) && (blackKingAlone || blackHasOnly2knights || blackHasOnly1knight || blackHasOnly1Bishop))
            return false;
        return true;
    }

}
