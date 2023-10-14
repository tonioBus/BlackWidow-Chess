package com.aquila.chess.strategy.mcts.inputs.aquila;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.utils.MovesUtils;
import com.aquila.chess.utils.Coordinate;
import com.aquila.chess.utils.Utils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import com.chess.engine.classic.player.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * <h1>Network Input</h1>
 * <ul>
 *     <li>
 *         Positions
 *         <ul>
 *              <li>[0-5] pieces for White</li>
 *              <li>[6-11] pieces for Black</li>
 *         </ul>
 *     </li>
 *     <li>
 *         Moves
 *         <ul>
 *              <li>[0-5] pieces for White</li>
 *              <li>[6-11] pieces for Black</li>
 *         </ul>
 *     </li>
 *     <li>
 *         Protects
 *         <ul>
 *              <li>[0-5] pieces for White</li>
 *              <li>[6-11] pieces for Black</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * </p>
 */
public class AquilaInputsManagerImpl implements InputsManager {

    public static final int PLANE_COLOR = 44;

    public static final int PAWN_INDEX = 0;
    public static final int KNIGHT_INDEX = 1;
    public static final int BISHOP_INDEX = 2;
    public static final int ROOK_INDEX = 3;
    public static final int QUEEN_INDEX = 4;
    public static final int KING_INDEX = 5;

    public static final int FEATURES_PLANES = 49;

    @Override
    public int getNbFeaturesPlanes() {
        return FEATURES_PLANES;
    }

    @Override
    public InputsFullNN createInputs(final Board board, final Move move, final List<Move> moves, final Alliance color2play) {
        final var inputs = new double[FEATURES_PLANES][BoardUtils.NUM_TILES_PER_ROW][BoardUtils.NUM_TILES_PER_ROW];
        if (move != null && !move.isInitMove())
            // if we move, the color2play will be the complementary of the player that just moved
            this.createInputs(inputs, move.execute(), moves, move.getAllegiance().complementary());
        else
            this.createInputs(inputs, board, moves, color2play);
        return new AquilaInputsFullNN(inputs);
    }

    /**
     * @param board - the board on which we apply the move
     * @return the normalize board for 1 position using board and move. dimensions:
     * [12][NB_COL][NB_COL]
     */
    private void createInputs(double[][][] inputs, Board board, final List<Move> allGamesMoves, Alliance color2play) {
        board.getAllPieces().parallelStream().forEach(currentPiece -> {
            Player player = switch (currentPiece.getPieceAllegiance()) {
                case WHITE -> board.whitePlayer();
                case BLACK -> board.blackPlayer();
            };
            // coordinate calculated from the point of view of the player
            Coordinate coordinate = new Coordinate(currentPiece);
            int currentPieceIndex = getPlanesIndex(currentPiece);
            // Position 0 (6+6 planes)
            inputs[currentPieceIndex][coordinate.getXInput()][coordinate.getYInput()] = 1;
            // Moves 12 (6+6 planes)
            Collection<Move> legalMoves = currentPiece.calculateLegalMoves(board);
            legalMoves.stream().forEach(move -> {
                // movesCoordinate calculated from the point of view of the player
                Coordinate movesCoordinate = Coordinate.destinationCoordinate(move);
                inputs[12 + currentPieceIndex][movesCoordinate.getXInput()][movesCoordinate.getYInput()] = 1;
            });
            // Attacks 24 (6+6 planes)
            legalMoves.stream().filter(move -> move.isAttack()).forEach(move -> {
                Piece attackingPiece = move.getAttackedPiece();
                Coordinate attackCoordinate = new Coordinate(attackingPiece);
                inputs[24 + getPlanesIndex(attackingPiece)][attackCoordinate.getXInput()][attackCoordinate.getYInput()] = 1;
            });
            // King liberty 36 (1+1 planes)
            if (currentPiece.getPieceType() == Piece.PieceType.KING) {
                int offsetBlack = currentPiece.getPieceAllegiance() == Alliance.BLACK ? 1 : 0;
                legalMoves.stream().forEach(move -> {
                    Move.MoveStatus status = player.makeMove(move).getMoveStatus();
                    if (status == Move.MoveStatus.DONE) {
                        Coordinate coordinateKingMoves = Coordinate.destinationCoordinate(move);
                        inputs[36 + offsetBlack][coordinateKingMoves.getXInput()][coordinateKingMoves.getYInput()] = 1;
                    }
                });
            }
            // Pawn moves 38 (1+1 planes)
            if (currentPiece.getPieceType() == Piece.PieceType.PAWN) {
                int offsetBlack = currentPiece.getPieceAllegiance() == Alliance.BLACK ? 1 : 0;
                int x = coordinate.getXInput();
                int y = coordinate.getYInput();
                Alliance color = currentPiece.getPieceAllegiance();
                for (int yIndex = y + 1; yIndex < BoardUtils.NUM_TILES_PER_ROW; yIndex++) {
                    if (board.getPiece(new Coordinate(x, yIndex, color).getBoardPosition()) != null) break;
                    inputs[38 + offsetBlack][x][yIndex] = 1;
                }
            }
        });
        List<Move> moveWhites = board.whitePlayer().getLegalMoves();
        Optional<Move> kingSideCastleWhite = moveWhites.stream().filter(m -> m instanceof Move.KingSideCastleMove).findFirst();
        Optional<Move> queenSideCastleWhite = moveWhites.stream().filter(m -> m instanceof Move.QueenSideCastleMove).findFirst();
        List<Move> moveBlacks = board.blackPlayer().getLegalMoves();
        Optional<Move> kingSideCastleBlack = moveBlacks.stream().filter(m -> m instanceof Move.KingSideCastleMove).findFirst();
        Optional<Move> queenSideCastleBlack = moveBlacks.stream().filter(m -> m instanceof Move.QueenSideCastleMove).findFirst();
        int currentIndex = 40;
        fill(inputs[currentIndex], !queenSideCastleWhite.isEmpty() ? 1.0 : 0.0);
        fill(inputs[currentIndex + 1], !kingSideCastleWhite.isEmpty() ? 1.0 : 0.0);
        fill(inputs[currentIndex + 2], !queenSideCastleBlack.isEmpty() ? 1.0 : 0.0);
        fill(inputs[currentIndex + 3], !kingSideCastleBlack.isEmpty() ? 1.0 : 0.0);
        fill(inputs[PLANE_COLOR], color2play.isBlack() ? 1.0 : 0.0);
        fill(inputs[currentIndex + 5], 1.0F);
        int nbRepeat = MovesUtils.nbMovesRepeat(allGamesMoves);
        fill(inputs[currentIndex + 6], nbRepeat >= 1 ? 1.0F : 0.0F); // 1 REPEAT
        fill(inputs[currentIndex + 7], nbRepeat >= 2 ? 1.0F : 0.0F); // 2 REPEAT
        fill(inputs[currentIndex + 8], 0.0F); // future use
    }

    private void fill(double[][] planes, double value) {
        if (value == 0.0) return;
        for (int i = 0; i < 8; i++) {
            Arrays.fill(planes[i], value);
        }
    }

    /**
     * @formatter:off <pre>
     * [0-6]: Pawn:0, Bishop:1, Knight:2, Rook:3, Queen:4, King:5
     * [0-6] pieces for White
     * [7-12] pieces for Black
     * </pre>
     * @formatter:on
     */
    private int getPlanesIndex(Piece piece) {
        int index = piece.getPieceAllegiance().isWhite() ? 0 : 6;
        if (piece.getPieceType() == Piece.PieceType.PAWN) return index + PAWN_INDEX;
        if (piece.getPieceType() == Piece.PieceType.BISHOP) return index + BISHOP_INDEX;
        if (piece.getPieceType() == Piece.PieceType.KNIGHT) return index + KNIGHT_INDEX;
        if (piece.getPieceType() == Piece.PieceType.ROOK) return index + ROOK_INDEX;
        if (piece.getPieceType() == Piece.PieceType.QUEEN) return index + QUEEN_INDEX;
        if (piece.getPieceType() == Piece.PieceType.KING) return index + KING_INDEX;
        return -100; // sure this will failed at least
    }

    @Override
    public void startMCTSStep(final Game game) {

    }

    @Override
    public InputsManager clone() {
        // no state on this inputManager, so no creation needed
        return new AquilaInputsManagerImpl();
        // return this;
    }

    @Override
    public long hashCode(Board board, Move move, List<Move> moves, Alliance color2play) {
        return Utils.hash(getHashCodeString(board, move, moves, color2play));
    }

    @Override
    public String getHashCodeString(Board board, Move move, List<Move> moves, Alliance color2play) {
        if (move != null && move.getMovedPiece() != null) {
            board = move.execute();
        }
        StringBuffer sb = new StringBuffer();
        sb.append(color2play.toString());
        sb.append("\n");
        sb.append(MovesUtils.nbMovesRepeat(moves));
        sb.append("\n");
        for (int position = 0; position < BoardUtils.NUM_TILES; position++) {
            Piece piece = board.getPiece(position);
            if (piece != null) {
                sb.append(String.format("%s=%d,", piece.getPieceType(), position));
            }
        }
        return sb.toString();
    }

    @Override
    public void processPlay(Board board, Move move) {

    }

}
