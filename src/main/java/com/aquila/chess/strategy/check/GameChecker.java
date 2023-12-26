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

    public GameChecker(final InputsManager inputsManager) {
        super(inputsManager, createStandardBoard());
        setup(new FixStrategy(Alliance.WHITE), new FixStrategy(Alliance.BLACK));
    }

    public Game.GameStatus play(String givenMove) throws Exception {
        final Collection<Move> currentMoves = super.getNextPlayer().getLegalMoves();
        if (!givenMove.equals(Move.INIT_MOVE)) {
            Optional<Move> currentMoveOpt = currentMoves.stream().filter(move -> move.toString().equals(givenMove)).findFirst();
            if (currentMoveOpt.isEmpty()) {
                Alliance alliance = super.getNextPlayer().getAlliance();
                log.error("[{}] no legal move found for: {}", alliance, givenMove);
                log.error("[{}] possible moves:{}", alliance, currentMoves.stream().map(move -> move.toString()).collect(Collectors.joining(",")));
                log.error("[{}] game:nb step:{}\n{}\n{}", alliance, super.getNbStep(), super.toPGN(), super.getBoard().toString());
                if (super.getNbStep() >= 300) return Game.GameStatus.DRAW_300;
                throw new RuntimeException("no legal move found for: " + givenMove);
            }
            Move currentMove = currentMoveOpt.get();
            switch (super.getCurrentPLayerColor()) {
                case WHITE -> {
                    ((FixStrategy) strategyWhite).setNextMove(currentMove);
                }
                case BLACK -> {
                    ((FixStrategy) strategyBlack).setNextMove(currentMove);
                }
            }
            super.board = currentMove.execute();
            Game.GameStatus gameStatus = calculateStatus(board, currentMove);
            registerMove(currentMove);
            return gameStatus;
        }
        return Game.GameStatus.IN_PROGRESS;
    }
}
