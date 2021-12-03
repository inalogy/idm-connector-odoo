#!/bin/bash

set -e -x

BASEDIR=$(readlink -f $(dirname $0))

cd "${BASEDIR}/../.."
docker build -f docker/build/Dockerfile -t builder .
mkdir -p build/libs
docker run -it --rm -v $PWD/build/libs:/output  builder
docker image rm builder
