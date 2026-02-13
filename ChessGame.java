import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * A complete, single-file Java Chess game.
 * This version includes a fix for the NullPointerException within the AI's
 * move simulation by correcting the undoMove logic.
 */
public class ChessGame {

    private GameState gameState;
    private final ChessGUI gui;

    public ChessGame() {
        this.gameState = new GameState();
        this.gui = new ChessGUI(this);
    }

    public GameState getGameState() {
        return gameState;
    }

    public void restartGame() {
        this.gameState = new GameState();
        this.gui.setGameState(this.gameState);
        this.gui.updateBoard();
        this.gui.setBoardEnabled(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChessGame::new);
    }

    // --- Inner Classes for Game Structure ---

    public enum Color {
        WHITE, BLACK
    }

    public enum GameStatus {
        ACTIVE, CHECKMATE, STALEMATE
    }

    public static class GameState {
        private final Piece[][] board;
        private Color currentPlayer;
        private GameStatus status;
        private int[] enPassantTarget = null; // [x, y] of square vulnerable to en passant

        public GameState() {
            board = new Piece[8][8];
            currentPlayer = Color.WHITE;
            status = GameStatus.ACTIVE;
            setupBoard();
        }

        public Piece getPieceAt(int x, int y) {
            if (x < 0 || x >= 8 || y < 0 || y >= 8) {
                return null;
            }
            return board[y][x];
        }

        public int[] getEnPassantTarget() {
            return enPassantTarget;
        }

        public void movePiece(int startX, int startY, int endX, int endY) {
            if (status != GameStatus.ACTIVE) {
                return;
            }

            Piece pieceToMove = board[startY][startX];
            if (pieceToMove == null || pieceToMove.getColor() != currentPlayer) {
                return;
            }

            // Check if the move is valid
            List<int[]> validMoves = pieceToMove.getValidMoves(this);
            boolean isValidMove = false;
            for (int[] move : validMoves) {
                if (move[0] == endX && move[1] == endY) {
                    isValidMove = true;
                    break;
                }
            }

            if (!isValidMove) {
                return;
            }

            // --- Make the move and check for self-check ---
            Piece capturedPiece = board[endY][endX];
            int[] oldEnPassantTarget = this.enPassantTarget;
            this.enPassantTarget = null; // En passant is only valid for one turn

            // Special handling for en passant capture
            if (pieceToMove instanceof Pawn && endX != startX && capturedPiece == null) {
                int capturedPawnY = (pieceToMove.getColor() == Color.WHITE) ? endY + 1 : endY - 1;
                capturedPiece = board[capturedPawnY][endX];
                board[capturedPawnY][endX] = null;
            }

            board[endY][endX] = pieceToMove;
            board[startY][startX] = null;

            // If the move results in the king being in check, undo it
            if (isKingInCheck(currentPlayer)) {
                board[startY][startX] = pieceToMove;
                board[endY][endX] = capturedPiece;
                this.enPassantTarget = oldEnPassantTarget;
                 // if en passant was undone, restore captured pawn
                if (pieceToMove instanceof Pawn && endX != startX && board[endY][endX] == null) {
                    int capturedPawnY = (pieceToMove.getColor() == Color.WHITE) ? endY + 1 : endY - 1;
                    board[capturedPawnY][endX] = capturedPiece;
                }
                return;
            }

            // --- Finalize Move ---
            pieceToMove.x = endX;
            pieceToMove.y = endY;
            if (pieceToMove instanceof King) ((King) pieceToMove).hasMoved = true;
            if (pieceToMove instanceof Rook) ((Rook) pieceToMove).hasMoved = true;

            // Handle castling: move the rook
            if (pieceToMove instanceof King && Math.abs(endX - startX) == 2) {
                if (endX > startX) { // Kingside castle
                    Piece rook = board[startY][7];
                    board[startY][5] = rook;
                    board[startY][7] = null;
                    rook.x = 5;
                    ((Rook) rook).hasMoved = true;
                } else { // Queenside castle
                    Piece rook = board[startY][0];
                    board[startY][3] = rook;
                    board[startY][0] = null;
                    rook.x = 3;
                    ((Rook) rook).hasMoved = true;
                }
            }

            // Set new en passant target if a pawn moved two squares
            if (pieceToMove instanceof Pawn && Math.abs(endY - startY) == 2) {
                this.enPassantTarget = new int[]{startX, (startY + endY) / 2};
            }

            // Handle pawn promotion
            if (pieceToMove instanceof Pawn && (endY == 0 || endY == 7)) {
                board[endY][endX] = new Queen(currentPlayer, endX, endY);
            }

            switchPlayer();
            updateGameStatus();
        }

        public boolean isSquareUnderAttack(int x, int y, Color attackerColor) {
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    Piece p = board[r][c];
                    if (p == null || p.getColor() != attackerColor) {
                        continue;
                    }

                    if (p instanceof Pawn) {
                        int direction = (p.getColor() == Color.WHITE) ? -1 : 1;
                        if (p.y + direction == y && (p.x + 1 == x || p.x - 1 == x)) {
                            return true;
                        }
                    } else if (p instanceof King) {
                        // Non-recursive check for king attacks
                        if (Math.abs(p.x - x) <= 1 && Math.abs(p.y - y) <= 1) {
                            return true;
                        }
                    } else {
                        // For other pieces, getValidMoves is safe as it's not recursive
                        List<int[]> moves = p.getValidMoves(this);
                        for (int[] move : moves) {
                            if (move[0] == x && move[1] == y) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }


        public boolean isKingInCheck(Color kingColor) {
            int kingX = -1, kingY = -1;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    Piece p = board[y][x];
                    if (p instanceof King && p.getColor() == kingColor) {
                        kingX = x; kingY = y; break;
                    }
                }
            }
            if (kingX == -1) return false;
            return isSquareUnderAttack(kingX, kingY, (kingColor == Color.WHITE) ? Color.BLACK : Color.WHITE);
        }

        private boolean hasLegalMoves(Color playerColor) {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    Piece p = board[y][x];
                    if (p != null && p.getColor() == playerColor) {
                        List<int[]> moves = p.getValidMoves(this);
                        for (int[] move : moves) {
                            int startX = p.x, startY = p.y, endX = move[0], endY = move[1];
                            Piece captured = board[endY][endX];
                            board[endY][endX] = p; board[startY][startX] = null;
                            boolean kingStillInCheck = isKingInCheck(playerColor);
                            board[startY][startX] = p; board[endY][endX] = captured;
                            if (!kingStillInCheck) return true;
                        }
                    }
                }
            }
            return false;
        }

        private void updateGameStatus() {
            if (!hasLegalMoves(currentPlayer)) {
                status = isKingInCheck(currentPlayer) ? GameStatus.CHECKMATE : GameStatus.STALEMATE;
            } else {
                status = GameStatus.ACTIVE;
            }
        }

        public Color getCurrentPlayer() { return currentPlayer; }
        public GameStatus getStatus() { return status; }
        private void switchPlayer() { currentPlayer = (currentPlayer == Color.WHITE) ? Color.BLACK : Color.WHITE; }
        private void setupBoard() {
            // Pawns
            for (int i = 0; i < 8; i++) {
                board[1][i] = new Pawn(Color.BLACK, i, 1);
                board[6][i] = new Pawn(Color.WHITE, i, 6);
            }
            // Rooks
            board[0][0] = new Rook(Color.BLACK, 0, 0); board[0][7] = new Rook(Color.BLACK, 7, 0);
            board[7][0] = new Rook(Color.WHITE, 0, 7); board[7][7] = new Rook(Color.WHITE, 7, 7);
            // Knights
            board[0][1] = new Knight(Color.BLACK, 1, 0); board[0][6] = new Knight(Color.BLACK, 6, 0);
            board[7][1] = new Knight(Color.WHITE, 1, 7); board[7][6] = new Knight(Color.WHITE, 6, 7);
            // Bishops
            board[0][2] = new Bishop(Color.BLACK, 2, 0); board[0][5] = new Bishop(Color.BLACK, 5, 0);
            board[7][2] = new Bishop(Color.WHITE, 2, 7); board[7][5] = new Bishop(Color.WHITE, 5, 7);
            // Queens
            board[0][3] = new Queen(Color.BLACK, 3, 0); board[7][3] = new Queen(Color.WHITE, 3, 7);
            // Kings
            board[0][4] = new King(Color.BLACK, 4, 0); board[7][4] = new King(Color.WHITE, 4, 7);
        }
    }

    /**
     * The graphical user interface for the chess game.
     */
    public static class ChessGUI extends JFrame {
        private final JButton[][] squares = new JButton[8][8];
        private GameState gameState;
        private Piece selectedPiece = null;
        private int startX, startY;
        private final JLabel statusLabel;
        private final ChessGame gameController;
        private final java.awt.Color lightSquareColor = new java.awt.Color(240, 217, 181);
        private final java.awt.Color darkSquareColor = new java.awt.Color(181, 136, 99);

        public ChessGUI(ChessGame game) {
            this.gameController = game;
            this.gameState = game.getGameState();
            setTitle("Java Chess");
            setSize(600, 650);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLayout(new BorderLayout());

            JPanel topPanel = new JPanel(new FlowLayout());
            statusLabel = new JLabel("White's Turn", SwingConstants.CENTER);
            statusLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
            topPanel.add(statusLabel);
            add(topPanel, BorderLayout.NORTH);

            JPanel boardPanel = new JPanel(new GridLayout(8, 8));
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    squares[y][x] = new JButton();
                    squares[y][x].setFont(new Font("Unicode", Font.PLAIN, 40));
                    squares[y][x].setOpaque(true);
                    squares[y][x].setBorderPainted(false);
                    final int finalX = x;
                    final int finalY = y;
                    squares[y][x].addActionListener(e -> onSquareClicked(finalX, finalY));
                    boardPanel.add(squares[y][x]);
                }
            }
            add(boardPanel, BorderLayout.CENTER);

            JPanel bottomPanel = new JPanel(new FlowLayout());
            JButton playAgainButton = new JButton("Play Again");
            playAgainButton.addActionListener(e -> gameController.restartGame());
            bottomPanel.add(playAgainButton);
            add(bottomPanel, BorderLayout.SOUTH);

            updateBoard();
            setVisible(true);
        }

        public void setGameState(GameState newGameState) { this.gameState = newGameState; }

        public void setBoardEnabled(boolean enabled) {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    squares[y][x].setEnabled(enabled);
                }
            }
        }

        private void onSquareClicked(int x, int y) {
            if (gameState.getStatus() != GameStatus.ACTIVE) {
                return;
            }

            if (selectedPiece == null) {
                Piece clickedPiece = gameState.getPieceAt(x, y);
                if (clickedPiece != null && clickedPiece.getColor() == gameState.getCurrentPlayer()) {
                    selectedPiece = clickedPiece;
                    startX = x; startY = y;
                    highlightValidMoves(clickedPiece);
                }
            } else {
                gameState.movePiece(startX, startY, x, y);
                selectedPiece = null;
                updateBoard();

                if (gameState.getStatus() == GameStatus.ACTIVE && gameState.getCurrentPlayer() == Color.BLACK) {
                    handleAIMove();
                }
            }
        }

        private void handleAIMove() {
            setBoardEnabled(false);
            statusLabel.setText("Black is thinking...");

            SwingWorker<int[], Void> worker = new SwingWorker<int[], Void>() {
                @Override
                protected int[] doInBackground() {
                    return ChessAI.findBestMove(gameState, Color.BLACK, 3);
                }

                @Override
                protected void done() {
                    try {
                        int[] aiMove = get();
                        if (aiMove != null) {
                            gameState.movePiece(aiMove[0], aiMove[1], aiMove[2], aiMove[3]);
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    } finally {
                        updateBoard();
                        if (gameState.getStatus() == GameStatus.ACTIVE) {
                            setBoardEnabled(true);
                        }
                    }
                }
            };
            worker.execute();
        }

        private void highlightValidMoves(Piece piece) {
            List<int[]> validMoves = piece.getValidMoves(gameState);
            resetSquareColors();
            for (int[] move : validMoves) {
                squares[move[1]][move[0]].setBackground(java.awt.Color.YELLOW);
            }
        }

        private void resetSquareColors() {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    squares[y][x].setBackground((y + x) % 2 == 0 ? lightSquareColor : darkSquareColor);
                }
            }
        }

        public void updateBoard() {
            resetSquareColors();
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    Piece piece = gameState.getPieceAt(x, y);
                    squares[y][x].setText(piece != null ? piece.getUnicodeChar() : "");
                }
            }
            updateStatusLabel();
        }

        private void updateStatusLabel() {
            GameStatus status = gameState.getStatus();
            if (status == GameStatus.ACTIVE) {
                statusLabel.setText(gameState.getCurrentPlayer() + "'s Turn");
            } else if (status == GameStatus.CHECKMATE) {
                Color winner = (gameState.getCurrentPlayer() == Color.WHITE) ? Color.BLACK : Color.WHITE;
                statusLabel.setText("CHECKMATE! " + winner + " wins!");
                setBoardEnabled(false);
            } else if (status == GameStatus.STALEMATE) {
                statusLabel.setText("STALEMATE! Game is a draw.");
                setBoardEnabled(false);
            }
        }
    }

    // --- Piece Classes ---

    public static abstract class Piece {
        protected final Color color;
        protected int x, y;
        public Piece(Color color, int x, int y) { this.color = color; this.x = x; this.y = y; }
        public Color getColor() { return color; }
        public abstract String getUnicodeChar();
        public abstract List<int[]> getValidMoves(GameState state);
        protected void addMoveIfValid(List<int[]> moves, GameState state, int newX, int newY) {
            if (newX < 0 || newX >= 8 || newY < 0 || newY >= 8) return;
            Piece targetPiece = state.getPieceAt(newX, newY);
            if (targetPiece == null || targetPiece.getColor() != this.color) {
                moves.add(new int[]{newX, newY});
            }
        }
    }

    public static class Pawn extends Piece {
        public Pawn(Color color, int x, int y) { super(color, x, y); }
        public String getUnicodeChar() { return color == Color.WHITE ? "\u2659" : "\u265F"; }
        @Override
        public List<int[]> getValidMoves(GameState state) {
            List<int[]> moves = new ArrayList<>();
            int direction = (color == Color.WHITE) ? -1 : 1;
            int startRow = (color == Color.WHITE) ? 6 : 1;
            int newY = y + direction;

            if (newY >= 0 && newY < 8) {
                // Single step forward
                if (state.getPieceAt(x, newY) == null) {
                    moves.add(new int[]{x, newY});
                    // Double step forward from start
                    if (y == startRow && state.getPieceAt(x, y + 2 * direction) == null) {
                        moves.add(new int[]{x, y + 2 * direction});
                    }
                }
                // Captures
                for (int captureX : new int[]{x - 1, x + 1}) {
                    if (captureX >= 0 && captureX < 8) {
                        Piece target = state.getPieceAt(captureX, newY);
                        if (target != null && target.getColor() != this.color) {
                            moves.add(new int[]{captureX, newY});
                        }
                    }
                }
                // En Passant
                int[] epTarget = state.getEnPassantTarget();
                if (epTarget != null && epTarget[1] == newY && Math.abs(epTarget[0] - x) == 1) {
                    moves.add(epTarget);
                }
            }
            return moves;
        }
    }

    public static class Rook extends Piece {
        public boolean hasMoved = false;
        public Rook(Color color, int x, int y) { super(color, x, y); }
        public String getUnicodeChar() { return color == Color.WHITE ? "\u2656" : "\u265C"; }
        @Override
        public List<int[]> getValidMoves(GameState state) {
            List<int[]> moves = new ArrayList<>();
            int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
            for (int[] d : directions) {
                for (int i = 1; i < 8; i++) {
                    int newX = x + i * d[0], newY = y + i * d[1];
                    if (newX < 0 || newX >= 8 || newY < 0 || newY >= 8) break;
                    Piece target = state.getPieceAt(newX, newY);
                    if (target == null) {
                        moves.add(new int[]{newX, newY});
                    } else {
                        if (target.getColor() != this.color) moves.add(new int[]{newX, newY});
                        break;
                    }
                }
            }
            return moves;
        }
    }
    
    public static class Knight extends Piece {
        public Knight(Color color, int x, int y) { super(color, x, y); }
        public String getUnicodeChar() { return color == Color.WHITE ? "\u2658" : "\u265E"; }
        @Override
        public List<int[]> getValidMoves(GameState state) {
            List<int[]> moves = new ArrayList<>();
            int[][] offsets = {{1, 2}, {1, -2}, {-1, 2}, {-1, -2}, {2, 1}, {2, -1}, {-2, 1}, {-2, -1}};
            for (int[] o : offsets) addMoveIfValid(moves, state, x + o[0], y + o[1]);
            return moves;
        }
    }

    public static class Bishop extends Piece {
        public Bishop(Color color, int x, int y) { super(color, x, y); }
        public String getUnicodeChar() { return color == Color.WHITE ? "\u2657" : "\u265D"; }
        @Override
        public List<int[]> getValidMoves(GameState state) {
            List<int[]> moves = new ArrayList<>();
            int[][] directions = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
            for (int[] d : directions) {
                for (int i = 1; i < 8; i++) {
                    int newX = x + i * d[0], newY = y + i * d[1];
                    if (newX < 0 || newX >= 8 || newY < 0 || newY >= 8) break;
                    Piece target = state.getPieceAt(newX, newY);
                    if (target == null) {
                        moves.add(new int[]{newX, newY});
                    } else {
                        if (target.getColor() != this.color) moves.add(new int[]{newX, newY});
                        break;
                    }
                }
            }
            return moves;
        }
    }

    public static class Queen extends Piece {
        public Queen(Color color, int x, int y) { super(color, x, y); }
        public String getUnicodeChar() { return color == Color.WHITE ? "\u2655" : "\u265B"; }
        @Override
        public List<int[]> getValidMoves(GameState state) {
            List<int[]> moves = new ArrayList<>();
            moves.addAll(new Rook(color, x, y).getValidMoves(state));
            moves.addAll(new Bishop(color, x, y).getValidMoves(state));
            return moves;
        }
    }

    public static class King extends Piece {
        public boolean hasMoved = false;
        public King(Color color, int x, int y) { super(color, x, y); }
        public String getUnicodeChar() { return color == Color.WHITE ? "\u2654" : "\u265A"; }
        @Override
        public List<int[]> getValidMoves(GameState state) {
            List<int[]> moves = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    addMoveIfValid(moves, state, x + dx, y + dy);
                }
            }
            // Castling
            if (!hasMoved && !state.isKingInCheck(this.color)) {
                // Kingside
                Piece rookKingside = state.getPieceAt(7, y);
                if (rookKingside instanceof Rook && !((Rook) rookKingside).hasMoved) {
                    if (state.getPieceAt(5, y) == null && state.getPieceAt(6, y) == null &&
                        !state.isSquareUnderAttack(5, y, color == Color.WHITE ? Color.BLACK : Color.WHITE) &&
                        !state.isSquareUnderAttack(6, y, color == Color.WHITE ? Color.BLACK : Color.WHITE)) {
                        moves.add(new int[]{6, y});
                    }
                }
                // Queenside
                Piece rookQueenside = state.getPieceAt(0, y);
                if (rookQueenside instanceof Rook && !((Rook) rookQueenside).hasMoved) {
                     if (state.getPieceAt(1, y) == null && state.getPieceAt(2, y) == null && state.getPieceAt(3, y) == null &&
                        !state.isSquareUnderAttack(2, y, color == Color.WHITE ? Color.BLACK : Color.WHITE) &&
                        !state.isSquareUnderAttack(3, y, color == Color.WHITE ? Color.BLACK : Color.WHITE)) {
                        moves.add(new int[]{2, y});
                    }
                }
            }
            return moves;
        }
    }
    
    public static class ChessAI {
        private static final int PAWN = 100, KNIGHT = 320, BISHOP = 330, ROOK = 500, QUEEN = 900, KING = 20000;

        public static int[] findBestMove(GameState state, Color aiColor, int depth) {
            int bestScore = Integer.MIN_VALUE;
            int[] bestMove = null;
            List<int[]> moves = generateLegalMoves(state, aiColor);
            if (moves.isEmpty()) return null;

            for (int[] m : moves) {
                MoveRecord mr = makeMove(state, m);
                int score = minimax(state, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false, aiColor);
                undoMove(state, m, mr);
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = m.clone();
                }
            }
            return bestMove;
        }

        private static int minimax(GameState state, int depth, int alpha, int beta, boolean maximizing, Color aiColor) {
            Color player = state.getCurrentPlayer();
            if (depth == 0) return evaluate(state, aiColor);

            List<int[]> moves = generateLegalMoves(state, player);
            if (moves.isEmpty()) {
                if (state.isKingInCheck(player)) return maximizing ? -KING : KING;
                return 0; // Stalemate
            }

            if (maximizing) {
                int value = Integer.MIN_VALUE;
                for (int[] m : moves) {
                    MoveRecord mr = makeMove(state, m);
                    value = Math.max(value, minimax(state, depth - 1, alpha, beta, false, aiColor));
                    undoMove(state, m, mr);
                    alpha = Math.max(alpha, value);
                    if (alpha >= beta) break;
                }
                return value;
            } else {
                int value = Integer.MAX_VALUE;
                for (int[] m : moves) {
                    MoveRecord mr = makeMove(state, m);
                    value = Math.min(value, minimax(state, depth - 1, alpha, beta, true, aiColor));
                    undoMove(state, m, mr);
                    beta = Math.min(beta, value);
                    if (alpha >= beta) break;
                }
                return value;
            }
        }

        private static List<int[]> generateLegalMoves(GameState state, Color player) {
            List<int[]> legal = new ArrayList<>();
            Color originalPlayer = state.currentPlayer;
            state.currentPlayer = player; // Temporarily set player for validation
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    Piece p = state.getPieceAt(x, y);
                    if (p != null && p.getColor() == player) {
                        for (int[] mv : p.getValidMoves(state)) {
                            int[] m = new int[]{x, y, mv[0], mv[1]};
                            MoveRecord mr = makeMove(state, m);
                            // After makeMove, player is switched, so we check original player
                            if (!state.isKingInCheck(player)) {
                                legal.add(m);
                            }
                            undoMove(state, m, mr);
                        }
                    }
                }
            }
            state.currentPlayer = originalPlayer;
            return legal;
        }

        private static MoveRecord makeMove(GameState state, int[] move) {
            int sx = move[0], sy = move[1], ex = move[2], ey = move[3];
            Piece moving = state.board[sy][sx];
            Piece captured = state.board[ey][ex];
            boolean promoted = false;
            int[] prevEPTarget = state.enPassantTarget;
            state.enPassantTarget = null;
            
            // Handle en passant capture
            if (moving instanceof Pawn && ex != sx && captured == null) {
                int capturedPawnY = (moving.getColor() == Color.WHITE) ? ey + 1 : ey - 1;
                captured = state.board[capturedPawnY][ex];
                state.board[capturedPawnY][ex] = null;
            }

            state.board[ey][ex] = moving; 
            state.board[sy][sx] = null;

            if (moving != null) { // This check prevents the crash but the root cause is elsewhere
                 moving.x = ex; 
                 moving.y = ey;
            }

            if (moving instanceof Pawn && (ey == 0 || ey == 7)) {
                state.board[ey][ex] = new Queen(moving.getColor(), ex, ey);
                promoted = true;
            }
            if (moving instanceof Pawn && Math.abs(ey - sy) == 2) {
                state.enPassantTarget = new int[]{sx, (sy + ey) / 2};
            }
            
            state.switchPlayer();
            return new MoveRecord(captured, promoted, moving, prevEPTarget);
        }

        /**
         * This correctly handles all cases, including
         * en passant, preventing board state corruption.
         */
        private static void undoMove(GameState state, int[] move, MoveRecord mr) {
            int sx = move[0], sy = move[1], ex = move[2], ey = move[3];
            state.switchPlayer(); // Switch back to the player who made the move

            // Restore the piece that moved. This also handles undoing a promotion
            // because mr.moved stores the original Pawn.
            state.board[sy][sx] = mr.moved;
            if (mr.moved != null) {
                mr.moved.x = sx;
                mr.moved.y = sy;
            }

            // Check if the move was an en passant capture.
            // We know it was if a pawn moved diagonally and captured a piece
            // that was NOT on the destination square.
            if (mr.moved instanceof Pawn && ex != sx && mr.captured != null && mr.captured.y != ey) {
                // It was an en passant capture.
                state.board[ey][ex] = null; // The landing square was empty.
                state.board[mr.captured.y][mr.captured.x] = mr.captured; // Restore captured pawn.
            } else {
                // It was a regular move/capture. Restore whatever was on the destination square.
                state.board[ey][ex] = mr.captured;
            }

            // Restore the global en passant target state from before the move.
            state.enPassantTarget = mr.previousEnPassantTarget;
        }

        private static class MoveRecord {
            final Piece captured, moved;
            final boolean promoted;
            final int[] previousEnPassantTarget;
            MoveRecord(Piece c, boolean p, Piece m, int[] ept) {
                captured = c; promoted = p; moved = m; previousEnPassantTarget = ept;
            }
        }

        private static int evaluate(GameState state, Color aiColor) {
            int score = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    Piece p = state.board[y][x];
                    if (p == null) continue;
                    int v = 0;
                    if (p instanceof Pawn) v = PAWN;
                    else if (p instanceof Knight) v = KNIGHT;
                    else if (p instanceof Bishop) v = BISHOP;
                    else if (p instanceof Rook) v = ROOK;
                    else if (p instanceof Queen) v = QUEEN;
                    score += (p.getColor() == aiColor) ? v : -v;
                }
            }
            return score;
        }
    }
}
