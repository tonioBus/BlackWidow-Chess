package com.aquila.chess.strategy.check;

import com.aquila.chess.AbstractGame;
import com.aquila.chess.Game;
import com.aquila.chess.strategy.FixStrategy;
import com.aquila.chess.strategy.mcts.inputs.InputRecord;
import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.aquila.AquilaInputsManagerImpl;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.chess.engine.classic.board.Board.createStandardBoard;

@Slf4j
public class GameChecker extends AbstractGame {
    private final Game game;
    private final FixStrategy whitePlayer;
    private final FixStrategy blackPlayer;
    private final List<Move> moves = new ArrayList<>();

    public GameChecker(final InputsManager inputsManager) {
        super(inputsManager, createStandardBoard());
        game = Game.builder().board(board).inputsManager(inputsManager).build();
        whitePlayer = new FixStrategy(Alliance.WHITE);
        blackPlayer = new FixStrategy(Alliance.BLACK);
        game.setup(whitePlayer, blackPlayer);
    }

    public Game.GameStatus play(String givenMove) throws Exception {
        final Collection<Move> currentMoves = game.getNextPlayer().getLegalMoves();
        if (!givenMove.equals(Move.INIT_MOVE)) {
            Optional<Move> currentMoveOpt = currentMoves.stream().filter(move -> move.toString().equals(givenMove.toString())).findFirst();
            if (currentMoveOpt.isEmpty()) {
                log.error("no legal move found for: {}", givenMove);
                log.error("possible moves:{}", currentMoves.stream().map(move -> move.toString()).collect(Collectors.joining(",")));
                log.error("game:nb step:{}\n{}\n{}", game.getNbStep(), game.toPGN(), game.getBoard().toString());
                if (game.getNbStep() >= 300) return Game.GameStatus.DRAW_300;
                throw new RuntimeException("no legal move found for: " + givenMove);
            }
            Move currentMove = currentMoveOpt.get();
            moves.add(currentMove);
            switch (game.getCurrentPLayerColor()) {
                case WHITE -> {
                    whitePlayer.setNextMove(currentMove);
                }
                case BLACK -> {
                    blackPlayer.setNextMove(currentMove);
                }
            }
            Game.GameStatus gameStatus = game.play();
            this.moves.add(currentMove);
            this.inputsManager.processPlay(getLastBoard(), currentMove);
            return gameStatus;
        }
        return Game.GameStatus.IN_PROGRESS;
    }
}
