/**************************************************************************
 * Copyright 2018 NextCash, LLC                                            *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.com>                                    *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
#ifndef NEXTCASH_JNI_HPP
#define NEXTCASH_JNI_HPP

#include <jni.h>

extern "C"
{
    // Base
    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_userAgent(JNIEnv *pEnvironment);
    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_networkName(JNIEnv *pEnvironment);

    // Info
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setPath(JNIEnv *pEnvironment,
                                                                             jclass pClass,
                                                                             jstring pPath);
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setIP(JNIEnv *pEnvironment,
                                                                           jclass pClass,
                                                                           jbyteArray pIP);

    // Daemon
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_load(JNIEnv *pEnvironment);
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isLoaded(JNIEnv *pEnvironment);

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_run(JNIEnv *pEnvironment,
                                                                         jclass pClass,
                                                                         jint pMode);
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_stop(JNIEnv *pEnvironment);
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setFinishMode(JNIEnv *pEnvironment,
                                                                                   jclass pClass,
                                                                                   jint pMode);

    // Status
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_peerCount(JNIEnv *pEnvironment);
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_status(JNIEnv *pEnvironment);
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_blockHeight(JNIEnv *pEnvironment);
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_merkleHeight(JNIEnv *pEnvironment);
    JNIEXPORT jlong JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_balance(JNIEnv *pEnvironment);
}

#endif
