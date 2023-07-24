package com.aquila.chess.strategy.mcts.inputs.aquila;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;

public class AquilaInputsManager implements InputsManager {
    @Override
    public int getNbFeaturesPlanes() {
        return 0;
    }

    @Override
    public InputsFullNN createInputs(Board board, Move move, Alliance color2play) {
        return null;
    }

    @Override
    public void startMCTSStep(Game game) {

    }

    @Override
    public InputsManager clone() {
        return null;
    }

    @Override
    public long hashCode(Board board, Move move, Alliance color2play) {
        return 0;
    }

    @Override
    public void processPlay(Board board, Move move) {

    }
}
