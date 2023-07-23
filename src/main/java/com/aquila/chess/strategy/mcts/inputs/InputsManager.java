package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsFullNN;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;

public interface InputsManager {

    int getNbFeaturesPlanes();

    Lc0InputsFullNN createInputs(Board board,
                                 Move move,
                                 Alliance color2play);

    void startMCTSStep(Game game);

    InputsManager clone();

    long hashCode(Board board, Move move, Alliance color2play);

    void processPlay(final Board board, final Move move);
}
