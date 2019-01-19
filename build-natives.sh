#!/bin/bash
docker build -t cclj_build -f Dockerfile .

CONTAINER_NAME="cclj_build-$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)"
docker run --name $CONTAINER_NAME cclj_build /root/build.sh

CONTAINER_ID=$(docker ps -aqf "name=$CONTAINER_NAME")

docker cp $CONTAINER_ID:/root/out/cclj.so build/libs/cclj.so
docker cp $CONTAINER_ID:/root/out/cclj.dll build/libs/cclj.dll
docker cp $CONTAINER_ID:/root/out/cclj.dylib build/libs/cclj.dylib

docker cp $CONTAINER_ID:/root/LuaJIT/bin/linux/libluajit-5.1.so.2 build/libs
docker cp $CONTAINER_ID:/root/LuaJIT/bin/windows/lua51.dll build/libs
docker cp $CONTAINER_ID:/root/LuaJIT/bin/osx/libluajit-5.1.2.dylib build/libs

docker container rm $CONTAINER_ID