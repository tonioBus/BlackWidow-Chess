package com.aquila.chess;

import com.aquila.chess.strategy.Strategy;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;

@Slf4j
public abstract class AbstractGame {

    @Getter
    protected Move moveOpponent = null;

    @Getter
    protected Strategy strategyWhite;

    @Getter
    protected Strategy strategyBlack;

    @Getter
    protected Strategy nextStrategy;

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

    public void setup(Strategy strategyWhite, Strategy strategyBlack) {
        if (strategyWhite != null && strategyBlack != null) {
            this.strategyWhite = strategyWhite;
            this.strategyBlack = strategyBlack;
            assert (strategyWhite.getAlliance() == Alliance.WHITE);
            assert (strategyBlack.getAlliance() == Alliance.BLACK);
            nextStrategy = switch (this.board.currentPlayer().getAlliance()) {
                case WHITE -> strategyWhite;
                case BLACK -> strategyBlack;
            };
        }
        Move.InitMove initMove = switch (this.board.currentPlayer().getAlliance()) {
            case WHITE -> new Move.InitMove(board, Alliance.BLACK);
            case BLACK -> new Move.InitMove(board, Alliance.WHITE);
        };
        moveOpponent = initMove;
        registerMove(moveOpponent);
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
        return this.getMoves().size() == 0 ? null : this.getMoves().get(this.getMoves().size() - 1);
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
        final InputRecord inputRecord = new InputRecord(this, getMoves(), null, alliance);
        return inputsManager.hashCode(inputRecord);
    }

    public synchronized long hashCode(@NonNull final Move move) {
        final Alliance moveColor = move.getAllegiance();
        final InputRecord inputRecord = new InputRecord(this, getMoves(), move, moveColor);
        return inputsManager.hashCode(inputRecord);
    }

    public long hashCode(final Alliance pieceAllegiance, final Move selectedMove) {
        final InputRecord inputRecord = new InputRecord(this, getMoves(), selectedMove, pieceAllegiance);
        return this.inputsManager.hashCode(inputRecord);
    }

    public Game.GameStatus calculateStatus(final Board board, final Move move) {
        if (move != null) {
            if (!move.isAttack() &&
                    move.getMovedPiece().getPieceType() != Piece.PieceType.PAWN)
                this.nbMoveNoAttackAndNoPawn++;
            else
                this.nbMoveNoAttackAndNoPawn = 0;
        }
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

    /**
     * Return the ratio of present pieces power between white and black
     * @return <ul>
     *     <li>1: full power for the WHITE</li>
     *     <li>0: full power for the BLACK</li>
     * </ul>
     */
    public double ratioPlayer() {
        double whiteValue = getPlayer(Alliance.WHITE).getActivePieces().stream().mapToInt(Piece::getPieceValue).sum();
        double blackValue = getPlayer(Alliance.BLACK).getActivePieces().stream().mapToInt(Piece::getPieceValue).sum();
        whiteValue = (whiteValue - 10000) / 10;
        blackValue = (blackValue - 10000) / 10;
        double ratio = (whiteValue - blackValue) / 386;
        ratio = (ratio + 1) / 2;
        if (ratio < 0.0) ratio = 0.0;
        if (ratio > 1.0) ratio = 1.0;
        return ratio;
    }

    /**
     * Add to the lastInputs the given move
     *
     * @param move
     */
    public void registerMove(final Move move) {
        if (move == null) return;
        this.moves.add(move);
        this.inputsManager.processPlay(getLastBoard(), move);
    }

    public String toPGN() {
        final StringBuffer sb = new StringBuffer();
        sb.append(String.format("[Event \"%s\"]\n", "AquilaChess"));
        sb.append(String.format("[Site \"%S\"]\n", "Mougins 06250"));
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd");
        final String date = simpleDateFormat.format(new Date());
        sb.append(String.format("[Date \"%s\"]\n", date));// "1992.11.04"));
        sb.append(String.format("[Round \"%d\"]\n", this.moves.size()));
        sb.append(String.format("[White \"%s\"]\n", strategyWhite != null ? strategyWhite.getClass().getSimpleName() : "null"));
        sb.append(String.format("[Black \"%s\"]\n", strategyBlack != null ? strategyBlack.getClass().getSimpleName() : "null"));
        String result = "*";
        if (this.status != null) {
            switch (this.status) {
                case WHITE_CHESSMATE:
                    result = "0-1";
                    break;
                case BLACK_CHESSMATE:
                    result = "1-0";
                    break;
                case DRAW_50:
                case DRAW_300:
                case PAT:
                case DRAW_3:
                case DRAW_NOT_ENOUGH_PIECES:
                    result = "1/2-1/2";
                    break;
            }
        }
        sb.append(String.format("[Result \"%s\"]\n", result)); // [Result "0-1"], [Result "1-0"], [Result "1/2-1/2"],
        movesToPGN(sb);
        return sb.toString();
    }

    private void movesToPGN(final StringBuffer sb) {
        int i = 1;
        int nbCol = 0;

        final ListIterator<Move> it = this.getMoves().listIterator();
        it.next(); // to remove the initial move INIT
        while (it.hasNext()) {
            nbCol += ("" + i).length() + 9;
            if (nbCol > 70) {
                nbCol = 0;
                sb.append("\n");
            }
            if ((i & 1) == 1) {
                sb.append((i / 2 + 1) + ".");
            }
            final Move move = it.next();
            sb.append(toPGN(move));
            sb.append(" ");
            i++;
        }
    }

    private String toPGN(final Move move) {
        return move.toString();
    }
}
