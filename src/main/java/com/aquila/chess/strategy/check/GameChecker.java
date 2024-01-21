package com.aquila.chess.strategy.check;

import com.aquila.chess.AbstractGame;
import com.aquila.chess.Game;
import com.aquila.chess.strategy.FixStrategy;
import com.aquila.chess.strategy.mcts.TrainException;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.chess.engine.classic.board.Board.createStandardBoard;

@Slf4j
public class GameChecker extends AbstractGame {

    private final String label;

    public GameChecker(final InputsManager inputsManager, final String label) {
        super(inputsManager, createStandardBoard());
        this.label=label;
        setup(new FixStrategy(Alliance.WHITE), new FixStrategy(Alliance.BLACK));
    }

    public Game.GameStatus play(String givenMove) throws Exception {
        final Collection<Move> currentMoves = super.getNextPlayer().getLegalMoves();
        if (!givenMove.equals(Move.INIT_MOVE)) {
            Optional<Move> currentMoveOpt = currentMoves.stream().filter(move -> move.toString().equals(givenMove)).findFirst();
            if (currentMoveOpt.isEmpty()) {
                Alliance alliance = super.getNextPlayer().getAlliance();
                final Collection<Move> opponentMoves = getPlayer(alliance.complementary()).getLegalMoves();
                log.error("[{}] no legal move found for steps:{} -> {}", alliance, this.getNbStep(), givenMove);
                log.error("[{}] possible moves:{}", alliance, currentMoves.stream().map(move -> move.toString()).collect(Collectors.joining(",")));
                log.error("[{}] possible opponentMoves:{}", alliance, opponentMoves.stream().map(move -> move.toString()).collect(Collectors.joining(",")));
                log.error("[{}] game:nb step:{}\n{}\n{}", alliance, super.getNbStep(), super.toPGN(), super.getBoard().toString());
                if (super.getNbStep() >= AbstractGame.NUMBER_OF_MAX_STEPS) return Game.GameStatus.DRAW_TOO_MUCH_STEPS;
                return Game.GameStatus.IN_PROGRESS;
                // throw new TrainException("no legal move found for: " + givenMove, label);
            }
            Move currentMove = currentMoveOpt.get();
            switch (super.getCurrentPLayerColor()) {
                case WHITE -> {
                    ((FixStrategy) strategyWhite).setNextMove(currentMove);
                    board = getPlayer(Alliance.WHITE).executeMove(currentMove);
                }
                case BLACK -> {
                    ((FixStrategy) strategyBlack).setNextMove(currentMove);
                    board = getPlayer(Alliance.BLACK).executeMove(currentMove);
                }
            }
            // super.board = currentMove.execute();
            inputsManager.updateHashsTables(currentMove, board);
            Game.GameStatus gameStatus = calculateStatus(board, currentMove);
            registerMove(currentMove, board);
            return gameStatus;
        }
        return Game.GameStatus.IN_PROGRESS;
    }
}
