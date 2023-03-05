package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.strategy.mcts.MCTSGame;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
public class InputsNNFactory {

    public static final int PLANE_COLOR = 108;

    public static final int PAWN_INDEX = 0;
    public static final int KNIGHT_INDEX = 1;
    public static final int BISHOP_INDEX = 2;
    public static final int ROOK_INDEX = 3;
    public static final int QUEEN_INDEX = 4;
    public static final int KING_INDEX = 5;

    public static InputsFullNN createInput(final MCTSGame mctsGame, final Move move, final Alliance color2play) {
        double[][][] inputs = new double[INN.FEATURES_PLANES][BoardUtils.NUM_TILES_PER_ROW][BoardUtils.NUM_TILES_PER_ROW];
        InputsNNFactory.createInputs(inputs, mctsGame, move, color2play);
        return new InputsFullNN(inputs);
    }

    /**
     * <h1>Network Input</h1>
     * <p>
     * The input encoding follows the approach taken for AlphaZero.
     * The main difference is that the move count is no longer encoded — it is
     * technically not required since it’s just some superfluous extra-information. We should
     * also mention that Leela Chess Zero is an ongoing project, and naturally improvements
     * and code changes happen. The input format was subject to such changes
     * as well, for example to cope with chess variants such as Chess960 or Armageddon, or
     * simply to experiment with encodings. The encoding described here is
     * the classic encoding, referred to in source code as INPUT_CLASSICAL_112_PLANE.
     * For those who want to look up things in code, the relevant source files are
     * lc0/src/neural/encoder.cc and lc0/src/neural/encoder_test.cc.
     * The input consists of 112 planes of size 8 × 8. Information w.r.t. the placement
     * of pieces is encoded from the perspective of the player whose current turn it
     * is. Assume that we take that player’s perspective. The first plane encodes
     * the position of our own pawns. The second plane encodes the position of our
     * knights, then our bishops, rooks, queens and finally the king. Starting from
     * plane 6 we encode the position of the enemy’s pawns, then knights, bishops,
     * rooks, queens and the enemy’s king. Plane 12 is set to all ones if one or more
     * repetitions occurred.
     * These 12 planes are repeated to encode not only the current position, but also
     * the seven previous ones. Planes 104 to 107 are set to 1 if White can castle
     * queenside, White can castle kingside, Black can castle queenside and Black can
     * castle kingside (in that order). Plane 108 is set to all ones if it is Black’s turn and
     * to 0 otherwise. Plane 109 encodes the number of moves where no capture has
     * been made and no pawn has been moved, i.e. the 50 moves rule. Plane 110 used
     * to be a move counter, but is simply set to always 0 in current generations of Lc0.
     * Last, plane 111 is set to all ones. This is, as previously mentioned, to help the
     * network detect the edge of the board when using convolutional filters.
     * </p>
     * <ul>
     * <li>0 - 103: 8 times 13 -> 104 planes:
     * <ul>
     * <li> 0: positions for white pawn</li>
     * <li> 1: positions for white knights</li>
     * <li> 2: positions for white bishops</li>
     * <li> 3: positions for white rooks</li>
     * <li> 4: positions for white queens</li>
     * <li> 5: positions for white king</li>
     * <li> 6: positions for black pawn</li>
     * <li> 7: positions for black knights</li>
     * <li> 8: positions for black bishops</li>
     * <li> 9: positions for black rooks</li>
     * <li>10: positions for black queens</li>
     * <li>11: positions for black king</li>
     * <li>12: 1 if One or more repetition, what does it mean ?</li>
     * </ul>
     * </li>
     * <li>104: White can castle queenside (LONG)</li>
     * <li>105: White can castle kingside (SHORT)</li>
     * <li>106: Black can castle queenside (LONG)</li>
     * <li>107: Black can castle kingside (SHORT)</li>
     * <li>108: set to all ones if it is Black’s turn and to 0 otherwise</li>
     * <li>109: encodes the number of moves where no capture has been made and no pawn has been moved</li>
     * <li>110: move counter, seems 0 from now on</li>
     * <li>111: all ones, to help the network detect the edge of the board when using convolutional filters</li>
     * </ul>
     *
     * @param inputs
     * @param mctsGame
     * @param color2play
     */
    private static void createInputs(final double[][][] inputs,
                                     final MCTSGame mctsGame,
                                     final Move move,
                                     final Alliance color2play) {
        int destinationOffset = 0;
        CircularFifoQueue<InputsOneNN> tmp = new CircularFifoQueue<>(8);
        tmp.addAll(mctsGame.getLast8Inputs());
        if (move != null) {
            InputsOneNN lastInput1 = InputsNNFactory.createInputsForOnePosition(mctsGame.getLastBoard(), move);
            log.info("++ createInouts({}):\n{}", move, lastInput1);
            tmp.add(lastInput1);
        }
        for (InputsOneNN lastInput : tmp) {
            log.info("createInouts({}):\n{}", destinationOffset, lastInput);
            System.arraycopy(lastInput.inputs(), 0, inputs, destinationOffset, INN.SIZE_POSITION);
            destinationOffset += INN.SIZE_POSITION;
        }
        final Board board = mctsGame.getLastBoard();
        List<Move> moveWhites = board.whitePlayer().getLegalMoves();
        Optional<Move> kingSideCastleWhite = moveWhites.stream().filter(m -> m instanceof Move.KingSideCastleMove).findFirst();
        Optional<Move> queenSideCastleWhite = moveWhites.stream().filter(m -> m instanceof Move.QueenSideCastleMove).findFirst();
        List<Move> moveBlacks = board.blackPlayer().getLegalMoves();
        Optional<Move> kingSideCastleBlack = moveBlacks.stream().filter(m -> m instanceof Move.KingSideCastleMove).findFirst();
        Optional<Move> queenSideCastleBlack = moveBlacks.stream().filter(m -> m instanceof Move.QueenSideCastleMove).findFirst();
        fill(inputs[104], !queenSideCastleWhite.isEmpty() ? 1.0 : 0.0);
        fill(inputs[105], !kingSideCastleWhite.isEmpty() ? 1.0 : 0.0);
        fill(inputs[106], !queenSideCastleBlack.isEmpty() ? 1.0 : 0.0);
        fill(inputs[107], !kingSideCastleBlack.isEmpty() ? 1.0 : 0.0);
        fill(inputs[PLANE_COLOR], color2play.isBlack() ? 1.0 : 0.0);
        // fill(inputs[109], mctsGame.getNbMoveNoAttackAndNoPawn() >= 50 ? 1.0 : 0.0);
        fill(inputs[111], 1.0);
    }

    /**
     * @param board - the board on which we apply the move
     * @param move  - the move to apply or null if nothing should be applied
     * @return the normalize board for 1 position using board and move. dimensions:
     * [13][NB_COL][NB_COL]
     */
    public static InputsOneNN createInputsForOnePosition(Board board, final Move move) {
        final double[][][] nbIn = new double[INN.SIZE_POSITION][BoardUtils.NUM_TILES_PER_ROW][BoardUtils.NUM_TILES_PER_ROW];
        if (move != null && move.getDestinationCoordinate() != -1) {
            board = move.execute();
        }
        for (int y = BoardUtils.NUM_TILES_PER_ROW - 1; y >= 0; y--) {
            for (int x = 0; x < BoardUtils.NUM_TILES_PER_ROW; x++) {
                Piece piece = board.getPiece((BoardUtils.NUM_TILES_PER_ROW - y - 1) * BoardUtils.NUM_TILES_PER_ROW + x);
                if (piece != null) {
                    int pieceIndex = getPlanesIndex(piece);
                    nbIn[pieceIndex][x][y] = 1;
                }
            }
        }
        // FIXME: optimize the copy
        final var nbInNew = new double[INN.SIZE_POSITION][BoardUtils.NUM_TILES_PER_ROW][BoardUtils.NUM_TILES_PER_ROW];
        // copy WHITE pieces without modification (player view)
        for (int planes = 0; planes < 6; planes++) {
            for (int y = 0; y < BoardUtils.NUM_TILES_PER_ROW; y++) {
                System.arraycopy(nbIn[planes][y], 0, nbInNew[planes][y], 0, BoardUtils.NUM_TILES_PER_ROW);
            }
        }
        // copy flipped board for BLACK (player view)
        for (int planes = 6; planes < 12; planes++) {
            for (int y = 0; y < BoardUtils.NUM_TILES_PER_ROW; y++) {
                for (int x = 0; x < BoardUtils.NUM_TILES_PER_ROW; x++) {
                    nbInNew[planes][x][y] = nbIn[planes][BoardUtils.NUM_TILES_PER_ROW - 1 - x][BoardUtils.NUM_TILES_PER_ROW - 1 - y];
                }
            }
        }
        //FIXME fill(nbInNew[INN.SIZE_POSITION - 1], game.nbMovesWithRepetition() > 0 ? 1.0 : 0.0);
        return new InputsOneNN(nbInNew);
    }

    /**
     * @formatter:off <pre>
     * [0-6]: Pawn:0, Bishop:1, Knight:2, Rook:3, Queen:4, King:5
     * [0-6] pieces for White
     * [7-12] pieces for Black
     * </pre>
     * @formatter:on
     */
    private static int getPlanesIndex(Piece piece) {
        int index = piece.getPieceAllegiance().isWhite() ? 0 : 6;
        if (piece.getPieceType() == Piece.PieceType.PAWN) return index + INN.PAWN_INDEX;
        if (piece.getPieceType() == Piece.PieceType.BISHOP) return index + INN.BISHOP_INDEX;
        if (piece.getPieceType() == Piece.PieceType.KNIGHT) return index + INN.KNIGHT_INDEX;
        if (piece.getPieceType() == Piece.PieceType.ROOK) return index + INN.ROOK_INDEX;
        if (piece.getPieceType() == Piece.PieceType.QUEEN) return index + INN.QUEEN_INDEX;
        if (piece.getPieceType() == Piece.PieceType.KING) return index + INN.KING_INDEX;
        return -100; // sure this will failed at least
    }

    private static void fill(double[][] planes, double value) {
        if (value == 0.0) return;
        for (int i = 0; i < 8; i++) {
            Arrays.fill(planes[i], (double) value);
        }
    }
}
