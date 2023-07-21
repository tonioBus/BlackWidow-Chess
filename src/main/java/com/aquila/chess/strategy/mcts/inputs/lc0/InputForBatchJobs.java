package com.aquila.chess.strategy.mcts.inputs.lc0;

import com.aquila.chess.strategy.mcts.MCTSGame;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.lc0.InputsNNFactory;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record InputForBatchJobs(Move move,
                                Alliance color2play,
                                MCTSGame mctsGame,
                                boolean isDirichlet,
                                boolean isRootNode,
                                Lc0InputsFullNN inputs) {

    public InputForBatchJobs(final Move move,
                             final Alliance color2play,
                             final MCTSGame mctsGame,
                             final boolean isDirichlet,
                             final boolean isRootNode) {
        this(move, color2play, mctsGame, isDirichlet, isRootNode, mctsGame.getInputsManager().createInputs(mctsGame.getLastBoard(), move, color2play));
    }

}
