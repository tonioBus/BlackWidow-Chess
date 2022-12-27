package com.aquilla.chess;

import com.aquilla.chess.strategy.Strategy;
import com.aquilla.chess.strategy.mcts.FixMCTSTreeStrategy;
import com.aquilla.chess.utils.Utils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import com.chess.engine.classic.player.Player;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

@Builder(toBuilder = true)
@Slf4j
public class Game {

    @Getter
    protected int nbMoveNoAttackAndNoPawn = 0;
    @Getter
    protected GameStatus status;

    @Getter
    protected final Vector<GameTransition> transitions = new Vector<>();

    @Getter
    protected final CircularFifoQueue<Move> lastMoves = new CircularFifoQueue<>(8);

    @Getter
    protected Board board;

    @Getter
    protected Player nextPlayer;
    @Getter
    protected Strategy nextStrategy;

    @Getter
    protected Strategy strategyWhite;

    @Getter
    protected Strategy strategyBlack;

    public boolean isInitialPosition() {
        // TODO
        return true;
    }

    public int getNbStep() {
        return this.getTransitions().size();
    }

    public boolean isLogBoard() {
        return true;
    }

    public Object toPGN() {
        return "toPGN()";
    }

    @Builder(toBuilder = true)
    @Getter
    public static class GameTransition {
        final Board board;
        final Move fromMove;
    }

    public Game copy(final Alliance alliance, final Strategy strategyWhite, final Strategy strategyBlack) {
        final Game copyGame = Game.builder().board(this.board).build();
        if( this.strategyWhite instanceof FixMCTSTreeStrategy && strategyWhite instanceof FixMCTSTreeStrategy)
            ((FixMCTSTreeStrategy)strategyWhite).copyLastInputs((FixMCTSTreeStrategy) this.strategyWhite);
        if( this.strategyBlack instanceof FixMCTSTreeStrategy && strategyBlack instanceof  FixMCTSTreeStrategy)
            ((FixMCTSTreeStrategy)strategyBlack).copyLastInputs((FixMCTSTreeStrategy) this.strategyBlack);
        copyGame.strategyWhite = strategyWhite;
        copyGame.strategyBlack = strategyBlack;
        copyGame.transitions.addAll(this.transitions);
        copyGame.lastMoves.addAll(this.lastMoves);
        if (alliance.isWhite()) {
            copyGame.nextPlayer = this.board.whitePlayer();
            copyGame.nextStrategy = this.strategyWhite;
        } else {
            copyGame.nextPlayer = this.board.blackPlayer();
            copyGame.nextStrategy = this.strategyBlack;
        }
        copyGame.nbMoveNoAttackAndNoPawn = this.nbMoveNoAttackAndNoPawn;
        copyGame.status = this.calculateStatus();
        return copyGame;
    }

    public void setup(final Strategy strategyPlayerWhite,
                      final Strategy strategyPlayerBlack) {
        assert(strategyPlayerWhite.getAlliance() ==  Alliance.WHITE);
        assert(strategyPlayerBlack.getAlliance() ==  Alliance.BLACK);
        this.strategyWhite = strategyPlayerWhite;
        this.strategyBlack = strategyPlayerBlack;
        nextPlayer = board.whitePlayer();
        nextStrategy = strategyPlayerWhite;
    }

    public GameStatus play() throws Exception {
        List<Move> moves = nextPlayer.getLegalMoves(Move.MoveStatus.DONE);
        Move moveOpponent = this.lastMoves.size() == 0 ? null : this.lastMoves.get(lastMoves.size()-1);
        Move move = nextStrategy.play(this, moveOpponent, moves);
        if (move.isAttack() == false &&
                move.getMovedPiece().getPieceType() != Piece.PieceType.PAWN)
            this.nbMoveNoAttackAndNoPawn++;
        else
            this.nbMoveNoAttackAndNoPawn = 0;
        board = nextPlayer.executeMove(move);
        transitions.add(new GameTransition(board, move));
        lastMoves.add(move);
        this.status = calculateStatus();
        this.nextPlayer = getPlayer(this.nextPlayer.getAlliance().complementary());
        this.nextStrategy = this.nextStrategy == this.strategyBlack ? this.strategyWhite : this.strategyBlack;
        return this.status;
    }

    public Player getPlayer(final Alliance alliance) {
        return alliance.choosePlayerByAlliance(this.board.whitePlayer(), this.board.blackPlayer());
    }

    public Strategy getStrategy(final Alliance alliance) {
        return alliance.isWhite() ? strategyWhite : strategyBlack;

    }

    public enum GameStatus {
        IN_PROGRESS,
        PAT,
        CHESSMATE_WHITE,
        CHESSMATE_BLACK,
        DRAW_50,
        DRAW_300,
        DRAW_3,
        DRAW_NOT_ENOUGH_PIECES
    }

    public GameStatus calculateStatus() {
        if (board.whitePlayer().isInCheckMate()) return GameStatus.CHESSMATE_WHITE;
        if (board.blackPlayer().isInCheckMate()) return GameStatus.CHESSMATE_BLACK;
        if (nextPlayer.isInStaleMate()) return GameStatus.PAT;
        if (transitions.size() >= 300) return GameStatus.DRAW_300;
        if (this.nbMoveNoAttackAndNoPawn >= 50) return GameStatus.DRAW_50;
        if (!isThereEnoughMaterials()) return GameStatus.DRAW_NOT_ENOUGH_PIECES;
        return GameStatus.IN_PROGRESS;
    }

    private boolean isThereEnoughMaterials() {
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

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(String.format("GAME STATUS:%s\n", getStatus()));
        sb.append(String.format("MOVES:%s\n", transitions
                .stream()
                .map(gameTransition -> gameTransition.fromMove.toString())
                .collect(Collectors.joining(","))));
        sb.append(String.format("nbStep:%d\n", transitions.size()));
        sb.append(String.format("current player:%s\n", this.nextPlayer.getAlliance()));
        sb.append(String.format("current legal move:%s\n", this
                .getNextPlayer()
                .getLegalMoves()
                .stream()
                .map(move -> move.toString())
                .collect(Collectors.joining(","))));
        sb.append(this.board);
        return sb.toString();
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
        final int nbStep = this.transitions.size();
        StringBuffer sb = new StringBuffer();
        Board board = this.transitions.size() == 0 ? this.getBoard() : this.transitions.lastElement().board;
        LinkedList<Move> lastMoves = new LinkedList<>();
        lastMoves.addAll(this.lastMoves);
        if (move != null) {
            board = getPlayer(color2play).executeMove(move);
            if (lastMoves.size() > 0) lastMoves.remove(0);
            lastMoves.add(move);
        }
        sb.append(board.toString());
        sb.append("\nM:");
        sb.append(this.lastMoves.stream().map(m -> m.toString()).collect(Collectors.joining(",")));
        sb.append("\nS:");
        sb.append(nbStep);
        sb.append("\nC:");
        sb.append(color2play);
        if (log.isDebugEnabled()) log.debug("HASH:{}", sb);
        long ret = hash(sb.toString());
        if (log.isDebugEnabled())
            log.warn("HASHCODE-1({}) -> [{}] MOVE:{} nbMaxBits:{} - {}", nbStep, color2play, move, Utils.nbMaxBits(ret), ret);
        return ret;
    }

    private long hash(String str) {
        long hash = 5381;
        for (byte b : str.getBytes()) {
            hash = ((hash << 5) + hash) + b; /* hash * 33 + c */
        }
        return hash;
    }

}
