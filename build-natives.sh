#!/bin/bash
./build-image.sh

CONTAINER_NAME=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)
docker run --name $CONTAINER_NAME cclj_build /root/build.sh

CONTAINER_ID=$(docker ps -aqf "name=$CONTAINER_NAME")

docker cp $CONTAINER_ID:/root/out/cclj.so build/libs/cclj.so
docker cp $CONTAINER_ID:/root/out/cclj.dll build/libs/cclj.dll
docker cp $CONTAINER_ID:/root/out/cclj.dylib build/libs/cclj.dylib

cp LuaJIT/bin/linux/* build/libs
cp LuaJIT/bin/windows/* build/libs
cp LuaJIT/bin/osx/* build/libs

docker container rm $CONTAINER_ID