package com.aquila.chess.fit;

import com.aquila.chess.strategy.mcts.StatisticsFit;

import java.io.File;
import java.util.Map;

@FunctionalInterface
public interface TrainFile {
    void train(File file, Map<String, StatisticsFit> statistics);
}
