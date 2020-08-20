#!/bin/bash

mkdir -p out

echo "# Building Linux natives..."
make TARGET_SYS=Linux

echo "# Building Windows natives..."
make TARGET_SYS=Windows

# TODO
# echo "# Building OSX natives..."
# make TARGET_SYS=OSX