package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.utils.MovesUtils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MCTSGame {

    @Getter
    private final InputsManager inputsManager;

    @Getter
    protected final List<Move> moves = new ArrayList<>(127);

    @Getter
    private int nbMoveNoAttackAndNoPawn = 0;

    @Getter
    protected Game.GameStatus status;

    @Getter
    protected Board board;

    public MCTSGame(final Game game) {
        this.board = game.getBoard();
        this.nbMoveNoAttackAndNoPawn = game.getNbMoveNoAttackAndNoPawn();
        this.moves.addAll(game.getMoves());
        this.status = game.calculateStatus();
        this.inputsManager = game.getInputsManager().clone();
        this.inputsManager.startMCTSStep(game);
    }

    public MCTSGame(final MCTSGame mctsGame) {
        this.board = mctsGame.getBoard();
        this.nbMoveNoAttackAndNoPawn = mctsGame.getNbMoveNoAttackAndNoPawn();
        this.moves.addAll(mctsGame.getMoves());
        this.status = mctsGame.calculateStatus(this.board);
        this.inputsManager = mctsGame.inputsManager.clone();
    }

    /**
     * @return the game hashcode
     */
    public long hashCode(final Alliance alliance) {
        return inputsManager.hashCode(getLastBoard(), null, moves, alliance);
    }

    public synchronized long hashCode(@NonNull final Move move) {
        final Alliance moveColor = move.getAllegiance();
        return inputsManager.hashCode(getLastBoard(), move, moves, moveColor);
    }

    public Move getLastMove() {
        return this.getMoves().get(this.getMoves().size() - 1);
    }

    public Board getLastBoard() {
        int size = moves.size();
        if (size == 0 || this.moves.get(size - 1).isInitMove()) return this.getBoard();
        return this.moves.get(size - 1).execute();
    }

    public Game.GameStatus play(final Move move) {
        if (!move.isAttack() &&
                move.getMovedPiece().getPieceType() != Piece.PieceType.PAWN)
            this.nbMoveNoAttackAndNoPawn++;
        else
            this.nbMoveNoAttackAndNoPawn = 0;
        add2Last8InputsAndPlay(move);
        this.board = inputsManager.updateHashsTables(board, move.getAllegiance());
        return this.status = calculateStatus(board);
    }

    /**
     * Add to the lastInputs the given move
     *
     * @param move
     */
    public void add2Last8InputsAndPlay(final Move move) {
        if (move == null) return;
        this.moves.add(move);
        this.inputsManager.processPlay(getLastBoard(), move);
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
        final boolean blackHasOnly2knights = board.blackPlayer().getActivePieces().size() == 3
                && board.blackPlayer().getActivePieces().stream().filter(piece -> piece.getPieceType() == Piece.PieceType.KNIGHT).count() == 2;
        final boolean blackHasOnly1knight = nbBlackPieces == 2 && nbBlackKnight == 1;
        final boolean blackHasOnly1Bishop = nbBlackPieces == 2 && nbBlackBishop == 1;

        if ((whiteKingAlone || whiteHasOnly2knights || whiteHasOnly1knight || whiteHasOnly1Bishop) &&
                (blackKingAlone || blackHasOnly2knights || blackHasOnly1knight || blackHasOnly1Bishop))
            return false;
        return true;
    }

    public int getNbStep() {
        return moves.size();
    }

    public long hashCode(final Alliance pieceAllegiance, final Move selectedMove) {
        return this.inputsManager.hashCode(this.getLastBoard(), selectedMove, moves, pieceAllegiance);
    }
}
