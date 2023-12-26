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

    final private Map<Alliance, CircularFifoQueue<Integer>> lastHashs = new HashMap<>();

    public abstract int getNbFeaturesPlanes();

    public InputsManager() {
        lastHashs.put(Alliance.WHITE, new CircularFifoQueue<>(13));
        lastHashs.put(Alliance.BLACK, new CircularFifoQueue<>(13));
    }

    /**
     * @param board      - the board on which we apply the move
     * @param move       - the move to apply or null if nothing need to be applied,
     *                   the complementary of the color of the move will be used as moveColor
     * @param moveColor the color that will play, used only if move is not defined
     * @return
     */
    public abstract <T extends InputsFullNN> T createInputs(InputRecord inputRecord);

    public abstract String getHashCodeString(InputRecord inputRecord);

    public abstract long hashCode(InputRecord inputRecord);

    public abstract void startMCTSStep(AbstractGame abstractGame);

    public abstract InputsManager clone();

    public void updateHashsTables(Board board, final Alliance alliance) {
        this.lastHashs.get(alliance).add(Utils.hashCode1Alliance(board, alliance));
    }

    public int getNbRepeat(final Alliance alliance) {
        int ret = 0;
        if(lastHashs.get(alliance)==null) return 0;
        List<Integer> hashs = lastHashs.get(alliance).stream().collect(Collectors.toList()); //6HashCodesAllegiance.stream().collect(Collectors.toList());
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
        List<Integer> hashs = lastHashs.get(alliance).stream().collect(Collectors.toList());
        Collections.reverse(hashs);
        return hashs;
    }
}
