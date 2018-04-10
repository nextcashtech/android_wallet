
cmake_minimum_required(VERSION 3.4.1)

add_definitions( -DANDROID -DLOW_MEM )


# Setup NextCash Base Library
set( nextcash_lib_src_dir ../../../nextcash )
set( nextcash_lib_build_dir ../../../nextcash/build )

add_subdirectory( ${nextcash_lib_src_dir}
                  ${nextcash_lib_build_dir} )


# Setup SECP256K1 Library
set( secp256k1_lib_src_dir ../../../secp256k1 )
set( secp256k1_lib_build_dir ../../../secp256k1/build )

add_subdirectory( ${secp256k1_lib_src_dir}
                  ${secp256k1_lib_build_dir} )


# Setup BitCoin Library
set( bitcoin_lib_src_dir ../../../bitcoin )
set( bitcoin_lib_build_dir ../../../bitcoin/build )

add_subdirectory( ${bitcoin_lib_src_dir}
                  ${bitcoin_lib_build_dir} )


# Setup JNI Code
include_directories( ../../../nextcash/src/base
                     ../../../nextcash/src/io
                     ../../../nextcash/src/crypto
                     ../../../nextcash/src/dev
                     ../../../secp256k1/include
                     ../../../bitcoin/src
                     src/main/cpp )

add_library( nextcash_jni STATIC SHARED
             src/main/cpp/nextcash_jni.cpp )

target_link_libraries( nextcash_jni bitcoin nextcash )
