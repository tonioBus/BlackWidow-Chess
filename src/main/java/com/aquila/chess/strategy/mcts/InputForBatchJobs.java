package com.aquila.chess.strategy.mcts;

import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import lombok.Data;
import lombok.Getter;

@Data
public class InputForBatchJobs {
    final Move move;
    final Alliance color2play;
    final MCTSGame mctsGame;

    @Getter
    final boolean isDirichlet;
    final boolean isRootNode;

    final double[][][] inputs;

    public InputForBatchJobs(final Move move,
                             final Alliance color2play,
                             final MCTSGame mctsGame,
                             final boolean isDirichlet,
                             final boolean isRootNode) {
        this.move = move;
        this.color2play = color2play;
        this.mctsGame = mctsGame;
        this.isDirichlet = isDirichlet;
        this.isRootNode = isRootNode;
        this.inputs = InputsNNFactory.createInput(mctsGame, color2play);
    }


}
