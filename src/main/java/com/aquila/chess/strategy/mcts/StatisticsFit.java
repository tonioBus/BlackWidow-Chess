package com.aquila.chess.strategy.mcts;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class StatisticsFit {
    final int startGame;
    final int endGame;
    public int nbTrainGame;
    public List<String> listErrorTrain = new ArrayList<>();
    public List<String> listFilteredTrain = new ArrayList<>();
    public int nbInputsFit;
    public int nbWin;
    public int nbLost;
    public int nbDrawn;
    public double scoreMin = Double.MAX_VALUE;
    public double scoreMax = Double.MIN_VALUE;

    public StatisticsFit(int startGame, int endGame) {
        this.startGame = startGame;
        this.endGame = endGame;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("\tstartGame: %d\n", startGame));
        sb.append(String.format("\tendGame: %d\n", endGame));
        sb.append(String.format("\tnbTrainGame: %d\n", nbTrainGame));
        sb.append(String.format("\tnbInputsFit: %d\n", nbInputsFit));
        sb.append(String.format("\tnbWin: %d\n", nbWin));
        sb.append(String.format("\tnbLost: %d\n", nbLost));
        sb.append(String.format("\tnbDrawn: %d\n", nbDrawn));
        sb.append(String.format("\tmediumValue: %f\n", ((double) nbWin - (double) nbLost) / ((double) nbWin + nbLost + nbDrawn)));
        sb.append(String.format("\tscoreMin: %f\n", scoreMin));
        sb.append(String.format("\tscoreMax: %f\n", scoreMax));
        sb.append(String.format("\tErrorTrains: \n%s\n",
                listErrorTrain.stream().collect(Collectors.joining(",", "[", "]"))));
        sb.append(String.format("\tFilteredTrains: \n%s\n",
                listFilteredTrain.stream().collect(Collectors.joining(",", "[", "]"))));
        return sb.toString();
    }
}
