package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.AbstractGame;
import com.aquila.chess.strategy.mcts.utils.MovesUtils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MCTSGame extends AbstractGame {

    public MCTSGame(final AbstractGame abstractGame) {
        super(abstractGame.getInputsManager().clone(), abstractGame.getBoard());
        this.board = abstractGame.getBoard();
        this.nbMoveNoAttackAndNoPawn = abstractGame.getNbMoveNoAttackAndNoPawn();
        this.moves.addAll(abstractGame.getMoves());
        this.status = abstractGame.calculateStatus(board);
        this.inputsManager.startMCTSStep(abstractGame);
    }

    public Game.GameStatus play(final Move move) {
        if (!move.isAttack() &&
                move.getMovedPiece().getPieceType() != Piece.PieceType.PAWN)
            this.nbMoveNoAttackAndNoPawn++;
        else
            this.nbMoveNoAttackAndNoPawn = 0;
        add2Last8InputsAndPlay(move);
        inputsManager.updateHashsTables(board, move.getAllegiance());
        return this.status = calculateStatus(move.execute());
    }

    /**
     * Add to the lastInputs the given move
     *
     * @param move
     */
    public void add2Last8InputsAndPlay(final Move move) {
        if (move == null) return;
        this.moves.add(move);
        this.inputsManager.processPlay(getLastBoard(), move);
    }

}
