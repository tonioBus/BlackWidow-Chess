package com.aquila.chess;

import com.aquila.chess.strategy.Strategy;
import com.aquila.chess.strategy.mcts.ResultGame;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import com.chess.engine.classic.player.Player;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class Game {

    static public final String CASTLING_SHORT = "O-O";

    static public final String CASTLING_LONG = "O-O-O";

    @Getter
    protected int nbMoveNoAttackAndNoPawn = 0;
    @Getter
    protected GameStatus status;

    @Getter
    protected final Vector<GameTransition> transitions = new Vector<>();

    @Getter
    protected final List<Move> moves = new ArrayList<>(127);

    @Getter
    protected Board board;

    @Getter
    protected Strategy nextStrategy;

    @Getter
    protected Strategy strategyWhite;

    @Getter
    protected Strategy strategyBlack;
    @Getter
    protected Move moveOpponent = null;

    @Getter
    private final TrainGame trainGame = new TrainGame();

    @Builder
    public Game(Board board, Strategy strategyWhite, Strategy strategyBlack) {
        this.board = board;
        this.strategyWhite = strategyWhite;
        this.strategyBlack = strategyBlack;
    }

    public Game() {
    }

    public Alliance getColor2play() {
        return this.board.currentPlayer().getAlliance();
    }

    public boolean isInitialPosition() {
        // TODO
        return true;
    }

    public int getNbStep() {
        return this.getTransitions().size();
    }

    public boolean isLogBoard() {
        return true;
    }

    public String toPGN() {
        final StringBuffer sb = new StringBuffer();
        sb.append(String.format("[Event \"%s\"]\n", "AquilaChess"));
        sb.append(String.format("[Site \"%S\"]\n", "Mougins 06250"));
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd");
        final String date = simpleDateFormat.format(new Date());
        sb.append(String.format("[Date \"%s\"]\n", date));// "1992.11.04"));
        sb.append(String.format("[Round \"%d\"]\n", this.moves.size()));
        sb.append(String.format("[White \"%s\"]\n", strategyWhite.getClass().getSimpleName()));
        sb.append(String.format("[Black \"%s\"]\n", strategyBlack.getClass().getSimpleName()));
        String result = "*";
        switch (this.status) {
            case WHITE_CHESSMATE:
                result = "1-0";
                break;
            case BLACK_CHESSMATE:
                result = "0-1";
                break;
            case DRAW_50:
            case DRAW_300:
            case PAT:
            case DRAW_3:
            case DRAW_NOT_ENOUGH_PIECES:
                result = "1/2-1/2";
                break;
        }
        sb.append(String.format("[Result \"%s\"]\n", result)); // [Result "0-1"], [Result "1-0"], [Result "1/2-1/2"],
        // [Result "*"]
        movesToPGN(sb);
        return sb.toString();
    }

    private void movesToPGN(final StringBuffer sb) {
        int i = 1;
        int nbCol = 0;

        final ListIterator<Move> it = this.getMoves().listIterator();
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

    public String toPGN(final Move move) {
        final StringBuffer sb = new StringBuffer();
//        if(move.isCastlingMove()) {
//
//        } else {
        sb.append(move.toString());
//        }
//        switch (this.castling) {
//            case NONE:
//                sb.append(piece.toPGN());
//                if ((this.capturePiece != null && piece instanceof Pawn) || this.doubleDestination) {
//                    sb.append(this.startLocation.coordAlgebrique());
//                }
//                if (this.capturePiece != null) {
//                    sb.append("x");
//                }
//                sb.append(this.endLocation.coordAlgebrique());
//                if (this.promotedPiece != null) {
//                    sb.append("=");
//                    sb.append(promotedPiece.toPGN());
//                }
//                if (this.isCheck()) {
//                    sb.append("+");
//                }
//                break;
//            case SHORT:
//                sb.append(CASTLING_SHORT);
//                break;
//            case LONG:
//                sb.append(CASTLING_LONG);
//                break;
//        }
        return sb.toString();
    }

    public Board getLastBoard() {
        return transitions.size() == 0 ? this.getBoard() : this.transitions.lastElement().getBoard();
    }

    public Move getLastMove() {
        return this.getMoves().get(this.getMoves().size() - 1);
    }

    @Builder(toBuilder = true)
    @Getter
    public static class GameTransition {
        final Board board;
        final Move fromMove;
    }

    public void setup(final Strategy strategyPlayerWhite,
                      final Strategy strategyPlayerBlack) {
        assert (strategyPlayerWhite.getAlliance() == Alliance.WHITE);
        assert (strategyPlayerBlack.getAlliance() == Alliance.BLACK);
        this.strategyWhite = strategyPlayerWhite;
        this.strategyBlack = strategyPlayerBlack;
        switch (this.board.currentPlayer().getAlliance()) {
            case WHITE:
                nextStrategy = strategyPlayerWhite;
                break;
            case BLACK:
                nextStrategy = strategyPlayerBlack;
                break;
        }
    }

    public Player getNextPlayer() {
        return this.board.currentPlayer();
    }

    public GameStatus play() throws Exception {
        assert (nextStrategy != null);
        List<Move> moves = getNextPlayer().getLegalMoves(Move.MoveStatus.DONE);
        Move move = nextStrategy.play(this, moveOpponent, moves);
        if (move.isAttack() == false &&
                move.getMovedPiece().getPieceType() != Piece.PieceType.PAWN)
            this.nbMoveNoAttackAndNoPawn++;
        else
            this.nbMoveNoAttackAndNoPawn = 0;
        board = getNextPlayer().executeMove(move);
        transitions.add(new GameTransition(board, move));
        this.status = calculateStatus();
        this.nextStrategy = this.nextStrategy == this.strategyBlack ? this.strategyWhite : this.strategyBlack;
        moveOpponent = move;
        this.moves.add(move);
        return this.status;
    }

    public Player getPlayer(final Alliance alliance) {
        return alliance.choosePlayerByAlliance(this.board.whitePlayer(), this.board.blackPlayer());
    }

    public Strategy getStrategy(final Alliance alliance) {
        return alliance.isWhite() ? strategyWhite : strategyBlack;

    }

    public enum GameStatus {
        IN_PROGRESS,
        PAT,
        WHITE_CHESSMATE,
        BLACK_CHESSMATE,
        DRAW_50,
        DRAW_300,
        DRAW_3,
        DRAW_NOT_ENOUGH_PIECES
    }

    public GameStatus calculateStatus() {
        if (board.whitePlayer().isInCheckMate()) return GameStatus.WHITE_CHESSMATE;
        if (board.blackPlayer().isInCheckMate()) return GameStatus.BLACK_CHESSMATE;
        if (getNextPlayer().isInStaleMate()) return GameStatus.PAT;
        if (transitions.size() >= 300) return GameStatus.DRAW_300;
        if (this.nbMoveNoAttackAndNoPawn >= 50) return GameStatus.DRAW_50;
        if (!isThereEnoughMaterials()) return GameStatus.DRAW_NOT_ENOUGH_PIECES;
        return GameStatus.IN_PROGRESS;
    }

    private boolean isThereEnoughMaterials() {
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

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(String.format("GAME STATUS:%s\n", getStatus()));
        sb.append(String.format("MOVES:%s\n", transitions
                .stream()
                .map(gameTransition -> gameTransition.fromMove.toString())
                .collect(Collectors.joining(","))));
        sb.append(String.format("nbStep:%d\n", transitions.size()));
        sb.append(String.format("current player:%s\n", getNextPlayer().getAlliance()));
        sb.append(String.format("current legal move:%s\n", this
                .getNextPlayer()
                .getLegalMoves()
                .stream()
                .map(move -> move.toString())
                .collect(Collectors.joining(","))));
        sb.append(this.board);
        return sb.toString();
    }

    public void saveBatch(ResultGame resultGame, int numGames) throws IOException {
        log.info("SAVING Batch (game number: {}) ... (do not stop the jvm)", numGames);
        log.info("Result: {}   Game size: {} inputsList(s)", resultGame.reward, trainGame.oneStepRecordList.size());
        trainGame.save(numGames, resultGame);
        log.info("SAVE DONE");
        clearTrainGame();
    }

    public void clearTrainGame() {
        this.trainGame.clear();
    }
}
