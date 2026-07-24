// nn_model.js - Custom Convolutional Neural Network (CNN) Model for Gomoku AI

class GomokuCNN {
    constructor() {
        // Initialize 3-layer CNN weights
        // Shape of weights:
        // conv1_weights: [8, 2, 3, 3] (8 filters, 2 input channels, 3x3 kernel)
        // conv2_weights: [8, 8, 3, 3] (8 filters, 8 input channels, 3x3 kernel)
        // conv3_weights: [1, 8, 3, 3] (1 filter, 8 input channels, 3x3 kernel)
        
        this.channels1 = 8;
        this.channels2 = 8;
        
        this.initWeights();
    }

    initWeights() {
        // Handcrafted/Tuned feature detection weights for Gomoku
        // Channel 0 represents AI pieces, Channel 1 represents Human pieces.
        this.conv1_weights = Array.from({length: this.channels1}, () => 
            Array.from({length: 2}, () => 
                Array.from({length: 3}, () => new Float32Array(3))
            )
        );
        this.conv1_bias = new Float32Array(this.channels1);

        this.conv2_weights = Array.from({length: this.channels2}, () => 
            Array.from({length: this.channels1}, () => 
                Array.from({length: 3}, () => new Float32Array(3))
            )
        );
        this.conv2_bias = new Float32Array(this.channels2);

        this.conv3_weights = Array.from({length: 1}, () => 
            Array.from({length: this.channels2}, () => 
                Array.from({length: 3}, () => new Float32Array(3))
            )
        );
        this.conv3_bias = new Float32Array(1);

        // --- CONV1 WEIGHTS ---
        // Filter 0: Horizontal line detector for AI (positive on AI channel, negative on empty/opponent)
        this.conv1_weights[0][0] = [
            [0.1, 0.1, 0.1],
            [0.8, 1.2, 0.8],
            [0.1, 0.1, 0.1]
        ];
        this.conv1_weights[0][1] = [
            [0, 0, 0],
            [-0.2, -0.4, -0.2],
            [0, 0, 0]
        ];
        this.conv1_bias[0] = -0.1;

        // Filter 1: Vertical line detector for AI
        this.conv1_weights[1][0] = [
            [0.1, 0.8, 0.1],
            [0.1, 1.2, 0.1],
            [0.1, 0.8, 0.1]
        ];
        this.conv1_weights[1][1] = [
            [0, -0.2, 0],
            [0, -0.4, 0],
            [0, -0.2, 0]
        ];
        this.conv1_bias[1] = -0.1;

        // Filter 2: Main Diagonal line detector for AI
        this.conv1_weights[2][0] = [
            [0.8, 0.1, 0.0],
            [0.1, 1.2, 0.1],
            [0.0, 0.1, 0.8]
        ];
        this.conv1_weights[2][1] = [
            [-0.2, 0, 0],
            [0, -0.4, 0],
            [0, 0, -0.2]
        ];
        this.conv1_bias[2] = -0.1;

        // Filter 3: Anti-Diagonal line detector for AI
        this.conv1_weights[3][0] = [
            [0.0, 0.1, 0.8],
            [0.1, 1.2, 0.1],
            [0.8, 0.1, 0.0]
        ];
        this.conv1_weights[3][1] = [
            [0, 0, -0.2],
            [0, -0.4, 0],
            [-0.2, 0, 0]
        ];
        this.conv1_bias[3] = -0.1;

        // Filter 4: Horizontal line detector for Human (blocking indicator)
        this.conv1_weights[4][0] = [
            [0, 0, 0],
            [-0.2, -0.4, -0.2],
            [0, 0, 0]
        ];
        this.conv1_weights[4][1] = [
            [0.1, 0.1, 0.1],
            [0.8, 1.2, 0.8],
            [0.1, 0.1, 0.1]
        ];
        this.conv1_bias[4] = -0.1;

        // Filter 5: Vertical line detector for Human
        this.conv1_weights[5][0] = [
            [0, -0.2, 0],
            [0, -0.4, 0],
            [0, -0.2, 0]
        ];
        this.conv1_weights[5][1] = [
            [0.1, 0.8, 0.1],
            [0.1, 1.2, 0.1],
            [0.1, 0.8, 0.1]
        ];
        this.conv1_bias[5] = -0.1;

        // Filter 6: Diagonal detector for Human
        this.conv1_weights[6][0] = [
            [-0.2, 0, 0],
            [0, -0.4, 0],
            [0, 0, -0.2]
        ];
        this.conv1_weights[6][1] = [
            [0.8, 0.1, 0.0],
            [0.1, 1.2, 0.1],
            [0.0, 0.1, 0.8]
        ];
        this.conv1_bias[6] = -0.1;

        // Filter 7: Anti-Diagonal detector for Human
        this.conv1_weights[7][0] = [
            [0, 0, -0.2],
            [0, -0.4, 0],
            [-0.2, 0, 0]
        ];
        this.conv1_weights[7][1] = [
            [0.0, 0.1, 0.8],
            [0.1, 1.2, 0.1],
            [0.8, 0.1, 0.0]
        ];
        this.conv1_bias[7] = -0.1;

        // --- CONV2 WEIGHTS ---
        // Combine features. For example, Filter 0 evaluates AI horizontal + vertical combination.
        for (let oc = 0; oc < this.channels2; oc++) {
            for (let ic = 0; ic < this.channels1; ic++) {
                if (oc === ic) {
                    this.conv2_weights[oc][ic] = [
                        [0.1, 0.2, 0.1],
                        [0.2, 0.8, 0.2],
                        [0.1, 0.2, 0.1]
                    ];
                } else if ((oc < 4 && ic < 4) || (oc >= 4 && ic >= 4)) {
                    // Similar features support each other
                    this.conv2_weights[oc][ic] = [
                        [0, 0.1, 0],
                        [0.1, 0.3, 0.1],
                        [0, 0.1, 0]
                    ];
                } else {
                    // Dissimilar features (e.g. AI vs Human features have inhibitory effect)
                    this.conv2_weights[oc][ic] = [
                        [0, -0.1, 0],
                        [-0.1, -0.2, -0.1],
                        [0, -0.1, 0]
                    ];
                }
            }
            this.conv2_bias[oc] = 0.0;
        }

        // --- CONV3 WEIGHTS (Output logit layer) ---
        // Synthesizes all combined features. AI's own lines are positive weights,
        // Human lines are also positive but scaled to prioritize defensive moves when threatened.
        for (let ic = 0; ic < this.channels2; ic++) {
            let scale = (ic >= 4) ? 1.1 : 1.0; // Slightly higher priority to defense (blocking human)
            this.conv3_weights[0][ic] = [
                [0.1 * scale, 0.2 * scale, 0.1 * scale],
                [0.2 * scale, 1.5 * scale, 0.2 * scale],
                [0.1 * scale, 0.2 * scale, 0.1 * scale]
            ];
        }
        this.conv3_bias[0] = 0.05;
    }

    // Helper: 2D Convolution operation with SAME padding and stride 1
    conv2d(input, weights, bias, inChannels, outChannels) {
        const H = 15;
        const W = 15;
        const output = Array.from({length: outChannels}, () => 
            Array.from({length: H}, () => new Float32Array(W))
        );

        for (let oc = 0; oc < outChannels; oc++) {
            const b = bias[oc];
            const outMat = output[oc];
            const curWeights = weights[oc];

            for (let r = 0; r < H; r++) {
                const outRow = outMat[r];
                for (let c = 0; c < W; c++) {
                    let sum = b;
                    
                    for (let ic = 0; ic < inChannels; ic++) {
                        const inMat = input[ic];
                        const wMat = curWeights[ic];

                        // Unroll the 3x3 kernel loop for speed
                        for (let kr = 0; kr < 3; kr++) {
                            const ir = r + kr - 1; // Padding = 1
                            if (ir < 0 || ir >= H) continue;
                            const inRow = inMat[ir];
                            const wRow = wMat[kr];

                            for (let kc = 0; kc < 3; kc++) {
                                const ic_c = c + kc - 1; // Padding = 1
                                if (ic_c < 0 || ic_c >= W) continue;
                                sum += inRow[ic_c] * wRow[kc];
                            }
                        }
                    }
                    outRow[c] = sum;
                }
            }
        }
        return output;
    }

    // Helper: ReLU Activation
    relu(input) {
        const channels = input.length;
        const H = 15;
        const W = 15;
        for (let c = 0; c < channels; c++) {
            const mat = input[c];
            for (let r = 0; r < H; r++) {
                const row = mat[r];
                for (let col = 0; col < W; col++) {
                    if (row[col] < 0) {
                        row[col] = 0;
                    }
                }
            }
        }
        return input;
    }

    // Predict best moves using forward pass
    forward(board, aiStone, humanStone) {
        const H = 15;
        const W = 15;

        // 1. Prepare input: [2, 15, 15]
        const input = [
            Array.from({length: H}, () => new Float32Array(W)), // Channel 0: AI pieces
            Array.from({length: H}, () => new Float32Array(W))  // Channel 1: Human pieces
        ];

        for (let r = 0; r < H; r++) {
            for (let c = 0; c < W; c++) {
                if (board[r][c] === aiStone) {
                    input[0][r][c] = 1.0;
                } else if (board[r][c] === humanStone) {
                    input[1][r][c] = 1.0;
                }
            }
        }

        // 2. Conv1: Input [2, 15, 15] -> Output [8, 15, 15]
        let x1 = this.conv2d(input, this.conv1_weights, this.conv1_bias, 2, this.channels1);
        x1 = this.relu(x1);

        // 3. Conv2: Input [8, 15, 15] -> Output [8, 15, 15]
        let x2 = this.conv2d(x1, this.conv2_weights, this.conv2_bias, this.channels1, this.channels2);
        x2 = this.relu(x2);

        // 4. Conv3: Input [8, 15, 15] -> Output [1, 15, 15]
        let x3 = this.conv2d(x2, this.conv3_weights, this.conv3_bias, this.channels2, 1);

        // 5. Extract logits for empty spots
        const logits = x3[0];
        const moves = [];

        for (let r = 0; r < H; r++) {
            for (let c = 0; c < W; c++) {
                if (board[r][c] === 0) {
                    // Neural network confidence score
                    let score = logits[r][c];
                    moves.push({ r, c, score });
                }
            }
        }

        // Softmax conversion over move confidence scores
        let maxScore = -Infinity;
        for (let i = 0; i < moves.length; i++) {
            if (moves[i].score > maxScore) maxScore = moves[i].score;
        }

        let sumExp = 0;
        for (let i = 0; i < moves.length; i++) {
            moves[i].exp = Math.exp(moves[i].score - maxScore); // stable softmax
            sumExp += moves[i].exp;
        }

        for (let i = 0; i < moves.length; i++) {
            moves[i].prob = moves[i].exp / sumExp;
        }

        // Sort descending by probability
        moves.sort((a, b) => b.prob - a.prob);
        return moves;
    }
}

// Export model
window.GomokuCNN = GomokuCNN;
