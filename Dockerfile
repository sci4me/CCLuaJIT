FROM blitznote/debase:16.04

RUN apt-get update && apt-get -y install \
    g++ \
    g++-mingw-w64-x86-64 \
    openjdk-8-jdk

ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

ADD /src/main/cpp /root/
ADD LuaJIT /root/LuaJIT

WORKDIR /root