/**************************************************************************
 * Copyright 2017-2018 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/

#include "nextcash_jni.hpp"

#include "base.hpp"
#include "interpreter.hpp"
#include "mnemonics.hpp"
#include "daemon.hpp"
#include "nextcash_test.hpp"
#include "bitcoin_test.hpp"

#include <algorithm>
#include <vector>

#define NEXTCASH_JNI_LOG_NAME "JNI"


extern "C"
{
    // Static Class/Field IDs
    // Bitcoin
    jclass sBitcoinClass = NULL;
    jfieldID sBitcoinHandleID = NULL;
    jfieldID sBitcoinWalletsID = NULL;
    jfieldID sBitcoinWalletsLoadedID = NULL;
    jfieldID sBitcoinChainLoadedID = NULL;

    // Wallet
    jclass sWalletClass = NULL;
    jfieldID sWalletLastUpdatedID = NULL;
    jfieldID sWalletTransactionsID = NULL;
    jfieldID sWalletUpdatedTransactionsID = NULL;
    jfieldID sWalletIsPrivateID = NULL;
    jfieldID sWalletNameID = NULL;
    jfieldID sWalletIsSynchronizedID = NULL;
    jfieldID sWalletIsBackedUpID = NULL;
    jfieldID sWalletBalanceID = NULL;
    jfieldID sWalletPendingBalanceID = NULL;
    jfieldID sWalletBlockHeightID = NULL;

    // Transaction
    jclass sTransactionClass = NULL;
    jmethodID sTransactionConstructor = NULL;
    jfieldID sTransactionHashID = NULL;
    jfieldID sTransactionBlockID = NULL;
    jfieldID sTransactionDateID = NULL;
    jfieldID sTransactionAmountID = NULL;
    jfieldID sTransactionCountID = NULL;

    // Block
    jclass sBlockClass = NULL;
    jmethodID sBlockConstructor = NULL;
    jfieldID sBlockHeightID = NULL;
    jfieldID sBlockHashID = NULL;
    jfieldID sBlockTimeID = NULL;

    // PaymentRequest
    jclass sPaymentRequestClass = NULL;
    jmethodID sPaymentRequestConstructor = NULL;
    jfieldID sPaymentRequestURIID = NULL;
    jfieldID sPaymentRequestFormatID = NULL;
    jfieldID sPaymentRequestTypeID = NULL;
    jfieldID sPaymentRequestAddressID = NULL;
    jfieldID sPaymentRequestAmountID = NULL;
    jfieldID sPaymentRequestAmountSpecifiedID = NULL;
    jfieldID sPaymentRequestLabelID = NULL;
    jfieldID sPaymentRequestMessageID = NULL;
    jfieldID sPaymentRequestSecureID = NULL;
    jfieldID sPaymentRequestSecureURLID = NULL;

    // Input
    jclass sInputClass = NULL;
    jmethodID sInputConstructor = NULL;
    jfieldID sInputOutpointID = NULL;
    jfieldID sInputOutpointIndexID = NULL;
    jfieldID sInputScriptID = NULL;
    jfieldID sInputSequenceID = NULL;
    jfieldID sInputAddressID = NULL;
    jfieldID sInputAmountID = NULL;

    // Output
    jclass sOutputClass = NULL;
    jmethodID sOutputConstructor = NULL;
    jfieldID sOutputAmountID = NULL;
    jfieldID sOutputScriptID = NULL;
    jfieldID sOutputAddressID = NULL;
    jfieldID sOutputRelatedID = NULL;

    // Full Transaction
    jclass sFullTransactionClass = NULL;
    jmethodID sFullTransactionConstructor = NULL;
    jfieldID sFullTransactionHashID = NULL;
    jfieldID sFullTransactionBlockID = NULL;
    jfieldID sFullTransactionCountID = NULL;
    jfieldID sFullTransactionVersionID = NULL;
    jfieldID sFullTransactionInputsID = NULL;
    jfieldID sFullTransactionOutputsID = NULL;
    jfieldID sFullTransactionLockTimeID = NULL;
    jfieldID sFullTransactionDateID = NULL;
    jfieldID sFullTransactionSizeID = NULL;

    // Outpoint
    jclass sOutpointClass = NULL;
    jmethodID sOutpointConstructor = NULL;
    jfieldID sOutpointTransactionID = NULL;
    jfieldID sOutpointIndexID = NULL;
    jfieldID sOutpointOutputID = NULL;
    jfieldID sOutpointConfirmationsID = NULL;


    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_NextCash_test(JNIEnv *pEnvironment,
                                                                               jclass pObject)
    {
        return (jboolean)NextCash::test();
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_NextCash_destroy(JNIEnv *pEnvironment,
                                                                              jclass pObject)
    {
        NextCash::Log::destroy();
    }

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_userAgent(JNIEnv *pEnvironment,
                                                                                  jclass pObject)
    {
        return pEnvironment->NewStringUTF(BITCOIN_USER_AGENT);
    }

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_networkName(JNIEnv *pEnvironment,
                                                                                    jclass pObject)
    {
        switch(BitCoin::network())
        {
            case BitCoin::MAINNET:
                return pEnvironment->NewStringUTF("MAINNET");
            case BitCoin::TESTNET:
                return pEnvironment->NewStringUTF("TESTNET");
            default:
                return pEnvironment->NewStringUTF("UNKNOWN");
        }
    }

    void setupTransactionClass(JNIEnv *pEnvironment)
    {
        sTransactionClass = pEnvironment->FindClass("tech/nextcash/nextcashwallet/Transaction");
        sTransactionConstructor = pEnvironment->GetMethodID(sTransactionClass, "<init>", "()V");
        sTransactionHashID = pEnvironment->GetFieldID(sTransactionClass, "hash",
          "Ljava/lang/String;");
        sTransactionBlockID = pEnvironment->GetFieldID(sTransactionClass, "block",
          "Ljava/lang/String;");
        sTransactionDateID = pEnvironment->GetFieldID(sTransactionClass, "date", "J");
        sTransactionAmountID = pEnvironment->GetFieldID(sTransactionClass, "amount", "J");
        sTransactionCountID = pEnvironment->GetFieldID(sTransactionClass, "count", "I");
    }

    void setupBlockClass(JNIEnv *pEnvironment)
    {
        sBlockClass = pEnvironment->FindClass("tech/nextcash/nextcashwallet/Block");
        sBlockConstructor = pEnvironment->GetMethodID(sBlockClass, "<init>", "()V");
        sBlockHeightID = pEnvironment->GetFieldID(sBlockClass, "height", "I");
        sBlockHashID = pEnvironment->GetFieldID(sBlockClass, "hash", "Ljava/lang/String;");
        sBlockTimeID = pEnvironment->GetFieldID(sBlockClass, "time", "J");
    }

    void setupPaymentRequestClass(JNIEnv *pEnvironment)
    {
        sPaymentRequestClass =
          pEnvironment->FindClass("tech/nextcash/nextcashwallet/PaymentRequest");
        sPaymentRequestConstructor = pEnvironment->GetMethodID(sPaymentRequestClass, "<init>",
          "()V");
        sPaymentRequestURIID = pEnvironment->GetFieldID(sPaymentRequestClass, "uri",
          "Ljava/lang/String;");
        sPaymentRequestFormatID = pEnvironment->GetFieldID(sPaymentRequestClass, "format", "I");
        sPaymentRequestTypeID = pEnvironment->GetFieldID(sPaymentRequestClass, "type", "I");
        sPaymentRequestAddressID = pEnvironment->GetFieldID(sPaymentRequestClass, "address",
          "Ljava/lang/String;");
        sPaymentRequestAmountID = pEnvironment->GetFieldID(sPaymentRequestClass, "amount", "J");
        sPaymentRequestAmountSpecifiedID = pEnvironment->GetFieldID(sPaymentRequestClass,
          "amountSpecified", "Z");
        sPaymentRequestLabelID = pEnvironment->GetFieldID(sPaymentRequestClass, "label",
          "Ljava/lang/String;");
        sPaymentRequestMessageID = pEnvironment->GetFieldID(sPaymentRequestClass, "message",
          "Ljava/lang/String;");
        sPaymentRequestSecureID = pEnvironment->GetFieldID(sPaymentRequestClass, "secure", "Z");
        sPaymentRequestSecureURLID = pEnvironment->GetFieldID(sPaymentRequestClass, "secureURL",
          "Ljava/lang/String;");
    }

    void setupFullTransactionClass(JNIEnv *pEnvironment)
    {
        // Input
        sInputClass = pEnvironment->FindClass("tech/nextcash/nextcashwallet/Input");
        sInputConstructor = pEnvironment->GetMethodID(sInputClass, "<init>", "()V");
        sInputOutpointID = pEnvironment->GetFieldID(sInputClass, "outpointID",
          "Ljava/lang/String;");
        sInputOutpointIndexID = pEnvironment->GetFieldID(sInputClass, "outpointIndex", "I");
        sInputScriptID = pEnvironment->GetFieldID(sInputClass, "script", "Ljava/lang/String;");
        sInputSequenceID = pEnvironment->GetFieldID(sInputClass, "sequence", "I");
        sInputAddressID = pEnvironment->GetFieldID(sInputClass, "address",
          "Ljava/lang/String;");
        sInputAmountID = pEnvironment->GetFieldID(sInputClass, "amount", "J");

        // Output
        sOutputClass = pEnvironment->FindClass("tech/nextcash/nextcashwallet/Output");
        sOutputConstructor = pEnvironment->GetMethodID(sOutputClass, "<init>", "()V");
        sOutputAmountID = pEnvironment->GetFieldID(sOutputClass, "amount", "J");
        sOutputScriptID = pEnvironment->GetFieldID(sOutputClass, "script", "Ljava/lang/String;");
        sOutputAddressID = pEnvironment->GetFieldID(sOutputClass, "address", "Ljava/lang/String;");
        sOutputRelatedID = pEnvironment->GetFieldID(sOutputClass, "related", "Z");

        // Full Transaction
        sFullTransactionClass =
          pEnvironment->FindClass("tech/nextcash/nextcashwallet/FullTransaction");
        sFullTransactionConstructor = pEnvironment->GetMethodID(sFullTransactionClass, "<init>", "()V");
        sFullTransactionHashID = pEnvironment->GetFieldID(sFullTransactionClass, "hash",
          "Ljava/lang/String;");
        sFullTransactionBlockID = pEnvironment->GetFieldID(sFullTransactionClass, "block",
          "Ljava/lang/String;");
        sFullTransactionCountID = pEnvironment->GetFieldID(sFullTransactionClass, "count", "I");
        sFullTransactionVersionID = pEnvironment->GetFieldID(sFullTransactionClass, "version", "I");
        sFullTransactionInputsID = pEnvironment->GetFieldID(sFullTransactionClass, "inputs",
          "[Ltech/nextcash/nextcashwallet/Input;");
        sFullTransactionOutputsID = pEnvironment->GetFieldID(sFullTransactionClass, "outputs",
          "[Ltech/nextcash/nextcashwallet/Output;");
        sFullTransactionLockTimeID = pEnvironment->GetFieldID(sFullTransactionClass, "lockTime",
          "I");
        sFullTransactionDateID = pEnvironment->GetFieldID(sFullTransactionClass, "date",
          "J");
        sFullTransactionSizeID = pEnvironment->GetFieldID(sFullTransactionClass, "size",
          "I");
    }

    void setupOutputClass(JNIEnv *pEnvironment)
    {
        sOutputClass = pEnvironment->FindClass("tech/nextcash/nextcashwallet/Output");
        sOutputConstructor = pEnvironment->GetMethodID(sOutputClass, "<init>", "()V");
        sOutputAmountID = pEnvironment->GetFieldID(sOutputClass, "amount", "J");
        sOutputScriptID = pEnvironment->GetFieldID(sOutputClass, "script", "Ljava/lang/String;");
        sOutputAddressID = pEnvironment->GetFieldID(sOutputClass, "address", "Ljava/lang/String;");
        sOutputRelatedID = pEnvironment->GetFieldID(sOutputClass, "related", "Z");
    }

    void setupOutpointClass(JNIEnv *pEnvironment)
    {
        sOutpointClass = pEnvironment->FindClass("tech/nextcash/nextcashwallet/Outpoint");
        sOutpointConstructor = pEnvironment->GetMethodID(sOutpointClass, "<init>", "()V");
        sOutpointTransactionID = pEnvironment->GetFieldID(sOutpointClass, "transactionID",
          "Ljava/lang/String;");
        sOutpointIndexID = pEnvironment->GetFieldID(sOutpointClass, "index", "I");
        sOutpointOutputID = pEnvironment->GetFieldID(sOutpointClass, "output",
          "Ltech/nextcash/nextcashwallet/Output;");
        sOutpointConfirmationsID = pEnvironment->GetFieldID(sOutpointClass, "confirmations", "I");
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setupJNI(JNIEnv *pEnvironment,
                                                                              jclass pObject)
    {
        // Bitcoin
        sBitcoinClass = pEnvironment->FindClass("tech/nextcash/nextcashwallet/Bitcoin");
        sBitcoinHandleID = pEnvironment->GetFieldID(sBitcoinClass, "mHandle", "J");
        sBitcoinWalletsID = pEnvironment->GetFieldID(sBitcoinClass, "mWallets",
          "[Ltech/nextcash/nextcashwallet/Wallet;");
        sBitcoinWalletsLoadedID = pEnvironment->GetFieldID(sBitcoinClass, "mWalletsLoaded", "Z");
        sBitcoinChainLoadedID = pEnvironment->GetFieldID(sBitcoinClass, "mChainLoaded", "Z");
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Wallet_setupJNI(JNIEnv *pEnvironment,
                                                                             jclass pObject)
    {
        sWalletClass = pEnvironment->FindClass("tech/nextcash/nextcashwallet/Wallet");
        sWalletLastUpdatedID = pEnvironment->GetFieldID(sWalletClass, "lastUpdated", "J");
        sWalletTransactionsID = pEnvironment->GetFieldID(sWalletClass, "transactions",
          "[Ltech/nextcash/nextcashwallet/Transaction;");
        sWalletUpdatedTransactionsID = pEnvironment->GetFieldID(sWalletClass, "updatedTransactions",
          "[Ltech/nextcash/nextcashwallet/Transaction;");
        sWalletIsPrivateID = pEnvironment->GetFieldID(sWalletClass, "isPrivate", "Z");
        sWalletNameID = pEnvironment->GetFieldID(sWalletClass, "name", "Ljava/lang/String;");
        sWalletIsSynchronizedID = pEnvironment->GetFieldID(sWalletClass, "isSynchronized", "Z");
        sWalletIsBackedUpID = pEnvironment->GetFieldID(sWalletClass, "isBackedUp", "Z");
        sWalletBalanceID = pEnvironment->GetFieldID(sWalletClass, "balance", "J");
        sWalletPendingBalanceID = pEnvironment->GetFieldID(sWalletClass, "pendingBalance", "J");
        sWalletBlockHeightID = pEnvironment->GetFieldID(sWalletClass, "blockHeight", "I");
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Transaction_setupJNI(JNIEnv *pEnvironment,
                                                                                  jclass pObject)
    {
        // This function gets called, but seems to be "unloaded" or invalidated somehow before any
        //   transactions are created with NewObject JNI function.
        // setupTransaction has to be called immediately before creating transactions.

//        sTransactionClass = pEnvironment->FindClass("tech/nextcash/nextcashwallet/Transaction");
//        sTransactionConstructor = pEnvironment->GetMethodID(sTransactionClass, "<init>",
//          "()V");
//        sTransactionHashID = pEnvironment->GetFieldID(sTransactionClass, "hash",
//          "Ljava/lang/String;");
//        sTransactionBlockID = pEnvironment->GetFieldID(sTransactionClass, "block",
//          "Ljava/lang/String;");
//        sTransactionDateID = pEnvironment->GetFieldID(sTransactionClass, "date", "J");
//        sTransactionAmountID = pEnvironment->GetFieldID(sTransactionClass, "amount", "J");
    }

    BitCoin::Daemon *getDaemon(JNIEnv *pEnvironment, jobject pBitcoinObject, bool pCreate = false)
    {
        jlong handle = pEnvironment->GetLongField(pBitcoinObject, sBitcoinHandleID);

        if(handle != 0)
            return reinterpret_cast<BitCoin::Daemon *>(handle);

        if(!pCreate)
            return NULL;

        // Create handle
        // NOTE : Daemon object apparently needs to be created in the thread running it. So make
        //   sure the functions with pCreate=true are only called on that Java thread.
        BitCoin::Daemon *daemon = new BitCoin::Daemon();
        handle = jlong(daemon);

        pEnvironment->SetLongField(pBitcoinObject, sBitcoinHandleID, handle);
        NextCash::Log::add(NextCash::Log::INFO, NEXTCASH_JNI_LOG_NAME, "Bitcoin handle created");

        return daemon;
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_destroy(JNIEnv *pEnvironment,
                                                                             jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject, false);
        if(daemon == NULL)
            return;

        // Zeroize handle in java object
        pEnvironment->SetLongField(pObject, sBitcoinHandleID, 0);
        pEnvironment->SetBooleanField(pObject, sBitcoinWalletsLoadedID, JNI_FALSE);
        pEnvironment->SetBooleanField(pObject, sBitcoinChainLoadedID, JNI_FALSE);

        // Delete C++ object
        delete daemon;
        BitCoin::Info::destroy();

        NextCash::Log::add(NextCash::Log::INFO, NEXTCASH_JNI_LOG_NAME, "Bitcoin handle destroyed");
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setPath(JNIEnv *pEnvironment,
                                                                             jobject pObject,
                                                                             jstring pPath)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject, true);
        if(daemon == NULL)
            return;

        const char *newPath = pEnvironment->GetStringUTFChars(pPath, NULL);
        BitCoin::Info::setPath(newPath);
        pEnvironment->ReleaseStringUTFChars(pPath, newPath);
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_loadWallets(JNIEnv *pEnvironment,
                                                                                     jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject, true);
        if(daemon == NULL)
            return JNI_FALSE;

        jboolean result = JNI_FALSE;

        try
        {
            if(daemon->loadWallets())
            {
                NextCash::Log::add(NextCash::Log::INFO, NEXTCASH_JNI_LOG_NAME,
                  "Bitcoin wallets are loaded");
                result = JNI_TRUE;
            }
            else
                NextCash::Log::add(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME,
                  "Bitcoin failed to load wallets");
        }
        catch(std::bad_alloc pException)
        {
            NextCash::Log::addFormatted(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME,
              "Bad allocation while loading wallets : %s", pException.what());
        }
        catch(std::exception pException)
        {
            NextCash::Log::addFormatted(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME,
              "Exception while loading wallets : %s", pException.what());
        }

        return result;
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_loadChain(JNIEnv *pEnvironment,
                                                                                   jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject, true);
        if(daemon == NULL)
            return JNI_FALSE;

        jboolean result = JNI_FALSE;

        try
        {
            if(daemon->loadChain())
            {
                NextCash::Log::add(NextCash::Log::INFO, NEXTCASH_JNI_LOG_NAME,
                  "Bitcoin chain is loaded");
                result = JNI_TRUE;
            }
            else
                NextCash::Log::add(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME,
                  "Bitcoin failed to load chain");
        }
        catch(std::bad_alloc pException)
        {
            NextCash::Log::addFormatted(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME,
              "Bad allocation while loading chain : %s", pException.what());
        }
        catch(std::exception pException)
        {
            NextCash::Log::addFormatted(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME,
              "Exception while loading chain : %s", pException.what());
        }

        return result;
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isRunning(JNIEnv *pEnvironment,
                                                                                   jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return JNI_FALSE;

        return (jboolean)daemon->isRunning();
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isStopping(JNIEnv *pEnvironment,
                                                                                    jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return JNI_FALSE;

        return (jboolean)daemon->isStopping();
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_initialBlockDownloadIsComplete(JNIEnv *pEnvironment,
                                                                                                        jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return JNI_FALSE;

        return (jboolean)BitCoin::Info::instance().initialBlockDownloadIsComplete();
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isInSync(JNIEnv *pEnvironment,
                                                                                  jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return JNI_FALSE;

        return (jboolean)daemon->chain()->isInSync();
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_wasInSync(JNIEnv *pEnvironment,
                                                                                   jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return JNI_FALSE;

        return (jboolean)daemon->chain()->wasInSync();
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isInRoughSync(JNIEnv *pEnvironment,
                                                                                       jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return JNI_FALSE;

        return (jboolean)(daemon->chain()->isInSync() &&
          daemon->chain()->headerHeight() == daemon->monitor()->roughHeight());
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_finishMode(JNIEnv *pEnvironment,
                                                                                jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject, true);
        if(daemon == NULL)
            return 0;

        return (jint)daemon->finishMode();
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setFinishMode(JNIEnv *pEnvironment,
                                                                                   jobject pObject,
                                                                                   jint pMode)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject, true);
        if(daemon == NULL)
            return;

        daemon->setFinishMode(pMode);
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setFinishTime(JNIEnv *pEnvironment,
                                                                                   jobject pObject,
                                                                                   jint pSecondsFromNow)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject, false);
        if(daemon == NULL)
            return;

        daemon->setFinishTime(BitCoin::getTime() + pSecondsFromNow);
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_clearFinishTime(JNIEnv *pEnvironment,
                                                                                     jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject, false);
        if(daemon == NULL)
            return;

        daemon->setFinishTime(0);
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_run(JNIEnv *pEnvironment,
                                                                         jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject, true);
        if(daemon == NULL)
            return;

        daemon->run(false);
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_stop(JNIEnv *pEnvironment,
                                                                              jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return JNI_FALSE;

        daemon->requestStop();
        return JNI_TRUE;
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_peerCount(JNIEnv *pEnvironment,
                                                                               jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return 0;

        return daemon->peerCount();
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_status(JNIEnv *pEnvironment,
                                                                            jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return (jint)0;

        switch(daemon->status())
        {
            default:
            case BitCoin::Daemon::INACTIVE:
                return (jint)0;
            case BitCoin::Daemon::LOADING_WALLETS:
                return (jint)1;
            case BitCoin::Daemon::LOADING_CHAIN:
                return (jint)2;
            case BitCoin::Daemon::FINDING_PEERS:
                return (jint)3;
            case BitCoin::Daemon::CONNECTING_TO_PEERS:
                return (jint)4;
            case BitCoin::Daemon::SYNCHRONIZING:
                return (jint)5;
            case BitCoin::Daemon::SYNCHRONIZED:
                return (jint)6;
            case BitCoin::Daemon::FINDING_TRANSACTIONS:
                return (jint)7;
        }
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_headerHeight(JNIEnv *pEnvironment,
                                                                                 jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return (jint)0;

        return daemon->chain()->headerHeight();
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_merkleHeight(JNIEnv *pEnvironment,
                                                                                  jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return (jint)0;

        return daemon->monitor()->height();
    }

    jobject createBlock(JNIEnv *pEnvironment,
                        int pHeight,
                        jstring pHash,
                        int32_t pTime)
    {
        jobject result = pEnvironment->NewObject(sBlockClass, sBlockConstructor);

        // Set height
        pEnvironment->SetIntField(result, sBlockHeightID, (jint)pHeight);

        // Set hash
        pEnvironment->SetObjectField(result, sBlockHashID, pHash);

        // Set time
        pEnvironment->SetLongField(result, sBlockTimeID, (jlong)pTime);

        return result;
    }

    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getBlockFromHeight(JNIEnv *pEnvironment,
                                                                                           jobject pObject,
                                                                                           jint pHeight)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return NULL;

        setupBlockClass(pEnvironment);

        NextCash::Hash hash;
        if(!daemon->chain()->getHash((unsigned int)pHeight, hash))
            return NULL;

        return createBlock(pEnvironment, pHeight, pEnvironment->NewStringUTF(hash.hex().text()),
          daemon->chain()->time((unsigned int)pHeight));
    }

    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getBlockFromHash(JNIEnv *pEnvironment,
                                                                                         jobject pObject,
                                                                                         jstring pHash)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return NULL;

        setupBlockClass(pEnvironment);

        const char *hashHex = pEnvironment->GetStringUTFChars(pHash, NULL);
        NextCash::Hash hash(hashHex);
        int height = daemon->chain()->hashHeight(hash);
        pEnvironment->ReleaseStringUTFChars(pHash, hashHex);

        return createBlock(pEnvironment, height, pHash,
          daemon->chain()->time((unsigned int)height));
    }

    bool savePublicKeys(BitCoin::Daemon *pDaemon)
    {
        NextCash::String tempFilePathName = BitCoin::Info::instance().path();
        tempFilePathName.pathAppend("keystore.temp");
        NextCash::FileOutputStream publicFile(tempFilePathName, true);

        if(!publicFile.isValid())
        {
            NextCash::Log::add(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME,
              "Failed to open public key file");
            return false;
        }

        pDaemon->keyStore()->write(&publicFile);
        publicFile.close();

        NextCash::String realFilePathName = BitCoin::Info::instance().path();
        realFilePathName.pathAppend("keystore");
        NextCash::renameFile(tempFilePathName, realFilePathName);
        return true;
    }

    bool loadPrivateKeys(JNIEnv *pEnvironment, BitCoin::Daemon *pDaemon, jstring pPassCode)
    {
        if(pDaemon->keyStore()->isPrivateLoaded())
            return true;

        NextCash::String filePathName = BitCoin::Info::instance().path();
        filePathName.pathAppend(".private_keystore");
        NextCash::FileInputStream privateFile(filePathName);

        if(!privateFile.isValid())
        {
            NextCash::Log::add(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME,
              "Failed to open private key file");
            return false;
        }

        const char *passcode = pEnvironment->GetStringUTFChars(pPassCode, NULL);
        bool success = pDaemon->keyStore()->readPrivate(&privateFile, (const uint8_t *)passcode,
          (unsigned int)std::strlen(passcode));
        pEnvironment->ReleaseStringUTFChars(pPassCode, passcode);

        return success;
    }

    bool savePrivateKeys(JNIEnv *pEnvironment, BitCoin::Daemon *pDaemon, jstring pPassCode)
    {
        if(!pDaemon->keyStore()->isPrivateLoaded())
            return false;

        NextCash::String tempFilePathName = BitCoin::Info::instance().path();
        tempFilePathName.pathAppend(".private_keystore.temp");
        NextCash::FileOutputStream privateFile(tempFilePathName, true);

        if(!privateFile.isValid())
        {
            NextCash::Log::add(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME,
              "Failed to open private key file");
            return false;
        }

        const char *passcode = pEnvironment->GetStringUTFChars(pPassCode, NULL);
        bool result = pDaemon->keyStore()->writePrivate(&privateFile, (const uint8_t *)passcode,
          (unsigned int)std::strlen(passcode));
        pEnvironment->ReleaseStringUTFChars(pPassCode, passcode);
        privateFile.close();

        NextCash::String realFilePathName = BitCoin::Info::instance().path();
        realFilePathName.pathAppend(".private_keystore");
        NextCash::renameFile(tempFilePathName, realFilePathName);
        return result;
    }

    // Keys
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_loadKey(JNIEnv *pEnvironment,
                                                                             jobject pObject,
                                                                             jstring pPassCode,
                                                                             jstring pKey,
                                                                             jint pDerivationMethod,
                                                                             jstring pName,
                                                                             jlong pRecoverTime)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return (jint)1;

        if(!loadPrivateKeys(pEnvironment, daemon, pPassCode))
            return (jint)5;

        BitCoin::Key::DerivationPathMethod method;
        switch(pDerivationMethod)
        {
            default:
            case 0:
                method = BitCoin::Key::BIP0044;
                break;
            case 1:
                method = BitCoin::Key::BIP0032;
                break;
            case 2:
                method = BitCoin::Key::SIMPLE;
                break;
        }

        const char *key = pEnvironment->GetStringUTFChars(pKey, NULL);
        int result = daemon->keyStore()->loadKey(key, method, (int32_t)pRecoverTime);
        pEnvironment->ReleaseStringUTFChars(pKey, key);

        if(result == 0)
        {
            unsigned int offset = daemon->keyStore()->size() - 1;
            const char *name = pEnvironment->GetStringUTFChars(pName, NULL);
            daemon->keyStore()->setName(offset, name);
            pEnvironment->ReleaseStringUTFChars(pName, name);

            daemon->resetKeysSynchronized();
            daemon->keyStore()->setBackedUp(offset);
            daemon->monitor()->refreshKeyStore();
            daemon->monitor()->updatePasses(daemon->chain());
        }

        if(!daemon->saveMonitor() && result == 0)
            result = 1;
        if(!savePrivateKeys(pEnvironment, daemon, pPassCode) && result == 0)
            result = 1;
        if(!savePublicKeys(daemon) && result == 0)
            result = 1;

        daemon->keyStore()->unloadPrivate();
        return (jint)result;
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_keyCount(JNIEnv *pEnvironment,
                                                                              jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return (jint)0;

        return (jint)daemon->keyStore()->size();
    }

    bool transGreater(BitCoin::Monitor::RelatedTransactionData &pLeft,
                      BitCoin::Monitor::RelatedTransactionData &pRight)
    {
        if(pLeft.blockHeight > pRight.blockHeight)
            return true;
        else if(pLeft.blockHeight < pRight.blockHeight)
            return false;
        else
            return pLeft.transaction.time() > pRight.transaction.time();
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getChangeID(JNIEnv *pEnvironment,
                                                                                 jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return 0;

        return (jint)daemon->monitor()->changeID();
    }

    jobject createTransaction(JNIEnv *pEnvironment,
                              BitCoin::Daemon *pDaemon,
                              BitCoin::Monitor::RelatedTransactionData &pTransaction,
                              bool pChainWasLoaded)
    {
        jobject result = pEnvironment->NewObject(sTransactionClass, sTransactionConstructor);

        // Set hash
        pEnvironment->SetObjectField(result, sTransactionHashID,
          pEnvironment->NewStringUTF(pTransaction.transaction.hash.hex()));

        // Set block
        if(!pTransaction.blockHash.isEmpty())
            pEnvironment->SetObjectField(result, sTransactionBlockID,
              pEnvironment->NewStringUTF(pTransaction.blockHash.hex()));

        // Set date
        if(pTransaction.transaction.time() != 0)
            pEnvironment->SetLongField(result, sTransactionDateID,
              (jlong)pTransaction.transaction.time());

        // Set amount
        pEnvironment->SetLongField(result, sTransactionAmountID,
          (jlong)pTransaction.amount());

        // Set count
        if(!pChainWasLoaded)
            pEnvironment->SetIntField(result, sTransactionCountID,
              (jint)-1);
        else if(pTransaction.blockHash.isEmpty())
            pEnvironment->SetIntField(result, sTransactionCountID,
              (jint)pTransaction.nodesVerified);
        else
            pEnvironment->SetIntField(result, sTransactionCountID,
              (jint)(pDaemon->chain()->headerHeight() + 1 -
              pDaemon->chain()->hashHeight(pTransaction.blockHash)));

        return result;
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_updateWallet(JNIEnv *pEnvironment,
                                                                                      jobject pBitcoin,
                                                                                      jobject pWallet,
                                                                                      jint pOffset)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pBitcoin);
        if(daemon == NULL || daemon->keyStore()->size() <= pOffset)
            return JNI_FALSE;

        // Loop through public chain keys getting transactions
        jint headerHeight;
        int64_t balance = 0, pendingBalance = 0;
        bool chainWasLoaded = daemon->chainIsLoaded();
        std::vector<BitCoin::Key *> *chainKeys =
          daemon->keyStore()->chainKeys((unsigned int)pOffset);
        std::vector<BitCoin::Monitor::RelatedTransactionData> transactions;

        if(chainWasLoaded)
            headerHeight = (jint)daemon->chain()->headerHeight();
        else
            headerHeight = 0;

        if(chainKeys != NULL && chainKeys->size() != 0)
        {
            daemon->monitor()->getTransactions(chainKeys->begin(), chainKeys->end(),
              transactions, true);

            balance = daemon->monitor()->balance(chainKeys->begin(), chainKeys->end(), false);
        }

        bool previousUpdate = pEnvironment->GetLongField(pWallet, sWalletLastUpdatedID) != 0;
        NextCash::HashList previousHashList, previousConfirmedHashList;
        bool hasPending = false;

        // Check for updated transactions since previous update
        if(previousUpdate)
        {
            jobjectArray previousTransactions = (jobjectArray)pEnvironment->GetObjectField(pWallet,
              sWalletTransactionsID);
            if(previousTransactions != NULL)
            {
                jsize previousCount = pEnvironment->GetArrayLength(previousTransactions);
                jobject hashString, blockString;
                const char *hashText;
                NextCash::Hash hash;
                jobject previousTransaction;
                for(jsize i = 0; i < previousCount; ++i)
                {
                    previousTransaction = pEnvironment->GetObjectArrayElement(previousTransactions,
                      i);
                    hashString = pEnvironment->GetObjectField(previousTransaction,
                      sTransactionHashID);
                    blockString = pEnvironment->GetObjectField(previousTransaction,
                      sTransactionBlockID);

                    hashText = pEnvironment->GetStringUTFChars((jstring)hashString, NULL);
                    hash.setHex(hashText);
                    previousHashList.push_back(hash);
                    if(blockString != NULL)
                        previousConfirmedHashList.push_back(hash);
                    pEnvironment->ReleaseStringUTFChars((jstring)hashString, hashText);
                }
            }
        }

        // Set transaction times based on block times
        if(chainWasLoaded)
            for(std::vector<BitCoin::Monitor::RelatedTransactionData>::iterator transaction =
              transactions.begin(); transaction != transactions.end(); ++transaction)
                if(transaction->blockHeight != 0xffffffff)
                    (*transaction).transaction
                      .setTime(daemon->chain()->time(transaction->blockHeight));

        std::sort(transactions.begin(), transactions.end(), transGreater);

        setupTransactionClass(pEnvironment);

        // Set transactions
        std::vector<BitCoin::Monitor::RelatedTransactionData> newUpdatedTransactions;
        jobjectArray newTransactions = pEnvironment->NewObjectArray((jsize)transactions.size(),
          sTransactionClass, NULL);
        unsigned int index = 0;

        for(std::vector<BitCoin::Monitor::RelatedTransactionData>::iterator transaction =
          transactions.begin(); transaction != transactions.end(); ++transaction, ++index)
        {
            // Set value in array
            pEnvironment->SetObjectArrayElement(newTransactions, index,
              createTransaction(pEnvironment, daemon, *transaction, chainWasLoaded));

            if(transaction->blockHash.isEmpty())
                hasPending = true;

            if(previousUpdate)
            {
                // Check if this is a new transaction
                if(!previousHashList.contains(transaction->transaction.hash))
                    newUpdatedTransactions.push_back(*transaction); // New transaction
                else if(!transaction->blockHash.isEmpty() &&
                  !previousConfirmedHashList.contains(transaction->transaction.hash))
                    newUpdatedTransactions.push_back(*transaction); // Newly confirmed transaction
            }
        }

        pEnvironment->SetObjectField(pWallet, sWalletTransactionsID, newTransactions);

        if(previousUpdate)
        {
            // Set updated transactions
            jobjectArray updatedTransactions =
              pEnvironment->NewObjectArray((jsize)newUpdatedTransactions.size(), sTransactionClass,
              NULL);

            index = 0;
            for(std::vector<BitCoin::Monitor::RelatedTransactionData>::iterator transaction =
              newUpdatedTransactions.begin(); transaction != newUpdatedTransactions.end();
              ++transaction, ++index)
            {
                // Set value in array
                pEnvironment->SetObjectArrayElement(updatedTransactions, index,
                  createTransaction(pEnvironment, daemon, *transaction, chainWasLoaded));
            }

            pEnvironment->SetObjectField(pWallet, sWalletUpdatedTransactionsID,
              updatedTransactions);
        }

        if(hasPending)
            pendingBalance = daemon->monitor()->balance(chainKeys->begin(), chainKeys->end(), true);
        else
            pendingBalance = balance;

        pEnvironment->SetObjectField(pWallet, sWalletNameID,
          pEnvironment->NewStringUTF(daemon->keyStore()->name((unsigned int)pOffset).text()));
        pEnvironment->SetBooleanField(pWallet, sWalletIsPrivateID,
          (jboolean)daemon->keyStore()->hasPrivate((unsigned int)pOffset));
        pEnvironment->SetBooleanField(pWallet, sWalletIsSynchronizedID,
          (jboolean)daemon->keyStore()->isSynchronized((unsigned int)pOffset));
        pEnvironment->SetBooleanField(pWallet, sWalletIsBackedUpID,
          (jboolean)daemon->keyStore()->isBackedUp((unsigned int)pOffset));
        pEnvironment->SetLongField(pWallet, sWalletBalanceID, (jlong)balance);
        pEnvironment->SetLongField(pWallet, sWalletPendingBalanceID, (jlong)pendingBalance);
        pEnvironment->SetIntField(pWallet, sWalletBlockHeightID, headerHeight);
        pEnvironment->SetLongField(pWallet, sWalletLastUpdatedID, (jlong)BitCoin::getTime());
        return JNI_TRUE;
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setName(JNIEnv *pEnvironment,
                                                                                 jobject pObject,
                                                                                 jint pOffset,
                                                                                 jstring pName)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pOffset)
            return JNI_FALSE;

        const char *newName = pEnvironment->GetStringUTFChars(pName, NULL);
        daemon->keyStore()->setName((unsigned int)pOffset, newName);
        pEnvironment->ReleaseStringUTFChars(pName, newName);

        jboolean result = (jboolean)savePublicKeys(daemon);
        if(result)
        {
            // Set name in java object
            jobjectArray wallets = (jobjectArray)pEnvironment->GetObjectField(pObject,
              sBitcoinWalletsID);
            if(pEnvironment->GetArrayLength(wallets) > pOffset)
            {
                jobject wallet = pEnvironment->GetObjectArrayElement(wallets, pOffset);
                pEnvironment->SetObjectField(wallet, sWalletNameID, pName);
            }
            daemon->monitor()->incrementChange();
        }

        return result;
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setIsBackedUp(JNIEnv *pEnvironment,
                                                                                       jobject pObject,
                                                                                       jint pOffset)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pOffset)
            return JNI_FALSE;

        daemon->keyStore()->setBackedUp((unsigned int)pOffset);
        daemon->monitor()->incrementChange();

        return (jboolean)savePublicKeys(daemon);
    }

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_generateMnemonicSeed(JNIEnv *pEnvironment,
                                                                                             jobject pObject,
                                                                                             jint pEntropy)
    {
        return pEnvironment->NewStringUTF(
          BitCoin::Key::generateMnemonicSeed(BitCoin::Mnemonic::English, pEntropy).text());
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_addSeed(JNIEnv *pEnvironment,
                                                                             jobject pObject,
                                                                             jstring pPassCode,
                                                                             jstring pSeed,
                                                                             jint pDerivationMethod,
                                                                             jstring pName,
                                                                             jboolean pIsBackedUp,
                                                                             jlong pRecoverTime)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return (jint)1;

        if(!loadPrivateKeys(pEnvironment, daemon, pPassCode))
            return (jint)5; // Invalid pass code

        BitCoin::Key::DerivationPathMethod method;
        switch(pDerivationMethod)
        {
            default:
            case 0:
                method = BitCoin::Key::BIP0044;
                break;
            case 1:
                method = BitCoin::Key::BIP0032;
                break;
            case 2:
                method = BitCoin::Key::SIMPLE;
                break;
        }

        const char *seed = pEnvironment->GetStringUTFChars(pSeed, NULL);
        int result = daemon->keyStore()->addSeed(seed, method, (int32_t)pRecoverTime);
        pEnvironment->ReleaseStringUTFChars(pSeed, seed);
        if(result != 0)
        {
            daemon->keyStore()->unloadPrivate();
            return (jint)result;
        }

        unsigned int offset = daemon->keyStore()->size() - 1;
        const char *name = pEnvironment->GetStringUTFChars(pName, NULL);
        daemon->keyStore()->setName(offset, name);
        daemon->resetKeysSynchronized();
        if(pIsBackedUp)
            daemon->keyStore()->setBackedUp(offset);
        pEnvironment->ReleaseStringUTFChars(pName, name);

        daemon->monitor()->refreshKeyStore();
        daemon->monitor()->updatePasses(daemon->chain());

        if(!daemon->saveMonitor())
            result = 1;
        if(!savePrivateKeys(pEnvironment, daemon, pPassCode) && result == 0)
            result = 1;
        if(!savePublicKeys(daemon) && result == 0)
            result = 1;

        daemon->keyStore()->unloadPrivate();
        return (jint)result;
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_addPrivateKey(JNIEnv *pEnvironment,
                                                                                   jobject pObject,
                                                                                   jstring pPassCode,
                                                                                   jstring pPrivateKey,
                                                                                   jstring pName,
                                                                                   jlong pRecoverTime)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return (jint)1;

        if(!loadPrivateKeys(pEnvironment, daemon, pPassCode))
            return (jint)5; // Invalid pass code

        const char *encodedKey = pEnvironment->GetStringUTFChars(pPrivateKey, NULL);
        int result = daemon->keyStore()->addKey(encodedKey,
          BitCoin::Key::DerivationPathMethod::INDIVIDUAL, (int32_t)pRecoverTime);
        pEnvironment->ReleaseStringUTFChars(pPrivateKey, encodedKey);
        if(result != 0)
        {
            daemon->keyStore()->unloadPrivate();
            return (jint)result;
        }

        unsigned int offset = daemon->keyStore()->size() - 1;
        const char *name = pEnvironment->GetStringUTFChars(pName, NULL);
        daemon->keyStore()->setName(offset, name);
        daemon->resetKeysSynchronized();
        daemon->keyStore()->setBackedUp(offset);
        pEnvironment->ReleaseStringUTFChars(pName, name);

        daemon->monitor()->refreshKeyStore();
        daemon->monitor()->updatePasses(daemon->chain());
        daemon->saveMonitor();

        if(!daemon->saveMonitor())
            result = 1;
        if(!savePrivateKeys(pEnvironment, daemon, pPassCode) && result == 0)
            result = 1;
        if(!savePublicKeys(daemon) && result == 0)
            result = 1;

        daemon->keyStore()->unloadPrivate();
        return (jint)result;
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_removeKey(JNIEnv *pEnvironment,
                                                                               jobject pObject,
                                                                               jstring pPassCode,
                                                                               jint pOffset)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pOffset)
            return (jint)1;

        if(!loadPrivateKeys(pEnvironment, daemon, pPassCode))
            return (jint)5; // Invalid pass code

        int result = 0;
        if(!daemon->keyStore()->remove((unsigned int)pOffset))
            result = 1;

        daemon->monitor()->resetKeyStore();
        if(!daemon->saveMonitor() && result == 0)
            result = 1;
        if(!savePrivateKeys(pEnvironment, daemon, pPassCode) && result == 0)
            result = 1;
        if(!savePublicKeys(daemon) && result == 0)
            result = 1;

        daemon->keyStore()->unloadPrivate();
        return (jint)result;
    }

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_seed(JNIEnv *pEnvironment,
                                                                             jobject pObject,
                                                                             jstring pPassCode,
                                                                             jint pOffset)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pOffset)
            return NULL;

        if(!loadPrivateKeys(pEnvironment, daemon, pPassCode))
            return NULL;

        NextCash::String result = daemon->keyStore()->seed((unsigned int)pOffset);

        daemon->keyStore()->unloadPrivate();

        if(result.length() == 0)
        {
            NextCash::Log::add(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME, "Zero length seed");
            return NULL;
        }

        return pEnvironment->NewStringUTF(result.text());
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_seedIsValid(JNIEnv *pEnvironment,
                                                                                     jobject pObject,
                                                                                     jstring pSeed)
    {
        const char *seed = pEnvironment->GetStringUTFChars(pSeed, NULL);
        bool result = BitCoin::Key::validateMnemonicSeed(seed);
        pEnvironment->ReleaseStringUTFChars(pSeed, seed);
        return (jboolean)result;
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_hasPassCode(JNIEnv *pEnvironment,
                                                                                     jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return JNI_FALSE;

        NextCash::String filePathName = BitCoin::Info::instance().path();
        filePathName.pathAppend(".private_keystore");

        return (jboolean)NextCash::fileExists(filePathName);
    }

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getNextReceiveAddress(JNIEnv *pEnvironment,
                                                                                              jobject pObject,
                                                                                              jint pKeyOffset,
                                                                                              jint pIndex)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pKeyOffset)
            return NULL;

        std::vector<BitCoin::Key *> *chainKeys = daemon->keyStore()->chainKeys((unsigned int)pKeyOffset);

        if(chainKeys != NULL && chainKeys->size() != 0)
        {
            for(std::vector<BitCoin::Key *>::iterator chainKey = chainKeys->begin();
              chainKey != chainKeys->end(); ++chainKey)
                if((*chainKey)->index() == pIndex)
                {
                    BitCoin::Key *unused = (*chainKey)->getNextUnused();

                    if(unused == NULL)
                        return NULL;

                    NextCash::String result = unused->address();
                    return pEnvironment->NewStringUTF(result.text());
                }

            if(chainKeys->size() == 1 && chainKeys->front()->depth() == BitCoin::Key::NO_DEPTH)
            {
                NextCash::String result = chainKeys->front()->address();
                return pEnvironment->NewStringUTF(result.text());
            }
        }

        return NULL;
    }

    JNIEXPORT jbyteArray JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getNextReceiveOutput(JNIEnv *pEnvironment,
                                                                                                jobject pObject,
                                                                                                jint pKeyOffset,
                                                                                                jint pIndex)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pKeyOffset)
            return NULL;

        std::vector<BitCoin::Key *> *chainKeys = daemon->keyStore()->chainKeys((unsigned int)pKeyOffset);

        if(chainKeys != NULL && chainKeys->size() != 0)
            for(std::vector<BitCoin::Key *>::iterator chainKey = chainKeys->begin();
                chainKey != chainKeys->end(); ++chainKey)
                if((*chainKey)->index() == pIndex)
                {
                    BitCoin::Key *unused = (*chainKey)->getNextUnused();

                    if(unused == NULL)
                        return NULL;

                    NextCash::Buffer outputScript;
                    BitCoin::ScriptInterpreter::writeP2PKHOutputScript(outputScript, unused->hash());
                    jbyteArray result = pEnvironment->NewByteArray((jsize)outputScript.length());
                    pEnvironment->SetByteArrayRegion(result, 0, (jsize)outputScript.length(),
                      (const jbyte *)outputScript.startPointer());
                    return result;
                }

        return NULL;
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_containsAddress(JNIEnv *pEnvironment,
                                                                                         jobject pObject,
                                                                                         jint pKeyOffset,
                                                                                         jstring pAddress)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pKeyOffset)
            return JNI_FALSE;

        const char *address = pEnvironment->GetStringUTFChars(pAddress, NULL);
        BitCoin::PaymentRequest request = BitCoin::decodePaymentCode(address);
        pEnvironment->ReleaseStringUTFChars(pAddress, address);

        if(request.format == BitCoin::PaymentRequest::Format::INVALID ||
          request.type == BitCoin::AddressType::UNKNOWN)
            return JNI_FALSE;

        return (jboolean)(daemon->keyStore()->findAddress((unsigned int)pKeyOffset,
          request.pubKeyHash) != NULL);
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_markAddressUsed(JNIEnv *pEnvironment,
                                                                                         jobject pObject,
                                                                                         jint pKeyOffset,
                                                                                         jstring pAddress)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pKeyOffset)
            return JNI_FALSE;

        const char *address = pEnvironment->GetStringUTFChars(pAddress, NULL);
        BitCoin::PaymentRequest request = BitCoin::decodePaymentCode(address);
        pEnvironment->ReleaseStringUTFChars(pAddress, address);

        if(request.network != BitCoin::MAINNET)
            return JNI_FALSE;

        std::vector<BitCoin::Key *> *chainKeys = daemon->keyStore()->chainKeys((unsigned int)pKeyOffset);

        if(chainKeys == NULL || chainKeys->size() == 0)
            return JNI_FALSE;

        bool newAddress = false;
        for(std::vector<BitCoin::Key *>::iterator chainKey = chainKeys->begin();
          chainKey != chainKeys->end(); ++chainKey)
            if((*chainKey)->markUsed(request.pubKeyHash, 0, newAddress) != NULL)
                return JNI_TRUE;

        return JNI_FALSE;
    }

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_encodePaymentCode(JNIEnv *pEnvironment,
                                                                                          jobject pObject,
                                                                                          jstring pAddress,
                                                                                          jint pFormat,
                                                                                          jint pProtocol)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return NULL;

        if(pProtocol != 1)
            return NULL; // Not supported

        BitCoin::PaymentRequest::Format format;

        switch(pFormat)
        {
        default:
        case 0: // INVALILD
            return NULL;
        case 1: // Legacy
            format = BitCoin::PaymentRequest::Format::LEGACY;
            break;
        case 2: // Cash
            format = BitCoin::PaymentRequest::Format::CASH;
            break;
        }

        // Parse
        const char *addressHashHex = pEnvironment->GetStringUTFChars(pAddress, NULL);
        NextCash::String result = BitCoin::encodePaymentCode(addressHashHex, format,
          BitCoin::AddressType::MAIN_PUB_KEY_HASH);
        pEnvironment->ReleaseStringUTFChars(pAddress, addressHashHex);

        return pEnvironment->NewStringUTF(result.text());
    }

    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_decodePaymentCode(JNIEnv *pEnvironment,
                                                                                          jobject pObject,
                                                                                          jstring pPaymentCode)
    {
        // Parse
        const char *paymentCode = pEnvironment->GetStringUTFChars(pPaymentCode, NULL);
        BitCoin::PaymentRequest request = BitCoin::decodePaymentCode(paymentCode);
        pEnvironment->ReleaseStringUTFChars(pPaymentCode, paymentCode);

        if(request.network != BitCoin::MAINNET)
            request.format = BitCoin::PaymentRequest::Format::INVALID;

        // Create result object
        setupPaymentRequestClass(pEnvironment);
        jobject result = pEnvironment->NewObject(sPaymentRequestClass, sPaymentRequestConstructor);

        // Set code
        pEnvironment->SetObjectField(result, sPaymentRequestURIID, pPaymentCode);

        // Set format
        switch(request.format)
        {
            case BitCoin::PaymentRequest::Format::INVALID:
                pEnvironment->SetIntField(result, sPaymentRequestFormatID, (jint)0);
                break;
            case BitCoin::PaymentRequest::Format::LEGACY:
                pEnvironment->SetIntField(result, sPaymentRequestFormatID, (jint)1);

                // Set address
                if(!request.pubKeyHash.isEmpty())
                    pEnvironment->SetObjectField(result, sPaymentRequestAddressID,
                      pEnvironment->NewStringUTF(BitCoin::encodeLegacyAddress(request.pubKeyHash,
                        request.type)));

                break;
            case BitCoin::PaymentRequest::Format::CASH:
                pEnvironment->SetIntField(result, sPaymentRequestFormatID, (jint)2);

                // Set address
                if(!request.pubKeyHash.isEmpty())
                    pEnvironment->SetObjectField(result, sPaymentRequestAddressID,
                      pEnvironment->NewStringUTF(BitCoin::encodeCashAddress(request.pubKeyHash,
                        request.type)));

                break;
        }

        // Set protocol
        switch(request.type)
        {
            case BitCoin::AddressType::UNKNOWN:
                pEnvironment->SetIntField(result, sPaymentRequestTypeID, (jint)0);
                break;
            case BitCoin::AddressType::MAIN_PUB_KEY_HASH:
            case BitCoin::AddressType::TEST_PUB_KEY_HASH:
                pEnvironment->SetIntField(result, sPaymentRequestTypeID, (jint)1);
                break;
            case BitCoin::AddressType::MAIN_SCRIPT_HASH:
            case BitCoin::AddressType::TEST_SCRIPT_HASH:
                pEnvironment->SetIntField(result, sPaymentRequestTypeID, (jint)2);
                break;
            case BitCoin::AddressType::MAIN_PRIVATE_KEY:
            case BitCoin::AddressType::TEST_PRIVATE_KEY:
                pEnvironment->SetIntField(result, sPaymentRequestTypeID, (jint)3);
                break;
            case BitCoin::AddressType::BIP0070:
                pEnvironment->SetIntField(result, sPaymentRequestTypeID, (jint)4);
                break;
        }

        // Set amount
        pEnvironment->SetLongField(result, sPaymentRequestAmountID, (jlong)request.amount);

        // Set amount specified
        pEnvironment->SetBooleanField(result, sPaymentRequestAmountSpecifiedID,
          (jboolean)request.amountSpecified);

        // Set label
        if(request.label)
            pEnvironment->SetObjectField(result, sPaymentRequestLabelID,
              pEnvironment->NewStringUTF(request.label));

        // Set description
        if(request.message)
            pEnvironment->SetObjectField(result, sPaymentRequestMessageID,
              pEnvironment->NewStringUTF(request.message));

        // Set secure
        pEnvironment->SetBooleanField(result, sPaymentRequestSecureID, (jboolean)request.secure);

        // Set secure URL
        if(request.secureURL)
            pEnvironment->SetObjectField(result, sPaymentRequestSecureURLID,
              pEnvironment->NewStringUTF(request.secureURL));

        return result;
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isValidPrivateKey(JNIEnv *pEnvironment,
                                                                                       jobject pObject,
                                                                                       jstring pPrivateKey)
    {
        const char *privateKeyText = pEnvironment->GetStringUTFChars(pPrivateKey, NULL);
        BitCoin::Key key;
        int result = 1;
        if(key.decodePrivateKey(privateKeyText))
        {
            if(key.version() == BitCoin::Key::MAINNET_PRIVATE)
                result = 0;
            else
                result = 2;
        }
        pEnvironment->ReleaseStringUTFChars(pPrivateKey, privateKeyText);
        return (jint)result;
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getTransaction(JNIEnv *pEnvironment,
                                                                                        jobject pObject,
                                                                                        jint pKeyOffset,
                                                                                        jstring pID,
                                                                                        jobject pTransaction)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pKeyOffset)
            return JNI_FALSE;

        std::vector<BitCoin::Key *> *chainKeys = daemon->keyStore()->chainKeys(pKeyOffset);
        if(chainKeys == NULL)
            return JNI_FALSE;

        const char *id = pEnvironment->GetStringUTFChars(pID, NULL);
        BitCoin::Monitor::RelatedTransactionData transaction;
        bool success = daemon->monitor()->getTransaction(id, chainKeys->begin(), chainKeys->end(),
          transaction);
        pEnvironment->ReleaseStringUTFChars(pID, id);

        NextCash::Buffer buffer;
        transaction.transaction.write(&buffer);
        NextCash::String hex = buffer.readHexString(buffer.length());

        if(!success)
            return JNI_FALSE;

        setupFullTransactionClass(pEnvironment);

        // Hash
        pEnvironment->SetObjectField(pTransaction, sFullTransactionHashID,
          pEnvironment->NewStringUTF(transaction.transaction.hash.hex().text()));

        // Block
        if(!transaction.blockHash.isEmpty())
            pEnvironment->SetObjectField(pTransaction, sFullTransactionBlockID,
              pEnvironment->NewStringUTF(transaction.blockHash.hex().text()));
        else
            pEnvironment->SetObjectField(pTransaction, sFullTransactionBlockID, NULL);

        // Count and Date
        if(!daemon->chainIsLoaded())
        {
            pEnvironment->SetIntField(pTransaction, sFullTransactionCountID, (jint)-1);

            pEnvironment->SetLongField(pTransaction, sFullTransactionDateID, (jlong)0);
        }
        else if(transaction.blockHash.isEmpty())
        {
            pEnvironment->SetIntField(pTransaction, sFullTransactionCountID,
              (jint)transaction.nodesVerified);

            pEnvironment->SetLongField(pTransaction, sFullTransactionDateID,
              (jlong)transaction.transaction.time());
        }
        else
        {
            pEnvironment->SetIntField(pTransaction, sFullTransactionCountID,
              (jint)(daemon->chain()->headerHeight() + 1 -
              daemon->chain()->hashHeight(transaction.blockHash)));

            pEnvironment->SetLongField(pTransaction, sFullTransactionDateID,
              (jlong)daemon->chain()->time(daemon->chain()->hashHeight(transaction.blockHash)));
        }

        // Size
        pEnvironment->SetIntField(pTransaction, sFullTransactionSizeID,
          transaction.transaction.size());

        // Version
        pEnvironment->SetIntField(pTransaction, sFullTransactionVersionID,
          (jint)transaction.transaction.version);

        // Inputs
        jobjectArray inputs =
          pEnvironment->NewObjectArray((jsize)transaction.transaction.inputs.size(), sInputClass,
            NULL);

        unsigned int offset = 0;
        jobject inputObject;
        NextCash::String script;
        BitCoin::ScriptInterpreter interpreter;
        for(std::vector<BitCoin::Input>::iterator input = transaction.transaction.inputs.begin();
          input != transaction.transaction.inputs.end(); ++input, ++offset)
        {
            inputObject = pEnvironment->NewObject(sInputClass, sInputConstructor);

            // Outpoint
            pEnvironment->SetObjectField(inputObject, sInputOutpointID,
              pEnvironment->NewStringUTF(input->outpoint.transactionID.hex().text()));
            pEnvironment->SetIntField(inputObject, sInputOutpointIndexID, input->outpoint.index);

            // Script
            pEnvironment->SetObjectField(inputObject, sInputScriptID,
              pEnvironment->NewStringUTF(BitCoin::ScriptInterpreter::scriptText(input->script,
                daemon->chain()->forks(), daemon->chain()->headerHeight())));

            // Sequence
            pEnvironment->SetIntField(inputObject, sInputSequenceID, (jint)input->sequence);

            // Address
            if(transaction.inputAddresses[offset])
                pEnvironment->SetObjectField(inputObject, sInputAddressID,
                  pEnvironment->NewStringUTF(transaction.inputAddresses[offset]));

            // Amount
            pEnvironment->SetLongField(inputObject, sInputAmountID,
              (jlong)transaction.relatedInputAmounts[offset]);

            pEnvironment->SetObjectArrayElement(inputs, offset, inputObject);
        }

        pEnvironment->SetObjectField(pTransaction, sFullTransactionInputsID, inputs);

        // Outputs
        jobjectArray outputs =
          pEnvironment->NewObjectArray((jsize)transaction.transaction.outputs.size(), sOutputClass,
          NULL);

        jobject outputObject;
        offset = 0;
        for(std::vector<BitCoin::Output>::iterator output =
          transaction.transaction.outputs.begin();
          output != transaction.transaction.outputs.end(); ++output, ++offset)
        {
            outputObject = pEnvironment->NewObject(sOutputClass, sOutputConstructor);

            // Amount
            pEnvironment->SetLongField(outputObject, sOutputAmountID, (jlong)output->amount);

            // Script
            pEnvironment->SetObjectField(outputObject, sOutputScriptID,
              pEnvironment->NewStringUTF(BitCoin::ScriptInterpreter::scriptText(output->script,
                daemon->chain()->forks(), daemon->chain()->headerHeight())));

            // Address
            if(transaction.outputAddresses[offset])
                pEnvironment->SetObjectField(outputObject, sOutputAddressID,
                  pEnvironment->NewStringUTF(transaction.outputAddresses[offset]));

            // Related
            pEnvironment->SetBooleanField(outputObject, sOutputRelatedID,
              (jboolean)transaction.relatedOutputs[offset]);

            pEnvironment->SetObjectArrayElement(outputs, offset, outputObject);
        }

        pEnvironment->SetObjectField(pTransaction, sFullTransactionOutputsID, outputs);

        // Lock Time
        pEnvironment->SetIntField(pTransaction, sFullTransactionLockTimeID,
          (jint)transaction.transaction.lockTime);

        return JNI_TRUE;
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_sendStandardPayment(JNIEnv *pEnvironment,
                                                                                         jobject pObject,
                                                                                         jint pWalletOffset,
                                                                                         jstring pPassCode,
                                                                                         jstring pAddress,
                                                                                         jlong pAmount,
                                                                                         jdouble pFeeRate,
                                                                                         jboolean pUsePending,
                                                                                         jboolean pSendAll)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pWalletOffset)
            return (jint)1;

        if(!loadPrivateKeys(pEnvironment, daemon, pPassCode))
            return (jint)1;

        NextCash::Hash hash;
        BitCoin::AddressType type;
        const char *address = pEnvironment->GetStringUTFChars(pAddress, NULL);
        if(!BitCoin::decodeCashAddress(address, hash, type) &&
          !BitCoin::decodeLegacyAddress(address, hash, type))
        {
            pEnvironment->ReleaseStringUTFChars(pAddress, address);
            daemon->keyStore()->unloadPrivate();
            return (jint)3; // Invalid Hash
        }
        pEnvironment->ReleaseStringUTFChars(pAddress, address);

        int result = daemon->sendStandardPayment((unsigned int)pWalletOffset, type, hash,
          (uint64_t)pAmount, pFeeRate, pUsePending, pSendAll);

        if(!daemon->saveMonitor() && result == 0)
            result = 1;
        if(!savePrivateKeys(pEnvironment, daemon, pPassCode) && result == 0)
            result = 1;
        if(!savePublicKeys(daemon) && result == 0)
            result = 1;

        daemon->keyStore()->unloadPrivate();
        return (jint)result;
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_sendOutputPayment(JNIEnv *pEnvironment,
                                                                                       jobject pObject,
                                                                                       jint pWalletOffset,
                                                                                       jstring pPassCode,
                                                                                       jbyteArray pOutputScript,
                                                                                       jlong pAmount,
                                                                                       jdouble pFeeRate,
                                                                                       jboolean pUsePending)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pWalletOffset)
            return (jint)1;

        if(!loadPrivateKeys(pEnvironment, daemon, pPassCode))
            return (jint)1;

        NextCash::Buffer outputScript;
        jbyte *outputScriptBytes = pEnvironment->GetByteArrayElements(pOutputScript, NULL);
        outputScript.write(outputScriptBytes,
          (NextCash::stream_size)pEnvironment->GetArrayLength(pOutputScript));
        pEnvironment->ReleaseByteArrayElements(pOutputScript, outputScriptBytes, 0);

        if(outputScript.length() == 0)
        {
            daemon->keyStore()->unloadPrivate();
            return (jint)3; // Invalid Hash
        }

        int result = daemon->sendSpecifiedOutputPayment((unsigned int)pWalletOffset, outputScript,
          (uint64_t)pAmount, pFeeRate, pUsePending, false);

        if(!daemon->saveMonitor() && result == 0)
            result = 1;
        if(!savePrivateKeys(pEnvironment, daemon, pPassCode) && result == 0)
            result = 1;
        if(!savePublicKeys(daemon) && result == 0)
            result = 1;

        daemon->keyStore()->unloadPrivate();
        return (jint)result;
    }

    JNIEXPORT jbyteArray JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getRawTransaction(JNIEnv *pEnvironment,
                                                                                             jobject pObject,
                                                                                             jbyteArray pPayingOutputScript,
                                                                                             jlong pAmount)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return NULL;

        NextCash::Buffer outputScript;
        jbyte *outputScriptBytes = pEnvironment->GetByteArrayElements(pPayingOutputScript, NULL);
        outputScript.write(outputScriptBytes,
          (NextCash::stream_size)pEnvironment->GetArrayLength(pPayingOutputScript));
        pEnvironment->ReleaseByteArrayElements(pPayingOutputScript, outputScriptBytes, 0);
        if(outputScript.length() == 0)
            return NULL;

        BitCoin::Transaction *transaction = daemon->monitor()->findTransactionPaying(outputScript,
          (int64_t)pAmount);
        if(transaction == NULL)
            return NULL;

        NextCash::Log::addFormatted(NextCash::Log::INFO, NEXTCASH_JNI_LOG_NAME,
          "Found raw transaction : %s", transaction->hash.hex().text());

        NextCash::Buffer data;
        transaction->write(&data);
        jbyteArray result = pEnvironment->NewByteArray((jsize)data.length());
        pEnvironment->SetByteArrayRegion(result, 0, (jsize)data.length(),
          (const jbyte *)data.startPointer());
        return result;
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isValidSeedWord(JNIEnv *pEnvironment,
                                                                                         jobject pObject,
                                                                                         jstring pWord)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return JNI_FALSE;

        const char *word = pEnvironment->GetStringUTFChars(pWord, NULL);
        for(unsigned int i = 0; i < BitCoin::Mnemonic::WORD_COUNT; ++i)
        {
            if(std::strcmp(word, BitCoin::Mnemonic::WORDS[BitCoin::Mnemonic::English][i]) == 0)
            {
                pEnvironment->ReleaseStringUTFChars(pWord, word);
                return JNI_TRUE;
            }
        }

        pEnvironment->ReleaseStringUTFChars(pWord, word);
        return JNI_FALSE;
    }

    JNIEXPORT jobjectArray JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getMnemonicWords(JNIEnv *pEnvironment,
                                                                                              jobject pObject,
                                                                                              jstring pStartingWith)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return NULL;

        std::vector<jstring> matching;
        matching.reserve(BitCoin::Mnemonic::WORD_COUNT);
        const char *word, *wordPtr, *startPtr;
        const char *startingWith = pEnvironment->GetStringUTFChars(pStartingWith, NULL);
        bool matches;
        for(unsigned int i = 0; i < BitCoin::Mnemonic::WORD_COUNT; ++i)
        {
            word = BitCoin::Mnemonic::WORDS[BitCoin::Mnemonic::English][i];
            matches = true;

            startPtr = startingWith;
            for(wordPtr=word;*wordPtr && *startPtr;++wordPtr,++startPtr)
                if(*wordPtr != *startPtr)
                {
                    matches = false;
                    break;
                }

            if(matches)
                matching.push_back(pEnvironment->NewStringUTF(word));
        }

        pEnvironment->ReleaseStringUTFChars(pStartingWith, startingWith);

        jobjectArray result = pEnvironment->NewObjectArray((jsize)matching.size(),
          pEnvironment->FindClass("java/lang/String"), NULL);

        jsize index = 0;
        for(std::vector<jstring>::iterator iter=matching.begin();iter!=matching.end();++iter,++index)
            pEnvironment->SetObjectArrayElement(result, index, *iter);

        return result;
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_test(JNIEnv *pEnvironment,
                                                                              jobject pObject)
    {
        return (jboolean)BitCoin::test();
    }

    jobject createOutput(JNIEnv *pEnvironment, BitCoin::Daemon *pDaemon, BitCoin::Output &pOutput,
      bool pIsRelated)
    {
        jobject result = pEnvironment->NewObject(sOutputClass, sOutputConstructor);

        // Set amount
        pEnvironment->SetLongField(result, sOutputAmountID, pOutput.amount);

        // Set script
        pOutput.script.setReadOffset(0);
        pEnvironment->SetObjectField(result, sOutputScriptID,
          pEnvironment->NewStringUTF(BitCoin::ScriptInterpreter::scriptText(pOutput.script,
            pDaemon->chain()->forks(), pDaemon->chain()->headerHeight())));

        // Set address
        NextCash::HashList payAddresses;
        pOutput.script.setReadOffset(0);
        BitCoin::ScriptInterpreter::ScriptType scriptType =
          BitCoin::ScriptInterpreter::parseOutputScript(pOutput.script, payAddresses);
        if(scriptType == BitCoin::ScriptInterpreter::P2PKH && payAddresses.size() == 1)
        {
            pEnvironment->SetObjectField(result, sOutputAddressID,
              pEnvironment->NewStringUTF(BitCoin::encodeCashAddress(payAddresses.front())));
        }

        // Set related
        pEnvironment->SetBooleanField(result, sOutputRelatedID, (jboolean)pIsRelated);

        return result;
    }

    jobject createOutpoint(JNIEnv *pEnvironment, BitCoin::Daemon *pDaemon, BitCoin::Outpoint &pOutpoint)
    {
        jobject result = pEnvironment->NewObject(sOutpointClass, sOutpointConstructor);

        // Set transactionID
        pEnvironment->SetObjectField(result, sOutpointTransactionID,
          pEnvironment->NewStringUTF(pOutpoint.transactionID.hex()));

        // Set index
        pEnvironment->SetIntField(result, sOutpointIndexID, pOutpoint.index);

        // Set output
        pEnvironment->SetObjectField(result, sOutpointOutputID,
          createOutput(pEnvironment, pDaemon, *pOutpoint.output, true));

        // Confirmations
        if(pOutpoint.confirmations != 0xffffffff)
            pEnvironment->SetIntField(result, sOutpointConfirmationsID, pOutpoint.confirmations);
        else
        {
            NextCash::Hash confirmBlock =
              pDaemon->monitor()->confirmBlockHash(pOutpoint.transactionID);
            jint confirms = 0;
            if(!confirmBlock.isEmpty())
                confirms = pDaemon->chain()->headerHeight() -
                  pDaemon->chain()->hashHeight(confirmBlock);
            pEnvironment->SetIntField(result, sOutpointConfirmationsID, confirms);
        }

        return result;
    }

    JNIEXPORT jobjectArray JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getUnspentOutputs(JNIEnv *pEnvironment,
                                                                                               jobject pObject,
                                                                                               jint pWalletOffset)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pWalletOffset)
            return NULL;

        std::vector<BitCoin::Key *> *chainKeys =
          daemon->keyStore()->chainKeys((unsigned int)pWalletOffset);
        if(chainKeys == NULL)
            return NULL;

        setupOutputClass(pEnvironment);
        setupOutpointClass(pEnvironment);

        // Get UTXOs from monitor
        std::vector<BitCoin::Outpoint> unspentOutputs;
        if(!daemon->monitor()->getUnspentOutputs(chainKeys->begin(), chainKeys->end(),
          unspentOutputs, daemon->chain(), true))
            return NULL;

        jobjectArray result = pEnvironment->NewObjectArray((jsize)unspentOutputs.size(),
          sOutpointClass, NULL);

        unsigned int index = 0;
        for(std::vector<BitCoin::Outpoint>::iterator output = unspentOutputs.begin();
          output != unspentOutputs.end(); ++output, ++index)
        {
            pEnvironment->SetObjectArrayElement(result, index,
              createOutpoint(pEnvironment, daemon, *output));
        }

        return result;
    }
}
