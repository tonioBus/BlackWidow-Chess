package com.aquila.chess.strategy.mcts.utils;

import com.chess.engine.classic.board.Move;

import java.util.List;
import java.util.stream.Collectors;

public class MovesUtils {

    static public int nbMovesRepeat(List<Move> moves) {
        // Repetition (a position arises three times in a raw)
        final int numberOfMoves = moves.size();
        if (numberOfMoves >= 12) {
            List<Move> last12Moves = moves.stream().skip(moves.size() - 12).collect(Collectors.toList());
            final Move[][] move = new Move[3][4];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 4; j++) {
                    move[i][j] = last12Moves.get(j + (4 * i));
                }
            }
            int ret = 0;
            for (int j = 0; j < 4; j++) {
                if (move[0][j].equals(move[1][j]) && move[1][j].equals(move[2][j]))
                    ret++;
            }
            return ret;
        } else return 0;
    }

    static public boolean is3MovesRepeat(List<Move> moves) {
        return nbMovesRepeat(moves) >= 3;
    }

}
