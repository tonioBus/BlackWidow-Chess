package com.aquila.chess;

import com.aquila.chess.strategy.mcts.MCTSStrategy;
import com.aquila.chess.strategy.mcts.inputs.lc0.Last8Inputs;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.strategy.mcts.utils.Coordinate2D;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.junit.jupiter.api.Test;
import umontreal.ssj.randvarmulti.DirichletGen;
import umontreal.ssj.rng.MRG32k3a;
import umontreal.ssj.rng.RandomStream;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class UtilsTest {

    public static boolean verify8inputs(final Lc0InputsManagerImpl inputsManager) {
        if (inputsManager == null) return true;
        CircularFifoQueue<Last8Inputs> last8Inputs = inputsManager.getLast8Inputs();
        Move lastMove = null;
        for (Last8Inputs last8Input : last8Inputs) {
            Move move = last8Input.move();
            if (lastMove != null) {
                if (move.equals(lastMove)) {
                    log.error("currentMove:{} lastMove:{}", move, lastMove);
                    return false;
                }
            }
            lastMove = move;
        }
        return true;
    }

    /**
     * y=7   "a8", "b8", "c8", "d8", "e8", "f8", "g8", "h8",
     * y=6   "a7", "b7", "c7", "d7", "e7", "f7", "g7", "h7",
     * y=5   "a6", "b6", "c6", "d6", "e6", "f6", "g6", "h6",
     * y=4   "a5", "b5", "c5", "d5", "e5", "f5", "g5", "h5",
     * y=3   "a4", "b4", "c4", "d4", "e4", "f4", "g4", "h4",
     * y=2   "a3", "b3", "c3", "d3", "e3", "f3", "g3", "h3",
     * y=1   "a2", "b2", "c2", "d2", "e2", "f2", "g2", "h2",
     * y=0   "a1", "b1", "c1", "d1", "e1", "f1", "g1", "h1"
     * x=     0     1     2     3     4     5     6     7
     */
    @Test
    public void testConvertCoordinate() {
        int coordinate1D;
        Coordinate2D coordinate2D;
        coordinate1D = BoardUtils.INSTANCE.getCoordinateAtPosition("a8");
        coordinate2D = new Coordinate2D(coordinate1D);
        assertEquals(0, coordinate2D.getX());
        assertEquals(7, coordinate2D.getY());

        coordinate1D = BoardUtils.INSTANCE.getCoordinateAtPosition("c7");
        coordinate2D = new Coordinate2D(coordinate1D);
        assertEquals(2, coordinate2D.getX());
        assertEquals(6, coordinate2D.getY());
    }


    private StringBuffer calcDirichlet(final RandomStream stream) {
        double[] policies = new double[5];
        Arrays.fill(policies, 0.2);
        double[] alpha = new double[5];
        Arrays.fill(alpha, 0.3);
        DirichletGen dirichletGen = new DirichletGen(stream, alpha);
        double epsilon = 0.25;
        int index = 0;
        double[] d = new double[alpha.length];
        dirichletGen.nextPoint(d);
        double p;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < policies.length; i++) {
            p = policies[i];
            double newP = (1 - epsilon) * p + epsilon * d[index];
            policies[i] = (float) newP;
            index++;
            sb.append(String.format("[%f]", policies[i]));
        }
        return sb;
    }

    @Test
    public void testDirichlet() {
        Random rand = new Random();
        rand.setSeed(System.currentTimeMillis());
        long[] seeds = new long[]{
                rand.nextLong(4294967087L),
                rand.nextLong(4294967087L),
                rand.nextLong(4294967087L),
                rand.nextLong(4294944443L),
                rand.nextLong(4294944443l),
                rand.nextLong(4294944443l)};
        MRG32k3a.setPackageSeed(seeds);
        final RandomStream stream = new MRG32k3a();
        StringBuffer sb = calcDirichlet(stream);
        log.info("policies1:{}", sb);
        sb = calcDirichlet(stream);
        log.info("policies2:{}", sb);
        sb = calcDirichlet(stream);
        log.info("policies3:{}", sb);
    }
}
