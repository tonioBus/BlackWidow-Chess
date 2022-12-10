package com.chess.pgn;

import java.util.List;

public class ValidGame
        extends GamePGN {

    public ValidGame(final PGNGameTags tags,
                     List<String> moves,
                     final String outcome) {
        super(tags, moves, outcome);
    }

    @Override
    public boolean isValid() {
        return true;
    }

}
