FROM ubuntu:18.04

RUN apt-get update && apt-get -y install mingw-w64 && apt-get -y install openjdk-8-jdk && apt-get install -y g++

ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

ADD /src/main/cpp /root/
ADD LuaJIT /root/LuaJIT

WORKDIR /root