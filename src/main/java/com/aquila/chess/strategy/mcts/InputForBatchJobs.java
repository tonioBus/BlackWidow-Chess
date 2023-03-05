package com.aquila.chess.strategy.mcts;

import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.InputsNNFactory;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record InputForBatchJobs(Move move,
                                Alliance color2play,
                                MCTSGame mctsGame,
                                boolean isDirichlet,
                                boolean isRootNode,
                                InputsFullNN inputs) {

    public InputForBatchJobs(final Move move,
                             final Alliance color2play,
                             final MCTSGame mctsGame,
                             final boolean isDirichlet,
                             final boolean isRootNode) {
        this(move, color2play, mctsGame, isDirichlet, isRootNode, InputsNNFactory.createInput(mctsGame, move, color2play));
    }

}
