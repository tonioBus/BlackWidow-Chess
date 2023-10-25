package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.strategy.mcts.MCTSGame;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;

public record ServiceNNInputsJobs(Move move,
                                  Alliance color2play,
                                  MCTSGame mctsGame,
                                  boolean isDirichlet,
                                  boolean isRootNode,
                                  InputsFullNN inputs) {

    public ServiceNNInputsJobs(final Move move,
                               final Alliance color2play,
                               final MCTSGame mctsGame,
                               final boolean isDirichlet,
                               final boolean isRootNode) {
        this(move,
                color2play,
                mctsGame,
                isDirichlet,
                isRootNode,
                mctsGame.getInputsManager().createInputs(
                        mctsGame.getLastBoard(),
                        move,
                        mctsGame.getMoves(),
                        color2play));
    }

}
