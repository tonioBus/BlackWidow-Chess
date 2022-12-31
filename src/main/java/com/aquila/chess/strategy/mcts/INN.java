package com.aquila.chess.strategy.mcts;

import org.deeplearning4j.nn.api.NeuralNetwork;

import java.io.IOException;
import java.util.List;

public interface INN {

    final int SIZE_POSITION = 13;

    // 5: Pawn:0, Bishop:1, Knight:2, Rook:3, Queen:4, King:5
    //
    // for 0..7
    //   [0-5] pieces for White
    //   [6-11] pieces for Black
    //   12: 1 or more repetition ??
    // end for
    // 104: white can castle queenside
    // 105: white can castle kingside
    // 106: black can castle queenside
    // 107: black can castle kingside
    // 108: 0 -> white turn  1 -> white turn
    // 109: repetitions whitout capture and pawn moves (50 moves rules)
    // 110: 0
    // 111: 1 -> edges detection
    final int FEATURES_PLANES = 112;
    int PAWN_INDEX = 0;
    int KNIGHT_INDEX = 1;
    int BISHOP_INDEX = 2;
    int ROOK_INDEX = 3;
    int QUEEN_INDEX = 4;
    int KING_INDEX = 5;

    double getScore();

    void setUpdateLr(UpdateLr updateLr, int nbGames);

    void updateLr(int nbGames);

    void save() throws IOException;

    void fit(double[][][][] inputs, double[][] policies, double[][] values);

    /**
     * @return the filename
     */
    String getFilename();

    double getLR();

    void setLR(double lr);

    List<OutputNN> outputs(double[][][][] nbIn, int len);

    NeuralNetwork getNetwork();

    void reset();

    void close();
}
