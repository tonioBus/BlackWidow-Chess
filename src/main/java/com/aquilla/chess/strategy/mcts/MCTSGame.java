package com.aquilla.chess.strategy.mcts;

import com.aquilla.chess.Game;
import com.aquilla.chess.strategy.FixMCTSTreeStrategy;
import com.aquilla.chess.utils.Utils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.stream.Collectors;

@Slf4j
public class MCTSGame extends Game {

    @Getter
    protected final CircularFifoQueue<double[][][]> lastInputs = new CircularFifoQueue<>(8);

    @Getter
    protected final CircularFifoQueue<Move> lastMoves = new CircularFifoQueue<>(8);

    public FixMCTSTreeStrategy getStrategyWhite() {
        return (FixMCTSTreeStrategy) strategyWhite;
    }

    public FixMCTSTreeStrategy getStrategyBlack() {
        return (FixMCTSTreeStrategy) strategyBlack;
    }

    public MCTSGame(final MCTSGame mctsGame) {
        super(mctsGame.getLastBoard(),
                new FixMCTSTreeStrategy(Alliance.WHITE),
                new FixMCTSTreeStrategy(Alliance.BLACK));
        if (mctsGame.getNextPlayer().getAlliance().isWhite()) {
            this.nextPlayer = this.board.whitePlayer();
            this.nextStrategy = this.strategyWhite;
        } else {
            this.nextPlayer = this.board.blackPlayer();
            this.nextStrategy = this.strategyBlack;
        }
        this.lastMoves.addAll(mctsGame.lastMoves);
        this.nbMoveNoAttackAndNoPawn = mctsGame.getNbMoveNoAttackAndNoPawn();
        this.status = mctsGame.calculateStatus();
        this.moveOpponent = mctsGame.getMoveOpponent();
        this.lastInputs.addAll(mctsGame.lastInputs);
    }

    public MCTSGame(final Game game) {
        super(game.getLastBoard(),
                new FixMCTSTreeStrategy(Alliance.WHITE),
                new FixMCTSTreeStrategy(Alliance.BLACK));
        if (game.getNextPlayer().getAlliance().isWhite()) {
            this.nextPlayer = this.board.whitePlayer();
            this.nextStrategy = this.strategyWhite;
        } else {
            this.nextPlayer = this.board.blackPlayer();
            this.nextStrategy = this.strategyBlack;
        }
        int nbMoves = game.getMoves().size();
        int start = nbMoves < 8 ? 0 : nbMoves - 8;
        int stop = nbMoves;
        for (int i = start; i < stop; i++) {
            lastMoves.add(game.getMoves().get(i));
        }
        this.nbMoveNoAttackAndNoPawn = game.getNbMoveNoAttackAndNoPawn();
        this.status = game.calculateStatus();
        this.moveOpponent = game.getMoveOpponent();
        initLastInputs(game);
    }

    private void initLastInputs(final Game game) {
        int nbMoves = game.getMoves().size();
        int skipMoves = nbMoves < 8 ? 0 : nbMoves - 8;
        game.getTransitions().stream().skip(skipMoves).forEach(transitionMove -> {
            double[][][] inputs = InputsNNFactory.createInputsForOnePosition(transitionMove.getBoard(), null);
            this.lastInputs.add(inputs);
        });
    }

    public void nextMoves(final String moveWhite, final String moveBlack) {
        this.getStrategyWhite().setNextMoveSz(moveWhite);
        this.getStrategyBlack().setNextMoveSz(moveBlack);
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

    public String getHashCodeString(final Alliance color2play, final Move move) {
        StringBuffer sb = new StringBuffer();
        Board board = this.transitions.size() == 0 ? this.getBoard() : this.transitions.lastElement().getBoard();
        if (move != null) {
            board = move.execute();
        }
        sb.append(board.toString());
        sb.append("\nM:");
        sb.append(this.lastMoves.stream().map(m -> m.toString()).collect(Collectors.joining(",")));
        sb.append("\nC:");
        sb.append(color2play);
        return sb.toString();
    }

    public long hash(String str) {
        long hash = 5381;
        for (byte b : str.getBytes()) {
            hash = ((hash << 5) + hash) + b; /* hash * 33 + c */
        }
        return hash;
    }

    @Override
    public GameStatus play() throws Exception {
        GameStatus gameStatus = super.play();
        this.lastMoves.add(this.moves.get(this.moves.size() - 1));
        this.pushNNInput();
        return gameStatus;
    }

    protected void pushNNInput() {
        double[][][] inputs = InputsNNFactory.createInputsForOnePosition(this.getLastBoard(), null);
        this.getLastInputs().add(inputs);
    }
}
