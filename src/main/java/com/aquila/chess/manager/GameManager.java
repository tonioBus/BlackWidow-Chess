package com.aquila.chess.manager;

import com.aquila.chess.Game;
import com.aquila.chess.manager.Record.Status;
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

    final String filename;
    int nbGames = 0;
    private final File file;
    private Record lastRecord = null;

    private final int maxPercentage;

    private int intermediateNbGame;

    private final int minNbGame;

    /**
     * initiialise itself by reading the CSV file corresponding to all games played until now
     *
     * @param filename
     * @param minNbGame
     * @param maxPercentage
     * @throws IOException
     */
    public GameManager(String filename, int minNbGame, int maxPercentage)
            throws IOException {
        this.minNbGame = minNbGame;
        this.maxPercentage = maxPercentage;
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

    public Status endGame(final Game game, final double nnScore, final Game.GameStatus gameStatus, final Sequence sequence, String filename)
            throws IOException, NoSuchAlgorithmException {
        Status retStatus = Status.NORMAL;

        this.intermediateNbGame++;
        this.nbGames++;
        Record record = new Record(lastRecord, retStatus, this.intermediateNbGame, gameStatus, sequence, game, nnScore, filename);
        log.info("Intermediate: whites:{} blacks:{} drawn:{} percentage White win:{} %"//
                , record.intermediateWhiteWin//
                , record.intermediateBlackWin//
                , record.intermediateDrawn//
                , record.percentage);
        if ((record.intermediateWhiteWin + record.intermediateBlackWin) >= this.minNbGame ||
                (((record.intermediateWhiteWin + record.intermediateBlackWin) >= this.minNbGame / 2) &&
                        record.percentage >= 65)) {
            log.warn(String.format(
                    "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n%%%% PERCENTAGE: %d (%d <-> %d)\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%",
                    record.percentage, record.whiteWin, record.blackWin));
            if (record.percentage >= this.maxPercentage) {
                retStatus = Status.SWITCHING;
                this.intermediateNbGame = 1;
                record = new Record(lastRecord, retStatus, this.intermediateNbGame, gameStatus, sequence, game, nnScore, filename);
            }
        }
        FileWriter fileWriter = new FileWriter(filename, true);
        try (CSVWriter writer = new CSVWriter(fileWriter)) {
            writer.writeNext(record.toArray());
        }
        fileWriter.close();
        this.lastRecord = record;
        return retStatus;
    }

    public int getNbGames() {
        return this.nbGames;
    }
}
