package com.aquila.chess.strategy.check;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.FixStrategy;
import com.aquila.chess.strategy.mcts.inputs.aquila.AquilaInputsManagerImpl;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class GameChecker {
    private final Board board;
    private final Game game;
    private final FixStrategy whitePlayer;
    private final FixStrategy blackPlayer;

    public GameChecker() {
        board = Board.createStandardBoard();
        game = Game.builder().board(board).inputsManager(new AquilaInputsManagerImpl()).build();
        whitePlayer = new FixStrategy(Alliance.WHITE);
        blackPlayer = new FixStrategy(Alliance.BLACK);
        game.setup(whitePlayer, blackPlayer);
    }

    public Collection<Move> getCurrentLegalMoves() {
        return game.getNextPlayer().getLegalMoves();
    }

    public void play(String givenMove) {
        final Collection<Move> currentMoves = game.getNextPlayer().getLegalMoves();
        Optional<Move> currentMoveOpt = currentMoves.stream().filter(move -> move.toString().equals(givenMove)).findFirst();
        if(currentMoveOpt.isEmpty()) {
            log.error("no legal move found for: {}", givenMove);
            log.error("possible moves:{}", currentMoves.stream().map(move -> move.toString()).collect(Collectors.joining(",")));
            log.error("game:nb step:{}\n{}\n{}", game.getNbStep(), game.toPGN(), game.getBoard().toString());
            throw new RuntimeException("no legal move found for: " + givenMove);
        }
        Move currentMove = currentMoveOpt.get();
        switch (game.getColor2play()) {
            case WHITE -> {
                whitePlayer.setNextMove(currentMove);
            }
            case BLACK -> {
                blackPlayer.setNextMove(currentMove);
            }
        }
        try {
            game.play();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
