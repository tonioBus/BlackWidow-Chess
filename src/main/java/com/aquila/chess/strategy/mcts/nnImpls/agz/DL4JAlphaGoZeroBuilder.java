/*******************************************************************************
 * Copyright (c) 2020 Konduit K.K.
 * Copyright (c) 2015-2019 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package com.aquila.chess.strategy.mcts.nnImpls.agz;

import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.graph.ElementWiseVertex;
import org.deeplearning4j.nn.conf.graph.ElementWiseVertex.Op;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer.AlgoMode;
import org.deeplearning4j.nn.conf.preprocessor.CnnToFeedForwardPreProcessor;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.HashMap;
import java.util.Map;


/**
 * Provides input blocks for dual residual or convolutional neural networks
 * for Chess move prediction.
 * Modifications for chess: Anthony Bussani
 *
 * @author Max Pumperla
 */
public class DL4JAlphaGoZeroBuilder {

    /**
     * <p>Network Input</p>
     * The input encoding follows the approach taken for AlphaZero.
     * The main difference is that the move count is no longer encoded — it is technically not required since it’s just some superfluous extra-information. We should
     * also mention that Leela Chess Zero is an ongoing project, and naturally improvements and code changes happen. The input format was subject to such changes
     * as well, for example to cope with chess variants such as Chess960 or Armageddon, or simply to experiment with encodings. The encoding described here is
     * the classic encoding, referred to in source code as INPUT_CLASSICAL_112_PLANE.
     * For those who want to look up things in code, the relevant source files are
     * lc0/src/neural/encoder.cc and lc0/src/neural/encoder_test.cc.
     * The input consists of 112 planes of size 8 × 8. Information w.r.t. the placement
     * of pieces is encoded from the perspective of the player whose current turn it
     * is. Assume that we take that player’s perspective. The first plane encodes
     * the position of our own pawns. The second plane encodes the position of our
     * knights, then our bishops, rooks, queens and finally the king. Starting from
     * plane 6 we encode the position of the enemy’s pawns, then knights, bishops,
     * rooks, queens and the enemy’s king. Plane 12 is set to all ones if one or more
     * repetitions occurred.
     * These 12 planes are repeated to encode not only the current position, but also
     * the seven previous ones. Planes 104 to 107 are set to 1 if White can castle
     * queenside, White can castle kingside, Black can castle queenside and Black can
     * 176 4. MODERN AI APPROACHES - A DEEP DIVE
     * castle kingside (in that order). Plane 108 is set to all ones if it is Black’s turn and
     * to 0 otherwise. Plane 109 encodes the number of moves where no capture has
     * been made and no pawn has been moved, i.e. the 50 moves rule. Plane 110 used
     * to be a move counter, but is simply set to always 0 in current generations of Lc0.
     * Last, plane 111 is set to all ones. This is, as previously mentioned, to help the
     * network detect the edge of the board when using convolutional filters.
     */
    // 5: Pawn:0, Bishop:1, Knight:2, Rook:3, Queen:4, King:5
    // for 0..7
    //   [0-5] pieces for White
    //   [6-11] pieces for Black
    //   12: 1 or more repetition ??
    // end for
    // 104: white can castle queenside
    // 105: white can castle kingside
    // 106: black can castle queenside
    // 107: black can castle kingside
    // 108: 0 -> white turn  1 -> white turn
    // 109: repetitions whitout capture and pawn moves (50 moves rules)
    // 110: 0
    // 111: 1 -> edges detection
    // public static final int FEATURES_PLANES = 112;

    public static final int RESIDUAL_FILTERS = 256; // 256

    private final ComputationGraphConfiguration.GraphBuilder conf;
    private final int[] strides;
    private final int[] kernelSize;
    private final ConvolutionMode convolutionMode;

    public DL4JAlphaGoZeroBuilder(final int[] kernel, final int[] strides, final ConvolutionMode mode, int nbFeaturesPLanes) {

        this.kernelSize = kernel;
        this.strides = strides;
        this.convolutionMode = mode;

        this.conf = new NeuralNetConfiguration.Builder()
                .cudnnAlgoMode(ConvolutionLayer.AlgoMode.PREFER_FASTEST)
                .updater(new Sgd())
                .weightInit(WeightInit.LECUN_NORMAL)
                .graphBuilder().setInputTypes(InputType.convolutional(8, 8, nbFeaturesPLanes));
    }

    // public DL4JAlphaZeroBuilder() {
    //    this(new int[]{3, 3}, new int[]{1, 1}, ConvolutionMode.Same);
    //}

    public void addInputs(final String name) {
        conf.addInputs(name);
    }

    public void addOutputs(final String... names) {
        conf.setOutputs(names);
    }

    public ComputationGraphConfiguration buildAndReturn() {
        return conf.build();
    }


    /**
     * Building block for AGZ residual blocks.
     * conv2d -> batch norm -> ReLU
     */
    public String addConvBatchNormBlock(final String blockName, final String inName, final int nIn,
                                        final boolean useActivation) {
        final String convName = "conv_" + blockName;
        final String bnName = "batch_norm_" + blockName;
        final String actName = "relu_" + blockName;

        conf.addLayer(convName, new ConvolutionLayer.Builder().kernelSize(kernelSize)
                .stride(strides).convolutionMode(convolutionMode).cudnnAlgoMode(AlgoMode.PREFER_FASTEST).nIn(nIn).nOut(256).build(), inName);
        conf.addLayer(bnName, new BatchNormalization.Builder().nOut(256).build(), convName);

        if (useActivation) {
            conf.addLayer(actName, new ActivationLayer.Builder().activation(Activation.RELU).build(), bnName);
            return actName;
        } else
            return bnName;
    }

    /**
     * Residual block for AGZ. Takes two conv-bn-relu blocks
     * and adds them to the original input.
     */
    public String addResidualBlock(final int blockNumber, final String inName) {
        final String firstBlock = "residual_1_" + blockNumber;
        final String firstOut = "relu_residual_1_" + blockNumber;
        final String secondBlock = "residual_2_" + blockNumber;
        final String mergeBlock = "add_" + blockNumber;
        final String actBlock = "relu_" + blockNumber;

        final String firstBnOut =
                addConvBatchNormBlock(firstBlock, inName, RESIDUAL_FILTERS, true);
        final String secondBnOut =
                addConvBatchNormBlock(secondBlock, firstOut, RESIDUAL_FILTERS, false);
        conf.addVertex(mergeBlock, new ElementWiseVertex(Op.Add), firstBnOut, secondBnOut);
        conf.addLayer(actBlock, new ActivationLayer.Builder().activation(Activation.RELU).build(), mergeBlock);
        return actBlock;
    }

    /**
     * Building a tower of residual blocks.
     */
    public String addResidualTower(final int numBlocks, final String inName) {
        String name = inName;
        for (int i = 0; i < numBlocks; i++) {
            name = addResidualBlock(i, name);
        }
        return name;
    }

    /**
     * Policy head, predicts next moves, so
     * outputs a vector of 8 * 8 = 64 values.
     */
    public String addPolicyHead(final String inName, final boolean useActivation) {
        final String convName = "policy_head_conv_";
        final String bnName = "policy_head_batch_norm_";
        final String actName = "policy_head_relu_";
        final String denseName = "policy_head_output_";

        conf.addLayer(convName, new ConvolutionLayer.Builder().kernelSize(kernelSize).stride(strides)
                .convolutionMode(convolutionMode).cudnnAlgoMode(AlgoMode.PREFER_FASTEST).nOut(2).nIn(256).build(), inName);
        conf.addLayer(bnName, new BatchNormalization.Builder().nOut(2).build(), convName);
        conf.addLayer(actName, new ActivationLayer.Builder().activation(Activation.RELU).build(), bnName);
        conf.addLayer(denseName, new OutputLayer.Builder().nIn(2 * 8 * 8).nOut(8 * 8 * 73).build(), actName);

        final Map<String, InputPreProcessor> preProcessorMap = new HashMap<String, InputPreProcessor>();
        preProcessorMap.put(denseName, new CnnToFeedForwardPreProcessor(8, 8, 3));
        conf.setInputPreProcessors(preProcessorMap);
        return denseName;
    }

    /**
     * Value head, estimates how valuable the current
     * board position is.
     */
    public String addValueHead(final String inName, final boolean useActivation) {
        final String convName = "value_head_conv_";
        final String bnName = "value_head_batch_norm_";
        final String actName = "value_head_relu_";
        final String denseName = "value_head_dense_";
        final String outputName = "value_head_output_";

        conf.addLayer(convName, new ConvolutionLayer.Builder().kernelSize(kernelSize).stride(strides)
                .convolutionMode(convolutionMode).cudnnAlgoMode(AlgoMode.PREFER_FASTEST).nOut(1).nIn(256).build(), inName);
        conf.addLayer(bnName, new BatchNormalization.Builder().nOut(1).build(), convName);
        conf.addLayer(actName, new ActivationLayer.Builder().activation(Activation.RELU).build(), bnName);
        conf.addLayer(denseName, new DenseLayer.Builder().nIn(8 * 8).nOut(256).build(), actName);
        final Map<String, InputPreProcessor> preProcessorMap = new HashMap<String, InputPreProcessor>();
        preProcessorMap.put(denseName, new CnnToFeedForwardPreProcessor(8, 8, 1));
        conf.setInputPreProcessors(preProcessorMap);
        conf.addLayer(outputName, new OutputLayer.Builder(LossFunctions.LossFunction.XENT).activation(Activation.SIGMOID).nIn(256).nOut(1).build(), denseName);
        return outputName;
    }

}
