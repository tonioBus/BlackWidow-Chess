package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.strategy.mcts.MCTSGame;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;

public record ServiceNNInputsJobs(Move move,
                                  Alliance moveColor,
                                  MCTSGame mctsGame,
                                  boolean isDirichlet,
                                  boolean isRootNode,
                                  InputsFullNN inputs) {

    public ServiceNNInputsJobs(final Move move,
                               final Alliance moveColor,
                               final MCTSGame mctsGame,
                               final boolean isDirichlet,
                               final boolean isRootNode) {
        this(move,
                moveColor,
                mctsGame,
                isDirichlet,
                isRootNode,
                mctsGame.getInputsManager().createInputs(
                        new InputRecord(
                        mctsGame,
                        mctsGame.getMoves(),
                        move,
                        moveColor))
                );
    }

}
