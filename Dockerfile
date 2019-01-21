FROM fedora:29

WORKDIR /root

# Install dependencies
RUN dnf -y install \
    clang \
    findutils \
    gcc-c++ \
    git \
    make \
    mingw64-gcc-c++.x86_64 \
    patch \
    wget \
    which \
    xz && \
    dnf clean all && \
    rm -rf /var/cache/yum

# Install osxcross for Mac cross-compilation
RUN git clone https://github.com/tpoechtrager/osxcross && \
    cd osxcross && \
    wget --no-check-certificate -P tarballs https://s3.dockerproject.org/darwin/v2/MacOSX10.11.sdk.tar.xz && \
    sed -i -e 's|-march=native||g' build_clang.sh wrapper/build.sh && \
    UNATTENDED=yes OSX_VERSION_MIN=10.7 ./build.sh && \
    mkdir /root/osxcross-target && \
    cp -r target/* /root/osxcross-target && \
    cd .. && \
    rm -rf osxcross
ENV PATH="/root/osxcross-target/bin:${PATH}"

# Install LuaJIT
RUN git clone https://github.com/LuaJIT/LuaJIT.git && \
    cd LuaJIT && \
    mkdir bin && \
    mkdir bin/linux && \
    mkdir bin/windows && \
    mkdir bin/osx && \
    cd src && \
    make BUILDMODE=dynamic && \
    mv libluajit.so ../bin/linux/libluajit-5.1.so.2 && \
    make clean && \
    mingw64-make HOST_CC="gcc" CROSS="x86_64-w64-mingw32-" TARGET_SYS=Windows BUILDMODE=dynamic && \
    mv lua51.dll ../bin/windows && \
    make clean && \
    make HOST_CC="gcc" CC="cc" CROSS="x86_64-apple-darwin15-" TARGET_SYS=Darwin BUILDMODE=dynamic && \
    mv libluajit.so ../bin/osx/libluajit-5.1.2.dylib && \
    make clean && \
    cd ../..

# Install JNI headers
RUN git clone https://git.sci4me.com/sci4me/jni-headers.git

ADD /src/main/cpp /root/