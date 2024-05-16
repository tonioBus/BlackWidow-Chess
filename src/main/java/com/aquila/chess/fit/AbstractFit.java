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
        SortedMap<Long, File> winLostGames = drawGames; //new TreeMap<>();
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
        sequences.stream().forEach(sequence -> {
            sequence.winLostGames.entrySet().stream().forEach(entry -> {
                log.info("winLostGames [{}] -> [{}]", entry.getKey(), entry.getValue());
            });
            sequence.drawGames.entrySet().stream().forEach(entry -> {
                log.info("drawGames [{}] -> [{}]", entry.getKey(), entry.getValue());
            });
        });
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
                                    long offset = 0;
                                    long index = getIndex(file1.lastModified(), offset);
                                    log.info("load file: {} -> {}", file1.toPath(), index);
                                    TrainGame trainGame = TrainGame.load(file1, TrainGame.MarshallingType.JSON);
                                    Double value = trainGame.getValue();
                                    if (value == -1.0 || value == 1.0) {
                                        while (sequence.winLostGames.containsKey(index)) {
                                            offset++;
                                            index = getIndex(file1.lastModified(), offset);
                                        }
                                        sequence.winLostGames.put(index, file1);
                                        log.info("winLostGames adding file [{}] to index:{}", file1, index);
                                    } else if (value == 0.0) {
                                        while (sequence.drawGames.containsKey(index)) {
                                            offset++;
                                            index = getIndex(file1.lastModified(), offset);
                                        }
                                        sequence.drawGames.put(index, file1);
                                        log.info("drawGames adding file [{}] to index:{}", file1, index);
                                    } else {
                                        log.error("{} -> Unidentified game value: {}", file1.toPath(), value);
                                        throw new RuntimeException("Unidentified game value:" + value);
                                    }
                                } catch (IOException | ClassNotFoundException e) {
                                    log.error("Can not read train file", e);
                                    throw new RuntimeException(e);
                                }
                            });
                        }
                    });
                });
    }

    private long getIndex(long lastModified, long offset) {
        return lastModified * 1000 + offset;
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
                log.error(pathname.getAbsolutePath(), e);
                return false;
            }
        };
        File[] trainFiles = file.listFiles(filters);
        return trainFiles;
    }
}
