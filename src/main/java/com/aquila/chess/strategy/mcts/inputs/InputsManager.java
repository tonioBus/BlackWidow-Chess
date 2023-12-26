package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.AbstractGame;
import com.aquila.chess.utils.Utils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public abstract class InputsManager {

    final protected Map<Alliance, Map<Integer, String>> lastHashs = new HashMap<>();

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
        inputsManager2clone.lastHashs.get(Alliance.WHITE).putAll(lastHashs.get(Alliance.WHITE));
        inputsManager2clone.lastHashs.get(Alliance.BLACK).putAll(lastHashs.get(Alliance.BLACK));
    }

    public void updateHashsTables(Board board, final Move move) {
        Alliance alliance = move.getAllegiance();
        this.lastHashs.get(alliance).put(Utils.hashCode1Alliance(board, alliance), move.toString());
    }

    public int getNbRepeat(final Alliance alliance) {
        int ret = 0;
        if(lastHashs.get(alliance)==null) return 0;
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
