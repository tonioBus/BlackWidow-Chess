package com.aquila.chess.strategy.mcts.inputs.aquila;

import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.utils.Coordinate;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.pieces.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

import static com.aquila.chess.strategy.mcts.inputs.aquila.AquilaInputsManagerImpl.*;

@Slf4j
public class AquilaUtils {
    /**
     * Display the board located in inputs at offset boardIndex (offset within the 8 encoded board)
     *
     * @param inputs
     * @param index
     * @return
     */
    static public String displayBoard(double[][][] inputs, int index) {
        Board.Builder builder = new Board.Builder();
        boolean empty = true;
        for (Alliance color : Arrays.asList(Alliance.WHITE, Alliance.BLACK)) {
            for (int piece = Lc0InputsManagerImpl.PAWN_INDEX; piece <= Lc0InputsManagerImpl.KING_INDEX; piece++) {
                int indexInput = (piece + 6 * color.ordinal()) + index;
                for (int x = 0; x < Board.NB_COL; x++) {
                    for (int y = 0; y < Board.NB_COL; y++) {
                        Coordinate coordinate = new Coordinate(x, y, color);
                        if (inputs[indexInput][x][y] != 0.0) {
                            int piecePosition;
                            if (color.isWhite()) {
                                piecePosition = 64 - ((8 - x) + y * 8);
                            } else {
                                piecePosition = ((7 - x) + y * 8);
                            }
                            switch (piece) {
                                case PAWN_INDEX:
                                    log.trace("PAWN color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new Pawn(color, piecePosition));
                                    break;
                                case BISHOP_INDEX:
                                    log.trace("BISHOP color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new Bishop(color, piecePosition));
                                    break;
                                case KING_INDEX:
                                    log.trace("KING color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new King(color, piecePosition, false, false));
                                    break;
                                case KNIGHT_INDEX:
                                    log.trace("KNIGHT color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new Knight(color, piecePosition));
                                    break;
                                case ROOK_INDEX:
                                    log.trace("ROOK color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new Rook(color, piecePosition));
                                    break;
                                case QUEEN_INDEX:
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
        builder.setCheckBoard(false);
        return builder.build().toString();
    }

    static public String displaySimplePlaneBoard(double[][][] inputs, int index) {
        Board.Builder builder = new Board.Builder();
        for (Alliance color : Arrays.asList(Alliance.WHITE, Alliance.BLACK)) {
            int indexInput = color.ordinal() + index;
            for (int x = 0; x < Board.NB_COL; x++) {
                for (int y = 0; y < Board.NB_COL; y++) {
                    if (inputs[indexInput][x][y] != 0.0) {
                        Coordinate coordinate = new Coordinate(x, y, color);
                        int piecePosition = coordinate.getBoardPosition();
                        builder.setPiece(new Pawn(color, piecePosition));
                    }
                }
            }
        }
        builder.setMoveMaker(Alliance.WHITE);
        builder.setCheckBoard(false);
        return builder.build().toString();
    }

}
