#!/bin/bash
./build-image.sh
sudo docker run -it -v "$(realpath build/libs)":/root/out cclj_build /root/build.sh