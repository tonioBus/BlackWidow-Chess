package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.AbstractGame;
import com.aquila.chess.utils.Utils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public abstract class InputsManager {

    final protected Map<Alliance, Map<Integer, Integer>> lastHashs = new HashMap<>();

    public abstract int getNbFeaturesPlanes();

    public InputsManager() {
        lastHashs.put(Alliance.WHITE, new HashMap<>());
        lastHashs.put(Alliance.BLACK, new HashMap<>());
    }

    /**
     *
     * @param inputRecord
     * @return
     * @param <T>
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

    public void updateHashsTables(final Move move) {
        Alliance alliance = move.getAllegiance();
        Map<Integer, Integer> hashs = this.lastHashs.get(alliance);
        int key = Utils.hashCode1Alliance(move.execute(), alliance);
        if (hashs.containsKey(key)) {
            log.info("updateHash to 1 move:{}, key:{}", move, key);
            hashs.put(key, 1);
        } else {
            hashs.put(key, 0);
        }
    }

    public boolean isRepeatMove(final Move move) {
        if (move.isInitMove()) return false;
        final Board destBoard = move.execute();
        Alliance alliance = move.getAllegiance();
        Map<Integer, Integer> hashs = this.lastHashs.get(alliance);
        log.info("isRepeatMove: move:{}", move);
        int key = Utils.hashCode1Alliance(destBoard, alliance);
        if (!hashs.containsKey(key)) return false;
        int ret = hashs.get(key).intValue();
        log.info("move:{} key:{} ret:{}", move, key, ret);
        return ret == 1;
    }

    public int getNbRepeat(final Alliance alliance) {
        int ret = 0;
        if (lastHashs.get(alliance) == null) return 0;
        List<Integer> hashs = lastHashs.get(alliance).keySet().stream().collect(Collectors.toList()); //6HashCodesAllegiance.stream().collect(Collectors.toList());
        Collections.reverse(hashs);
        if (hashs.size() > 3) {
            ret = hashs.get(0).intValue() == hashs.get(2).intValue()
                    ? 1 : 0;
            if (ret > 0) log.debug("[{}] hash0:{} hash2:{}",
                    alliance,
                    hashs.get(0),
                    hashs.get(2));
        }
        if (hashs.size() > 5) {
            ret += hashs.get(0).intValue() == hashs.get(2).intValue() &&
                    hashs.get(0).intValue() == hashs.get(4).intValue()
                    ? 1 : 0;
            if (ret > 0) log.debug("[{}] hash0:{} hash2:{} hash4:{}",
                    alliance,
                    hashs.get(0),
                    hashs.get(2),
                    hashs.get(4));
        }
        if (hashs.size() > 7) {
            //log.info("hash0:{} hash2:{} hash4:{} hash6:{}",
            //        hashs.get(0), hashs.get(2), hashs.get(4), hashs.get(6)); // FIXME to remove
            ret += hashs.get(0).intValue() == hashs.get(2).intValue() &&
                    hashs.get(0).intValue() == hashs.get(4).intValue() &&
                    hashs.get(0).intValue() == hashs.get(6).intValue()
                    ? 1 : 0;
            if (ret > 0) log.debug("[{}] hash0:{} hash2:{} hash4:{} hash6:{}",
                    alliance,
                    hashs.get(0),
                    hashs.get(2),
                    hashs.get(4),
                    hashs.get(6));
        }
        return ret;
    }

    /**
     * Register the input calculated using the given board and the given move
     * @param board
     * @param move
     */
    public abstract void registerInput(final Board board, final Move move);

    public List<Integer> getHashs(final Alliance alliance) {
        List<Integer> hashs = lastHashs.get(alliance).keySet().stream().collect(Collectors.toList());
        Collections.reverse(hashs);
        return hashs;
    }
}
