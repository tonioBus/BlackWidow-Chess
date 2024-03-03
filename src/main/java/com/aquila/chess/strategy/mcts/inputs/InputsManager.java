package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.AbstractGame;
import com.aquila.chess.utils.Utils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import com.twelvemonkeys.util.LRUMap;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public abstract class InputsManager {

    final protected Map<Alliance, Map<Integer, Integer>> lastHashs = new LRUMap<>(7);

    public abstract int getNbFeaturesPlanes();

    public InputsManager() {
        lastHashs.put(Alliance.WHITE, new HashMap<>());
        lastHashs.put(Alliance.BLACK, new HashMap<>());
    }

    /**
     * @param inputRecord
     * @param <T>
     * @return
     */
    public abstract <T extends InputsFullNN> T createInputs(InputRecord inputRecord);

    public abstract String getHashCodeString(InputRecord inputRecord);

    public abstract long hashCode(InputRecord inputRecord);

    public abstract void startMCTSStep(AbstractGame abstractGame);

    public abstract InputsManager clone();

    protected void doClone(InputsManager inputsManager2clone) {
        lastHashs.get(Alliance.WHITE).entrySet().stream().forEach(entry -> {
            inputsManager2clone.lastHashs.get(Alliance.WHITE).put(Integer.valueOf(entry.getKey()), Integer.valueOf(entry.getValue()));
        });
        lastHashs.get(Alliance.BLACK).entrySet().stream().forEach(entry -> {
            inputsManager2clone.lastHashs.get(Alliance.BLACK).put(Integer.valueOf(entry.getKey()), Integer.valueOf(entry.getValue()));
        });
    }

    public void updateHashsTables(final Move move, final Board board) {
        if (move.isInitMove() || move.isAttack() || move.isCastlingMove() || move.getMovedPiece().getPieceType() == Piece.PieceType.PAWN)
            return;
        Alliance alliance = move.getAllegiance();
        Map<Integer, Integer> hashs = this.lastHashs.get(alliance);
        int key = Utils.hashCode1Alliance(board, alliance);
        log.debug("updateHash move:{}, key:{}", move, key);
        if (hashs.containsKey(key)) {
            log.debug("updateHash SET TO 1 move:{}, key:{}", move, key);
            hashs.put(key, 1);
        } else {
            hashs.put(key, 0);
        }
    }

    public boolean isRepeatMove(final Move move) {
        if (move.isInitMove() || move.isAttack() || move.isCastlingMove() || move.getMovedPiece().getPieceType() == Piece.PieceType.PAWN)
            return false;
        final Board destBoard = move.execute();
        Alliance alliance = move.getAllegiance();
        Map<Integer, Integer> hashs = this.lastHashs.get(alliance);
        log.debug("isRepeatMove: move:{}", move);
        int key = Utils.hashCode1Alliance(destBoard, alliance);
        if (!hashs.containsKey(key)) return false;
        int ret = hashs.get(key).intValue();
        log.debug("move:{} key:{} ret:{}", move, key, ret);
        return ret == 1;
    }

    /**
     * Register the input calculated using the given board and the given move
     *
     * @param board
     * @param move
     */
    public abstract void registerInput(final Board board, final Move move);

    public List<Integer> getHashs(final Alliance alliance) {
        return lastHashs.get(alliance).keySet().stream().collect(Collectors.toList());
    }
}
