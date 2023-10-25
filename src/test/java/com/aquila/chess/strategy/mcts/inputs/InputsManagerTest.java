package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.StaticStrategy;
import com.aquila.chess.strategy.mcts.MCTSGame;
import com.aquila.chess.strategy.mcts.inputs.aquila.AquilaInputsManagerImpl;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static com.chess.engine.classic.Alliance.BLACK;
import static com.chess.engine.classic.Alliance.WHITE;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class InputsManagerTest {

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  p-B --- --- --- --- --- K-B ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- K-W  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @Test
    void getNbRepeat() throws Exception {
        final AquilaInputsManagerImpl inputsManager = new AquilaInputsManagerImpl();
        Board board = Board.createBoard("kh1", "pa3,kg3", BLACK);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final StaticStrategy whiteStrategy = new StaticStrategy(WHITE, "H1-G1;G1-F1;F1-G1;G1-H1;H1-G1;G1-H1;H1-G1;G1-H1");
        final StaticStrategy blackStrategy = new StaticStrategy(BLACK, "G3-H3;H3-G3;G3-H3;H3-G3;G3-H3;H3-G3;G3-H3;H3-G3");
        game.setup(whiteStrategy, blackStrategy);
        for (int i = 0; i < 16; i++) {
            MCTSGame mctsGame = new MCTSGame(game);
            play(game, mctsGame);
            log.info("hashs:{}", inputsManager.getHashs(game.getLastMove().getAllegiance()));
        }
        assertEquals(2, inputsManager.getNbRepeat(WHITE));
        assertEquals(3, inputsManager.getNbRepeat(BLACK));
    }

    private void play(final Game game, final MCTSGame mctsGame) throws Exception {
        log.info("\n{}\n", game.getBoard().toString());
        Move move;
        if (game.getLastMove() != null) {
            move=game.getLastMove();
            log.info("[{}] move:{} nbRepeat:{}",
                    move.getAllegiance(),
                    move,
                    game.getInputsManager().getNbRepeat(move.getAllegiance()));
        }
        game.play();
        move = game.getLastMove();
        Game.GameStatus status = mctsGame.play(move);
        log.info("[{}] move:{} status:{} nbRepeat:{}",
                move.getAllegiance(),
                move,
                status, game.getInputsManager().getNbRepeat(move.getAllegiance()));
    }
}