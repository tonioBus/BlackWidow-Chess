package com.aquila.chess;

import com.aquila.chess.strategy.Strategy;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.strategy.mcts.utils.MovesUtils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import com.chess.engine.classic.player.Player;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class Game {

    @Getter
    protected Strategy strategyWhite;

    @Getter
    protected Strategy strategyBlack;

    @Getter
    protected Board board;

    @Getter
    protected Move moveOpponent = null;

    @Getter
    private InputsManager inputsManager;

    @Getter
    protected int nbMoveNoAttackAndNoPawn = 0;
    @Getter
    protected GameStatus status;

    @Getter
    protected final List<Move> moves = new ArrayList<>(127);

    @Getter
    protected Strategy nextStrategy;


    @Builder
    public Game(InputsManager inputsManager, Board board, Strategy strategyWhite, Strategy strategyBlack) {
        if (inputsManager == null) {
            log.warn("USING DEFAULT INPUT-MANAGER -> LC0");
            this.inputsManager = new Lc0InputsManagerImpl();
        } else {
            this.inputsManager = inputsManager;
        }
        this.board = board;
        this.strategyWhite = strategyWhite;
        this.strategyBlack = strategyBlack;
    }

    public Alliance getColor2play() {
        return this.board.currentPlayer().getAlliance();
    }

    public boolean isInitialPosition() {
        return this.getNbStep() == 0;
    }

    public int getNbStep() {
        return this.getMoves().size();
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

    public void playAll() {
        this.board = Board.createStandardBoard();
        AtomicInteger nbStep = new AtomicInteger(1);
        this.moves.stream().forEach(move -> {
            try {
                GameStatus status1 = this.play();
                log.info("Status:{} nbStep:{}", status1, nbStep.getAndIncrement());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public String toPGN(final Move move) {
        return move.toString();
    }

    public Board getLastBoard() {
        int size = moves.size();
        return size == 0 ? this.getBoard() : this.moves.get(size - 1).execute();
    }

    public Move getLastMove() {
        return this.getMoves().get(this.getMoves().size() - 1);
    }

    public void setup(final Strategy strategyPlayerWhite,
                      final Strategy strategyPlayerBlack) {
        assert (strategyPlayerWhite.getAlliance() == Alliance.WHITE);
        assert (strategyPlayerBlack.getAlliance() == Alliance.BLACK);
        this.strategyWhite = strategyPlayerWhite;
        this.strategyBlack = strategyPlayerBlack;
        nextStrategy = switch (this.board.currentPlayer().getAlliance()) {
            case WHITE -> strategyPlayerWhite;
            case BLACK -> strategyPlayerBlack;
        };
        Move.InitMove initMove = switch (this.board.currentPlayer().getAlliance()) {
            case WHITE -> new Move.InitMove(board, Alliance.BLACK);
            case BLACK -> new Move.InitMove(board, Alliance.WHITE);
        };
        moveOpponent = initMove;
        this.getMoves().add(initMove);

    }

    public Player getNextPlayer() {
        return this.board.currentPlayer();
    }

    public GameStatus play() throws Exception {
        assert (nextStrategy != null);
        List<Move> possibleMoves = getNextPlayer().getLegalMoves(Move.MoveStatus.DONE);
        Move move = nextStrategy.play(this, moveOpponent, possibleMoves);
        if (possibleMoves.stream().filter(move1 -> move1.equals(move)).findFirst().isEmpty()) {
            throw new RuntimeException(String.format("move:%s not in possible move:%s", move, possibleMoves));
        }
        if (!move.isAttack() &&
                move.getMovedPiece().getPieceType() != Piece.PieceType.PAWN)
            this.nbMoveNoAttackAndNoPawn++;
        else
            this.nbMoveNoAttackAndNoPawn = 0;
        board = getNextPlayer().executeMove(move);
        this.status = calculateStatus();
        this.nextStrategy = opponentStrategy(this.nextStrategy);
        moveOpponent = move;
        this.moves.add(move);
        return this.status;
    }

    Strategy opponentStrategy(final Strategy strategy) {
        return switch (strategy.getAlliance()) {
            case BLACK -> strategyWhite;
            case WHITE -> strategyBlack;
        };
    }

    public Player getPlayer(final Alliance alliance) {
        return alliance.choosePlayerByAlliance(this.board.whitePlayer(), this.board.blackPlayer());
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
        if (moves.size() >= 300) return GameStatus.DRAW_300;
        if (MovesUtils.is3MovesRepeat(moves)) return Game.GameStatus.DRAW_3;
        if (this.nbMoveNoAttackAndNoPawn >= 50) return GameStatus.DRAW_50;
        if (!isThereEnoughMaterials(this.board)) return GameStatus.DRAW_NOT_ENOUGH_PIECES;
        return GameStatus.IN_PROGRESS;
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

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(String.format("GAME STATUS:%s\n", getStatus()));
        sb.append(String.format("MOVES:%s\n", moves
                .stream()
                .map(move -> move.toString())
                .collect(Collectors.joining(","))));
        sb.append(String.format("nbStep:%d\n", moves.size()));
        sb.append(String.format("Repetition:%d  |  50 draws counter:%d\n", inputsManager.getNbRepeat(getColor2play().complementary()), this.nbMoveNoAttackAndNoPawn));
        sb.append(String.format("current player:%s\n", getNextPlayer().getAlliance()));
        List<Move> legalMoves = this
                .getNextPlayer()
                .getLegalMoves(Move.MoveStatus.DONE);
        sb.append(String.format("current legal move:[%d] %s\n",
                legalMoves.size(),
                legalMoves.stream().map(move -> move.toString()).collect(Collectors.joining(","))));
        sb.append(this.board);
        return sb.toString();
    }

}
