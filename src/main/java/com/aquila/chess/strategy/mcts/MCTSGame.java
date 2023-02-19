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

    @Getter
    protected final CircularFifoQueue<InputsOneNN> last8Inputs = new CircularFifoQueue<>(8);

    @Getter
    protected final List<Move> moves = new ArrayList<>(127);

    @Getter
    protected final CircularFifoQueue<Move> last8Moves = new CircularFifoQueue<>(8);
    @Getter
    private int nbMoveNoAttackAndNoPawn = 0;

    @Getter
    protected Game.GameStatus status;

    @Getter
    protected Board board;

    public MCTSGame(final Game game) {
        this.board = game.getBoard();
        int nbMoves = game.getMoves().size();
        int start = nbMoves < 8 ? 0 : nbMoves - 8;
        for (int i = start; i < nbMoves; i++) {
            last8Moves.add(game.getMoves().get(i));
        }
        this.nbMoveNoAttackAndNoPawn = game.getNbMoveNoAttackAndNoPawn();
        this.status = game.calculateStatus();
        initLastInputs(game);
    }

    public MCTSGame(final MCTSGame game) {
        this.board = game.getBoard();
        int nbMoves = game.getMoves().size();
        int start = nbMoves < 8 ? 0 : nbMoves - 8;
        for (int i = start; i < nbMoves; i++) {
            last8Moves.add(game.getMoves().get(i));
        }
        this.nbMoveNoAttackAndNoPawn = game.getNbMoveNoAttackAndNoPawn();
        this.status = game.calculateStatus(this.board);
        this.last8Inputs.addAll(game.getLast8Inputs());
    }

    private void initLastInputs(final Game game) {
        log.info("initLastInputs");
        int nbMoves = game.getMoves().size();
        if (nbMoves == 0 && this.last8Inputs.size() == 0) {
            final InputsOneNN inputs = InputsNNFactory.createInputsForOnePosition(board, null);
            log.info("push inputs init");
            this.last8Inputs.add(inputs);
        } else {
            int skipMoves = nbMoves < 8 ? 0 : nbMoves - 8;
            this.last8Inputs.clear();
            game.getMoves().stream().skip(skipMoves).forEach(move -> {
                final InputsOneNN inputs = move.hashCode() == -1 ?
                        InputsNNFactory.createInputsForOnePosition(board, null) :
                        InputsNNFactory.createInputsForOnePosition(move.getBoard(), move);
                log.info("push input after init move:{}:\n{}", move, inputs);
                this.last8Inputs.add(inputs);
            });
        }
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
        if (log.isDebugEnabled()) log.debug("HASH:{}", hashCodeString);
        long ret = hash(hashCodeString);
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
        if (move != null) {
            try {
                board = move.execute();
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
        sb.append(this.last8Moves.stream().map(Object::toString).collect(Collectors.joining(",")));
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
        update(move);
        return this.status = calculateStatus(board);
    }

    public void update(final Move move) {
        if (move == null) return;
        board = move.execute();
        this.moves.add(move);
        this.last8Moves.add(move);
        this.pushNNInput();
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

    /**
     * push the last board of this game on the list of inputs
     */
    protected void pushNNInput() {
        InputsOneNN inputs = InputsNNFactory.createInputsForOnePosition(this.getLastBoard(), null);
        // log.info("pushNNInput:\n{}\n", inputs);
        this.getLast8Inputs().add(inputs);
    }

    public int getNbStep() {
        return moves.size();
    }
}
