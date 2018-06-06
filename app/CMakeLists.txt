
cmake_minimum_required(VERSION 3.4.1)

# Specify standard library
#add_definitions( "-DANDROID_STL=gnustl" )
add_definitions( "-DANDROID_STL=libc++" )

# Set flags
set( CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -frtti -fexceptions -fstack-protector" )

# Add sanitizer
#set( CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fsanitize=address -fno-omit-frame-pointer" )
#set( CMAKE_EXE_LINKER_FLAGS  "${CMAKE_EXE_LINKER_FLAGS} -fsanitize=address" )

# Set preprocessor values to configure code
add_definitions( "-DANDROID -DLOW_MEM" )

# Disable multi-threading
#add_definitions( "-DSINGLE_THREAD" )


# Setup NextCash Base library
set( nextcash_lib_src_dir ../../../nextcash )
set( nextcash_lib_build_dir ../../../nextcash/build )

add_subdirectory( ${nextcash_lib_src_dir}
                  ${nextcash_lib_build_dir} )


# Setup SECP256K1 library
set( secp256k1_lib_src_dir ../../../secp256k1 )
set( secp256k1_lib_build_dir ../../../secp256k1/build )

add_subdirectory( ${secp256k1_lib_src_dir}
                  ${secp256k1_lib_build_dir} )


# Setup BitCoin library
set( bitcoin_lib_src_dir ../../../bitcoin )
set( bitcoin_lib_build_dir ../../../bitcoin/build )

add_subdirectory( ${bitcoin_lib_src_dir}
                  ${bitcoin_lib_build_dir} )


# Setup JNI code
include_directories( ../../../nextcash
                     ../../../nextcash/src/base
                     ../../../nextcash/src/io
                     ../../../nextcash/src/crypto
                     ../../../nextcash/src/dev
                     ../../../secp256k1/include
                     ../../../bitcoin
                     ../../../bitcoin/src
                     src/main/cpp )

add_library( nextcash_jni STATIC SHARED
             src/main/cpp/nextcash_jni.cpp )

target_link_libraries( nextcash_jni bitcoin nextcash )

set_property( TARGET nextcash_jni PROPERTY CXX_STANDARD 11 )
set_property( TARGET nextcash_jni PROPERTY CXX_STANDARD_REQUIRED ON )
