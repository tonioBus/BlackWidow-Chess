# BlackWidow-Chess
Author: Anthony Bussani (https://www.linkedin.com/in/anthony-bussani-a4a06924/)

Implementation of an alpha zero AI: double heads neuronal network (policy and values) used to give directions to a MCTS engine.
This implementation use deep4Learning (https://github.com/eclipse/deeplearning4j) and Cuda / Cudnn as backend provider.
The learning hosts used:
*) a PC with a RTX-2080 TI: ~40 MCTS search calls / seconds
*) a PC with a RTX-4090, ~70 MCTS search calls / seconds
*) a ROG PC with a mobile RTX-3080, ~30 MCTS search calls / seconds
Neuronal Networks codes inherited from an implementation from Max Pumperla using 20 ResNet blocks.
I used a different approach for the input of the NN, instead of using the last 8 encoded moves, I tried to encode what a human player chess is looking for 
when playing: 
*) the current position and add more planes
*) the whole possible moves
*) the possible moves of the opponent King
*) our possible captures positions
*) opponent capture positions 
and some repeat position counters.
The usage of only 1 input increase the use of cache for value and policies retrieve from NN.
It also allow to train or play on the middle of a game without starting with a not filled inputs.
