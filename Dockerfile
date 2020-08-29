FROM fedora:30

WORKDIR /root

# Install dependencies
RUN dnf -y install \
    clang \
    findutils \
    git \
    make \
    cmake \
    mingw64-gcc-c++.x86_64 \
    patch \
    which \
    libxml2-devel \
    #libssl \
    openssl \
    openssl-devel \
    xz && \
    dnf clean all && \
    rm -rf /var/cache/yum


# Install osxcross for Mac cross-compilation
RUN git clone https://github.com/tpoechtrager/osxcross && \
    cd osxcross && \
    #curl -o tarballs/MacOSX10.11.sdk.tar.xz https://s3.dockerproject.org/darwin/v2/MacOSX10.11.sdk.tar.xz && \
    curl -LJ -o tarballs/MacOSX10.13.sdk.tar.xz https://github.com/phracker/MacOSX-SDKs/releases/download/10.13/MacOSX10.13.sdk.tar.xz && \
    #sed -i -e 's|-march=native||g' build_clang.sh wrapper/build.sh && \
    UNATTENDED=yes OSX_VERSION_MIN=10.7 ./build.sh && \
    mkdir /root/osxcross-target && \
    cp -r target/* /root/osxcross-target && \
    cd .. && \
    rm -rf osxcross
ENV PATH="/root/osxcross-target/bin:${PATH}"


ADD vendor /root/vendor


# Install LuaJIT
RUN cd vendor/luajit && \
    mkdir bin && \
    mkdir bin/linux && \
    mkdir bin/windows && \
    mkdir bin/osx && \
    cd src && \
    echo "Building LuaJIT for Linux..." && \
    make BUILDMODE=dynamic && \
    mv libluajit.so ../bin/linux/libluajit-5.1.so && \
    make clean && \
    echo "Building LuaJIT for Windows..." && \
    mingw64-make HOST_CC="gcc" CROSS="x86_64-w64-mingw32-" TARGET_SYS=Windows BUILDMODE=dynamic && \
    mv lua51.dll ../bin/windows && \
    # TODO
    # make clean && \
    # echo "Building LuaJIT for OSX..." && \
    # make HOST_CC="gcc" CC="cc" CROSS="x86_64-apple-darwin15-" TARGET_SYS=Darwin BUILDMODE=dynamic MACOSX_DEPLOYMENT_TARGET=10.11 && \
    # mv libluajit.so ../bin/osx/libluajit-5.1.dylib && \
    cd ../../..


ADD /src/main/cpp /root/
