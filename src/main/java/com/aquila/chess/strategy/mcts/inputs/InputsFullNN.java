package com.aquila.chess.strategy.mcts.inputs;

import java.io.Serializable;

public interface InputsFullNN extends Serializable {

    double[][][] inputs();
}
