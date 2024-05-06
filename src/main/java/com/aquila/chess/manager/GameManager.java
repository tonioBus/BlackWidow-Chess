package com.aquila.chess.manager;

import com.aquila.chess.Game;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;

@Slf4j
public class GameManager {

    public static final String STOP_FILE = "config/STOP";

    final String filename;
    int nbGames = 0;
    private final File file;
    private Record lastRecord = null;

    private int intermediateNbGame;

    /**
     * initialize itself by reading the CSV file corresponding to all games played until now
     *
     * @param filename
     * @param minNbGame
     * @param maxPercentage
     * @throws IOException
     */
    public GameManager(String filename)
            throws IOException {
        this.filename = filename;
        file = new File(filename);
        if (file.canRead()) {
            try (CSVReader reader = new CSVReader(new FileReader(file))) {
                reader.forEach(recordSz -> {
                    nbGames++;
                    try {
                        lastRecord = new Record(recordSz);
                        this.intermediateNbGame = lastRecord.intermediateNbGame;
                    } catch (ParseException e) {
                        log.error("Error when reading: " + file, e);
                    }
                });
                reader.close();
                log.info("Number of line read from {}: {}", file.getAbsolutePath(), reader.getLinesRead());
                log.info("Number of games played: {}", nbGames);
            }
        }
    }

    public Sequence createSequence() {
        return new Sequence();
    }

    public void endGame(final Game game, final double nnScore, final Game.GameStatus gameStatus, final Sequence sequence, String trainFileName)
            throws IOException, NoSuchAlgorithmException {

        this.intermediateNbGame++;
        this.nbGames++;
        Record record = new Record(lastRecord, this.intermediateNbGame, gameStatus, sequence, game, nnScore, trainFileName);
        log.info("Intermediate: whites:{} blacks:{} drawn:{} percentage White win:{} %"//
                , record.intermediateWhiteWin//
                , record.intermediateBlackWin//
                , record.intermediateDrawn//
                , record.percentage);
        FileWriter fileWriter = new FileWriter(filename, true);
        try (CSVWriter writer = new CSVWriter(fileWriter)) {
            writer.writeNext(record.toArray());
        }
        fileWriter.close();
        this.lastRecord = record;
    }

    public int getNbGames() {
        return this.nbGames;
    }

    public boolean stopDetected(boolean delete) {
        File file = new File(STOP_FILE);
        if (file.isFile()) {
            if(delete) log.warn("Stop file detected: removing {} success:{}", STOP_FILE, file.delete());
            return true;
        }
        return false;
    }
}
