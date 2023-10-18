package com.aquila.chess.strategy.mcts;

import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class MCTSNodePath extends ArrayList<Move> {

    /**
     * Create a MCTSNodePath using a directory path and a element
     *
     * @param path         the path
     * @param selectedMove the last move of the path to build
     */
    public MCTSNodePath(MCTSNodePath path, Move selectedMove) {
        this.addAll(path);
        this.add(selectedMove);
    }

    public MCTSNodePath(List<Move> moves) {
        this.addAll(moves);
    }

    @Override
    public int hashCode() {
        int ret = toString().hashCode();
        return ret;
    }

    public String toString() {
        return this.stream().map(move -> move.toString()).collect(Collectors.joining("/", "[//", "]"));
    }
}
