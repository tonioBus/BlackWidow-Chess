package com.aquila.chess.strategy.mcts.inputs.lc0;

import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.pieces.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
public class Lc0Utils {

    /**
     * Display the board located in inputs at offset boardIndex (offset within the 8 encoded board)
     *
     * @param inputs
     * @param boardIndex
     * @return
     */
    static public String displayBoard(double[][][] inputs, int boardIndex) {
        Board.Builder builder = new Board.Builder();
        boolean empty = true;
        for (Alliance color : Arrays.asList(Alliance.WHITE, Alliance.BLACK)) {
            for (int piece = Lc0InputsManagerImpl.PAWN_INDEX; piece <= Lc0InputsManagerImpl.KING_INDEX; piece++) {
                int indexInput = (piece + 6 * color.ordinal()) + (boardIndex * 13);
                for (int x = 0; x < Board.NB_COL; x++) {
                    for (int y = 0; y < Board.NB_COL; y++) {
                        if (inputs[indexInput][x][y] != 0.0) {
                            int piecePosition;
                            if (color.isWhite()) {
                                piecePosition = 64 - ((8 - x) + y * 8);
                            } else {
                                piecePosition = ((7 - x) + y * 8);
                            }
                            switch (piece) {
                                case Lc0InputsManagerImpl.PAWN_INDEX:
                                    log.trace("PAWN color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new Pawn(color, piecePosition));
                                    break;
                                case Lc0InputsManagerImpl.BISHOP_INDEX:
                                    log.trace("BISHOP color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new Bishop(color, piecePosition));
                                    break;
                                case Lc0InputsManagerImpl.KING_INDEX:
                                    log.trace("KING color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new King(color, piecePosition, false, false));
                                    break;
                                case Lc0InputsManagerImpl.KNIGHT_INDEX:
                                    log.trace("KNIGHT color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new Knight(color, piecePosition));
                                    break;
                                case Lc0InputsManagerImpl.ROOK_INDEX:
                                    log.trace("ROOK color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new Rook(color, piecePosition));
                                    break;
                                case Lc0InputsManagerImpl.QUEEN_INDEX:
                                    log.trace("QUEEN color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new Queen(color, piecePosition));
                                    break;
                            }
                            empty = false;
                        }
                    }
                }
            }
        }
        if (empty) return null;
        builder.setMoveMaker(Alliance.WHITE);
        return builder.build().toString();
    }

}
