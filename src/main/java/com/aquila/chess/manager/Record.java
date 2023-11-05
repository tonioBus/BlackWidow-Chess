package com.aquila.chess.manager;

import com.aquila.chess.Game;
import com.aquila.chess.utils.Utils;

import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

public class Record {

    static final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL,
            new Locale("EN", "en"));

    public enum Status {
        NORMAL, SWITCHING
    }

    public final Status status;
    public int totalWhiteWin = 0;
    public int totalBlackWin = 0;
    public int totalDrawn = 0;
    public int intermediateWhiteWin = 0;
    public int intermediateBlackWin = 0;
    public int intermediateDrawn = 0;
    public int whiteWin = 0;
    public int blackWin = 0;
    public int drawn = 0;
    public int intermediateNbGame = 0;
    public final String reason;
    public final int percentage;
    public final int round;
    public Date startDate;
    public Date endDate;
    public long durationSeconds;
    public double nnScore;
    public String gameSha1;
    public String png;

    public Record(String[] recordSz) throws ParseException {
        int i = 0;
        this.status = Status.valueOf(recordSz[i++]);
        this.intermediateNbGame = Integer.parseInt(recordSz[i++]);
        this.totalWhiteWin = Integer.parseInt(recordSz[i++]);
        this.totalBlackWin = Integer.parseInt(recordSz[i++]);
        this.totalDrawn = Integer.parseInt(recordSz[i++]);
        this.intermediateWhiteWin = Integer.parseInt(recordSz[i++]);
        this.intermediateBlackWin = Integer.parseInt(recordSz[i++]);
        this.intermediateDrawn = Integer.parseInt(recordSz[i++]);
        this.whiteWin = Integer.parseInt(recordSz[i++]);
        this.blackWin = Integer.parseInt(recordSz[i++]);
        this.drawn = Integer.parseInt(recordSz[i++]);
        this.reason = recordSz[i++];
        this.percentage = Integer.parseInt(recordSz[i++]);
        this.round = Integer.parseInt(recordSz[i++]);
        this.startDate = dateFormat.parse(recordSz[i++]);
        this.endDate = dateFormat.parse(recordSz[i++]);
        this.durationSeconds = Integer.parseInt(recordSz[i++]);
        this.nnScore = Double.valueOf(recordSz[i++]);
        this.gameSha1 = recordSz[i++];
        this.png = recordSz[i++];
    }

    public Record(final Record lastRecord, final Status status, final int intermediateNbGame, final Game.GameStatus gameStatus, final Sequence sequence,
                  Game game, double nnScore, String filename) throws NoSuchAlgorithmException {
        long endDate = System.currentTimeMillis();
        this.status = status;
        switch (gameStatus) {
            case WHITE_CHESSMATE -> this.blackWin = 1;
            case BLACK_CHESSMATE -> this.whiteWin = 1;
            case DRAW_50, DRAW_300, DRAW_3, DRAW_NOT_ENOUGH_PIECES -> this.drawn = 1;
        }
        if (lastRecord != null) {
            if (status == Status.SWITCHING) {
                this.intermediateWhiteWin = this.whiteWin;
                this.intermediateBlackWin = this.blackWin;
                this.intermediateDrawn = this.drawn;
            } else {
                this.intermediateWhiteWin = lastRecord.intermediateWhiteWin + this.whiteWin;
                this.intermediateBlackWin = lastRecord.intermediateBlackWin + this.blackWin;
                this.intermediateDrawn = lastRecord.intermediateDrawn + this.drawn;
            }
            this.totalWhiteWin = lastRecord.totalWhiteWin + this.whiteWin;
            this.totalBlackWin = lastRecord.totalBlackWin + this.blackWin;
            this.totalDrawn = lastRecord.totalDrawn + this.drawn;
        } else {
            this.intermediateWhiteWin = this.whiteWin;
            this.intermediateBlackWin = this.blackWin;
            this.intermediateDrawn = this.drawn;
            this.totalWhiteWin = this.whiteWin;
            this.totalBlackWin = this.blackWin;
            this.totalDrawn = this.drawn;
        }
        this.intermediateNbGame = intermediateNbGame;
        this.reason = gameStatus.toString();
        this.startDate = new Date(sequence.startDate);
        this.endDate = new Date(endDate);
        this.durationSeconds = endDate - sequence.startDate;
        if (this.intermediateWhiteWin + this.intermediateBlackWin > 0) {
            this.percentage = ((this.intermediateWhiteWin) * 100)
                    / (this.intermediateNbGame);
//        this.percentage = ((this.intermediateWhiteWin) * 100)
//                / (this.intermediateWhiteWin + this.intermediateBlackWin);
        } else
            this.percentage = 0;
        this.round = sequence.nbStep;
        this.nnScore = nnScore;
        String first6moves = game.getMoves().stream().limit(6).map(Object::toString).collect(Collectors.joining(","));
        this.gameSha1 = String.format("%s %s %s", filename, first6moves, Utils.toSha1(game.toPGN()));
        this.png = Base64.getEncoder().encodeToString(game.toPGN().getBytes());
    }

    public String[] toArray() {
        List<String> records = new ArrayList<>();
        records.add(this.status.toString());
        records.add(String.valueOf(this.intermediateNbGame));
        records.add(String.valueOf(this.totalWhiteWin));
        records.add(String.valueOf(this.totalBlackWin));
        records.add(String.valueOf(this.totalDrawn));
        records.add(String.valueOf(this.intermediateWhiteWin));
        records.add(String.valueOf(this.intermediateBlackWin));
        records.add(String.valueOf(this.intermediateDrawn));
        records.add(String.valueOf(this.whiteWin));
        records.add(String.valueOf(this.blackWin));
        records.add(String.valueOf(this.drawn));
        records.add(this.reason);
        records.add(String.valueOf(this.percentage));
        records.add(String.valueOf(this.round));
        records.add(dateFormat.format(this.startDate));
        records.add(dateFormat.format(this.endDate));
        records.add(String.valueOf(this.durationSeconds));
        records.add(String.valueOf(this.nnScore));
        records.add(this.gameSha1);
        records.add(this.png);
        return records.toArray(new String[0]);
    }

}