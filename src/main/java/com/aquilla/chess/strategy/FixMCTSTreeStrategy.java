package com.aquilla.chess.strategy;

import com.aquilla.chess.Game;
import com.aquilla.chess.strategy.mcts.InputsNNFactory;
import com.aquilla.chess.strategy.mcts.MCTSGame;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Optional;

public class FixMCTSTreeStrategy implements Strategy {

    @Setter
    protected String nextMoveSz;

    @Setter
    protected Move nextMove = null;

    @Getter
    protected final Alliance alliance;

    public FixMCTSTreeStrategy(final Alliance alliance) {
        this.alliance = alliance;
    }

    @Override
    public Move play(final Game game, final Move opponentMove, final List<Move> moves) throws Exception {
        final Move move = this.nextMove;
        this.nextMove = null;
        if(move != null) {
            return move;
        }
        Optional<Move> nextMove = BoardUtils.getMove(nextMoveSz, moves);
        if(nextMove.isEmpty()) throw new RuntimeException(String.format("Incorrect move:%s", nextMoveSz));
        return nextMove.get();
    }

    @Override
    public String getName() {
        return toString();
    }

    @Override
    public String toString() {
        return String.format("%s{nextMoveSz=%s,nextMove=%s,alliance=%s}",
                this.getClass().getSimpleName(),
                nextMoveSz,
                nextMove,
                alliance);
    }
}