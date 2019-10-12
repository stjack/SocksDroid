#!/bin/bash

rm -rf assets
rm -rf ../../libs

/home/jack/Android/Sdk/ndk/20.0.5594570/ndk-build -B

for p in armeabi-v7a arm64-v8a; do
	mkdir -p assets/$p
	cp libs/$p/{tun2socks,pdnsd,libsystem.so} assets/$p/
done

rm -rf libs/*/{tun2socks,pdnsd,libsystem.so}
mv libs ../../
