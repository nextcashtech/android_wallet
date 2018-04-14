
#ifndef NEXTCASH_JNI_HPP
#define NEXTCASH_JNI_HPP

#include <jni.h>


extern "C"
{
    // NextCash
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_NextCash_destroy(JNIEnv *pEnvironment,
                                                                              jobject pObject);

    // Base
    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_userAgent(JNIEnv *pEnvironment,
                                                                                  jobject pObject);
    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_networkName(JNIEnv *pEnvironment,
                                                                                    jobject pObject);

    // Info
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setPath(JNIEnv *pEnvironment,
                                                                             jobject pObject,
                                                                             jstring pPath);

    // Daemon
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_load(JNIEnv *pEnvironment,
                                                                              jobject pObject);
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isLoaded(JNIEnv *pEnvironment,
                                                                                  jobject pObject);
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isRunning(JNIEnv *pEnvironment,
                                                                                   jobject pObject);

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setFinishMode(JNIEnv *pEnvironment,
                                                                                   jobject pObject,
                                                                                   jint pMode);
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_run(JNIEnv *pEnvironment,
                                                                         jobject pObject,
                                                                         jint pMode);
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_stop(JNIEnv *pEnvironment,
                                                                          jobject pObject);

    // Status
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_peerCount(JNIEnv *pEnvironment,
                                                                               jobject pObject);
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_status(JNIEnv *pEnvironment,
                                                                            jobject pObject);
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_blockHeight(JNIEnv *pEnvironment,
                                                                                 jobject pObject);
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_merkleHeight(JNIEnv *pEnvironment,
                                                                                  jobject pObject);
    JNIEXPORT jlong JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_balance(JNIEnv *pEnvironment,
                                                                              jobject pObject);

    // Keys
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_addKey(JNIEnv *pEnvironment,
                                                                            jobject pObject,
                                                                            jstring pKey,
                                                                            jint pDerivationMethod);

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_keyCount(JNIEnv *pEnvironment,
                                                                              jobject pObject);
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_keyIsPrivate(JNIEnv *pEnvironment,
                                                                                      jobject pObject,
                                                                                      jint pKeyOffset);
    JNIEXPORT jlong JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_keyBalance(JNIEnv *pEnvironment,
                                                                                 jobject pObject,
                                                                                 jint pKeyOffset,
                                                                                 jboolean pIncludePending);
}

#endif
