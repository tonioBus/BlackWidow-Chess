package com.aquila.chess.strategy.mcts;

import com.aquila.chess.AbstractGame;
import com.aquila.chess.Game;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import com.chess.engine.classic.player.Player;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MCTSGame extends AbstractGame {

    public MCTSGame(final AbstractGame abstractGame) {
        super(abstractGame.getInputsManager().clone(), abstractGame.getBoard());
        this.board = abstractGame.getBoard();
        this.nbMoveNoAttackAndNoPawn = abstractGame.getNbMoveNoAttackAndNoPawn();
        this.moves.addAll(abstractGame.getMoves());
        this.status = abstractGame.calculateStatus(board, null);
    }

    public Game.GameStatus play(final Move move) {
        if (!move.isAttack() &&
                move.getMovedPiece().getPieceType() != Piece.PieceType.PAWN)
            this.nbMoveNoAttackAndNoPawn++;
        else
            this.nbMoveNoAttackAndNoPawn = 0;
        Player player = getPlayer(move.getAllegiance());
        board = player.executeMove(move);
        inputsManager.updateHashsTables(move, board);
        this.status = calculateStatus(board, move);
        registerMove(move, board);
        return this.status;
    }

}
