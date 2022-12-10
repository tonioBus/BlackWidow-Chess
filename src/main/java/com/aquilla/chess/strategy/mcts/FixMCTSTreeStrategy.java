package com.aquilla.chess.strategy.mcts;

import com.aquilla.chess.Game;
import com.aquilla.chess.strategy.FixStrategy;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.List;

@Slf4j
public class FixMCTSTreeStrategy extends FixStrategy {

    @Getter
    protected final CircularFifoQueue<double[][][]> lastInputs = new CircularFifoQueue<>(8);

    public FixMCTSTreeStrategy(final Alliance alliance) {
        super(alliance);
    }

    protected void pushNNInput(final Game game, final Move move) {
        double[][][] inputs = InputsNNFactory.createInputsForOnePosition(game, move);
        this.lastInputs.add(inputs);
    }

    public void copyLastInputs(final FixMCTSTreeStrategy chessPlayer) {
        this.lastInputs.clear();
        for (double[][][] inputs : chessPlayer.lastInputs) {
            this.lastInputs.add(inputs);
        }
    }

    @Override
    public Move play(final Game game, final Move moveOpponent, final List<Move> moves) throws Exception {
        final Move move = super.play(game, moveOpponent, moves);
        log.info("{} nextPlay() -> {}", this, move);
        pushNNInput(game, move);
        return move;
    }

    @Override
    public String getName() {
        return String.format("[%s %s nextMove:%s] ", alliance, this.getClass().getSimpleName(),
                this.nextMoveSz);
    }

}