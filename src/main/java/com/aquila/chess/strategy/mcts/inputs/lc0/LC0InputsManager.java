package com.aquila.chess.strategy.mcts.inputs.lc0;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.mcts.MCTSGame;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
 public class LC0InputsManager {

    @Getter
    protected final CircularFifoQueue<MCTSGame.Last8Inputs> last8Inputs = new CircularFifoQueue<>(8);
    
    public void initLastInputs(final Game game) {
        Board board = game.getBoard();
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
            MCTSGame.Last8Inputs lastInput = this.getLast8Inputs().get(size - 1);
            String moves = this.getLast8Inputs().stream().map(input -> input.move().toString()).collect(Collectors.joining(","));
            if (lastInput != null) {
                if (move.getMovedPiece().getPieceAllegiance().equals(lastInput.move().getMovedPiece().getPieceAllegiance()) &&
                        lastInput.move().toString().equals(move.toString())) {
                    log.error("Move:{} already inserted as last position, moves:{}", move, moves);
                    throw new RuntimeException("Move already inserted as last position");
                }
            }
        }
        this.last8Inputs.add(new MCTSGame.Last8Inputs(inputsOneNN, move));
    }

    public String getHashCodeString(final MCTSGame mctsGame, final Alliance color2play, final Move move) {
        StringBuilder sb = new StringBuilder();
        Board board = mctsGame.getLastBoard();
        List<Move> moves8inputs = mctsGame.getLast8Inputs().stream().map(in -> in.move()).collect(Collectors.toList());
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
}
