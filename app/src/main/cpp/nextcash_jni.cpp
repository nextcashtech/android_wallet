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

    static jfieldID sBitcoinHandleID = NULL;

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setupJNI(JNIEnv *pEnvironment,
                                                                              jclass pClass)
    {
        sBitcoinHandleID = pEnvironment->GetFieldID(pClass, "mHandle", "J");
    }

    class Instance
    {
    public:
        BitCoin::Daemon *daemon;

        // Static Class/Field IDs
        // Bitcoin
        jclass jBitcoinClass;
        jfieldID jBitcoinWalletsLoadedID;
        jfieldID jBitcoinWalletsID;
        jfieldID jBitcoinChainLoadedID;

        // Wallet
        jclass jWalletClass;
        jfieldID jWalletLastUpdatedID;
        jfieldID jWalletTransactionsID;
        jfieldID jWalletUpdatedTransactionsID;
        jfieldID jWalletIsPrivateID;
        jfieldID jWalletNameID;
        jfieldID jWalletIsSynchronizedID;
        jfieldID jWalletIsBackedUpID;
        jfieldID jWalletBalanceID;
        jfieldID jWalletPendingBalanceID;
        jfieldID jWalletBlockHeightID;

        // Transaction
        jclass jTransactionClass;
        jmethodID jTransactionConstructor;
        jfieldID jTransactionHashID;
        jfieldID jTransactionBlockID;
        jfieldID jTransactionDateID;
        jfieldID jTransactionAmountID;
        jfieldID jTransactionCountID;

        // Block
        jclass jBlockClass;
        jmethodID jBlockConstructor;
        jfieldID jBlockHeightID;
        jfieldID jBlockHashID;
        jfieldID jBlockTimeID;

        // PaymentRequest
        jclass jPaymentRequestClass;
        jmethodID jPaymentRequestConstructor;
        jfieldID jPaymentRequestURIID;
        jfieldID jPaymentRequestFormatID;
        jfieldID jPaymentRequestTypeID;
        jfieldID jPaymentRequestAddressID;
        jfieldID jPaymentRequestAmountID;
        jfieldID jPaymentRequestAmountSpecifiedID;
        jfieldID jPaymentRequestLabelID;
        jfieldID jPaymentRequestMessageID;
        jfieldID jPaymentRequestSecureID;
        jfieldID jPaymentRequestSecureURLID;

        // KeyData
        jclass jKeyDataClass;
        jmethodID jKeyDataConstructor;
        jfieldID jKeyDataVersionID;
        jfieldID jKeyDataDepthID;
        jfieldID jKeyDataIndexID;

        // Input
        jclass jInputClass;
        jmethodID jInputConstructor;
        jfieldID jInputOutpointID;
        jfieldID jInputOutpointIndexID;
        jfieldID jInputScriptID;
        jfieldID jInputSequenceID;
        jfieldID jInputAddressID;
        jfieldID jInputAmountID;

        // Output
        jclass jOutputClass;
        jmethodID jOutputConstructor;
        jfieldID jOutputAmountID;
        jfieldID jOutputScriptID;
        jfieldID jOutputScriptDataID;
        jfieldID jOutputAddressID;
        jfieldID jOutputRelatedID;

        // Full Transaction
        jclass jFullTransactionClass;
        jmethodID jFullTransactionConstructor;
        jfieldID jFullTransactionHashID;
        jfieldID jFullTransactionBlockID;
        jfieldID jFullTransactionCountID;
        jfieldID jFullTransactionVersionID;
        jfieldID jFullTransactionInputsID;
        jfieldID jFullTransactionOutputsID;
        jfieldID jFullTransactionLockTimeID;
        jfieldID jFullTransactionDateID;
        jfieldID jFullTransactionSizeID;

        // Outpoint
        jclass jOutpointClass;
        jmethodID jOutpointConstructor;
        jfieldID jOutpointTransactionID;
        jfieldID jOutpointIndexID;
        jfieldID jOutpointOutputID;
        jfieldID jOutpointConfirmationsID;

        // SendResult
        jclass jSendResultClass;
        jmethodID jSendResultConstructor;
        jfieldID jSendResultResultID;
        jfieldID jSendResultTransactionID;
        jfieldID jSendResultRawTransactionID;

        Instance(JNIEnv *pEnvironment)
        {
            daemon = new BitCoin::Daemon();

            // Bitcoin
            jBitcoinClass = reinterpret_cast<jclass>(pEnvironment->NewGlobalRef(
              pEnvironment->FindClass("tech/nextcash/nextcashwallet/Bitcoin")));
            jBitcoinWalletsLoadedID = pEnvironment->GetFieldID(jBitcoinClass, "mWalletsLoaded",
              "Z");
            jBitcoinChainLoadedID = pEnvironment->GetFieldID(jBitcoinClass, "mChainLoaded", "Z");
            jBitcoinWalletsID = pEnvironment->GetFieldID(jBitcoinClass, "mWallets",
              "[Ltech/nextcash/nextcashwallet/Wallet;");

            // Wallet
            jWalletClass = NULL;
            jWalletLastUpdatedID = NULL;
            jWalletTransactionsID = NULL;
            jWalletUpdatedTransactionsID = NULL;
            jWalletIsPrivateID = NULL;
            jWalletNameID = NULL;
            jWalletIsSynchronizedID = NULL;
            jWalletIsBackedUpID = NULL;
            jWalletBalanceID = NULL;
            jWalletPendingBalanceID = NULL;
            jWalletBlockHeightID = NULL;

            // Transaction
            jTransactionClass = NULL;
            jTransactionConstructor = NULL;
            jTransactionHashID = NULL;
            jTransactionBlockID = NULL;
            jTransactionDateID = NULL;
            jTransactionAmountID = NULL;
            jTransactionCountID = NULL;

            // Block
            jBlockClass = NULL;
            jBlockConstructor = NULL;
            jBlockHeightID = NULL;
            jBlockHashID = NULL;
            jBlockTimeID = NULL;

            // PaymentRequest
            jPaymentRequestClass = NULL;
            jPaymentRequestConstructor = NULL;
            jPaymentRequestURIID = NULL;
            jPaymentRequestFormatID = NULL;
            jPaymentRequestTypeID = NULL;
            jPaymentRequestAddressID = NULL;
            jPaymentRequestAmountID = NULL;
            jPaymentRequestAmountSpecifiedID = NULL;
            jPaymentRequestLabelID = NULL;
            jPaymentRequestMessageID = NULL;
            jPaymentRequestSecureID = NULL;
            jPaymentRequestSecureURLID = NULL;

            // KeyData
            jKeyDataClass = NULL;
            jKeyDataConstructor = NULL;
            jKeyDataVersionID = NULL;
            jKeyDataDepthID = NULL;
            jKeyDataIndexID = NULL;

            // Input
            jInputClass = NULL;
            jInputConstructor = NULL;
            jInputOutpointID = NULL;
            jInputOutpointIndexID = NULL;
            jInputScriptID = NULL;
            jInputSequenceID = NULL;
            jInputAddressID = NULL;
            jInputAmountID = NULL;

            // Output
            jOutputClass = NULL;
            jOutputConstructor = NULL;
            jOutputAmountID = NULL;
            jOutputScriptID = NULL;
            jOutputScriptDataID = NULL;
            jOutputAddressID = NULL;
            jOutputRelatedID = NULL;

            // Full Transaction
            jFullTransactionClass = NULL;
            jFullTransactionConstructor = NULL;
            jFullTransactionHashID = NULL;
            jFullTransactionBlockID = NULL;
            jFullTransactionCountID = NULL;
            jFullTransactionVersionID = NULL;
            jFullTransactionInputsID = NULL;
            jFullTransactionOutputsID = NULL;
            jFullTransactionLockTimeID = NULL;
            jFullTransactionDateID = NULL;
            jFullTransactionSizeID = NULL;

            // Outpoint
            jOutpointClass = NULL;
            jOutpointConstructor = NULL;
            jOutpointTransactionID = NULL;
            jOutpointIndexID = NULL;
            jOutpointOutputID = NULL;
            jOutpointConfirmationsID = NULL;

            // SendResult
            jSendResultClass = NULL;
            jSendResultConstructor = NULL;
            jSendResultResultID = NULL;
            jSendResultTransactionID = NULL;
            jSendResultRawTransactionID = NULL;

        }

        void clean(JNIEnv *pEnvironment)
        {
            if(jBitcoinClass != NULL)
            {
                pEnvironment->DeleteGlobalRef(jBitcoinClass);
                jBitcoinClass = NULL;
            }

            if(jWalletClass != NULL)
            {
                pEnvironment->DeleteGlobalRef(jWalletClass);
                jWalletClass = NULL;
            }

            if(jTransactionClass != NULL)
            {
                pEnvironment->DeleteGlobalRef(jTransactionClass);
                jTransactionClass = NULL;
            }

            if(jBlockClass != NULL)
            {
                pEnvironment->DeleteGlobalRef(jBlockClass);
                jBlockClass = NULL;
            }

            if(jPaymentRequestClass != NULL)
            {
                pEnvironment->DeleteGlobalRef(jPaymentRequestClass);
                jPaymentRequestClass = NULL;
            }

            if(jKeyDataClass != NULL)
            {
                pEnvironment->DeleteGlobalRef(jKeyDataClass);
                jKeyDataClass = NULL;
            }

            if(jInputClass != NULL)
            {
                pEnvironment->DeleteGlobalRef(jInputClass);
                jInputClass = NULL;
            }

            if(jFullTransactionClass != NULL)
            {
                pEnvironment->DeleteGlobalRef(jFullTransactionClass);
                jFullTransactionClass = NULL;
            }

            if(jOutputClass != NULL)
            {
                pEnvironment->DeleteGlobalRef(jOutputClass);
                jOutputClass = NULL;
            }

            if(jOutpointClass != NULL)
            {
                pEnvironment->DeleteGlobalRef(jOutpointClass);
                jOutpointClass = NULL;
            }

            if(jSendResultClass != NULL)
            {
                pEnvironment->DeleteGlobalRef(jSendResultClass);
                jSendResultClass = NULL;
            }
        }

        void setupWalletClass(JNIEnv *pEnvironment)
        {
            if(jWalletClass != NULL)
              return;

            jWalletClass = reinterpret_cast<jclass>(pEnvironment->NewGlobalRef(
              pEnvironment->FindClass("tech/nextcash/nextcashwallet/Wallet")));
            jWalletLastUpdatedID = pEnvironment->GetFieldID(jWalletClass, "lastUpdated", "J");
            jWalletTransactionsID = pEnvironment->GetFieldID(jWalletClass, "transactions",
              "[Ltech/nextcash/nextcashwallet/Transaction;");
            jWalletUpdatedTransactionsID = pEnvironment->GetFieldID(jWalletClass, "updatedTransactions",
              "[Ltech/nextcash/nextcashwallet/Transaction;");
            jWalletIsPrivateID = pEnvironment->GetFieldID(jWalletClass, "isPrivate", "Z");
            jWalletNameID = pEnvironment->GetFieldID(jWalletClass, "name", "Ljava/lang/String;");
            jWalletIsSynchronizedID = pEnvironment->GetFieldID(jWalletClass, "isSynchronized", "Z");
            jWalletIsBackedUpID = pEnvironment->GetFieldID(jWalletClass, "isBackedUp", "Z");
            jWalletBalanceID = pEnvironment->GetFieldID(jWalletClass, "balance", "J");
            jWalletPendingBalanceID = pEnvironment->GetFieldID(jWalletClass, "pendingBalance", "J");
            jWalletBlockHeightID = pEnvironment->GetFieldID(jWalletClass, "blockHeight", "I");
        }

        void setupTransactionClass(JNIEnv *pEnvironment)
        {
            if(jTransactionClass != NULL)
                return;

            jTransactionClass = reinterpret_cast<jclass>(pEnvironment->NewGlobalRef(
              pEnvironment->FindClass("tech/nextcash/nextcashwallet/Transaction")));
            jTransactionConstructor = pEnvironment->GetMethodID(jTransactionClass, "<init>", "()V");
            jTransactionHashID = pEnvironment->GetFieldID(jTransactionClass, "hash",
              "Ljava/lang/String;");
            jTransactionBlockID = pEnvironment->GetFieldID(jTransactionClass, "block",
              "Ljava/lang/String;");
            jTransactionDateID = pEnvironment->GetFieldID(jTransactionClass, "date", "J");
            jTransactionAmountID = pEnvironment->GetFieldID(jTransactionClass, "amount", "J");
            jTransactionCountID = pEnvironment->GetFieldID(jTransactionClass, "count", "I");
        }

        void setupBlockClass(JNIEnv *pEnvironment)
        {
            if(jBlockClass != NULL)
                return;

            jBlockClass = reinterpret_cast<jclass>(pEnvironment->NewGlobalRef(
              pEnvironment->FindClass("tech/nextcash/nextcashwallet/Block")));
            jBlockConstructor = pEnvironment->GetMethodID(jBlockClass, "<init>", "()V");
            jBlockHeightID = pEnvironment->GetFieldID(jBlockClass, "height", "I");
            jBlockHashID = pEnvironment->GetFieldID(jBlockClass, "hash", "Ljava/lang/String;");
            jBlockTimeID = pEnvironment->GetFieldID(jBlockClass, "time", "J");
        }

        void setupPaymentRequestClass(JNIEnv *pEnvironment)
        {
            if(jPaymentRequestClass != NULL)
                return;

            jPaymentRequestClass = reinterpret_cast<jclass>(pEnvironment->NewGlobalRef(
              pEnvironment->FindClass("tech/nextcash/nextcashwallet/PaymentRequest")));
            jPaymentRequestConstructor = pEnvironment->GetMethodID(jPaymentRequestClass, "<init>",
              "()V");
            jPaymentRequestURIID = pEnvironment->GetFieldID(jPaymentRequestClass, "uri",
              "Ljava/lang/String;");
            jPaymentRequestFormatID = pEnvironment->GetFieldID(jPaymentRequestClass, "format", "I");
            jPaymentRequestTypeID = pEnvironment->GetFieldID(jPaymentRequestClass, "type", "I");
            jPaymentRequestAddressID = pEnvironment->GetFieldID(jPaymentRequestClass, "address",
              "Ljava/lang/String;");
            jPaymentRequestAmountID = pEnvironment->GetFieldID(jPaymentRequestClass, "amount", "J");
            jPaymentRequestAmountSpecifiedID = pEnvironment->GetFieldID(jPaymentRequestClass,
              "amountSpecified", "Z");
            jPaymentRequestLabelID = pEnvironment->GetFieldID(jPaymentRequestClass, "label",
              "Ljava/lang/String;");
            jPaymentRequestMessageID = pEnvironment->GetFieldID(jPaymentRequestClass, "message",
              "Ljava/lang/String;");
            jPaymentRequestSecureID = pEnvironment->GetFieldID(jPaymentRequestClass, "secure", "Z");
            jPaymentRequestSecureURLID = pEnvironment->GetFieldID(jPaymentRequestClass, "secureURL",
              "Ljava/lang/String;");
        }

        void setupKeyDataClass(JNIEnv *pEnvironment)
        {
            if(jKeyDataClass != NULL)
                return;

            jKeyDataClass = reinterpret_cast<jclass>(pEnvironment->NewGlobalRef(
              pEnvironment->FindClass("tech/nextcash/nextcashwallet/KeyData")));
            jKeyDataConstructor = pEnvironment->GetMethodID(jKeyDataClass, "<init>",
              "()V");
            jKeyDataVersionID = pEnvironment->GetFieldID(jPaymentRequestClass, "version", "I");
            jKeyDataDepthID = pEnvironment->GetFieldID(jPaymentRequestClass, "depth", "I");
            jKeyDataIndexID = pEnvironment->GetFieldID(jPaymentRequestClass, "index", "I");
        }

        void setupFullTransactionClass(JNIEnv *pEnvironment)
        {
            if(jFullTransactionClass != NULL)
                return;

            // Input
            jInputClass = reinterpret_cast<jclass>(pEnvironment->NewGlobalRef(
              pEnvironment->FindClass("tech/nextcash/nextcashwallet/Input")));
            jInputConstructor = pEnvironment->GetMethodID(jInputClass, "<init>", "()V");
            jInputOutpointID = pEnvironment->GetFieldID(jInputClass, "outpointID",
              "Ljava/lang/String;");
            jInputOutpointIndexID = pEnvironment->GetFieldID(jInputClass, "outpointIndex", "I");
            jInputScriptID = pEnvironment->GetFieldID(jInputClass, "script", "Ljava/lang/String;");
            jInputSequenceID = pEnvironment->GetFieldID(jInputClass, "sequence", "I");
            jInputAddressID = pEnvironment->GetFieldID(jInputClass, "address",
              "Ljava/lang/String;");
            jInputAmountID = pEnvironment->GetFieldID(jInputClass, "amount", "J");

            setupOutputClass(pEnvironment);

            // Full Transaction
            jFullTransactionClass = reinterpret_cast<jclass>(pEnvironment->NewGlobalRef(
              pEnvironment->FindClass("tech/nextcash/nextcashwallet/FullTransaction")));
            jFullTransactionConstructor = pEnvironment->GetMethodID(jFullTransactionClass, "<init>", "()V");
            jFullTransactionHashID = pEnvironment->GetFieldID(jFullTransactionClass, "hash",
              "Ljava/lang/String;");
            jFullTransactionBlockID = pEnvironment->GetFieldID(jFullTransactionClass, "block",
              "Ljava/lang/String;");
            jFullTransactionCountID = pEnvironment->GetFieldID(jFullTransactionClass, "count", "I");
            jFullTransactionVersionID = pEnvironment->GetFieldID(jFullTransactionClass, "version", "I");
            jFullTransactionInputsID = pEnvironment->GetFieldID(jFullTransactionClass, "inputs",
              "[Ltech/nextcash/nextcashwallet/Input;");
            jFullTransactionOutputsID = pEnvironment->GetFieldID(jFullTransactionClass, "outputs",
              "[Ltech/nextcash/nextcashwallet/Output;");
            jFullTransactionLockTimeID = pEnvironment->GetFieldID(jFullTransactionClass, "lockTime",
              "I");
            jFullTransactionDateID = pEnvironment->GetFieldID(jFullTransactionClass, "date",
              "J");
            jFullTransactionSizeID = pEnvironment->GetFieldID(jFullTransactionClass, "size",
              "I");
        }

        void setupOutputClass(JNIEnv *pEnvironment)
        {
            if(jOutputClass != NULL)
                return;

            jOutputClass = reinterpret_cast<jclass>(pEnvironment->NewGlobalRef(
              pEnvironment->FindClass("tech/nextcash/nextcashwallet/Output")));
            jOutputConstructor = pEnvironment->GetMethodID(jOutputClass, "<init>", "()V");
            jOutputAmountID = pEnvironment->GetFieldID(jOutputClass, "amount", "J");
            jOutputScriptID = pEnvironment->GetFieldID(jOutputClass, "script", "Ljava/lang/String;");
            jOutputScriptDataID = pEnvironment->GetFieldID(jOutputClass,"scriptData", "[B");
            jOutputAddressID = pEnvironment->GetFieldID(jOutputClass, "address", "Ljava/lang/String;");
            jOutputRelatedID = pEnvironment->GetFieldID(jOutputClass, "related", "Z");
        }

        void setupOutpointClass(JNIEnv *pEnvironment)
        {
            if(jOutpointClass != NULL)
                return;

            jOutpointClass = reinterpret_cast<jclass>(pEnvironment->NewGlobalRef(
              pEnvironment->FindClass("tech/nextcash/nextcashwallet/Outpoint")));
            jOutpointConstructor = pEnvironment->GetMethodID(jOutpointClass, "<init>", "()V");
            jOutpointTransactionID = pEnvironment->GetFieldID(jOutpointClass, "transactionID",
              "Ljava/lang/String;");
            jOutpointIndexID = pEnvironment->GetFieldID(jOutpointClass, "index", "I");
            jOutpointOutputID = pEnvironment->GetFieldID(jOutpointClass, "output",
              "Ltech/nextcash/nextcashwallet/Output;");
            jOutpointConfirmationsID = pEnvironment->GetFieldID(jOutpointClass, "confirmations", "I");
        }

        void setupSendResultClass(JNIEnv *pEnvironment)
        {
            if(jSendResultClass != NULL)
                return;

            setupTransactionClass(pEnvironment);

            jSendResultClass = reinterpret_cast<jclass>(pEnvironment->NewGlobalRef(
              pEnvironment->FindClass("tech/nextcash/nextcashwallet/SendResult")));
            jSendResultConstructor = pEnvironment->GetMethodID(jSendResultClass, "<init>", "()V");
            jSendResultResultID = pEnvironment->GetFieldID(jSendResultClass, "result", "I");
            jSendResultTransactionID = pEnvironment->GetFieldID(jSendResultClass, "transaction",
              "Ltech/nextcash/nextcashwallet/Transaction;");
            jSendResultRawTransactionID = pEnvironment->GetFieldID(jSendResultClass, "rawTransaction",
              "[B");
        }

    };

    Instance *getInstance(JNIEnv *pEnvironment, jobject pBitcoinObject, bool pCreate = false)
    {
        jlong handle = pEnvironment->GetLongField(pBitcoinObject, sBitcoinHandleID);
        if(handle != 0)
            return reinterpret_cast<Instance *>(handle);

        if(!pCreate)
            return NULL;

        // Create handle
        // NOTE : Daemon object apparently needs to be created in the thread running it. So make
        //   sure the functions with pCreate=true are only called on that Java thread.
        Instance *instance = new Instance(pEnvironment);
        handle = jlong(instance);

        pEnvironment->SetLongField(pBitcoinObject, sBitcoinHandleID, handle);
        NextCash::Log::add(NextCash::Log::INFO, NEXTCASH_JNI_LOG_NAME, "Bitcoin handle created");

        return instance;
    }

    BitCoin::Daemon *getDaemon(JNIEnv *pEnvironment, jobject pBitcoinObject, bool pCreate = false)
    {
        Instance *instance = getInstance(pEnvironment, pBitcoinObject, pCreate);
        if(instance != NULL)
            return instance->daemon;
        else
            return NULL;
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_destroy(JNIEnv *pEnvironment,
                                                                             jobject pObject)
    {
        Instance *instance = getInstance(pEnvironment, pObject, false);
        if(instance == NULL)
            return;

        // Zeroize handle in java object
        pEnvironment->SetLongField(pObject, sBitcoinHandleID, 0);
        pEnvironment->SetBooleanField(pObject, instance->jBitcoinWalletsLoadedID, JNI_FALSE);
        pEnvironment->SetBooleanField(pObject, instance->jBitcoinChainLoadedID, JNI_FALSE);

        // Delete C++ object
        instance->clean(pEnvironment);
        delete instance;
        BitCoin::Info::destroy();

        NextCash::Log::add(NextCash::Log::INFO, NEXTCASH_JNI_LOG_NAME, "Bitcoin handle destroyed");
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setup(JNIEnv *pEnvironment,
                                                                           jobject pObject,
                                                                           jstring pPath,
                                                                           jint pChainID)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject, true);
        if(daemon == NULL)
            return;

        const char *newPath = pEnvironment->GetStringUTFChars(pPath, NULL);
        BitCoin::Info::setPath(newPath);
        pEnvironment->ReleaseStringUTFChars(pPath, newPath);

        BitCoin::Info &info = BitCoin::Info::instance();
        switch(pChainID)
        {
        case 1: // ABC
            info.configureChain(BitCoin::CHAIN_ABC);
            break;
        case 2: // SV
            info.configureChain(BitCoin::CHAIN_SV);
            break;
        default:
            break;
        }
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

        daemon->monitor()->ensurePassIsActive(daemon->chain()->headerHeight());
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

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_chainID(JNIEnv *pEnvironment,
                                                                             jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return (jint)0;

        if(daemon->chain()->hashHeight(BitCoin::ABC_SPLIT_HASH) !=
          BitCoin::Chain::INVALID_HEIGHT)
            return (jint)1;
        else if(daemon->chain()->hashHeight(BitCoin::SV_SPLIT_HASH) !=
          BitCoin::Chain::INVALID_HEIGHT)
            return (jint)2;
        else
            return (jint)0;
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_activateChain(JNIEnv *pEnvironment,
                                                                                       jobject pObject,
                                                                                       jint pChainID)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || !daemon->chainIsLoaded())
            return JNI_FALSE;

        BitCoin::Info &info = BitCoin::Info::instance();
        NextCash::Hash desiredHash;
        unsigned int splitHeight;

        switch(pChainID)
        {
        case 1: // ABC
            desiredHash = BitCoin::ABC_SPLIT_HASH;
            splitHeight = BitCoin::ABC_SPLIT_HEIGHT;
            info.configureChain(BitCoin::CHAIN_ABC);
            break;
        case 2: // SV
            desiredHash = BitCoin::SV_SPLIT_HASH;
            splitHeight = BitCoin::SV_SPLIT_HEIGHT;
            info.configureChain(BitCoin::CHAIN_SV);
            break;
        default:
            return JNI_FALSE;
        }

        // Check if we are already on desired chain.
        if(daemon->chain()->hashHeight(desiredHash) != BitCoin::Chain::INVALID_HEIGHT)
        {
            daemon->monitor()->incrementChange();
            return JNI_TRUE;
        }

        // Revert to chain height before split.
        daemon->monitor()->incrementChange();
        daemon->resetNodes();
        daemon->chain()->clearInSync();
        return (jboolean)daemon->chain()->revert(splitHeight - 1);
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_peerCount(JNIEnv *pEnvironment,
                                                                               jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return 0;

        return daemon->peerCount();
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_resetPeers(JNIEnv *pEnvironment,
                                                                                jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || !daemon->chainIsLoaded())
            return;

        BitCoin::Info::instance().resetPeers();
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

    jobject createBlock(Instance *pInstance, JNIEnv *pEnvironment, int pHeight, jstring pHash,
      int32_t pTime)
    {
        pInstance->setupBlockClass(pEnvironment);

        jobject result = pEnvironment->NewObject(pInstance->jBlockClass,
          pInstance->jBlockConstructor);

        // Set height
        pEnvironment->SetIntField(result, pInstance->jBlockHeightID, (jint)pHeight);

        // Set hash
        pEnvironment->SetObjectField(result, pInstance->jBlockHashID, pHash);

        // Set time
        pEnvironment->SetLongField(result, pInstance->jBlockTimeID, (jlong)pTime);

        return result;
    }

    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getBlockFromHeight(JNIEnv *pEnvironment,
                                                                                           jobject pObject,
                                                                                           jint pHeight)
    {
        Instance *instance = getInstance(pEnvironment, pObject);
        if(instance == NULL)
            return NULL;

        instance->setupBlockClass(pEnvironment);

        NextCash::Hash hash;
        if(!instance->daemon->chain()->getHash((unsigned int)pHeight, hash))
            return NULL;

        return createBlock(instance, pEnvironment, pHeight,
          pEnvironment->NewStringUTF(hash.hex().text()),
          instance->daemon->chain()->time((unsigned int)pHeight));
    }

    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getBlockFromHash(JNIEnv *pEnvironment,
                                                                                         jobject pObject,
                                                                                         jstring pHash)
    {
        Instance *instance = getInstance(pEnvironment, pObject);
        if(instance == NULL)
            return NULL;

        instance->setupBlockClass(pEnvironment);

        const char *hashHex = pEnvironment->GetStringUTFChars(pHash, NULL);
        NextCash::Hash hash(hashHex);
        int height = instance->daemon->chain()->hashHeight(hash);
        pEnvironment->ReleaseStringUTFChars(pHash, hashHex);

        return createBlock(instance, pEnvironment, height, pHash,
          instance->daemon->chain()->time((unsigned int)height));
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

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_importKeys(JNIEnv *pEnvironment,
                                                                                jobject pObject,
                                                                                jstring pPassCode,
                                                                                jint pType,
                                                                                jobjectArray pKeys,
                                                                                jstring pName,
                                                                                jlong pRecoverTime,
                                                                                jint pGap)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return (jint)1;

        if(!loadPrivateKeys(pEnvironment, daemon, pPassCode))
            return (jint)5;

        int result;
        switch(pType)
        {
        case 0: // Chain
        {
            if(pEnvironment->GetArrayLength(pKeys) != 2)
                return (jint)1;

            BitCoin::Key *receivingKey = new BitCoin::Key();
            jstring keyString = (jstring)pEnvironment->GetObjectArrayElement(pKeys, 0);
            const char *keyText = pEnvironment->GetStringUTFChars(keyString, NULL);
            if(!receivingKey->decode(keyText))
            {
                delete receivingKey;
                pEnvironment->ReleaseStringUTFChars(keyString, keyText);
                return (jint)2; // Invalid format
            }
            if(receivingKey->version() != BitCoin::Key::MAINNET_PRIVATE &&
              receivingKey->version() != BitCoin::Key::MAINNET_PUBLIC)
            {
                delete receivingKey;
                pEnvironment->ReleaseStringUTFChars(keyString, keyText);
                return (jint)2; // Invalid format
            }
            pEnvironment->ReleaseStringUTFChars(keyString, keyText);

            BitCoin::Key *changeKey = new BitCoin::Key();
            keyString = (jstring)pEnvironment->GetObjectArrayElement(pKeys, 1);
            keyText = pEnvironment->GetStringUTFChars(keyString, NULL);
            if(!changeKey->decode(keyText))
            {
                delete receivingKey;
                delete changeKey;
                pEnvironment->ReleaseStringUTFChars(keyString, keyText);
                return (jint)2; // Invalid format
            }
            if(changeKey->version() != BitCoin::Key::MAINNET_PRIVATE &&
              changeKey->version() != BitCoin::Key::MAINNET_PUBLIC)
            {
                delete receivingKey;
                delete changeKey;
                pEnvironment->ReleaseStringUTFChars(keyString, keyText);
                return (jint)2; // Invalid format
            }
            pEnvironment->ReleaseStringUTFChars(keyString, keyText);

            result = daemon->keyStore()->addFromChainKeys(receivingKey, changeKey,
              (int32_t)pRecoverTime);
            break;
        }
        case 1: // Account
        {
            if(pEnvironment->GetArrayLength(pKeys) != 1)
                return (jint)1;

            BitCoin::Key *accountKey = new BitCoin::Key();
            jstring keyString = (jstring)pEnvironment->GetObjectArrayElement(pKeys, 0);
            const char *keyText = pEnvironment->GetStringUTFChars(keyString, NULL);
            if(!accountKey->decode(keyText))
            {
                delete accountKey;
                pEnvironment->ReleaseStringUTFChars(keyString, keyText);
                return (jint)2; // Invalid format
            }
            if(accountKey->version() != BitCoin::Key::MAINNET_PRIVATE &&
              accountKey->version() != BitCoin::Key::MAINNET_PUBLIC)
            {
                delete accountKey;
                pEnvironment->ReleaseStringUTFChars(keyString, keyText);
                return (jint)2; // Invalid format
            }
            pEnvironment->ReleaseStringUTFChars(keyString, keyText);

            BitCoin::Key *receivingKey = accountKey->deriveChild(0);
            BitCoin::Key *changeKey = accountKey->deriveChild(1);

            if(receivingKey == NULL || changeKey == NULL)
            {
                // Must be a hardened key
                delete accountKey;
                return (jint)2; // Invalid format
            }

            result = daemon->keyStore()->addFromChainKeys(receivingKey, changeKey,
              (int32_t)pRecoverTime);
            break;
        }
        case 2: // Address
        {
            if(pEnvironment->GetArrayLength(pKeys) != 1)
                return (jint)1;

            jstring keyString = (jstring)pEnvironment->GetObjectArrayElement(pKeys, 0);
            const char *keyText = pEnvironment->GetStringUTFChars(keyString, NULL);
            BitCoin::PaymentRequest request = BitCoin::decodePaymentCode(keyText);
            pEnvironment->ReleaseStringUTFChars(keyString, keyText);

            if(request.format == BitCoin::PaymentRequest::INVALID ||
               request.network != BitCoin::MAINNET)
                return (jint)2; // Invalid format

            BitCoin::Key *addressKey = new BitCoin::Key();
            addressKey->loadHash(request.pubKeyHash);

            result = daemon->keyStore()->addIndividualKey(addressKey, (int32_t)pRecoverTime);
            break;
        }
        default:
            return (jint)1;
        }

        if(result == 0)
        {
            unsigned int offset = daemon->keyStore()->size() - 1;
            const char *name = pEnvironment->GetStringUTFChars(pName, NULL);
            daemon->keyStore()->setName(offset, name);
            pEnvironment->ReleaseStringUTFChars(pName, name);

            daemon->resetKeysSynchronized();
            daemon->keyStore()->setBackedUp(offset);
            if(pGap != 0)
                daemon->keyStore()->setGap(offset, pGap);
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
            return pLeft.transaction->time() > pRight.transaction->time();
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getChangeID(JNIEnv *pEnvironment,
                                                                                 jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return 0;

        return (jint)daemon->monitor()->changeID();
    }

    jobject createTransaction(Instance *pInstance, JNIEnv *pEnvironment,
      BitCoin::Monitor::RelatedTransactionData &pTransaction, bool pChainWasLoaded)
    {
        pInstance->setupTransactionClass(pEnvironment);

        jobject result = pEnvironment->NewObject(pInstance->jTransactionClass,
          pInstance->jTransactionConstructor);

        // Set hash
        pEnvironment->SetObjectField(result, pInstance->jTransactionHashID,
          pEnvironment->NewStringUTF(pTransaction.transaction->hash().hex()));

        // Set block
        if(!pTransaction.blockHash.isEmpty())
            pEnvironment->SetObjectField(result, pInstance->jTransactionBlockID,
              pEnvironment->NewStringUTF(pTransaction.blockHash.hex()));

        // Set date
        if(pTransaction.transaction->time() != 0)
            pEnvironment->SetLongField(result, pInstance->jTransactionDateID,
              (jlong)pTransaction.transaction->time());

        // Set amount
        pEnvironment->SetLongField(result, pInstance->jTransactionAmountID,
          (jlong)pTransaction.amount());

        // Set count
        if(!pChainWasLoaded)
            pEnvironment->SetIntField(result, pInstance->jTransactionCountID, (jint)-1);
        else if(pTransaction.blockHash.isEmpty())
            pEnvironment->SetIntField(result, pInstance->jTransactionCountID,
              (jint)pTransaction.nodesVerified);
        else
            pEnvironment->SetIntField(result, pInstance->jTransactionCountID,
              (jint)(pInstance->daemon->chain()->headerHeight() + 1 -
                pInstance->daemon->chain()->hashHeight(pTransaction.blockHash)));

        return result;
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_updateWallet(JNIEnv *pEnvironment,
                                                                                      jobject pBitcoin,
                                                                                      jobject pWallet,
                                                                                      jint pOffset)
    {
        Instance *instance = getInstance(pEnvironment, pBitcoin);
        if(instance == NULL || instance->daemon->keyStore()->size() <= pOffset)
            return JNI_FALSE;

        // Loop through public chain keys getting transactions
        jint headerHeight;
        int64_t balance = 0, pendingBalance = 0;
        bool chainWasLoaded = instance->daemon->chainIsLoaded();
        std::vector<BitCoin::Key *> *chainKeys =
          instance->daemon->keyStore()->chainKeys((unsigned int)pOffset);
        std::vector<BitCoin::Monitor::RelatedTransactionData> transactions;

        if(chainWasLoaded)
            headerHeight = (jint)instance->daemon->chain()->headerHeight();
        else
            headerHeight = 0;

        if(chainKeys != NULL && chainKeys->size() != 0)
        {
            instance->daemon->monitor()->getTransactions(chainKeys->begin(), chainKeys->end(),
              transactions, true);

            balance = instance->daemon->monitor()->balance(chainKeys->begin(), chainKeys->end(), false);
        }

        instance->setupWalletClass(pEnvironment);
        instance->setupTransactionClass(pEnvironment);

        bool previousUpdate = pEnvironment->GetLongField(pWallet, instance->jWalletLastUpdatedID) !=
          0;
        NextCash::HashList previousHashList, previousConfirmedHashList;
        bool hasPending = false;

        // Check for updated transactions since previous update
        if(previousUpdate)
        {
            jobjectArray previousTransactions = (jobjectArray)pEnvironment->GetObjectField(pWallet,
              instance->jWalletTransactionsID);
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
                      instance->jTransactionHashID);
                    blockString = pEnvironment->GetObjectField(previousTransaction,
                      instance->jTransactionBlockID);

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
                      ->setTime(instance->daemon->chain()->time(transaction->blockHeight));

        std::sort(transactions.begin(), transactions.end(), transGreater);

        // Set transactions
        std::vector<BitCoin::Monitor::RelatedTransactionData> newUpdatedTransactions;
        jobjectArray newTransactions = pEnvironment->NewObjectArray((jsize)transactions.size(),
          instance->jTransactionClass, NULL);
        unsigned int index = 0;

        for(std::vector<BitCoin::Monitor::RelatedTransactionData>::iterator transaction =
          transactions.begin(); transaction != transactions.end(); ++transaction, ++index)
        {
            // Set value in array
            pEnvironment->SetObjectArrayElement(newTransactions, index,
              createTransaction(instance, pEnvironment, *transaction, chainWasLoaded));

            if(transaction->blockHash.isEmpty())
                hasPending = true;

            if(previousUpdate)
            {
                // Check if this is a new transaction
                if(!previousHashList.contains(transaction->transaction->hash()))
                    newUpdatedTransactions.push_back(*transaction); // New transaction
                else if(!transaction->blockHash.isEmpty() &&
                  !previousConfirmedHashList.contains(transaction->transaction->hash()))
                    newUpdatedTransactions.push_back(*transaction); // Newly confirmed transaction
            }
        }

        pEnvironment->SetObjectField(pWallet, instance->jWalletTransactionsID, newTransactions);

        if(previousUpdate)
        {
            // Set updated transactions
            jobjectArray updatedTransactions =
              pEnvironment->NewObjectArray((jsize)newUpdatedTransactions.size(),
              instance->jTransactionClass, NULL);

            index = 0;
            for(std::vector<BitCoin::Monitor::RelatedTransactionData>::iterator transaction =
              newUpdatedTransactions.begin(); transaction != newUpdatedTransactions.end();
              ++transaction, ++index)
            {
                // Set value in array
                pEnvironment->SetObjectArrayElement(updatedTransactions, index,
                  createTransaction(instance, pEnvironment, *transaction,
                    chainWasLoaded));
            }

            pEnvironment->SetObjectField(pWallet, instance->jWalletUpdatedTransactionsID,
              updatedTransactions);
        }
        else
            pEnvironment->SetObjectField(pWallet, instance->jWalletUpdatedTransactionsID,
              pEnvironment->NewObjectArray((jsize)0, instance->jTransactionClass, NULL));

        if(hasPending)
            pendingBalance = instance->daemon->monitor()->balance(chainKeys->begin(), chainKeys->end(), true);
        else
            pendingBalance = balance;

        pEnvironment->SetObjectField(pWallet, instance->jWalletNameID,
          pEnvironment->NewStringUTF(instance->daemon->keyStore()->name((unsigned int)pOffset).text()));
        pEnvironment->SetBooleanField(pWallet, instance->jWalletIsPrivateID,
          (jboolean)instance->daemon->keyStore()->hasPrivate((unsigned int)pOffset));
        pEnvironment->SetBooleanField(pWallet, instance->jWalletIsSynchronizedID,
          (jboolean)instance->daemon->keyStore()->isSynchronized((unsigned int)pOffset));
        pEnvironment->SetBooleanField(pWallet, instance->jWalletIsBackedUpID,
          (jboolean)instance->daemon->keyStore()->isBackedUp((unsigned int)pOffset));
        pEnvironment->SetLongField(pWallet, instance->jWalletBalanceID, (jlong)balance);
        pEnvironment->SetLongField(pWallet, instance->jWalletPendingBalanceID,
          (jlong)pendingBalance);
        pEnvironment->SetIntField(pWallet, instance->jWalletBlockHeightID, headerHeight);
        pEnvironment->SetLongField(pWallet, instance->jWalletLastUpdatedID,
          (jlong)BitCoin::getTime());
        return JNI_TRUE;
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setName(JNIEnv *pEnvironment,
                                                                                 jobject pObject,
                                                                                 jint pOffset,
                                                                                 jstring pName)
    {
        Instance *instance = getInstance(pEnvironment, pObject);
        if(instance == NULL || instance->daemon->keyStore()->size() <= pOffset)
            return JNI_FALSE;

        const char *newName = pEnvironment->GetStringUTFChars(pName, NULL);
        instance->daemon->keyStore()->setName((unsigned int)pOffset, newName);
        pEnvironment->ReleaseStringUTFChars(pName, newName);

        jboolean result = (jboolean)savePublicKeys(instance->daemon);
        if(result)
        {
            // Set name in java object
            jobjectArray wallets = (jobjectArray)pEnvironment->GetObjectField(pObject,
              instance->jBitcoinWalletsID);
            if(pEnvironment->GetArrayLength(wallets) > pOffset)
            {
                jobject wallet = pEnvironment->GetObjectArrayElement(wallets, pOffset);
                pEnvironment->SetObjectField(wallet, instance->jWalletNameID, pName);
            }
            instance->daemon->monitor()->incrementChange();
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

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setGap(JNIEnv *pEnvironment,
                                                                                       jobject pObject,
                                                                                       jint pOffset,
                                                                                       jint pGap)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pOffset)
            return JNI_FALSE;

        daemon->keyStore()->setGap((unsigned int)pOffset, pGap);
        daemon->monitor()->incrementChange();

        return (jboolean)savePublicKeys(daemon);
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getGap(JNIEnv *pEnvironment,
                                                                                jobject pObject,
                                                                                jint pOffset)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pOffset)
            return JNI_FALSE;

        return daemon->keyStore()->gap((unsigned int)pOffset);
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
                                                                             jlongArray pDerivationPath,
                                                                             jlong pReceivingIndex,
                                                                             jlong pChangeIndex,
                                                                             jstring pName,
                                                                             jboolean pIsBackedUp,
                                                                             jlong pRecoverTime,
                                                                             jint pGap)
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

        std::vector<uint32_t> path;
        unsigned int pathDepth = (unsigned int)pEnvironment->GetArrayLength(pDerivationPath);
        jlong *pathInts = pEnvironment->GetLongArrayElements(pDerivationPath, 0);
        for(unsigned int i = 0; i < pathDepth; ++i)
            path.emplace_back((uint32_t)pathInts[i]);
        pEnvironment->ReleaseLongArrayElements(pDerivationPath, pathInts, 0);

        const char *seed = pEnvironment->GetStringUTFChars(pSeed, NULL);
        int result = daemon->keyStore()->addSeed(seed, method, path, (uint32_t)pReceivingIndex,
          (uint32_t)pChangeIndex, (int32_t)pRecoverTime);
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
        if(pGap != 0)
            daemon->keyStore()->setGap(offset, pGap);
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
                                                                                   jint pDerivationMethod,
                                                                                   jlongArray pDerivationPath,
                                                                                   jlong pReceivingIndex,
                                                                                   jlong pChangeIndex,
                                                                                   jstring pName,
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

        std::vector<uint32_t> path;
        unsigned int pathDepth = (unsigned int)pEnvironment->GetArrayLength(pDerivationPath);
        jlong *pathInts = pEnvironment->GetLongArrayElements(pDerivationPath, 0);
        for(unsigned int i = 0; i < pathDepth; ++i)
            path.emplace_back((uint32_t)pathInts[i]);
        pEnvironment->ReleaseLongArrayElements(pDerivationPath, pathInts, 0);

        const char *encodedKey = pEnvironment->GetStringUTFChars(pPrivateKey, NULL);
        int result = daemon->keyStore()->addEncodedKey(encodedKey, method, path,
          (uint32_t)pReceivingIndex, (uint32_t)pChangeIndex, (int32_t)pRecoverTime);
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

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_derivationPathMethod(JNIEnv *pEnvironment,
                                                                                          jobject pObject,
                                                                                          jint pOffset)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pOffset)
            return 3;

        switch(daemon->keyStore()->derivationPathMethod(pOffset))
        {
        case BitCoin::Key::BIP0044:
            return 0;
        case BitCoin::Key::SIMPLE:
            return 2;
        case BitCoin::Key::BIP0032:
            return 1;
        case BitCoin::Key::INDIVIDUAL:
            return 4;
        default:
        case BitCoin::Key::DERIVE_UNKNOWN:
        case BitCoin::Key::DERIVE_CUSTOM:
            return 3;
        }
    }

    JNIEXPORT jlongArray JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_derivationPath(JNIEnv *pEnvironment,
                                                                                          jobject pObject,
                                                                                          jint pOffset,
                                                                                          jint pChainOffset)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pOffset)
            return NULL;

        std::vector<uint32_t> path;
        daemon->keyStore()->getDerivationPath((unsigned int)pOffset, (unsigned int)pChainOffset,
          path);

        jlongArray result = pEnvironment->NewLongArray(path.size());
        jlong values[path.size()];
        jlong *value = values;
        for(std::vector<uint32_t>::iterator index = path.begin(); index != path.end();
          ++index, ++value)
            *value = *index;
        pEnvironment->SetLongArrayRegion(result, 0, path.size(), values);
        return result;
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

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getNextReceiveAddress(JNIEnv *pEnvironment,
                                                                                              jobject pObject,
                                                                                              jint pKeyOffset,
                                                                                              jint pType)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pKeyOffset)
            return NULL;

        std::vector<BitCoin::Key *> *chainKeys = daemon->keyStore()->chainKeys((unsigned int)pKeyOffset);
        if(chainKeys == NULL || chainKeys->size() == 0)
            return NULL;

        BitCoin::Key *chainKey;
        if(chainKeys->size() > 0 && pType > 0)
            chainKey = chainKeys->at(1);
        else
            chainKey = chainKeys->at(0);

        if(chainKey->depth() == BitCoin::Key::NO_DEPTH)
        {
            NextCash::String result = chainKey->address();
            return pEnvironment->NewStringUTF(result.text());
        }
        else
        {
            BitCoin::Key *unused = chainKey->getNextUnused();

            if(unused == NULL)
                return NULL;

            NextCash::String result = unused->address();
            return pEnvironment->NewStringUTF(result.text());
        }
    }

    JNIEXPORT jbyteArray JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getNextReceiveOutput(JNIEnv *pEnvironment,
                                                                                                jobject pObject,
                                                                                                jint pKeyOffset,
                                                                                                jint pType)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pKeyOffset)
            return NULL;

        std::vector<BitCoin::Key *> *chainKeys = daemon->keyStore()->chainKeys((unsigned int)pKeyOffset);
        if(chainKeys == NULL || chainKeys->size() == 0)
            return NULL;

        BitCoin::Key *chainKey;
        if(chainKeys->size() > 0 && pType > 0)
            chainKey = chainKeys->at(1);
        else
            chainKey = chainKeys->at(0);

        if(chainKey->depth() == BitCoin::Key::NO_DEPTH)
        {
            NextCash::Buffer outputScript;
            BitCoin::ScriptInterpreter::writeP2PKHOutputScript(outputScript, chainKey->hash());
            jbyteArray result = pEnvironment->NewByteArray((jsize)outputScript.length());
            pEnvironment->SetByteArrayRegion(result, 0, (jsize)outputScript.length(),
              (const jbyte *)outputScript.begin());
            return result;
        }
        else
        {
            BitCoin::Key *unused = chainKey->getNextUnused();

            if(unused == NULL)
                return NULL;

            NextCash::Buffer outputScript;
            BitCoin::ScriptInterpreter::writeP2PKHOutputScript(outputScript, unused->hash());
            jbyteArray result = pEnvironment->NewByteArray((jsize)outputScript.length());
            pEnvironment->SetByteArrayRegion(result, 0, (jsize)outputScript.length(),
              (const jbyte *)outputScript.begin());
            return result;
        }
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

        Instance *instance = getInstance(pEnvironment, pObject);
        if(instance == NULL)
            return NULL;

        // Create result object
        instance->setupPaymentRequestClass(pEnvironment);
        jobject result = pEnvironment->NewObject(instance->jPaymentRequestClass,
          instance->jPaymentRequestConstructor);

        // Set code
        pEnvironment->SetObjectField(result, instance->jPaymentRequestURIID, pPaymentCode);

        // Set format
        switch(request.format)
        {
        case BitCoin::PaymentRequest::Format::INVALID:
            pEnvironment->SetIntField(result, instance->jPaymentRequestFormatID, (jint)0);
            break;
        case BitCoin::PaymentRequest::Format::LEGACY:
            pEnvironment->SetIntField(result, instance->jPaymentRequestFormatID, (jint)1);

            // Set address
            if(!request.pubKeyHash.isEmpty())
                pEnvironment->SetObjectField(result, instance->jPaymentRequestAddressID,
                  pEnvironment->NewStringUTF(BitCoin::encodeLegacyAddress(request.pubKeyHash,
                    request.type)));

            break;
        case BitCoin::PaymentRequest::Format::CASH:
            pEnvironment->SetIntField(result, instance->jPaymentRequestFormatID, (jint)2);

            // Set address
            if(!request.pubKeyHash.isEmpty())
                pEnvironment->SetObjectField(result, instance->jPaymentRequestAddressID,
                  pEnvironment->NewStringUTF(BitCoin::encodeCashAddress(request.pubKeyHash,
                    request.type)));

            break;
        }

        // Set protocol
        switch(request.type)
        {
        case BitCoin::AddressType::UNKNOWN:
            pEnvironment->SetIntField(result, instance->jPaymentRequestTypeID, (jint)0);
            break;
        case BitCoin::AddressType::MAIN_PUB_KEY_HASH:
        case BitCoin::AddressType::TEST_PUB_KEY_HASH:
            pEnvironment->SetIntField(result, instance->jPaymentRequestTypeID, (jint)1);
            break;
        case BitCoin::AddressType::MAIN_SCRIPT_HASH:
        case BitCoin::AddressType::TEST_SCRIPT_HASH:
            pEnvironment->SetIntField(result, instance->jPaymentRequestTypeID, (jint)2);
            break;
        case BitCoin::AddressType::MAIN_PRIVATE_KEY:
        case BitCoin::AddressType::TEST_PRIVATE_KEY:
            pEnvironment->SetIntField(result, instance->jPaymentRequestTypeID, (jint)3);
            break;
        case BitCoin::AddressType::BIP0070:
            pEnvironment->SetIntField(result, instance->jPaymentRequestTypeID, (jint)4);
            break;
        }

        // Set amount
        pEnvironment->SetLongField(result, instance->jPaymentRequestAmountID,
          (jlong)request.amount);

        // Set amount specified
        pEnvironment->SetBooleanField(result, instance->jPaymentRequestAmountSpecifiedID,
          (jboolean)request.amountSpecified);

        // Set label
        if(request.label)
            pEnvironment->SetObjectField(result, instance->jPaymentRequestLabelID,
              pEnvironment->NewStringUTF(request.label));

        // Set description
        if(request.message)
            pEnvironment->SetObjectField(result, instance->jPaymentRequestMessageID,
              pEnvironment->NewStringUTF(request.message));

        // Set secure
        pEnvironment->SetBooleanField(result, instance->jPaymentRequestSecureID,
          (jboolean)request.secure);

        // Set secure URL
        if(request.secureURL)
            pEnvironment->SetObjectField(result, instance->jPaymentRequestSecureURLID,
              pEnvironment->NewStringUTF(request.secureURL));

        return result;
    }

    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_decodeKey(JNIEnv *pEnvironment,
                                                                                  jobject pObject,
                                                                                  jstring pEncodedText)
    {
        // Parse
        const char *encodedKey = pEnvironment->GetStringUTFChars(pEncodedText, NULL);
        BitCoin::Key key;
        if(!key.decode(encodedKey))
        {
            pEnvironment->ReleaseStringUTFChars(pEncodedText, encodedKey);
            return NULL;
        }
        pEnvironment->ReleaseStringUTFChars(pEncodedText, encodedKey);

        Instance *instance = getInstance(pEnvironment, pObject);
        if(instance == NULL)
            return NULL;

        // Create result object
        instance->setupKeyDataClass(pEnvironment);
        jobject result = pEnvironment->NewObject(instance->jKeyDataClass,
          instance->jKeyDataConstructor);

        pEnvironment->SetIntField(result, instance->jKeyDataVersionID, (jint)key.version());
        pEnvironment->SetIntField(result, instance->jKeyDataDepthID, (jint)key.depth());
        pEnvironment->SetIntField(result, instance->jKeyDataIndexID, (jint)key.index());

        return result;
    }

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_publicKey(JNIEnv *pEnvironment,
                                                                                  jobject pObject,
                                                                                  jint pKeyOffset,
                                                                                  jint pType)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pKeyOffset)
            return JNI_FALSE;

        std::vector<BitCoin::Key *> *chainKeys =
          daemon->keyStore()->chainKeys((unsigned int)pKeyOffset);
        if(chainKeys == NULL || chainKeys->size() == 0)
            return NULL;

        BitCoin::Key *chainKey;
        if(chainKeys->size() > 1 && pType > 0)
            chainKey = chainKeys->at(1);
        else
            chainKey = chainKeys->at(0);

        return pEnvironment->NewStringUTF(chainKey->encode());
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
        Instance *instance = getInstance(pEnvironment, pObject);
        if(instance == NULL || instance->daemon->keyStore()->size() <= pKeyOffset)
            return JNI_FALSE;

        std::vector<BitCoin::Key *> *chainKeys =
          instance->daemon->keyStore()->chainKeys(pKeyOffset);
        if(chainKeys == NULL)
            return JNI_FALSE;

        const char *id = pEnvironment->GetStringUTFChars(pID, NULL);
        BitCoin::Monitor::RelatedTransactionData transaction;
        bool success = instance->daemon->monitor()->getTransaction(id, chainKeys->begin(),
          chainKeys->end(), transaction);
        pEnvironment->ReleaseStringUTFChars(pID, id);

        NextCash::Buffer buffer;
        transaction.transaction->write(&buffer);
        NextCash::String hex = buffer.readHexString(buffer.length());

        if(!success)
            return JNI_FALSE;

        instance->setupFullTransactionClass(pEnvironment);

        // Hash
        pEnvironment->SetObjectField(pTransaction, instance->jFullTransactionHashID,
          pEnvironment->NewStringUTF(transaction.transaction->hash().hex().text()));

        // Block
        if(!transaction.blockHash.isEmpty())
            pEnvironment->SetObjectField(pTransaction, instance->jFullTransactionBlockID,
              pEnvironment->NewStringUTF(transaction.blockHash.hex().text()));
        else
            pEnvironment->SetObjectField(pTransaction, instance->jFullTransactionBlockID, NULL);

        // Count and Date
        if(!instance->daemon->chainIsLoaded())
        {
            pEnvironment->SetIntField(pTransaction, instance->jFullTransactionCountID, (jint)-1);

            pEnvironment->SetLongField(pTransaction, instance->jFullTransactionDateID, (jlong)0);
        }
        else if(transaction.blockHash.isEmpty())
        {
            pEnvironment->SetIntField(pTransaction, instance->jFullTransactionCountID,
              (jint)transaction.nodesVerified);

            pEnvironment->SetLongField(pTransaction, instance->jFullTransactionDateID,
              (jlong)transaction.transaction->time());
        }
        else
        {
            pEnvironment->SetIntField(pTransaction, instance->jFullTransactionCountID,
              (jint)(instance->daemon->chain()->headerHeight() + 1 -
                instance->daemon->chain()->hashHeight(transaction.blockHash)));

            pEnvironment->SetLongField(pTransaction, instance->jFullTransactionDateID,
              (jlong)instance->daemon->chain()->time(
              instance->daemon->chain()->hashHeight(transaction.blockHash)));
        }

        // Size
        pEnvironment->SetIntField(pTransaction, instance->jFullTransactionSizeID,
          transaction.transaction->size());

        // Version
        pEnvironment->SetIntField(pTransaction, instance->jFullTransactionVersionID,
          (jint)transaction.transaction->version);

        // Inputs
        jobjectArray inputs =
          pEnvironment->NewObjectArray((jsize)transaction.transaction->inputs.size(),
            instance->jInputClass, NULL);

        unsigned int offset = 0;
        jobject inputObject;
        NextCash::String script;
        BitCoin::ScriptInterpreter interpreter;
        for(std::vector<BitCoin::Input>::iterator input = transaction.transaction->inputs.begin();
          input != transaction.transaction->inputs.end(); ++input, ++offset)
        {
            inputObject = pEnvironment->NewObject(instance->jInputClass, instance->jInputConstructor);

            // Outpoint
            pEnvironment->SetObjectField(inputObject, instance->jInputOutpointID,
              pEnvironment->NewStringUTF(input->outpoint.transactionID.hex().text()));
            pEnvironment->SetIntField(inputObject, instance->jInputOutpointIndexID,
              input->outpoint.index);

            // Script
            pEnvironment->SetObjectField(inputObject, instance->jInputScriptID,
              pEnvironment->NewStringUTF(BitCoin::ScriptInterpreter::scriptText(input->script,
                instance->daemon->chain()->forks(), instance->daemon->chain()->headerHeight())));

            // Sequence
            pEnvironment->SetIntField(inputObject, instance->jInputSequenceID,
              (jint)input->sequence);

            // Address
            if(transaction.inputAddresses[offset])
                pEnvironment->SetObjectField(inputObject, instance->jInputAddressID,
                  pEnvironment->NewStringUTF(transaction.inputAddresses[offset]));

            // Amount
            pEnvironment->SetLongField(inputObject, instance->jInputAmountID,
              (jlong)transaction.relatedInputAmounts[offset]);

            pEnvironment->SetObjectArrayElement(inputs, offset, inputObject);
        }

        pEnvironment->SetObjectField(pTransaction, instance->jFullTransactionInputsID, inputs);

        // Outputs
        jobjectArray outputs =
          pEnvironment->NewObjectArray((jsize)transaction.transaction->outputs.size(),
            instance->jOutputClass,
          NULL);

        jobject outputObject;
        offset = 0;
        for(std::vector<BitCoin::Output>::iterator output =
          transaction.transaction->outputs.begin();
          output != transaction.transaction->outputs.end(); ++output, ++offset)
        {
            outputObject = pEnvironment->NewObject(instance->jOutputClass,
              instance->jOutputConstructor);

            // Amount
            pEnvironment->SetLongField(outputObject, instance->jOutputAmountID,
              (jlong)output->amount);

            // Script
            pEnvironment->SetObjectField(outputObject, instance->jOutputScriptID,
              pEnvironment->NewStringUTF(BitCoin::ScriptInterpreter::scriptText(output->script,
                instance->daemon->chain()->forks(), instance->daemon->chain()->headerHeight())));

            // Script data
            jbyteArray scriptBytes = pEnvironment->NewByteArray((jsize)output->script.length());
            pEnvironment->SetByteArrayRegion(scriptBytes, 0, (jsize)output->script.length(),
              (const jbyte *)output->script.begin());
            pEnvironment->SetObjectField(outputObject, instance->jOutputScriptDataID, scriptBytes);

            // Address
            if(transaction.outputAddresses[offset])
                pEnvironment->SetObjectField(outputObject, instance->jOutputAddressID,
                  pEnvironment->NewStringUTF(transaction.outputAddresses[offset]));

            // Related
            pEnvironment->SetBooleanField(outputObject, instance->jOutputRelatedID,
              (jboolean)transaction.relatedOutputs[offset]);

            pEnvironment->SetObjectArrayElement(outputs, offset, outputObject);
        }

        pEnvironment->SetObjectField(pTransaction, instance->jFullTransactionOutputsID, outputs);

        // Lock Time
        pEnvironment->SetIntField(pTransaction, instance->jFullTransactionLockTimeID,
          (jint)transaction.transaction->lockTime);

        return JNI_TRUE;
    }

    jobject createSendResult(Instance *pInstance, JNIEnv *pEnvironment, int pWalletOffset,
      int pResult, BitCoin::TransactionReference &pTransaction)
    {
        pInstance->setupSendResultClass(pEnvironment);
        jobject result = pEnvironment->NewObject(pInstance->jSendResultClass,
          pInstance->jSendResultConstructor);
        pEnvironment->SetIntField(result, pInstance->jSendResultResultID, (jint)pResult);

        if(!pTransaction)
            pEnvironment->SetObjectField(result, pInstance->jSendResultRawTransactionID, NULL);
        else
        {
            // Transaction
            if(pInstance->daemon->keyStore()->size() > pWalletOffset)
            {
                std::vector<BitCoin::Key *> *chainKeys =
                  pInstance->daemon->keyStore()->chainKeys(pWalletOffset);
                if(chainKeys != NULL)
                {
                    BitCoin::Monitor::RelatedTransactionData relatedData;
                    pInstance->daemon->monitor()->getTransaction(pTransaction->hash(),
                      chainKeys->begin(), chainKeys->end(), relatedData);
                    pEnvironment->SetObjectField(result, pInstance->jSendResultTransactionID,
                      createTransaction(pInstance, pEnvironment, relatedData,
                        pInstance->daemon->chainIsLoaded()));
                }
                else
                    pEnvironment->SetObjectField(result, pInstance->jSendResultTransactionID, NULL);
            }
            else
                pEnvironment->SetObjectField(result, pInstance->jSendResultTransactionID, NULL);

            // Setup raw transaction data.
            NextCash::Buffer rawData;
            pTransaction->write(&rawData);
            jbyteArray array = pEnvironment->NewByteArray((jsize)rawData.length());
            pEnvironment->SetByteArrayRegion(array, 0, (jsize)rawData.length(),
              (const jbyte *)rawData.begin());
            pEnvironment->SetObjectField(result, pInstance->jSendResultRawTransactionID, array);
        }

        return result;
    }

    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_sendStandardPayment(JNIEnv *pEnvironment,
                                                                                            jobject pObject,
                                                                                            jint pWalletOffset,
                                                                                            jstring pPassCode,
                                                                                            jstring pAddress,
                                                                                            jlong pAmount,
                                                                                            jdouble pFeeRate,
                                                                                            jboolean pUsePending,
                                                                                            jboolean pSendAll)
    {
        Instance *instance = getInstance(pEnvironment, pObject);
        BitCoin::TransactionReference transaction;
        if(instance == NULL || instance->daemon->keyStore()->size() <= pWalletOffset)
            return createSendResult(instance, pEnvironment, pWalletOffset, 1, transaction);

        if(!loadPrivateKeys(pEnvironment, instance->daemon, pPassCode))
            return createSendResult(instance, pEnvironment, pWalletOffset, 1, transaction);

        NextCash::Hash hash;
        BitCoin::AddressType type;
        const char *address = pEnvironment->GetStringUTFChars(pAddress, NULL);
        if(!BitCoin::decodeCashAddress(address, hash, type) &&
          !BitCoin::decodeLegacyAddress(address, hash, type))
        {
            pEnvironment->ReleaseStringUTFChars(pAddress, address);
            instance->daemon->keyStore()->unloadPrivate();
            return createSendResult(instance, pEnvironment, pWalletOffset, 3, transaction); // Invalid Hash
        }
        pEnvironment->ReleaseStringUTFChars(pAddress, address);

        int result = instance->daemon->sendStandardPayment((unsigned int)pWalletOffset, type, hash,
          (uint64_t)pAmount, pFeeRate, pUsePending, pSendAll, true, transaction);

        if(!instance->daemon->saveMonitor() && result == 0)
            result = 1;
        if(!savePrivateKeys(pEnvironment, instance->daemon, pPassCode) && result == 0)
            result = 1;
        if(!savePublicKeys(instance->daemon) && result == 0)
            result = 1;

        instance->daemon->keyStore()->unloadPrivate();
        return createSendResult(instance, pEnvironment, pWalletOffset, result, transaction);
    }

    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_sendOutputsPayment(JNIEnv *pEnvironment,
                                                                                           jobject pObject,
                                                                                           jint pWalletOffset,
                                                                                           jstring pPassCode,
                                                                                           jobjectArray pOutputs,
                                                                                           jdouble pFeeRate,
                                                                                           jboolean pUsePending,
                                                                                           jboolean pTransmit)
    {
        Instance *instance = getInstance(pEnvironment, pObject);
        BitCoin::TransactionReference transaction;
        if(instance == NULL || instance->daemon->keyStore()->size() <= pWalletOffset)
            return createSendResult(instance, pEnvironment, pWalletOffset, 1, transaction);

        if(!loadPrivateKeys(pEnvironment, instance->daemon, pPassCode))
            return createSendResult(instance, pEnvironment, pWalletOffset, 1, transaction);

        // Convert outputs
        jobject jOutput;
        BitCoin::Output *output;
        jbyteArray scriptByteArray;
        jbyte *scriptBytes;
        std::vector<BitCoin::Output> outputs(
          (NextCash::stream_size)pEnvironment->GetArrayLength(pOutputs));
        for(unsigned int i = 0; i < pEnvironment->GetArrayLength(pOutputs); ++i)
        {
            jOutput = pEnvironment->GetObjectArrayElement(pOutputs, i);
            output = &outputs.at(i);

            output->amount = pEnvironment->GetLongField(jOutput, instance->jOutputAmountID);
            if(output->amount <= 0)
            {
                instance->daemon->keyStore()->unloadPrivate();
                return createSendResult(instance, pEnvironment, pWalletOffset, 7, transaction); // Invalid Outputs
            }

            scriptByteArray = reinterpret_cast<jbyteArray>(
              pEnvironment->GetObjectField(jOutput, instance->jOutputScriptDataID));
            if(pEnvironment->GetArrayLength(scriptByteArray) == 0)
            {
                instance->daemon->keyStore()->unloadPrivate();
                return createSendResult(instance, pEnvironment, pWalletOffset, 7, transaction); // Invalid Outputs
            }

            scriptBytes = pEnvironment->GetByteArrayElements(scriptByteArray, NULL);
            output->script.write(scriptBytes,
              (NextCash::stream_size)pEnvironment->GetArrayLength(scriptByteArray));
            pEnvironment->ReleaseByteArrayElements(scriptByteArray, scriptBytes, 0);
        }

        int result = instance->daemon->sendSpecifiedOutputsPayment((unsigned int)pWalletOffset,
          outputs, pFeeRate, pUsePending, pTransmit, transaction);

        if(!instance->daemon->saveMonitor() && result == 0)
            result = 1;
        if(!savePrivateKeys(pEnvironment, instance->daemon, pPassCode) && result == 0)
            result = 1;
        if(!savePublicKeys(instance->daemon) && result == 0)
            result = 1;

        instance->daemon->keyStore()->unloadPrivate();
        return createSendResult(instance, pEnvironment, pWalletOffset, result, transaction);
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

    jobject createOutput(Instance *pInstance, JNIEnv *pEnvironment, BitCoin::Output &pOutput,
      bool pIsRelated)
    {
        jobject result = pEnvironment->NewObject(pInstance->jOutputClass,
          pInstance->jOutputConstructor);

        // Set amount
        pEnvironment->SetLongField(result, pInstance->jOutputAmountID, pOutput.amount);

        // Set script
        pOutput.script.setReadOffset(0);
        pEnvironment->SetObjectField(result, pInstance->jOutputScriptID,
          pEnvironment->NewStringUTF(BitCoin::ScriptInterpreter::scriptText(pOutput.script,
            pInstance->daemon->chain()->forks(), pInstance->daemon->chain()->headerHeight())));

        // Script data
        jbyteArray scriptBytes = pEnvironment->NewByteArray((jsize)pOutput.script.length());
        pEnvironment->SetByteArrayRegion(scriptBytes, 0, (jsize)pOutput.script.length(),
          (const jbyte *)pOutput.script.begin());
        pEnvironment->SetObjectField(result, pInstance->jOutputScriptDataID, scriptBytes);

        // Set address
        NextCash::HashList payAddresses;
        pOutput.script.setReadOffset(0);
        BitCoin::ScriptInterpreter::ScriptType scriptType =
          BitCoin::ScriptInterpreter::parseOutputScript(pOutput.script, payAddresses);
        if(scriptType == BitCoin::ScriptInterpreter::P2PKH && payAddresses.size() == 1)
        {
            pEnvironment->SetObjectField(result, pInstance->jOutputAddressID,
              pEnvironment->NewStringUTF(BitCoin::encodeCashAddress(payAddresses.front())));
        }

        // Set related
        pEnvironment->SetBooleanField(result, pInstance->jOutputRelatedID, (jboolean)pIsRelated);

        return result;
    }

    jobject createOutpoint(Instance *pInstance, JNIEnv *pEnvironment, BitCoin::Outpoint &pOutpoint)
    {
        jobject result = pEnvironment->NewObject(pInstance->jOutpointClass,
          pInstance->jOutpointConstructor);

        // Set transactionID
        pEnvironment->SetObjectField(result, pInstance->jOutpointTransactionID,
          pEnvironment->NewStringUTF(pOutpoint.transactionID.hex()));

        // Set index
        pEnvironment->SetIntField(result, pInstance->jOutpointIndexID, pOutpoint.index);

        // Set output
        pEnvironment->SetObjectField(result, pInstance->jOutpointOutputID,
          createOutput(pInstance, pEnvironment, *pOutpoint.output, true));

        // Confirmations
        if(pOutpoint.confirmations != 0xffffffff)
            pEnvironment->SetIntField(result, pInstance->jOutpointConfirmationsID,
              pOutpoint.confirmations);
        else
        {
            NextCash::Hash confirmBlock =
              pInstance->daemon->monitor()->confirmBlockHash(pOutpoint.transactionID);
            jint confirms = 0;
            if(!confirmBlock.isEmpty())
                confirms = pInstance->daemon->chain()->headerHeight() -
                  pInstance->daemon->chain()->hashHeight(confirmBlock);
            pEnvironment->SetIntField(result, pInstance->jOutpointConfirmationsID, confirms);
        }

        return result;
    }

    JNIEXPORT jobjectArray JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getUnspentOutputs(JNIEnv *pEnvironment,
                                                                                               jobject pObject,
                                                                                               jint pWalletOffset)
    {
        Instance *instance = getInstance(pEnvironment, pObject);
        if(instance == NULL || instance->daemon->keyStore()->size() <= pWalletOffset)
            return NULL;

        std::vector<BitCoin::Key *> *chainKeys =
          instance->daemon->keyStore()->chainKeys((unsigned int)pWalletOffset);
        if(chainKeys == NULL)
            return NULL;

        instance->setupOutputClass(pEnvironment);
        instance->setupOutpointClass(pEnvironment);

        // Get UTXOs from monitor
        std::vector<BitCoin::Outpoint> unspentOutputs;
        if(!instance->daemon->monitor()->getUnspentOutputs(chainKeys->begin(), chainKeys->end(),
          unspentOutputs, instance->daemon->chain(), true))
            return NULL;

        jobjectArray result = pEnvironment->NewObjectArray((jsize)unspentOutputs.size(),
          instance->jOutpointClass, NULL);

        unsigned int index = 0;
        for(std::vector<BitCoin::Outpoint>::iterator output = unspentOutputs.begin();
          output != unspentOutputs.end(); ++output, ++index)
        {
            pEnvironment->SetObjectArrayElement(result, index,
              createOutpoint(instance, pEnvironment, *output));
        }

        return result;
    }
}
