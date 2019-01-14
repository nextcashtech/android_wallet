/**************************************************************************
 * Copyright 2017-2018 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/

#ifndef NEXTCASH_JNI_HPP
#define NEXTCASH_JNI_HPP

#include <jni.h>


extern "C"
{
    // NextCash
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_NextCash_test(JNIEnv *pEnvironment,
                                                                               jclass pObject);

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_NextCash_destroy(JNIEnv *pEnvironment,
                                                                              jclass pObject);

    // Base
    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_userAgent(JNIEnv *pEnvironment,
                                                                                  jclass pObject);
    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_networkName(JNIEnv *pEnvironment,
                                                                                    jclass pObject);

    // Info
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setup(JNIEnv *pEnvironment,
                                                                           jobject pObject,
                                                                           jstring pPath,
                                                                           jint pChainID);

    // Initialization
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setupJNI(JNIEnv *pEnvironment,
                                                                              jclass pObject);

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

    // Headers are in sync and at least one merkle block has been verified for every header.
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isInRoughSync(JNIEnv *pEnvironment,
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

    // Chain IDs
    //   1 = ABC
    //   2 = SV
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_chainID(JNIEnv *pEnvironment,
                                                                             jobject pObject);
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_activateChain(JNIEnv *pEnvironment,
                                                                                       jobject pObject,
                                                                                       jint pChainID);

    // Status
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_peerCount(JNIEnv *pEnvironment,
                                                                               jobject pObject);
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_resetPeers(JNIEnv *pEnvironment,
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
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_importKeys(JNIEnv *pEnvironment,
                                                                                jobject pObject,
                                                                                jstring pPassCode,
                                                                                jint pType,
                                                                                jobjectArray pKeys,
                                                                                jstring pName,
                                                                                jlong pRecoverTime,
                                                                                jint pGap);

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

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setGap(JNIEnv *pEnvironment,
                                                                                jobject pObject,
                                                                                jint pOffset,
                                                                                jint pGap);

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getGap(JNIEnv *pEnvironment,
                                                                            jobject pObject,
                                                                            jint pOffset);

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_addSeed(JNIEnv *pEnvironment,
                                                                             jobject pObject,
                                                                             jstring pPassCode,
                                                                             jstring pSeed,
                                                                             jint pDerivationMethod,
                                                                             jlongArray pDerivationPath,
                                                                             jlong pReceivingIndex,
                                                                             jlong pChangeIndex,
                                                                             jstring pName,
                                                                             jboolean pIsBackedUp,
                                                                             jlong pRecoverTime,
                                                                             jint pGap);

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_addPrivateKey(JNIEnv *pEnvironment,
                                                                                   jobject pObject,
                                                                                   jstring pPassCode,
                                                                                   jstring pPrivateKey,
                                                                                   jint pDerivationMethod,
                                                                                   jlongArray pDerivationPath,
                                                                                   jlong pReceivingIndex,
                                                                                   jlong pChangeIndex,
                                                                                   jstring pName,
                                                                                   jlong pRecoverTime);

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

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_derivationPathMethod(JNIEnv *pEnvironment,
                                                                                          jobject pObject,
                                                                                          jint pOffset);

    JNIEXPORT jlongArray JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_derivationPath(JNIEnv *pEnvironment,
                                                                                          jobject pObject,
                                                                                          jint pOffset,
                                                                                          jint pChainOffset);

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_seedIsValid(JNIEnv *pEnvironment,
                                                                                     jobject pObject,
                                                                                     jstring pSeed);

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_hasPassCode(JNIEnv *pEnvironment,
                                                                                     jobject pObject);

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getNextReceiveAddress(JNIEnv *pEnvironment,
                                                                                              jobject pObject,
                                                                                              jint pKeyOffset,
                                                                                              jint pType);

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_containsAddress(JNIEnv *pEnvironment,
                                                                                         jobject pObject,
                                                                                         jint pKeyOffset,
                                                                                         jstring pAddress);

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_markAddressUsed(JNIEnv *pEnvironment,
                                                                                         jobject pObject,
                                                                                         jint pKeyOffset,
                                                                                         jstring pAddress);

    JNIEXPORT jbyteArray JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getNextReceiveOutput(JNIEnv *pEnvironment,
                                                                                                jobject pObject,
                                                                                                jint pKeyOffset,
                                                                                                jint pType);

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_encodePaymentCode(JNIEnv *pEnvironment,
                                                                                          jobject pObject,
                                                                                          jstring pAddress,
                                                                                          jint pFormat,
                                                                                          jint pProtocol);

    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_decodePaymentCode(JNIEnv *pEnvironment,
                                                                                          jobject pObject,
                                                                                          jstring pPaymentCode);

    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_decodeKey(JNIEnv *pEnvironment,
                                                                                  jobject pObject,
                                                                                  jstring pEncodedText);

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_publicKey(JNIEnv *pEnvironment,
                                                                                  jobject pObject,
                                                                                  jint pKeyOffset,
                                                                                  jint pType);

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isValidPrivateKey(JNIEnv *pEnvironment,
                                                                                           jobject pObject,
                                                                                           jstring pPrivateKey);

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getTransaction(JNIEnv *pEnvironment,
                                                                                        jobject pObject,
                                                                                        jint pWalletOffset,
                                                                                        jstring pID,
                                                                                        jobject pTransaction);

    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_sendStandardPayment(JNIEnv *pEnvironment,
                                                                                            jobject pObject,
                                                                                            jint pWalletOffset,
                                                                                            jstring pPassCode,
                                                                                            jstring pAddress,
                                                                                            jlong pAmount,
                                                                                            jdouble pFeeRate,
                                                                                            jboolean pUsePending,
                                                                                            jboolean pSendAll);

    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_sendOutputsPayment(JNIEnv *pEnvironment,
                                                                                           jobject pObject,
                                                                                           jint pWalletOffset,
                                                                                           jstring pPassCode,
                                                                                           jobjectArray pOutputs,
                                                                                           jdouble pFeeRate,
                                                                                           jboolean pUsePending,
                                                                                           jboolean pTransmit);

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isValidSeedWord(JNIEnv *pEnvironment,
                                                                                         jobject pObject,
                                                                                         jstring pWord);

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
