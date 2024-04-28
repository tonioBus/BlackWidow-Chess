package com.aquila.chess.fit;

import com.aquila.chess.TrainGame;
import com.aquila.chess.strategy.mcts.StatisticsFit;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class AbstractFit {

    class Sequence {
        SortedMap<Long, File> drawGames = new TreeMap<>();
        SortedMap<Long, File> winLostGames = new TreeMap<>();
    }

    final List<Sequence> sequences = new ArrayList<>();

    @Getter
    private final File file;

    @Getter
    ConfigFit configFit;


    public AbstractFit(String configFile) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(ConfigFit.class);
        file = new File(configFile);
        this.configFit = (ConfigFit) context.createUnmarshaller().unmarshal(file);
    }

    public void run(TrainFile trainFile, Map<String, StatisticsFit> statistics) {
        retrieveAllFiles();
        log.info("winLostGames  games:{}", sequences.stream().mapToDouble(sequence -> sequence.winLostGames.size()).sum());
        log.info("Drawn         games:{}", sequences.stream().mapToDouble(sequence -> sequence.drawGames.size()).sum());
        sequences.stream()
                .forEach(sequence -> {
                    sequence.winLostGames.values().forEach(file -> {
                        try {
                            trainFile.train(file, statistics);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    sequence.drawGames.values().forEach(file -> {
                        try {
                            trainFile.train(file, statistics);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    });

                });
    }

    private void retrieveAllFiles() {
        List<ConfigSet> configSets = this.configFit
                .getConfigSets()
                .stream()
                .filter(ConfigSet::isEnable)
                .sorted()
                .toList();
        log.info("Sequences: {}", configSets
                .stream()
                .map(configSet -> String.format("[%s]", configSet.getSequence()))
                .collect(Collectors.joining(",")));
        configSets
                .forEach(configSet -> {
                    final Sequence sequence = new Sequence();
                    sequences.add(sequence);
                    configSet.getConfigDirs().forEach(configDir -> {
                        File[] trainFiles = getFiles(configDir, TrainGame.MarshallingType.JSON);
                        if (trainFiles != null) {
                            Arrays.stream(trainFiles).forEach(file1 -> {
                                try {
                                    TrainGame trainGame = TrainGame.load(file1, TrainGame.MarshallingType.JSON);
                                    Double value = trainGame.getValue();
                                    if (value == -1.0 || value == 1.0) {
                                        sequence.winLostGames.put(file1.lastModified(), file1);
                                    } else if (value == 0.0) {
                                        sequence.drawGames.put(file1.lastModified(), file1);
                                    } else
                                        throw new RuntimeException("Unidentified game value:" + value);
                                } catch (IOException | ClassNotFoundException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        }
                    });
                });
    }

    private static File[] getFiles(ConfigDir configDir, TrainGame.MarshallingType marshallingType) {
        File file = new File(configDir.getDirectory());
        FileFilter filters = pathname -> {
            try {
                String filename = pathname.toPath().getFileName().toString();
                if (!TrainGame.isCorrectFilename(filename, marshallingType)) return false;
                int fileNum = TrainGame.getNum(filename, marshallingType);
                return pathname.canRead() &&
                        pathname.isFile() &&
                        fileNum >= configDir.getStartNumber() &&
                        fileNum <= configDir.getEndNumber();
            } catch (NumberFormatException e) {
                return false;
            }
        };
        File[] trainFiles = file.listFiles(filters);
        return trainFiles;
    }
}
