package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsFullNN;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;

import java.util.List;

public interface InputsManager {

    int getNbFeaturesPlanes();

    /**
     *
     * @param board - the board on which we apply the move
     * @param move  - the move to apply or null if nothing need to be applied,
     *              the complementary of the color of the move will be used as color2play
     * @param color2play the color that will play, used only if move is not defined
     * @return
     */
    InputsFullNN createInputs(final Board board, final Move move, final List<Move> moves, final Alliance color2play);

    String getHashCodeString(final Board board, final Move move, final List<Move> moves, final Alliance color2play);

    long hashCode(final Board board, final Move move, final List<Move> moves, final Alliance color2play);

    void startMCTSStep(Game game);

    InputsManager clone();

    void processPlay(final Board board, final Move move);

}
