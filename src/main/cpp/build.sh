#!/bin/bash

mkdir -p out

echo "# Building Linux natives..."
./build-linux.sh

echo "# Building Windows natives..."
./build-windows.sh

echo "# Building OSX natives..."
./build-osx.sh