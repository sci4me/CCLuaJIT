FROM blitznote/debase:16.04

WORKDIR /root

RUN apt-get update && apt-get -y install \
    clang \
    g++ \
    g++-mingw-w64-x86-64 \
    git \
    make \
    openjdk-8-jdk \
    wget

ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

RUN git clone https://github.com/tpoechtrager/osxcross && \
    cd osxcross && \
    wget --no-check-certificate -P tarballs https://s3.dockerproject.org/darwin/v2/MacOSX10.11.sdk.tar.xz && \
    sed -i -e 's|-march=native||g' build_clang.sh wrapper/build.sh && \
    UNATTENDED=yes OSX_VERSION_MIN=10.7 ./build.sh && \
    mkdir -p /usr/local/osx-ndk-x86 && \
    mv target/* /usr/local/osx-ndk-x86 && \
    cd .. && \
    rm -rf osxcross

ENV PATH="/usr/local/osx-ndk-x86/bin:${PATH}"

ADD /src/main/cpp /root/
ADD LuaJIT /root/LuaJIT