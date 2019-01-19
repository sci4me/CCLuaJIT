FROM fedora:29

WORKDIR /root

RUN dnf -y install \
    clang \
    findutils \
    gcc-c++ \
    git \
    java-1.8.0-openjdk-devel \
    make \
    mingw64-gcc-c++.x86_64 \
    patch \
    wget \
    which \
    which \
    xz

ENV JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk

RUN git clone https://github.com/tpoechtrager/osxcross && \
    cd osxcross && \
    wget --no-check-certificate -P tarballs https://s3.dockerproject.org/darwin/v2/MacOSX10.11.sdk.tar.xz && \
    sed -i -e 's|-march=native||g' build_clang.sh wrapper/build.sh && \
    UNATTENDED=yes OSX_VERSION_MIN=10.7 ./build.sh

ENV PATH="/root/osxcross/target/bin:${PATH}"

ADD /src/main/cpp /root/
ADD LuaJIT /root/LuaJIT