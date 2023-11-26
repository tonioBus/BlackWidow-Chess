package com.aquila.chess;

import com.aquila.chess.strategy.Strategy;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import com.chess.engine.classic.player.Player;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class Game extends AbstractGame {

    @Getter
    protected Strategy strategyWhite;

    @Getter
    protected Strategy strategyBlack;

    @Getter
    protected Move moveOpponent = null;

    @Getter
    protected Strategy nextStrategy;

    @Builder
    public Game(InputsManager inputsManager, Board board, Strategy strategyWhite, Strategy strategyBlack) {
        super(inputsManager, board);
        this.strategyWhite = strategyWhite;
        this.strategyBlack = strategyBlack;
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

    public GameStatus play() throws Exception {
        assert (nextStrategy != null);
        this.inputsManager.updateHashsTables(board, nextStrategy.getAlliance());
        List<Move> possibleMoves = getNextPlayer().getLegalMoves(Move.MoveStatus.DONE);
        log.info("current player:[{}] legal move:[{}] {}",
                getNextPlayer().getAlliance(),
                possibleMoves.size(),
                possibleMoves.stream().map(move -> move.toString()).collect(Collectors.joining(",")));
        Move move = nextStrategy.play(this, moveOpponent, possibleMoves);
        if (possibleMoves.stream().filter(move1 -> move1.toString().equals(move.toString())).findFirst().isEmpty()) {
            throw new RuntimeException(String.format("move:%s not in possible move:%s", move, possibleMoves));
        }
        if (!move.isAttack() &&
                move.getMovedPiece().getPieceType() != Piece.PieceType.PAWN)
            this.nbMoveNoAttackAndNoPawn++;
        else
            this.nbMoveNoAttackAndNoPawn = 0;
        board = getNextPlayer().executeMove(move);
        this.status = calculateStatus(board);
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

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(String.format("GAME STATUS:%s\n", getStatus()));
        sb.append(String.format("MOVES:%s\n", moves
                .stream()
                .map(move -> move.toString())
                .collect(Collectors.joining(","))));
        sb.append(String.format("nbStep:%d\n", moves.size()));
        sb.append(String.format("Repetition:%d  |  50 draws counter:%d\n", inputsManager.getNbRepeat(getCurrentPLayerColor().complementary()), this.nbMoveNoAttackAndNoPawn));
        sb.append(this.board);
        return sb.toString();
    }

}
