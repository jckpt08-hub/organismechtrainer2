#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
rm -rf build
mkdir build
javac --release 17 -encoding UTF-8 -d build OrganicMechanismTrainer.java
jar --create --file OrganicMechanismTrainer.jar --main-class OrganicMechanismTrainer -C build .
java -jar OrganicMechanismTrainer.jar --self-test
echo "Build completed: OrganicMechanismTrainer.jar"
