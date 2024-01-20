package com.aquila.chess;

import com.aquila.chess.strategy.Strategy;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class Game extends AbstractGame {

    @Builder
    public Game(InputsManager inputsManager, Board board) {
        super(inputsManager, board);
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

    public GameStatus play() throws Exception {
        assert (nextStrategy != null);
        List<Move> possibleMoves = getNextPlayer().getLegalMoves(Move.MoveStatus.DONE);
        log.info("[{}] current player:[{}] legal move:[{}] {}",
                this.moves.size(),
                getNextPlayer().getAlliance(),
                possibleMoves.size(),
                possibleMoves.stream().map(move -> move.toString()).collect(Collectors.joining(",")));
        Move move = nextStrategy.evaluateNextMove(this, moveOpponent, possibleMoves);
        if (possibleMoves.stream().filter(move1 -> move1.toString().equals(move.toString())).findFirst().isEmpty()) {
            throw new RuntimeException(String.format("move:%s not in possible move:%s", move, possibleMoves));
        }
        board = getNextPlayer().executeMove(move);
        this.inputsManager.updateHashsTables(move, board);
        this.status = calculateStatus(board, move);
        this.nextStrategy = opponentStrategy(this.nextStrategy);
        moveOpponent = move;
        registerMove(move, board);
        return this.status;
    }

    Strategy opponentStrategy(final Strategy strategy) {
        return switch (strategy.getAlliance()) {
            case BLACK -> strategyWhite;
            case WHITE -> strategyBlack;
        };
    }

    public void end(final Move move) {
        switch (move.getAllegiance()) {
            case BLACK -> strategyWhite.end(move);
            case WHITE -> strategyBlack.end(move);
        }
    }

    public enum GameStatus {
        IN_PROGRESS,
        PAT,
        WHITE_CHESSMATE,
        BLACK_CHESSMATE,
        DRAW_50,
        DRAW_TOO_MUCH_STEPS,
        DRAW_3,
        DRAW_NOT_ENOUGH_PIECES;

        public boolean isTheEnd() {
            return this != IN_PROGRESS;
        }
    }

    ;

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(String.format("GAME STATUS:%s\n", getStatus()));
        sb.append(String.format("MOVES:%s\n", moves
                .stream()
                .map(move -> move.toString())
                .collect(Collectors.joining(","))));
        sb.append(String.format("nbStep:%d\n", moves.size()));
        String repetition = getRepetition();
        sb.append(String.format("Repetition:%s  |  50 draws counter:%d\n", repetition, this.nbMoveNoAttackAndNoPawn));
        sb.append(this.board);
        return sb.toString();
    }

    public int getNbRepeat() {
        int nbMoves = moves.size();
        int skipMoves = nbMoves < 8 ? 0 : nbMoves - 8;
        return (int) moves
                .stream()
                .skip(skipMoves)
                .filter(move -> inputsManager.isRepeatMove(move))
                .count();
    }

    public String getRepetition() {
        int nbMoves = moves.size();
        int skipMoves = nbMoves < 8 ? 0 : nbMoves - 8;
        return moves
                .stream()
                .skip(skipMoves)
                .filter(move -> inputsManager.isRepeatMove(move))
                .map(move -> String.format("%s-%s", move.getAllegiance(), move))
                .collect(Collectors.joining(","));
    }
}
