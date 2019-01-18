#!/bin/bash

mkdir out

echo "#"
echo "# Building Linux natives..."
echo "#"

./build-linux.sh

echo ""

echo "#"
echo "# Building Windows natives..."
echo "#"

./build-windows.sh

echo ""

echo "#"
echo "# Building OSX natives..."
echo "#"

./build-osx.sh