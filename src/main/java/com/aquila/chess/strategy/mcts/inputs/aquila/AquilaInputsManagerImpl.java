package com.aquila.chess.strategy.mcts.inputs.aquila;

import com.aquila.chess.AbstractGame;
import com.aquila.chess.Game;
import com.aquila.chess.strategy.mcts.inputs.InputRecord;
import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
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
public class AquilaInputsManagerImpl extends InputsManager {

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
    public InputsFullNN createInputs(final InputRecord inputRecord) {
        final AbstractGame abstractGame = inputRecord.abstractGame();
        final Board board = abstractGame.getBoard();
        final Move move = inputRecord.move();
        final Alliance moveColor = inputRecord.moveColor();
        final var inputs = new double[FEATURES_PLANES][BoardUtils.NUM_TILES_PER_ROW][BoardUtils.NUM_TILES_PER_ROW];
        if (move != null && !move.isInitMove())
            // if we move, the moveColor will be the complementary of the player that just moved
            this.createInputs(
                    inputs,
                    new InputRecord(
                            abstractGame,
                            abstractGame.getMoves(),
                            null,
                            move.getAllegiance().complementary())
            );
        else
            this.createInputs(
                    inputs,
                    new InputRecord(
                            abstractGame,
                            abstractGame.getMoves(),
                            null,
                            moveColor)
            );
        return new AquilaInputsFullNN(inputs);
    }

    /**
     * @param board - the board on which we apply the move
     * @return the normalize board for 1 position using board and move. dimensions:
     * [12][NB_COL][NB_COL]
     */
    private void createInputs(double[][][] inputs, InputRecord inputRecord) {
        final Board board = inputRecord.abstractGame().getBoard();
        final AbstractGame abstractGame = inputRecord.abstractGame();
        int nbRepeat = 0; //getNbRepeat(inputRecord.moveColor());
        board.getAllPieces().stream().forEach(currentPiece -> {
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
            fill(inputs[36], abstractGame.ratioPlayer());
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
        fill(inputs[PLANE_COLOR], inputRecord.moveColor().isBlack() ? 1.0 : 0.0);
        fill(inputs[currentIndex + 5], 1.0F);
        fill(inputs[currentIndex + 6], nbRepeat >= 1 ? 1.0F : 0.0F); // 1 REPEAT
        fill(inputs[currentIndex + 7], nbRepeat >= 2 ? 1.0F : 0.0F); // 2 REPEAT
        fill(inputs[currentIndex + 8], nbRepeat >= 3 ? 1.0F : 0.0F); // 3 REPEAT
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
    public void startMCTSStep(final AbstractGame abstractGame) {
    }

    @Override
    public InputsManager clone() {
        // no state on this inputManager, so no creation needed
        return new AquilaInputsManagerImpl();
    }

    @Override
    public long hashCode(final InputRecord inputRecord) {
        return Utils.hash(getHashCodeString(inputRecord));
    }

    @Override
    public String getHashCodeString(final InputRecord inputRecord) {
        final Move move = inputRecord.move();
        final Alliance moveColor = inputRecord.moveColor();
        Board board;
        if (move != null && move.getMovedPiece() != null) {
            assert moveColor == move.getAllegiance();
            board = move.execute();
        } else {
            board = inputRecord.abstractGame().getBoard();
        }
        StringBuffer sb = new StringBuffer();
        sb.append(moveColor.toString());
        sb.append("\n");
        sb.append(0); //getNbRepeat(moveColor));
        sb.append("\n");
        for (int position = 0; position < BoardUtils.NUM_TILES; position++) {
            Piece piece = board.getPiece(position);
            if (piece != null) {
                sb.append(String.format("%s=%d,", piece.getPieceType(), position));
            }
        }
        sb.append(String.format("Ratio:%f\n", inputRecord.abstractGame().ratioPlayer()));
        List<Move> moveWhites = board.whitePlayer().getLegalMoves();
        Optional<Move> kingSideCastleWhite = moveWhites.stream().filter(m -> m instanceof Move.KingSideCastleMove).findFirst();
        Optional<Move> queenSideCastleWhite = moveWhites.stream().filter(m -> m instanceof Move.QueenSideCastleMove).findFirst();
        List<Move> moveBlacks = board.blackPlayer().getLegalMoves();
        Optional<Move> kingSideCastleBlack = moveBlacks.stream().filter(m -> m instanceof Move.KingSideCastleMove).findFirst();
        Optional<Move> queenSideCastleBlack = moveBlacks.stream().filter(m -> m instanceof Move.QueenSideCastleMove).findFirst();
        sb.append(!queenSideCastleWhite.isEmpty() ? 1.0 : 0.0);
        sb.append("\n");
        sb.append(!kingSideCastleWhite.isEmpty() ? 1.0 : 0.0);
        sb.append("\n");
        sb.append(!queenSideCastleBlack.isEmpty() ? 1.0 : 0.0);
        sb.append("\n");
        sb.append(!kingSideCastleBlack.isEmpty() ? 1.0 : 0.0);
        return sb.toString();
    }

    @Override
    public void registerInput(Board board, Move move) {

    }

}
