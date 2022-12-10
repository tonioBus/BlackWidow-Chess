package com.aquilla.chess.strategy.mcts;

import com.aquilla.chess.Game;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import lombok.Data;

@Data
public class InputForBatchJobs {
    final Move move;
    final Alliance color2play;
    final Game game;
    final boolean isDirichlet;
    final boolean isRootNode;

    final double[][][] inputs;

    public InputForBatchJobs(final Move move,
                             final Alliance color2play,
                             final Game game,
                             final boolean isDirichlet,
                             final boolean isRootNode) {
        this.move = move;
        this.color2play = color2play;
        this.game = game;
        this.isDirichlet = isDirichlet;
        this.isRootNode = isRootNode;
        this.inputs = createInput(game);
    }

    private double[][][] createInput(final Game game) {
        double[][][] inputs = new double[INN.FEATURES_PLANES][BoardUtils.NUM_TILES_PER_ROW][BoardUtils.NUM_TILES_PER_ROW];
        InputsNNFactory.createInputs(inputs, game, color2play);
        return inputs;
    }

}
