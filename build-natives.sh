#!/bin/bash
./build-image.sh
docker run -v "$(realpath build/libs)":/root/out cclj_build /root/build.sh

cp LuaJIT/bin/linux/* build/libs
cp LuaJIT/bin/windows/* build/libs
cp LuaJIT/bin/osx/* build/libs