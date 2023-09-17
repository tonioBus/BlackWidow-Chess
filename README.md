# BlackWidow-Chess
Chess

Implementation of an alpha zero AI: double heads neuronal network (policy and values) used to give directions to a MCTS engine.
This implementation use deep4Learning (https://github.com/eclipse/deeplearning4j) and Cuda / Cudnn as backend provider.
The learning host is currently a PC with a RTX-2080 TI, learning rate ~20 games per day with 8 seconds of MCTS search per step.
Neuronal Networks codes inherited from an implementation from Max Pumperla.
