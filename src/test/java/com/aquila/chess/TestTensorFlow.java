package com.aquila.chess;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.tensorflow.ConcreteFunction;
import org.tensorflow.Signature;
import org.tensorflow.Tensor;
import org.tensorflow.TensorFlow;
import org.tensorflow.op.Ops;
import org.tensorflow.op.core.Placeholder;
import org.tensorflow.op.math.Add;
import org.tensorflow.types.TInt32;

@Slf4j
public class TestTensorFlow {

    @Test
    void test() {
        log.info("Hello TensorFlow {}", TensorFlow.version());

        ConcreteFunction concreteFunction = ConcreteFunction.create(TestTensorFlow::dbl);
        TInt32 x = TInt32.scalarOf(10);
        Tensor tensor = concreteFunction.call(x); //expect(TInt32.class)) {
        log.info("{} doubled is {}", x.getInt(), tensor.dataType().getNumber());

    }

    private static Signature dbl(Ops tf) {
        Placeholder<TInt32> x = tf.placeholder(TInt32.class);
        Add<TInt32> dblX = tf.math.add(x, x);
        return Signature.builder().input("x", x).output("dbl", dblX).build();
    }
}
