package com.aquila.chess.strategy.mcts.nnImpls;

import com.aquila.chess.strategy.mcts.*;
import org.deeplearning4j.nn.api.NeuralNetwork;
import org.deeplearning4j.nn.conf.CacheMode;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.optimize.listeners.PerformanceListener;
import org.nd4j.jita.allocator.enums.AllocationStatus;
import org.nd4j.jita.conf.Configuration;
import org.nd4j.jita.conf.CudaEnvironment;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NNDeep4j implements INN {

    static private final Logger logger = LoggerFactory.getLogger(NNDeep4j.class);
    private final String filename;
    public final int NUM_RESIDUAL_BLOCKS = 20;
    public final int NUM_FEATURE_PLANES = DL4JAlphaGoZeroBuilder.FEATURES_PLANES;
    private UpdateLr updateLr;

    private ComputationGraph network;

    public NNDeep4j(final String filename, final boolean loadUpdater) {
        Nd4j.setDefaultDataTypes(DataType.FLOAT16, DataType.FLOAT16);
        Nd4j.getMemoryManager().togglePeriodicGc(true);
        Nd4j.getMemoryManager().setAutoGcWindow(5000);
        CudaEnvironment.getInstance().getConfiguration()
                // key option enabled
                .allowMultiGPU(false) //
                .setFirstMemory(AllocationStatus.DEVICE)
                .setAllocationModel(Configuration.AllocationModel.DIRECT)
                .setMaximumDeviceMemoryUsed(0.90) //
                .setMemoryModel(Configuration.MemoryModel.IMMEDIATE) //
                // cross-device access is used for faster model averaging over pcie
                .allowCrossDeviceAccess(true) //
                .setNumberOfGcThreads(4)
                // .setMaximumBlockSize(-1)
                .setMaximumGridSize(256)
                .setMaximumDeviceCacheableLength(8L * 1024 * 1024 * 1024L)  // (6L * 1024 * 1024 * 1024L) //
                .setMaximumDeviceCache(8L * 1024 * 1024 * 1024L) //
                .setMaximumHostCacheableLength(-1) // (6L * 1024 * 1024 * 1024L) //
                .setMaximumHostCache(8L * 1024 * 1024 * 1024L)
                .setNoGcWindowMs(100)
                .enableDebug(false)
                .setVerbose(false);
        logger.info("getMaximumDeviceCache: {}", CudaEnvironment.getInstance().getConfiguration().getMaximumDeviceCache());
        this.filename = filename;
        try {
            network = load(loadUpdater);
        } catch (final IOException e) {
            logger.error(String.format("Exception when trying to load [%s], creating a default", filename), e);
        }
        if (network == null) {
            network = DualResnetModel.getModel(NUM_RESIDUAL_BLOCKS, NUM_FEATURE_PLANES);
        }
        network.setListeners(new PerformanceListener(1));
        network.setCacheMode(CacheMode.DEVICE);
        network.getConfiguration().setTrainingWorkspaceMode(WorkspaceMode.NONE);
    }

    public void train(boolean train) {
        network.getConfiguration().setTrainingWorkspaceMode(train ? WorkspaceMode.ENABLED : WorkspaceMode.NONE);
    }

    @Override
    public void reset() {

    }

    @Override
    public double getScore() {
        return network.score();
    }

    @Override
    public void setUpdateLr(UpdateLr updateLr, int nbGames) {
        this.updateLr = updateLr;
        this.updateLr(nbGames);
    }

    @Override
    public void updateLr(int nbGames) {
        double lr = updateLr.update(nbGames);
        logger.info("[{}] Setting learning rate: {}", nbGames, lr);
        this.setLR(lr);
        logger.info("[{}] Getting learning rate: {}", nbGames, this.getLR());
    }

    private ComputationGraph load(final boolean loadUpdater) throws IOException {
        final File file = new File(filename);
        if (!file.canRead()) return null;
        final ComputationGraph ret = ComputationGraph.load(file, loadUpdater);
        logger.info("LOADED ComputationGraph");
        return ret;
    }

    @Override
    public void save() throws IOException {
        final File file = new File(filename);
        this.network.save(file);
    }

    @Override
    public void close() {
        network.close();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Score: ");
        sb.append(network.score());
        return sb.toString();
    }

    @Override
    public void fit(final double[][][][] inputs, final double[][] policies, final double[][] values) {
        if (this.network.score() == Double.NaN) throw new RuntimeException("network broken !!");
        INDArray[] inputsArray = new INDArray[]{Nd4j.create(inputs)};
        INDArray[] labelsArray = new INDArray[]{Nd4j.create(policies), Nd4j.create(values)};
        network.fit(inputsArray, labelsArray);
    }

    /**
     * @return the filename
     */
    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public double getLR() {
        return this.network.getLearningRate("policy_head_conv_");
    }

    @Override
    public void setLR(double lr) {
        this.network.setLearningRate(lr);
    }

    @Override
    public List<OutputNN> outputs(double[][][][] nbIn, final int len) {
        System.gc();
        List<OutputNN> ret = new ArrayList<>();
        INDArray[] outputs = output(nbIn);
        System.out.printf("%%");
        for (int i = 0; i < len; i++) {
            double value = outputs[1].getColumn(0).getDouble(i);
            double[] policies = outputs[0].getRow(i).toDoubleVector();
            ret.add(new OutputNN(value, policies));
        }
        return ret;
    }

    @Override
    public NeuralNetwork getNetwork() {
        return this.network;
    }


    private INDArray[] output(double[][][][] nbIn) {
        INDArray inputsArray = Nd4j.create(nbIn);
        INDArray[] ret = network.output(inputsArray);
        // System.gc();
        return ret;
    }
}
