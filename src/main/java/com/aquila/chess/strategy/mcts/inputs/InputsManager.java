package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.Game;
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
    // final private CircularFifoQueue<Integer> last6HashCodesAllegiance = new CircularFifoQueue<>(13);

    public abstract int getNbFeaturesPlanes();

    public InputsManager() {
        lastHashs.put(Alliance.WHITE, new CircularFifoQueue<>(13));
        lastHashs.put(Alliance.BLACK, new CircularFifoQueue<>(13));
    }

    /**
     * @param board      - the board on which we apply the move
     * @param move       - the move to apply or null if nothing need to be applied,
     *                   the complementary of the color of the move will be used as color2play
     * @param color2play the color that will play, used only if move is not defined
     * @return
     */
    public abstract InputsFullNN createInputs(final Board board, final Move move, final List<Move> moves, int nbRepeat, final Alliance color2play);

    public abstract String getHashCodeString(final Board board, final Move move, final List<Move> moves, final Alliance color2play);

    public abstract long hashCode(final Board board, final Move move, final List<Move> moves, final Alliance color2play);

    public abstract void startMCTSStep(Game game);

    public abstract InputsManager clone();

    public Board executeMove(Board board, final Move move) {
        board = move.execute();
        // this.last6HashCodesAllegiance.add(hashCode1Alliance(board, move.getAllegiance()));
        this.lastHashs.get(move.getAllegiance()).add(hashCode1Alliance(board, move.getAllegiance()));
        return board;
    }

    public int getNbRepeat(final Alliance alliance) {
        int ret = 0;
        List<Integer> hashs = lastHashs.get(alliance).stream().collect(Collectors.toList()); //6HashCodesAllegiance.stream().collect(Collectors.toList());
        Collections.reverse(hashs);
        if (hashs.size() > 3) {
            ret = hashs.get(0).intValue() == hashs.get(2).intValue()
                    ? 1 : 0;
            if (ret > 0) log.info("hash0:{} hash2:{}",
                    hashs.get(0), hashs.get(2));
        }
        if (hashs.size() > 5) {
            ret += hashs.get(0).intValue() == hashs.get(2).intValue() &&
                    hashs.get(0).intValue() == hashs.get(4).intValue()
                    ? 1 : 0;
            if (ret > 0) log.info("hash0:{} hash2:{} hash4:{}",
                    hashs.get(0), hashs.get(2), hashs.get(4));
        }
        if (hashs.size() > 7) {
            //log.info("hash0:{} hash2:{} hash4:{} hash6:{}",
            //        hashs.get(0), hashs.get(2), hashs.get(4), hashs.get(6)); // FIXME to remove
            ret += hashs.get(0).intValue() == hashs.get(2).intValue() &&
                    hashs.get(0).intValue() == hashs.get(4).intValue() &&
                    hashs.get(0).intValue() == hashs.get(6).intValue()
                    ? 1 : 0;
            if (ret > 0) log.info("hash0:{} hash2:{} hash4:{} hash6:{}",
                    hashs.get(0), hashs.get(2), hashs.get(4), hashs.get(6));
        }
        return ret;
    }

    private int hashCode1Alliance(final Board board, final Alliance alliance) {
        StringBuffer sb = new StringBuffer();
        for (int position = 0; position < BoardUtils.NUM_TILES; position++) {
            Piece piece = board.getPiece(position);
            if (piece != null && piece.getPieceAllegiance() == alliance) {
                sb.append(String.format("%s=%d,", piece.getPieceType(), position));
            }
        }
        return sb.toString().hashCode();
    }

    public abstract void processPlay(final Board board, final Move move);

    public List<Integer> getHashs(final Alliance alliance) {
        List<Integer> hashs = lastHashs.get(alliance).stream().collect(Collectors.toList());
        Collections.reverse(hashs);
        return hashs;
    }
}
