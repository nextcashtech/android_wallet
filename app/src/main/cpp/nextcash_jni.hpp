
#ifndef NEXTCASH_JNI_HPP
#define NEXTCASH_JNI_HPP

#include <jni.h>


extern "C"
{
    // NextCash
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_NextCash_test(JNIEnv *pEnvironment,
                                                                               jobject pObject);

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

    // Initialization
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setupJNI(JNIEnv *pEnvironment,
                                                                              jobject pObject);
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Wallet_setupJNI(JNIEnv *pEnvironment,
                                                                             jobject pObject);
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Transaction_setupJNI(JNIEnv *pEnvironment,
                                                                                  jobject pObject);

    // Daemon
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_loadWallets(JNIEnv *pEnvironment,
                                                                                     jobject pObject);
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_loadChain(JNIEnv *pEnvironment,
                                                                                   jobject pObject);
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isRunning(JNIEnv *pEnvironment,
                                                                                   jobject pObject);
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isStopping(JNIEnv *pEnvironment,
                                                                                    jobject pObject);
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_initialBlockDownloadIsComplete(JNIEnv *pEnvironment,
                                                                                                        jobject pObject);
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isInSync(JNIEnv *pEnvironment,
                                                                                  jobject pObject);
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_wasInSync(JNIEnv *pEnvironment,
                                                                                   jobject pObject);

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_finishMode(JNIEnv *pEnvironment,
                                                                                jobject pObject);
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setFinishMode(JNIEnv *pEnvironment,
                                                                                   jobject pObject,
                                                                                   jint pMode);
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setFinishTime(JNIEnv *pEnvironment,
                                                                                   jobject pObject,
                                                                                   jint pSecondsFromNow);
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_clearFinishTime(JNIEnv *pEnvironment,
                                                                                     jobject pObject);
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_run(JNIEnv *pEnvironment,
                                                                         jobject pObject);
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_stop(JNIEnv *pEnvironment,
                                                                              jobject pObject);

    // Status
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_peerCount(JNIEnv *pEnvironment,
                                                                               jobject pObject);
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_status(JNIEnv *pEnvironment,
                                                                            jobject pObject);
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_headerHeight(JNIEnv *pEnvironment,
                                                                                  jobject pObject);
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_merkleHeight(JNIEnv *pEnvironment,
                                                                                  jobject pObject);

    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getBlockFromHeight(JNIEnv *pEnvironment,
                                                                                           jobject pObject,
                                                                                           jint pHeight);
    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getBlockFromHash(JNIEnv *pEnvironment,
                                                                                         jobject pObject,
                                                                                         jstring pHash);

    // Keys
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_loadKey(JNIEnv *pEnvironment,
                                                                             jobject pObject,
                                                                             jstring pPassCode,
                                                                             jstring pKey,
                                                                             jint pDerivationMethod,
                                                                             jstring pName);

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_keyCount(JNIEnv *pEnvironment,
                                                                              jobject pObject);


    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getChangeID(JNIEnv *pEnvironment,
                                                                                 jobject pObject);

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_updateWallet(JNIEnv *pEnvironment,
                                                                                      jobject pBitcoin,
                                                                                      jobject pWallet,
                                                                                      jint pOffset);

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setName(JNIEnv *pEnvironment,
                                                                                 jobject pObject,
                                                                                 jint pOffset,
                                                                                 jstring pName);

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setIsBackedUp(JNIEnv *pEnvironment,
                                                                                       jobject pObject,
                                                                                       jint pOffset);

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_addSeed(JNIEnv *pEnvironment,
                                                                             jobject pObject,
                                                                             jstring pPassCode,
                                                                             jstring pSeed,
                                                                             jint pDerivationMethod,
                                                                             jstring pName,
                                                                             jboolean pStartNewPass,
                                                                             jboolean pIsBackedUp);

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_removeKey(JNIEnv *pEnvironment,
                                                                               jobject pObject,
                                                                               jstring pPassCode,
                                                                               jint pOffset);

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_generateMnemonicSeed(JNIEnv *pEnvironment,
                                                                                             jobject pObject,
                                                                                             jint pEntropy);

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_seed(JNIEnv *pEnvironment,
                                                                             jobject pObject,
                                                                             jstring pPassCode,
                                                                             jint pOffset);

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_seedIsValid(JNIEnv *pEnvironment,
                                                                                     jobject pObject,
                                                                                     jstring pSeed);

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_hasPassCode(JNIEnv *pEnvironment,
                                                                                     jobject pObject);

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getNextReceiveAddress(JNIEnv *pEnvironment,
                                                                                              jobject pObject,
                                                                                              jint pKeyOffset,
                                                                                              jint pIndex);

    JNIEXPORT jbyteArray JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getNextReceiveOutput(JNIEnv *pEnvironment,
                                                                                                jobject pObject,
                                                                                                jint pKeyOffset,
                                                                                                jint pIndex);

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_encodePaymentCode(JNIEnv *pEnvironment,
                                                                                          jobject pObject,
                                                                                          jstring pAddress,
                                                                                          jint pFormat,
                                                                                          jint pProtocol);

    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_decodePaymentCode(JNIEnv *pEnvironment,
                                                                                          jobject pObject,
                                                                                          jstring pPaymentCode);

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getTransaction(JNIEnv *pEnvironment,
                                                                                        jobject pObject,
                                                                                        jint pWalletOffset,
                                                                                        jstring pID,
                                                                                        jobject pTransaction);

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_sendP2PKHPayment(JNIEnv *pEnvironment,
                                                                                      jobject pObject,
                                                                                      jint pWalletOffset,
                                                                                      jstring pPassCode,
                                                                                      jstring pAddress,
                                                                                      jlong pAmount,
                                                                                      jdouble pFeeRate,
                                                                                      jboolean pUsePending,
                                                                                      jboolean pSendAll);

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_sendOutputPayment(JNIEnv *pEnvironment,
                                                                                       jobject pObject,
                                                                                       jint pWalletOffset,
                                                                                       jstring pPassCode,
                                                                                       jbyteArray pOutputScript,
                                                                                       jlong pAmount,
                                                                                       jdouble pFeeRate,
                                                                                       jboolean pUsePending);

    JNIEXPORT jbyteArray JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getRawTransaction(JNIEnv *pEnvironment,
                                                                                             jobject pObject,
                                                                                             jbyteArray pPayingOutputScript,
                                                                                             jlong pAmount);

    JNIEXPORT jobjectArray JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getMnemonicWords(JNIEnv *pEnvironment,
                                                                                              jobject pObject,
                                                                                              jstring pStartingWith);

    JNIEXPORT jobjectArray JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getUnspentOutputs(JNIEnv *pEnvironment,
                                                                                               jobject pObject,
                                                                                               jint pWalletOffset);

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_test(JNIEnv *pEnvironment,
                                                                              jobject pObject);
}

#endif
