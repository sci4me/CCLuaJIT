#!/bin/bash
docker build -t cclj_build -f Dockerfile .

CONTAINER_NAME="cclj_build-$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)"
docker run --name "$CONTAINER_NAME" cclj_build /root/build.sh

CONTAINER_ID=$(docker ps -aqf "name=$CONTAINER_NAME")

rm -rf src/main/resources/natives
mkdir src/main/resources/natives

status=0
docker cp "${CONTAINER_ID}":/root/out/cclj.so src/main/resources/natives/cclj.so
if [[ $? -ne 0 ]]; then
    status=1
fi
docker cp "${CONTAINER_ID}":/root/out/cclj.dll src/main/resources/natives/cclj.dll
if [[ $? -ne 0 ]]; then
    status=1
fi
# TODO
# docker cp "${CONTAINER_ID}":/root/out/cclj.dylib src/main/resources/natives/cclj.dylib
# if [[ $? -ne 0 ]]; then
#     status=1
# fi

docker cp "${CONTAINER_ID}":/root/vendor/luajit/bin/linux/libluajit-5.1.so src/main/resources/natives
docker cp "${CONTAINER_ID}":/root/vendor/luajit/bin/windows/lua51.dll src/main/resources/natives
# TODO
# docker cp "${CONTAINER_ID}":/root/vendor/luajit/bin/osx/libluajit-5.1.2.dylib src/main/resources/natives

docker container rm "${CONTAINER_ID}"

if [[ $status -ne 0 ]]; then
    echo "Building natives failed!"
    exit $status
else
    gzip -9 src/main/resources/natives/*
fi