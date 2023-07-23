package com.aquila.chess.strategy.mcts;

import org.deeplearning4j.nn.api.NeuralNetwork;

import java.io.IOException;
import java.util.List;

public interface INN {

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

    void train(boolean train);

    void close();
}
