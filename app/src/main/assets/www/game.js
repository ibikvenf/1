// game.js - Ultra-High-Performance Unbeatable Gobang AI (With Transposition Tables, 5-Ply Alpha-Beta Search & CNN Guidance)

const BOARD_SIZE = 15;
let board = Array.from({ length: BOARD_SIZE }, () => Array(BOARD_SIZE).fill(0));
let moveHistory = []; // { r, c, player }
let gameOver = false;
let winner = 0; // 1 = Black, 2 = White, 0 = None

// Configs
let playerColor = 1; // 1 = Black, 2 = White
let aiColor = 2;     // Black moves first
let aiDifficulty = 'medium'; // 'easy', 'medium', 'hard' (CNN)
let playerFirst = true; // Does player go first? (Determines if player is Black or White)

// Transposition Table (Cache) for minimax state acceleration
const transpositionTable = new Map();

// CNN Instance
const cnnModel = new GomokuCNN();

// DOM elements
let canvas, ctx;
let lastMove = null;

// Initialize
window.addEventListener('DOMContentLoaded', () => {
    canvas = document.getElementById('boardCanvas');
    ctx = canvas.getContext('2d');
    
    setupCanvas();
    resetGame();
    
    // UI Event Listeners
    document.getElementById('restartBtn').addEventListener('click', () => {
        audio.playClick();
        resetGame();
    });
    
    document.getElementById('undoBtn').addEventListener('click', () => {
        audio.playClick();
        undoMove();
    });

    // Player color selection
    const firstSelect = document.getElementById('playerFirstSelect');
    firstSelect.addEventListener('change', (e) => {
        audio.playClick();
        playerFirst = e.target.value === 'player';
        if (playerFirst) {
            playerColor = 1;
            aiColor = 2;
        } else {
            playerColor = 2;
            aiColor = 1;
        }
        resetGame();
    });

    // Difficulty selection
    const diffSelect = document.getElementById('difficultySelect');
    diffSelect.addEventListener('change', (e) => {
        audio.playClick();
        aiDifficulty = e.target.value;
    });

    // Canvas click
    canvas.addEventListener('click', handleCanvasClick);
    
    // Help instructions modal
    const helpBtn = document.getElementById('helpBtn');
    const helpModal = document.getElementById('helpModal');
    const closeHelp = document.getElementById('closeHelp');
    
    helpBtn.addEventListener('click', () => {
        audio.playClick();
        helpModal.style.display = 'flex';
    });
    closeHelp.addEventListener('click', () => {
        audio.playClick();
        helpModal.style.display = 'none';
    });
});

// Setup Canvas scaling for high-DPI displays
function setupCanvas() {
    const size = Math.min(canvas.parentElement.clientWidth, 600);
    canvas.style.width = size + 'px';
    canvas.style.height = size + 'px';
    
    const scale = window.devicePixelRatio || 1;
    canvas.width = size * scale;
    canvas.height = size * scale;
    ctx.scale(scale, scale);
}

window.addEventListener('resize', () => {
    setupCanvas();
    drawBoard();
});

function resetGame() {
    board = Array.from({ length: BOARD_SIZE }, () => Array(BOARD_SIZE).fill(0));
    moveHistory = [];
    gameOver = false;
    winner = 0;
    lastMove = null;
    transpositionTable.clear();
    
    document.getElementById('statusText').innerText = "游戏开始！请落子";
    document.getElementById('statusText').className = "status-active";
    
    drawBoard();
    
    // If AI goes first
    if (!playerFirst) {
        document.getElementById('statusText').innerText = "AI 正在思考中...";
        setTimeout(makeAIMove, 400);
    }
}

// Draw Gomoku board and pieces
function drawBoard() {
    const w = canvas.style.width ? parseInt(canvas.style.width) : canvas.width;
    const padding = w / (BOARD_SIZE + 1);
    const spacing = (w - padding * 2) / (BOARD_SIZE - 1);
    
    ctx.clearRect(0, 0, w, w);
    
    // Draw wood background texture color
    ctx.fillStyle = '#E4A853';
    ctx.fillRect(0, 0, w, w);
    
    // Outer border
    ctx.strokeStyle = '#3c2409';
    ctx.lineWidth = 2.5;
    ctx.strokeRect(padding - 2, padding - 2, (BOARD_SIZE - 1) * spacing + 4, (BOARD_SIZE - 1) * spacing + 4);
    
    // Draw grid lines
    ctx.strokeStyle = '#6e4513';
    ctx.lineWidth = 1.0;
    for (let i = 0; i < BOARD_SIZE; i++) {
        // Horizontal line
        ctx.beginPath();
        ctx.moveTo(padding, padding + i * spacing);
        ctx.lineTo(padding + (BOARD_SIZE - 1) * spacing, padding + i * spacing);
        ctx.stroke();
        
        // Vertical line
        ctx.beginPath();
        ctx.moveTo(padding + i * spacing, padding);
        ctx.lineTo(padding + i * spacing, padding + (BOARD_SIZE - 1) * spacing);
        ctx.stroke();
    }
    
    // Draw star points (Gomoku standards)
    const starPoints = [3, 7, 11];
    ctx.fillStyle = '#3c2409';
    starPoints.forEach(r => {
        starPoints.forEach(c => {
            ctx.beginPath();
            ctx.arc(padding + r * spacing, padding + c * spacing, 4, 0, Math.PI * 2);
            ctx.fill();
        });
    });

    // Draw board coordinates (numbers and letters)
    ctx.fillStyle = '#5c3a10';
    ctx.font = 'bold 11px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    
    for (let i = 0; i < BOARD_SIZE; i++) {
        // Letters A-O at the bottom
        const letter = String.fromCharCode(65 + i); // 65 = 'A'
        ctx.fillText(letter, padding + i * spacing, padding / 2);
        ctx.fillText(letter, padding + i * spacing, w - padding / 2);
        
        // Numbers 1-15 on the left
        const num = (BOARD_SIZE - i).toString();
        ctx.fillText(num, padding / 2, padding + i * spacing);
        ctx.fillText(num, w - padding / 2, padding + i * spacing);
    }
    
    // Draw pieces
    for (let r = 0; r < BOARD_SIZE; r++) {
        for (let c = 0; c < BOARD_SIZE; c++) {
            if (board[r][c] !== 0) {
                drawPiece(r, c, board[r][c], spacing, padding);
            }
        }
    }
    
    // Draw last move marker
    if (lastMove) {
        const x = padding + lastMove.c * spacing;
        const y = padding + lastMove.r * spacing;
        ctx.fillStyle = '#ff3b30';
        ctx.beginPath();
        ctx.arc(x, y, 4, 0, Math.PI * 2);
        ctx.fill();
    }
}

function drawPiece(r, c, type, spacing, padding) {
    const x = padding + c * spacing;
    const y = padding + r * spacing;
    const radius = spacing * 0.44;
    
    ctx.shadowBlur = 6;
    ctx.shadowColor = 'rgba(0,0,0,0.45)';
    ctx.shadowOffsetX = 2;
    ctx.shadowOffsetY = 3;
    
    // Radial gradient for 3D sphere look
    const grad = ctx.createRadialGradient(x - radius * 0.25, y - radius * 0.25, radius * 0.1, x, y, radius);
    
    if (type === 1) { // Black stone
        grad.addColorStop(0, '#555555');
        grad.addColorStop(0.35, '#222222');
        grad.addColorStop(1, '#020202');
    } else { // White stone
        grad.addColorStop(0, '#ffffff');
        grad.addColorStop(0.2, '#f6f6f6');
        grad.addColorStop(0.7, '#e0e0e0');
        grad.addColorStop(1, '#cccccc');
    }
    
    ctx.beginPath();
    ctx.arc(x, y, radius, 0, Math.PI * 2);
    ctx.fillStyle = grad;
    ctx.fill();
    
    // Reset shadow
    ctx.shadowBlur = 0;
    ctx.shadowOffsetX = 0;
    ctx.shadowOffsetY = 0;
}

// Handle Board click
function handleCanvasClick(e) {
    if (gameOver) return;
    
    // If it's not player's turn (AI is playing)
    if (moveHistory.length % 2 === 0 && !playerFirst) return;
    if (moveHistory.length % 2 !== 0 && playerFirst) return;
    
    const w = parseInt(canvas.style.width);
    const padding = w / (BOARD_SIZE + 1);
    const spacing = (w - padding * 2) / (BOARD_SIZE - 1);
    
    // Get mouse click coordinates relative to canvas
    const rect = canvas.getBoundingClientRect();
    const clickX = e.clientX - rect.left;
    const clickY = e.clientY - rect.top;
    
    // Map to grid coordinates
    const c = Math.round((clickX - padding) / spacing);
    const r = Math.round((clickY - padding) / spacing);
    
    // Validate bounds
    if (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE) {
        if (board[r][c] === 0) {
            placePiece(r, c, playerColor);
            
            if (!gameOver) {
                document.getElementById('statusText').innerText = "AI 正在思考中...";
                document.getElementById('statusText').className = "status-thinking";
                setTimeout(makeAIMove, 300);
            }
        }
    }
}

function placePiece(r, c, player) {
    board[r][c] = player;
    lastMove = { r, c };
    moveHistory.push({ r, c, player });
    
    audio.playPlaceStone();
    drawBoard();
    
    if (checkWin(r, c, player)) {
        gameOver = true;
        winner = player;
        setTimeout(() => {
            audio.playWin();
            const winName = player === playerColor ? "恭喜你！你赢了！" : "电脑赢了！继续加油哦";
            document.getElementById('statusText').innerText = winName;
            document.getElementById('statusText').className = "status-over";
            alert(winName);
        }, 100);
    } else if (moveHistory.length === BOARD_SIZE * BOARD_SIZE) {
        gameOver = true;
        setTimeout(() => {
            document.getElementById('statusText').innerText = "和局！";
            document.getElementById('statusText').className = "status-over";
            alert("和棋，太精彩了！");
        }, 100);
    }
}

// Undo Move function
function undoMove() {
    if (moveHistory.length === 0 || gameOver) return;
    
    // Pop AI's move and player's move
    if (playerFirst) {
        if (moveHistory.length >= 2) {
            const m1 = moveHistory.pop();
            const m2 = moveHistory.pop();
            board[m1.r][m1.c] = 0;
            board[m2.r][m2.c] = 0;
        } else if (moveHistory.length === 1) {
            const m1 = moveHistory.pop();
            board[m1.r][m1.c] = 0;
        }
    } else { // AI went first
        if (moveHistory.length >= 2) {
            const m1 = moveHistory.pop();
            const m2 = moveHistory.pop();
            board[m1.r][m1.c] = 0;
            board[m2.r][m2.c] = 0;
        }
    }
    
    lastMove = moveHistory.length > 0 ? moveHistory[moveHistory.length - 1] : null;
    gameOver = false;
    winner = 0;
    transpositionTable.clear();
    
    document.getElementById('statusText').innerText = "已悔棋，请继续";
    document.getElementById('statusText').className = "status-active";
    drawBoard();
}

// Check 5-in-a-row winning condition
function checkWin(r, c, player) {
    const directions = [
        [0, 1],  // Horizontal
        [1, 0],  // Vertical
        [1, 1],  // Main Diagonal
        [1, -1]  // Anti-Diagonal
    ];
    
    for (const [dr, dc] of directions) {
        let count = 1;
        
        // Positive direction
        let nr = r + dr;
        let nc = c + dc;
        while (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE && board[nr][nc] === player) {
            count++;
            nr += dr;
            nc += dc;
        }
        
        // Negative direction
        nr = r - dr;
        nc = c - dc;
        while (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE && board[nr][nc] === player) {
            count++;
            nr -= dr;
            nc -= dc;
        }
        
        if (count >= 5) return true;
    }
    return false;
}

// ==========================================
// AI MOVE GENERATORS
// ==========================================

function makeAIMove() {
    if (gameOver) return;
    
    let bestMove;
    if (aiDifficulty === 'easy') {
        bestMove = getEasyAIMove();
    } else if (aiDifficulty === 'medium') {
        bestMove = getMediumAIMove();
    } else { // 'hard' (God-Like CNN + Minimax Search)
        bestMove = getHardAIMove();
    }
    
    if (bestMove) {
        placePiece(bestMove.r, bestMove.c, aiColor);
        if (!gameOver) {
            document.getElementById('statusText').innerText = "轮到你下棋了";
            document.getElementById('statusText').className = "status-active";
        }
    }
}

// EASY AI: Picks from candidates with small noise factor
function getEasyAIMove() {
    const candidates = evaluateAllSpots();
    candidates.sort((a, b) => b.score - a.score);
    const index = Math.floor(Math.random() * Math.min(candidates.length, 5));
    return candidates[index];
}

// MEDIUM AI: Picks the exact optimal move according to advanced evaluation
function getMediumAIMove() {
    const candidates = evaluateAllSpots();
    candidates.sort((a, b) => b.score - a.score);
    return candidates[0];
}

// GOD-LIKE HARD AI: Deep 5-Ply Minimax Search with Alpha-Beta Pruning, Transposition Tables and CNN Policy Priors
function getHardAIMove() {
    // 1. Instantly capture immediate wins or block immediate losses
    const candidates = evaluateAllSpots();
    candidates.sort((a, b) => b.score - a.score);
    
    if (candidates[0]) {
        if (candidates[0].ai_pattern >= 100000) return candidates[0]; // Take immediate win!
        if (candidates[0].player_pattern >= 100000) return candidates[0]; // Urgent block!
    }
    
    // 2. Generate spatial guidelines from CNN Model
    const cnnMoves = cnnModel.forward(board, aiColor, playerColor);
    
    // Get tight neighborhood empty spots (distance = 1) to compress branching factor for 5-ply depth
    const activeSpots = getTightActiveSpots();
    if (activeSpots.length === 0) {
        return { r: 7, c: 7 }; // Trivial central start
    }
    
    // Prioritize candidates using combined heuristic scores and CNN policy probabilities
    const candidateMoves = [];
    for (const spot of activeSpots) {
        const { r, c } = spot;
        const cnnMove = cnnMoves.find(m => m.r === r && m.c === c);
        const cnnProb = cnnMove ? cnnMove.prob : 0.0;
        
        const scoreAI = evaluateSpot(r, c, aiColor);
        const scorePlayer = evaluateSpot(r, c, playerColor);
        
        // Balance offense, defense and double-threat shaping
        const heuristicScore = scoreAI * 1.0 + scorePlayer * 1.3;
        const blendScore = heuristicScore + cnnProb * 25000; // Heavy weighting to CNN guidance
        
        candidateMoves.push({ r, c, score: blendScore });
    }
    
    // Sort and restrict branching factor to top 8 most elite tactical candidates (extremely optimized)
    candidateMoves.sort((a, b) => b.score - a.score);
    const topCandidates = candidateMoves.slice(0, 8);
    
    // Clear transposition table for this turn
    transpositionTable.clear();
    
    // 3. Perform 5-Ply Iterative Deepening Minimax Search with Alpha-Beta Pruning
    let bestVal = -Infinity;
    let bestMove = topCandidates[0];
    
    for (let depth = 1; depth <= 5; depth++) {
        let currentBestMove = null;
        let currentBestVal = -Infinity;
        
        for (const move of topCandidates) {
            const { r, c } = move;
            
            // Make virtual move
            board[r][c] = aiColor;
            // Run Alpha-Beta search
            const val = alphaBetaMinimax(depth, -Infinity, Infinity, false);
            // Revert virtual move
            board[r][c] = 0;
            
            if (val > currentBestVal) {
                currentBestVal = val;
                currentBestMove = move;
            }
        }
        
        // Prevent timeout, if a definitive winning line is found, commit immediately
        if (currentBestMove) {
            bestMove = currentBestMove;
            bestVal = currentBestVal;
            if (bestVal >= 900000) break; 
        }
    }
    
    return bestMove;
}

// Optimized 5-ply Alpha-Beta Minimax search with Transposition Table caching
function alphaBetaMinimax(depth, alpha, beta, isMaximizing) {
    // Generate unique board hash key for cache
    const boardHash = getBoardHash();
    const cacheKey = `${boardHash}_${depth}_${isMaximizing}`;
    if (transpositionTable.has(cacheKey)) {
        return transpositionTable.get(cacheKey);
    }
    
    if (depth === 0) {
        const val = evaluateFullBoard(aiColor) - evaluateFullBoard(playerColor) * 1.35;
        transpositionTable.set(cacheKey, val);
        return val;
    }
    
    const activeSpots = getTightActiveSpots().slice(0, 6); // Branching factor of 6 for extreme speed
    if (activeSpots.length === 0) return 0;
    
    if (isMaximizing) {
        let maxEval = -Infinity;
        for (const spot of activeSpots) {
            board[spot.r][spot.c] = aiColor;
            const ev = alphaBetaMinimax(depth - 1, alpha, beta, false);
            board[spot.r][spot.c] = 0;
            
            maxEval = Math.max(maxEval, ev);
            alpha = Math.max(alpha, ev);
            if (beta <= alpha) break; // Beta cut-off
        }
        transpositionTable.set(cacheKey, maxEval);
        return maxEval;
    } else {
        let minEval = Infinity;
        for (const spot of activeSpots) {
            board[spot.r][spot.c] = playerColor;
            const ev = alphaBetaMinimax(depth - 1, alpha, beta, true);
            board[spot.r][spot.c] = 0;
            
            minEval = Math.min(minEval, ev);
            beta = Math.min(beta, ev);
            if (beta <= alpha) break; // Alpha cut-off
        }
        transpositionTable.set(cacheKey, minEval);
        return minEval;
    }
}

// Helper: Generates a fast hash of the current board state for transposition caching
function getBoardHash() {
    let hash = "";
    for (let r = 0; r < BOARD_SIZE; r++) {
        for (let c = 0; c < BOARD_SIZE; c++) {
            if (board[r][c] !== 0) {
                hash += `${r},${c},${board[r][c]};`;
            }
        }
    }
    return hash;
}

// Get tight empty spots adjacent to existing pieces (distance = 1) for laser focus and super speed
function getTightActiveSpots() {
    const spots = [];
    const H = BOARD_SIZE;
    
    for (let r = 0; r < H; r++) {
        for (let c = 0; c < H; c++) {
            if (board[r][c] === 0) {
                let hasNeighbor = false;
                // Scan within 1 step (vertical, horizontal, diagonal)
                for (let dr = -1; dr <= 1 && !hasNeighbor; dr++) {
                    for (let dc = -1; dc <= 1; dc++) {
                        const nr = r + dr;
                        const nc = c + dc;
                        if (nr >= 0 && nr < H && nc >= 0 && nc < H && board[nr][nc] !== 0) {
                            hasNeighbor = true;
                            break;
                        }
                    }
                }
                
                if (hasNeighbor) {
                    const score = evaluateSpot(r, c, aiColor) + evaluateSpot(r, c, playerColor) * 1.25;
                    spots.push({ r, c, score });
                }
            }
        }
    }
    
    // Sort descending to optimize alpha-beta cut-off performance
    spots.sort((a, b) => b.score - a.score);
    return spots;
}

// Full-board evaluation scorer
function evaluateAllSpots() {
    const candidates = [];
    for (let r = 0; r < BOARD_SIZE; r++) {
        for (let c = 0; c < BOARD_SIZE; c++) {
            if (board[r][c] === 0) {
                const scoreAI = evaluateSpot(r, c, aiColor);
                const scorePlayer = evaluateSpot(r, c, playerColor);
                
                // Formulate double threes / double fours and combo patterns
                const aiDoubleThree = checkDoubleThree(r, c, aiColor);
                const playerDoubleThree = checkDoubleThree(r, c, playerColor);
                
                let bonus = 0;
                if (aiDoubleThree) bonus += 80000;
                if (playerDoubleThree) bonus += 95000; // Prioritize blocking player's double three traps!
                
                const totalScore = scoreAI + scorePlayer * 1.35 + bonus;
                candidates.push({
                    r, c,
                    score: totalScore,
                    ai_pattern: scoreAI,
                    player_pattern: scorePlayer
                });
            }
        }
    }
    
    if (candidates.length === BOARD_SIZE * BOARD_SIZE) {
        return [{ r: 7, c: 7, score: 1000, ai_pattern: 1000, player_pattern: 0 }];
    }
    return candidates;
}

// Sophisticated pattern detector for double-three traps (unstoppable moves)
function checkDoubleThree(r, c, player) {
    const directions = [[0, 1], [1, 0], [1, 1], [1, -1]];
    let liveThreeCount = 0;
    
    // Simulate placing the stone
    board[r][c] = player;
    
    for (const [dr, dc] of directions) {
        let count = 1;
        let openEnds = 0;
        
        let nr = r + dr;
        let nc = c + dc;
        while (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE && board[nr][nc] === player) {
            count++;
            nr += dr;
            nc += dc;
        }
        if (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE && board[nr][nc] === 0) {
            openEnds++;
        }
        
        nr = r - dr;
        nc = c - dc;
        while (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE && board[nr][nc] === player) {
            count++;
            nr -= dr;
            nc -= dc;
        }
        if (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE && board[nr][nc] === 0) {
            openEnds++;
        }
        
        // Forms a live 3
        if (count === 3 && openEnds === 2) {
            liveThreeCount++;
        }
    }
    
    // Revert simulated placement
    board[r][c] = 0;
    
    return liveThreeCount >= 2;
}

// Single position shape evaluation
function evaluateSpot(r, c, player) {
    const directions = [[0, 1], [1, 0], [1, 1], [1, -1]];
    let totalScore = 0;
    
    for (const [dr, dc] of directions) {
        let count = 1;
        let openEnds = 0;
        
        let nr = r + dr;
        let nc = c + dc;
        while (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE && board[nr][nc] === player) {
            count++;
            nr += dr;
            nc += dc;
        }
        if (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE && board[nr][nc] === 0) {
            openEnds++;
        }
        
        nr = r - dr;
        nc = c - dc;
        while (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE && board[nr][nc] === player) {
            count++;
            nr -= dr;
            nc -= dc;
        }
        if (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE && board[nr][nc] === 0) {
            openEnds++;
        }
        
        totalScore += getPatternScore(count, openEnds);
    }
    return totalScore;
}

// Advanced Gobang Pattern Evaluation weights
function getPatternScore(count, openEnds) {
    if (count >= 5) return 2000000; // Five-in-a-row (Definitive win)
    if (count === 4) {
        if (openEnds === 2) return 180000; // Active Live 4 (Unstoppable win next step)
        if (openEnds === 1) return 30000;  // Closed 4 (Needs immediate block/defense)
    }
    if (count === 3) {
        if (openEnds === 2) return 25000;  // Active Live 3 (Extremely dangerous)
        if (openEnds === 1) return 2000;   // Closed 3
    }
    if (count === 2) {
        if (openEnds === 2) return 1500;   // Live 2
        if (openEnds === 1) return 200;    // Closed 2
    }
    if (count === 1) {
        if (openEnds === 2) return 30;
    }
    return 0;
}

function evaluateFullBoard(player) {
    let score = 0;
    for (let r = 0; r < BOARD_SIZE; r++) {
        for (let c = 0; c < BOARD_SIZE; c++) {
            if (board[r][c] === player) {
                score += evaluateSpot(r, c, player);
                // Extra bonus for double-three shaping
                if (checkDoubleThree(r, c, player)) {
                    score += 50000;
                }
            }
        }
    }
    return score;
}
