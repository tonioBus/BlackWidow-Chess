package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.mcts.inputs.InputsNNFactory;
import com.aquila.chess.strategy.mcts.inputs.InputsOneNN;
import com.aquila.chess.utils.Utils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class MCTSGame {

    public static record Last8Inputs(InputsOneNN inputs, Move move) {

    }

    @Getter
    protected final CircularFifoQueue<Last8Inputs> last8Inputs = new CircularFifoQueue<>(8);

    @Getter
    protected final List<Move> moves = new ArrayList<>(127);

    @Getter
    private int nbMoveNoAttackAndNoPawn = 0;

    @Getter
    protected Game.GameStatus status;

    @Getter
    protected Board board;

    public MCTSGame(final Game game) {
        this.board = game.getBoard();
        this.nbMoveNoAttackAndNoPawn = game.getNbMoveNoAttackAndNoPawn();
        this.status = game.calculateStatus();
        initLastInputs(game);
    }

    public MCTSGame(final MCTSGame game) {
        this.board = game.getBoard();
        this.nbMoveNoAttackAndNoPawn = game.getNbMoveNoAttackAndNoPawn();
        this.status = game.calculateStatus(this.board);
        this.last8Inputs.addAll(game.getLast8Inputs());
    }

    private void initLastInputs(final Game game) {
        if (log.isDebugEnabled()) {
            Move move = game.getLastMove();
            if (move.getMovedPiece() == null)
                log.info("INIT POSITION");
            else
                log.info("[{}:{}] initLastInputs", move.getMovedPiece().getPieceAllegiance(), move);
        }
        int nbMoves = game.getMoves().size();
        if (nbMoves == 0 && this.last8Inputs.size() == 0) {
            final InputsOneNN inputs = InputsNNFactory.createInputsForOnePosition(board, null);
            log.debug("push inputs init");
            this.add(null, inputs);
        } else {
            int skipMoves = nbMoves < 8 ? 0 : nbMoves - 8;
            this.last8Inputs.clear();
            game.getMoves().stream().skip(skipMoves).forEach(move -> {
                final InputsOneNN inputs = move.hashCode() == -1 ?
                        InputsNNFactory.createInputsForOnePosition(board, null) :
                        InputsNNFactory.createInputsForOnePosition(move.getBoard(), move);
                log.debug("push input after init move:{}:\n{}", move, inputs);
                this.add(move, inputs);
            });
        }
    }

    private void add(final Move move, final InputsOneNN inputsOneNN) {
        int size = this.getLast8Inputs().size();
        if (size > 1 && move != null) {
            Last8Inputs lastInput = this.getLast8Inputs().get(size - 1);
            String moves = this.getLast8Inputs().stream().map(input -> input.move().toString()).collect(Collectors.joining(","));
            if (lastInput != null) {
                if (move.getMovedPiece().getPieceAllegiance().equals(lastInput.move().getMovedPiece().getPieceAllegiance()) &&
                        lastInput.move().toString().equals(move.toString())) {
                    log.error("Move:{} already inserted as last position, moves:{}", move, moves);
                    throw new RuntimeException("Move already inserted as last position");
                }
            }
        }
        this.last8Inputs.add(new Last8Inputs(inputsOneNN, move));
//        if (this.getLast8Inputs().size() > 1) {
//            size = this.getLast8Inputs().size();
//            for (int i = 0; i < size - 1; i++) {
//                Move move1 = this.getLast8Inputs().get(i).move();
//                Move move2 = this.getLast8Inputs().get(i + 1).move();
//                if (move1.toString().equals(move2.toString())) {
//                    log.error("Move already inserted as last position, moves:{}", moves);
//                    throw new RuntimeException("Move already inserted as last position");
//                }
//            }
//        }
    }

    /**
     * @return the game hashcode
     */
    public long hashCode(final Alliance alliance) {
        return hashCode(alliance, null);
    }

    public synchronized long hashCode(@NonNull final Move move) {
        final Alliance color2play = move.getMovedPiece().getPieceAllegiance();
        return this.hashCode(color2play, move);
    }

    /**
     * @return the game hashcode
     */
    public synchronized long hashCode(final Alliance color2play, final Move move) {
        String hashCodeString = getHashCodeString(color2play, move);
        long ret = hash(hashCodeString);
        log.debug("[{}] HASHCODE:{}\n{}", color2play, ret, hashCodeString);
        if (log.isDebugEnabled())
            log.warn("HASHCODE-1() -> [{}] MOVE:{} nbMaxBits:{} - {}", color2play, move, Utils.nbMaxBits(ret), ret);
        return ret;
    }

    public Move getLastMove() {
        return this.getMoves().get(this.getMoves().size() - 1);
    }

    public Board getLastBoard() {
        int size = moves.size();
        return size == 0 ? this.getBoard() : this.moves.get(size - 1).execute();
    }

    public String getHashCodeString(final Alliance color2play, final Move move) {
        StringBuilder sb = new StringBuilder();
        Board board = getLastBoard();
        List<Move> moves8inputs = this.last8Inputs.stream().map(in -> in.move()).collect(Collectors.toList());
        if (move != null) {
            try {
                board = move.execute();
                moves8inputs.add(move);
            } catch (Exception e) {
                log.error("[{}] move:{}", move.getMovedPiece().getPieceAllegiance(), move);
                log.error("\n{}\n{}\n",
                        "##########################################",
                        board.toString()
                );
                throw e;
            }
        }
        sb.append(board.toString());
        sb.append("\nM:");
        sb.append(moves8inputs.stream().map(Move::toString).collect(Collectors.joining(",")));
        sb.append("\nC:");
        sb.append(color2play);
        return sb.toString();
    }

    public long hash(String str) {
        long hash = 5381;
        byte[] data = str.getBytes();
        for (byte b : data) {
            hash = ((hash << 5) + hash) + b;
        }
        return hash;
    }

    public Game.GameStatus play(final MCTSNode opponentNode, final Move move) {
        if (move.isAttack() == false &&
                move.getMovedPiece().getPieceType() != Piece.PieceType.PAWN)
            this.nbMoveNoAttackAndNoPawn++;
        else
            this.nbMoveNoAttackAndNoPawn = 0;
        add2Last8Inputs(move);
        return this.status = calculateStatus(board);
    }

    /**
     * Add to lastInputs fhe given move
     * @param move
     */
    public void add2Last8Inputs(final Move move) {
        if (move == null) return;
        this.moves.add(move);
        InputsOneNN inputs = InputsNNFactory.createInputsForOnePosition(this.getLastBoard(), move);
        this.last8Inputs.add(new Last8Inputs(inputs, move));
        board = move.execute();
    }

    public Game.GameStatus calculateStatus(final Board board) {
        if (board.whitePlayer().isInCheckMate()) return Game.GameStatus.WHITE_CHESSMATE;
        if (board.blackPlayer().isInCheckMate()) return Game.GameStatus.BLACK_CHESSMATE;
        if (board.currentPlayer().isInStaleMate()) return Game.GameStatus.PAT;
        if (moves.size() >= 300) return Game.GameStatus.DRAW_300;
        if (this.nbMoveNoAttackAndNoPawn >= 50) return Game.GameStatus.DRAW_50;
        if (!isThereEnoughMaterials(board)) return Game.GameStatus.DRAW_NOT_ENOUGH_PIECES;
        return Game.GameStatus.IN_PROGRESS;
    }

    private boolean isThereEnoughMaterials(final Board board) {
        long nbWhitePawn = board.whitePlayer().getActivePieces().stream().filter(piece -> piece.getPieceType() == Piece.PieceType.PAWN).count();
        long nbBlackPawn = board.blackPlayer().getActivePieces().stream().filter(piece -> piece.getPieceType() == Piece.PieceType.PAWN).count();
        if (nbWhitePawn + nbBlackPawn > 0) return true;
        long nbWhitePieces = board.whitePlayer().getActivePieces().size();
        long nbBlackPieces = board.blackPlayer().getActivePieces().size();
        long nbWhiteKnight = board.whitePlayer().getActivePieces().stream().filter(piece -> piece.getPieceType() == Piece.PieceType.KNIGHT).count();
        long nbBlackKnight = board.blackPlayer().getActivePieces().stream().filter(piece -> piece.getPieceType() == Piece.PieceType.KNIGHT).count();
        long nbWhiteBishop = board.whitePlayer().getActivePieces().stream().filter(piece -> piece.getPieceType() == Piece.PieceType.BISHOP).count();
        long nbBlackBishop = board.blackPlayer().getActivePieces().stream().filter(piece -> piece.getPieceType() == Piece.PieceType.BISHOP).count();

        final boolean whiteKingAlone = nbWhitePieces == 1;
        final boolean whiteHasOnly2knights = nbWhitePieces == 3 && nbWhiteKnight == 2;
        final boolean whiteHasOnly1knight = nbWhitePieces == 2 && nbWhiteKnight == 1;
        final boolean whiteHasOnly1Bishop = nbWhitePieces == 2 && nbWhiteBishop == 1;

        final boolean blackKingAlone = board.blackPlayer().getActivePieces().size() == 1;
        final boolean blackHasOnly2knights = board.blackPlayer().getActivePieces().size() == 3
                && board.blackPlayer().getActivePieces().stream().filter(piece -> piece.getPieceType() == Piece.PieceType.KNIGHT).count() == 2;
        final boolean blackHasOnly1knight = nbBlackPieces == 2 && nbBlackKnight == 1;
        final boolean blackHasOnly1Bishop = nbBlackPieces == 2 && nbBlackBishop == 1;

        if ((whiteKingAlone || whiteHasOnly2knights || whiteHasOnly1knight || whiteHasOnly1Bishop) &&
                (blackKingAlone || blackHasOnly2knights || blackHasOnly1knight || blackHasOnly1Bishop))
            return false;
        return true;
    }

    public int getNbStep() {
        return moves.size();
    }
}
