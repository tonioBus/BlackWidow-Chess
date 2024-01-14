package com.chess.engine.classic.board;

import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move.MoveFactory;
import com.chess.engine.classic.pieces.*;
import com.chess.engine.classic.player.BlackPlayer;
import com.chess.engine.classic.player.Player;
import com.chess.engine.classic.player.WhitePlayer;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Board {

    public static final int NB_COL = 8;
    private final Map<Integer, Piece> boardConfig;
    private final Collection<Piece> whitePieces;
    private final Collection<Piece> blackPieces;
    private final WhitePlayer whitePlayer;
    private final BlackPlayer blackPlayer;
    private final Player currentPlayer;
    private final Pawn enPassantPawn;
    private final Move transitionMove;

    @Getter
    @Setter
    private boolean checkBoard = true;

    private static final Board STANDARD_BOARD = createStandardBoardImpl();

    private Board(final Builder builder) {
        this(builder, builder.checkBoard);
    }

    private Board(final Builder builder, boolean checkBoard) {
        this.checkBoard = checkBoard;
        this.boardConfig = Collections.unmodifiableMap(builder.boardConfig);
        this.whitePieces = calculateActivePieces(builder, Alliance.WHITE);
        this.blackPieces = calculateActivePieces(builder, Alliance.BLACK);
        this.enPassantPawn = builder.enPassantPawn;
        final List<Move> whiteStandardMoves = calculateLegalMoves(this.whitePieces);
        final List<Move> blackStandardMoves = calculateLegalMoves(this.blackPieces);
        this.whitePlayer = new WhitePlayer(this, whiteStandardMoves, blackStandardMoves);
        this.blackPlayer = new BlackPlayer(this, whiteStandardMoves, blackStandardMoves);
        this.currentPlayer = builder.nextMoveMaker.choosePlayerByAlliance(this.whitePlayer, this.blackPlayer);
        this.transitionMove = builder.transitionMove != null ? builder.transitionMove : MoveFactory.getNullMove();
    }

    public String toStringOrigin() {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < BoardUtils.NUM_TILES; i++) {
            final String tileText = prettyPrint(this.boardConfig.get(i));
            builder.append(String.format("%3s", tileText));
            if ((i + 1) % 8 == 0) {
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("    ");
        for (int x = 0; x < BoardUtils.NUM_TILES_PER_ROW; x++) {
            sb.append("[" + (char) ('a' + x) + "] ");
        }
        sb.append("\n");
        for (int y = BoardUtils.NUM_TILES_PER_ROW - 1; y >= 0; y--) {
            sb.append(" " + (y + 1) + "  ");
            for (int x = 0; x < BoardUtils.NUM_TILES_PER_ROW; x++) {
                Piece piece = this.boardConfig.get((BoardUtils.NUM_TILES_PER_ROW - y - 1) * BoardUtils.NUM_TILES_PER_ROW + x);
                if (piece != null) {
                    sb.append(String.format("%s-%c ", piece.toString(), piece.getPieceAllegiance().isBlack() ? 'B' : 'W'));
                } else {
                    sb.append("--- ");
                }
            }
            sb.append(" " + (y + 1) + "  ");
            sb.append("\n");
        }
        sb.append("    ");
        for (int x = 0; x < BoardUtils.NUM_TILES_PER_ROW; x++) {
            sb.append("[" + (char) ('a' + x) + "] ");
        }
        sb.append("\n");
        return sb.toString();
    }


    private static String prettyPrint(final Piece piece) {
        if (piece != null) {
            return piece.getPieceAllegiance().isBlack() ?
                    piece.toString().toLowerCase() : piece.toString();
        }
        return "-";
    }

    public Collection<Piece> getBlackPieces() {
        return this.blackPieces;
    }

    public Collection<Piece> getWhitePieces() {
        return this.whitePieces;
    }

    public Collection<Piece> getAllPieces() {
        return Stream.concat(this.whitePieces.stream(),
                this.blackPieces.stream()).collect(Collectors.toList());
    }

    public Collection<Move> getAllLegalMoves() {
        return Stream.concat(this.whitePlayer.getLegalMoves().stream(),
                this.blackPlayer.getLegalMoves().stream()).collect(Collectors.toList());
    }

    public WhitePlayer whitePlayer() {
        return this.whitePlayer;
    }

    public BlackPlayer blackPlayer() {
        return this.blackPlayer;
    }

    public Player currentPlayer() {
        return this.currentPlayer;
    }

    public Piece getPiece(final int coordinate) {
        return this.boardConfig.get(coordinate);
    }

    public Pawn getEnPassantPawn() {
        return this.enPassantPawn;
    }

    public Move getTransitionMove() {
        return this.transitionMove;
    }

    public static Board createStandardBoard() {
        return STANDARD_BOARD;
    }

    private static final Pattern pieceAndPositionPattern = Pattern.compile("([pbnrqk]?)([a-h][1-8])(k?)(q?)");

    public static Board createBoard(final String whitePieces,
                                    final String blackPieces,
                                    Alliance firstMove) {
        final Builder builder = new Builder();
        placePieces(builder, Alliance.WHITE, whitePieces.toLowerCase());
        placePieces(builder, Alliance.BLACK, blackPieces.toLowerCase());
        //white to move
        builder.setMoveMaker(firstMove);
        //build the board
        return builder.build();
    }

    private static void placePieces(final Board.Builder builder, final Alliance alliance, final String pieces) {
        Arrays.stream(pieces.toLowerCase().split("[;,]")).forEach(pieceAndPosition -> {
            Matcher matcher = pieceAndPositionPattern.matcher(pieceAndPosition);
            if (!matcher.matches())
                throw new RuntimeException(String.format("pieces description incorrect: (%s)", pieces));
            String piece = matcher.group(1);
            String position = matcher.group(2);
            String kingSideCastleCapable = matcher.group(3);
            String queenSideCastleCapable = matcher.group(4);
            int coordinate = BoardUtils.INSTANCE.getCoordinateAtPosition(position);
            builder.setPiece(createPiece(piece, coordinate, alliance, kingSideCastleCapable.equals("k"), queenSideCastleCapable.equals("q")));
        });
    }

    private static Piece createPiece(String piece,
                                     int coordinate,
                                     final Alliance alliance,
                                     boolean kingSideCastleCapable,
                                     boolean queenSideCastleCapable) {
        switch (piece) {
            case "":
            case "p":
                return new Pawn(alliance, coordinate);
            case "b":
                return new Bishop(alliance, coordinate);
            case "n":
                return new Knight(alliance, coordinate);
            case "r":
                return new Rook(alliance, coordinate);
            case "q":
                return new Queen(alliance, coordinate);
            case "k":
                return new King(alliance, coordinate, kingSideCastleCapable, queenSideCastleCapable);
        }
        throw new RuntimeException(String.format("Piece type not found %s", piece));
    }

    private static Board createStandardBoardImpl() {
        final Builder builder = new Builder();
        // Black Layout
        builder.setPiece(new Rook(Alliance.BLACK, 0));
        builder.setPiece(new Knight(Alliance.BLACK, 1));
        builder.setPiece(new Bishop(Alliance.BLACK, 2));
        builder.setPiece(new Queen(Alliance.BLACK, 3));
        builder.setPiece(new King(Alliance.BLACK, 4, true, true));
        builder.setPiece(new Bishop(Alliance.BLACK, 5));
        builder.setPiece(new Knight(Alliance.BLACK, 6));
        builder.setPiece(new Rook(Alliance.BLACK, 7));
        builder.setPiece(new Pawn(Alliance.BLACK, 8));
        builder.setPiece(new Pawn(Alliance.BLACK, 9));
        builder.setPiece(new Pawn(Alliance.BLACK, 10));
        builder.setPiece(new Pawn(Alliance.BLACK, 11));
        builder.setPiece(new Pawn(Alliance.BLACK, 12));
        builder.setPiece(new Pawn(Alliance.BLACK, 13));
        builder.setPiece(new Pawn(Alliance.BLACK, 14));
        builder.setPiece(new Pawn(Alliance.BLACK, 15));
        // White Layout
        builder.setPiece(new Pawn(Alliance.WHITE, 48));
        builder.setPiece(new Pawn(Alliance.WHITE, 49));
        builder.setPiece(new Pawn(Alliance.WHITE, 50));
        builder.setPiece(new Pawn(Alliance.WHITE, 51));
        builder.setPiece(new Pawn(Alliance.WHITE, 52));
        builder.setPiece(new Pawn(Alliance.WHITE, 53));
        builder.setPiece(new Pawn(Alliance.WHITE, 54));
        builder.setPiece(new Pawn(Alliance.WHITE, 55));
        builder.setPiece(new Rook(Alliance.WHITE, 56));
        builder.setPiece(new Knight(Alliance.WHITE, 57));
        builder.setPiece(new Bishop(Alliance.WHITE, 58));
        builder.setPiece(new Queen(Alliance.WHITE, 59));
        builder.setPiece(new King(Alliance.WHITE, 60, true, true));
        builder.setPiece(new Bishop(Alliance.WHITE, 61));
        builder.setPiece(new Knight(Alliance.WHITE, 62));
        builder.setPiece(new Rook(Alliance.WHITE, 63));
        //white to move
        builder.setMoveMaker(Alliance.WHITE);
        //build the board
        return builder.build();
    }

    private List<Move> calculateLegalMoves(final Collection<Piece> pieces) {
        return pieces.stream().flatMap(piece -> piece.calculateLegalMoves(this).stream())
                .collect(Collectors.toList());
    }

    private static Collection<Piece> calculateActivePieces(final Builder builder,
                                                           final Alliance alliance) {
        return builder.boardConfig.values().stream()
                .filter(piece -> piece.getPieceAllegiance() == alliance)
                .collect(Collectors.toList());
    }

    public static class Builder {

        Map<Integer, Piece> boardConfig;
        Alliance nextMoveMaker;
        Pawn enPassantPawn;
        Move transitionMove;

        @Setter
        private boolean checkBoard = true;

        public Builder() {
            this.boardConfig = new HashMap<>(32, 1.0f);
        }

        public Builder setPiece(final Piece piece) {
            this.boardConfig.put(piece.getPiecePosition(), piece);
            return this;
        }

        public Builder setMoveMaker(final Alliance nextMoveMaker) {
            this.nextMoveMaker = nextMoveMaker;
            return this;
        }

        public Builder setEnPassantPawn(final Pawn enPassantPawn) {
            this.enPassantPawn = enPassantPawn;
            return this;
        }

        public Builder setMoveTransition(final Move transitionMove) {
            this.transitionMove = transitionMove;
            return this;
        }

        public Board build() {
            Board ret = new Board(this, checkBoard);
            return ret;
        }

    }

}
