#!/usr/bin/env bash

export MAVEN_OPTS="-Xms10G -Xmx15G -XX:+UseG1GC --enable-preview"
export GIT_DIR="/c/Users/bussa/git"

log() {
  cd ${GIT_DIR}/BlackWindow-Chess/
  tail -n 1000 -f ./nohup.out
}

goMainAGZ() {
  cd ${GIT_DIR}/BlackWindow-Chess/
  nohup mvn compile -U exec:java -Dexec.mainClass=com.aquila.chess.MainTrainingAGZ &
}
