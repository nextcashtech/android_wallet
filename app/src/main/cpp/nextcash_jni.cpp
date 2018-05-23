
#include "nextcash_jni.hpp"

#include "base.hpp"
#include "daemon.hpp"

#include <algorithm>

#define NEXTCASH_JNI_LOG_NAME "JNI"


extern "C"
{
    // Static Class/Field IDs
    // Bitcoin
    jclass sBitcoinClass = NULL;
    jfieldID sBitcoinHandleID = NULL;
    jfieldID sBitcoinWalletsID = NULL;
    jfieldID sBitcoinLoadedID = NULL;

    // Wallet
    jclass sWalletClass = NULL;
    jfieldID sWalletLastUpdatedID = NULL;
    jfieldID sWalletTransactionsID = NULL;
    jfieldID sWalletUpdatedTransactionsID = NULL;
    jfieldID sWalletIsPrivateID = NULL;
    jfieldID sWalletNameID = NULL;
    jfieldID sWalletBalanceID = NULL;
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
    jfieldID sPaymentRequestCodeID = NULL;
    jfieldID sPaymentRequestFormatID = NULL;
    jfieldID sPaymentRequestProtocolID = NULL;
    jfieldID sPaymentRequestAddressID = NULL;
    jfieldID sPaymentRequestAmountID = NULL;
    jfieldID sPaymentRequestDescriptionID = NULL;
    jfieldID sPaymentRequestSecureID = NULL;


    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_NextCash_destroy(JNIEnv *pEnvironment,
                                                                              jobject pObject)
    {
        NextCash::Log::destroy();
    }

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_userAgent(JNIEnv *pEnvironment,
                                                                                  jobject pObject)
    {
        return pEnvironment->NewStringUTF(BITCOIN_USER_AGENT);
    }

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_networkName(JNIEnv *pEnvironment,
                                                                                    jobject pObject)
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
        sPaymentRequestCodeID = pEnvironment->GetFieldID(sPaymentRequestClass, "code",
          "Ljava/lang/String;");
        sPaymentRequestFormatID = pEnvironment->GetFieldID(sPaymentRequestClass, "format", "I");
        sPaymentRequestProtocolID = pEnvironment->GetFieldID(sPaymentRequestClass, "protocol", "I");
        sPaymentRequestAddressID = pEnvironment->GetFieldID(sPaymentRequestClass, "address",
          "Ljava/lang/String;");
        sPaymentRequestAmountID = pEnvironment->GetFieldID(sPaymentRequestClass, "amount", "J");
        sPaymentRequestDescriptionID = pEnvironment->GetFieldID(sPaymentRequestClass, "description",
          "Ljava/lang/String;");
        sPaymentRequestSecureID = pEnvironment->GetFieldID(sPaymentRequestClass, "secure", "Z");
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setupJNI(JNIEnv *pEnvironment,
                                                                              jclass pClass)
    {
        // Bitcoin
        sBitcoinClass = pClass;
        sBitcoinHandleID = pEnvironment->GetFieldID(sBitcoinClass, "mHandle", "J");
        sBitcoinWalletsID = pEnvironment->GetFieldID(sBitcoinClass, "wallets",
          "[Ltech/nextcash/nextcashwallet/Wallet;");
        sBitcoinLoadedID = pEnvironment->GetFieldID(sBitcoinClass, "mLoaded", "Z");
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Wallet_setupJNI(JNIEnv *pEnvironment,
                                                                             jclass pClass)
    {
        sWalletClass = pClass;
        sWalletLastUpdatedID = pEnvironment->GetFieldID(sWalletClass, "lastUpdated", "J");
        sWalletTransactionsID = pEnvironment->GetFieldID(sWalletClass, "transactions",
          "[Ltech/nextcash/nextcashwallet/Transaction;");
        sWalletUpdatedTransactionsID = pEnvironment->GetFieldID(sWalletClass, "updatedTransactions",
          "[Ltech/nextcash/nextcashwallet/Transaction;");
        sWalletIsPrivateID = pEnvironment->GetFieldID(sWalletClass, "isPrivate", "Z");
        sWalletNameID = pEnvironment->GetFieldID(sWalletClass, "name", "Ljava/lang/String;");
        sWalletBalanceID = pEnvironment->GetFieldID(sWalletClass, "balance", "J");
        sWalletBlockHeightID = pEnvironment->GetFieldID(sWalletClass, "blockHeight", "I");
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Transaction_setupJNI(JNIEnv *pEnvironment,
                                                                                  jclass pClass)
    {
        // This function gets called, but seems to be "unloaded" or invalidated somehow before any
        //   transactions are created with NewObject JNI function.
        // setupTransaction has to be called immediately before creating transactions.

//        sTransactionClass = pClass;
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
        pEnvironment->SetBooleanField(pObject, sBitcoinLoadedID, JNI_FALSE);

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
        BitCoin::Info::instance().setPath(newPath);
        pEnvironment->ReleaseStringUTFChars(pPath, newPath);
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_load(JNIEnv *pEnvironment,
                                                                              jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject, true);
        if(daemon == NULL)
            return JNI_FALSE;

        jboolean result = JNI_FALSE;

        try
        {
            if(daemon->load())
            {
                NextCash::Log::add(NextCash::Log::INFO, NEXTCASH_JNI_LOG_NAME, "Bitcoin is loaded");
                result = JNI_TRUE;
            }
            else
                NextCash::Log::add(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME,
                  "Bitcoin failed to load");
        }
        catch(std::bad_alloc pException)
        {
            NextCash::Log::addFormatted(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME,
              "Bad allocation while loading : %s", pException.what());
        }
        catch(std::exception pException)
        {
            NextCash::Log::addFormatted(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME,
              "Exception while loading : %s", pException.what());
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

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isInSync(JNIEnv *pEnvironment,
                                                                                  jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return JNI_FALSE;

        return (jboolean)daemon->chain()->isInSync();
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

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setFinishModeNoCreate(JNIEnv *pEnvironment,
                                                                                           jobject pObject,
                                                                                           jint pMode)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject, false);
        if(daemon == NULL)
            return;

        daemon->setFinishMode(pMode);
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_run(JNIEnv *pEnvironment,
                                                                         jobject pObject,
                                                                         jint pMode)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject, true);
        if(daemon == NULL)
            return;

        daemon->setFinishMode(pMode);
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
            case BitCoin::Daemon::LOADING:
                return (jint)1;
            case BitCoin::Daemon::FINDING_PEERS:
                return (jint)2;
            case BitCoin::Daemon::CONNECTING_TO_PEERS:
                return (jint)3;
            case BitCoin::Daemon::SYNCHRONIZING:
                return (jint)4;
            case BitCoin::Daemon::SYNCHRONIZED:
                return (jint)5;
            case BitCoin::Daemon::FINDING_TRANSACTIONS:
                return (jint)6;
        }
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_blockHeight(JNIEnv *pEnvironment,
                                                                                 jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return (jint)0;

        return daemon->chain()->height();
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
        if(!daemon->chain()->getBlockHash((unsigned int)pHeight, hash))
            return NULL;

        return createBlock(pEnvironment, pHeight, pEnvironment->NewStringUTF(hash.hex().text()),
          daemon->chain()->blockStats().time((unsigned int)pHeight));
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
        int height = daemon->chain()->blockHeight(hash);
        pEnvironment->ReleaseStringUTFChars(pHash, hashHex);

        return createBlock(pEnvironment, height, pHash,
          daemon->chain()->blockStats().time((unsigned int)height));
    }

    JNIEXPORT jlong JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_balance(JNIEnv *pEnvironment,
                                                                              jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return (jlong)0L;

        return daemon->monitor()->balance();
    }

    bool loadPublicKeys(BitCoin::Daemon *pDaemon)
    {
        NextCash::String filePathName = BitCoin::Info::instance().path();
        filePathName.pathAppend("keystore");
        NextCash::FileInputStream publicFile(filePathName);

        if(!publicFile.isValid())
        {
            NextCash::Log::add(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME,
              "Failed to open public key file");
            return NULL;
        }

        return pDaemon->keyStore()->read(&publicFile);
    }

    bool savePublicKeys(BitCoin::Daemon *pDaemon)
    {
        NextCash::String filePathName = BitCoin::Info::instance().path();
        filePathName.pathAppend("keystore");
        NextCash::FileOutputStream publicFile(filePathName, true);

        if(!publicFile.isValid())
        {
            NextCash::Log::add(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME,
              "Failed to open public key file");
            return false;
        }

        pDaemon->keyStore()->write(&publicFile);
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
            return NULL;
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

        NextCash::String filePathName = BitCoin::Info::instance().path();
        filePathName.pathAppend(".private_keystore");
        NextCash::FileOutputStream privateFile(filePathName, true);

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
        return result;
    }

    // Keys
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_loadKey(JNIEnv *pEnvironment,
                                                                             jobject pObject,
                                                                             jstring pPassCode,
                                                                             jstring pKey,
                                                                             jint pDerivationMethod,
                                                                             jstring pName)
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
        int result = daemon->keyStore()->loadKey(key, method);
        pEnvironment->ReleaseStringUTFChars(pKey, key);

        if(result == 0)
        {
            const char *name = pEnvironment->GetStringUTFChars(pName, NULL);
            daemon->keyStore()->setName(daemon->keyStore()->size() - 1, name);
            pEnvironment->ReleaseStringUTFChars(pName, name);

            if(savePrivateKeys(pEnvironment, daemon, pPassCode) && savePublicKeys(daemon))
            {
                daemon->monitor()->setKeyStore(daemon->keyStore());
                daemon->saveMonitor();
            }
            else
                result = 1; // Failed to save
        }

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

    int32_t transTime(BitCoin::Monitor::SPVTransactionData *pTransaction, BitCoin::Daemon *pDaemon)
    {
        int32_t result = 0;

        if(!pTransaction->blockHash.isEmpty())
        {
            // Use block time
            int blockHeight = pDaemon->chain()->blockHeight(pTransaction->blockHash);

            if(blockHeight >= 0)
                result = pDaemon->chain()->blockStats().time((unsigned int)blockHeight);
        }

        if(result == 0) // Use announce time
            result = pTransaction->announceTime;

        return result;
    }

    bool transGreater(BitCoin::Monitor::SPVTransactionData *pLeft,
                      BitCoin::Monitor::SPVTransactionData *pRight)
    {
        return pLeft->announceTime > pRight->announceTime;
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
                              BitCoin::Monitor::SPVTransactionData *pTransaction)
    {
        jobject result = pEnvironment->NewObject(sTransactionClass, sTransactionConstructor);

        // Set hash
        jstring hash = pEnvironment->NewStringUTF(pTransaction->transaction->hash.hex());
        pEnvironment->SetObjectField(result, sTransactionHashID, hash);

        // Set block
        if(!pTransaction->blockHash.isEmpty())
        {
            hash = pEnvironment->NewStringUTF(pTransaction->blockHash.hex());
            pEnvironment->SetObjectField(result, sTransactionBlockID, hash);
        }

        // Set date
        if(pTransaction->announceTime != 0)
            pEnvironment->SetLongField(result, sTransactionDateID,
              (jlong)pTransaction->announceTime);

        // Set amount
        pEnvironment->SetLongField(result, sTransactionAmountID,
          (jlong)pTransaction->amount);

        // Set count
        if(pTransaction->blockHash.isEmpty())
            pEnvironment->SetIntField(result, sTransactionCountID,
              (jint)pTransaction->nodes.size());
        else
            pEnvironment->SetIntField(result, sTransactionCountID,
              (jint)(pDaemon->chain()->height() + 1 -
              pDaemon->chain()->blockHeight(pTransaction->blockHash)));

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
        jint blockHeight = (jint)daemon->chain()->height();
        int64_t balance = 0;
        std::vector<BitCoin::Key *> children;
        std::vector<BitCoin::Key *> *chainKeys = daemon->keyStore()->chainKeys(pOffset);
        std::vector<BitCoin::Monitor::SPVTransactionData *> transactions, keyTransactions;

        if(chainKeys != NULL && chainKeys->size() != 0)
            for(std::vector<BitCoin::Key *>::iterator chainKey = chainKeys->begin();
                chainKey != chainKeys->end(); ++chainKey)
            {
                keyTransactions.clear();

                if((*chainKey)->depth() == BitCoin::Key::NO_DEPTH)
                {
                    if(daemon->monitor()->getTransactions(*chainKey, keyTransactions, true))
                        transactions.insert(transactions.end(), keyTransactions.begin(),
                          keyTransactions.end());
                }
                else
                {
                    (*chainKey)->getChildren(children);
                    for(std::vector<BitCoin::Key *>::iterator child = children.begin();
                      child != children.end(); ++child)
                    {
                        if(daemon->monitor()->getTransactions(*child, keyTransactions, true))
                            transactions.insert(transactions.end(), keyTransactions.begin(),
                              keyTransactions.end());
                    }
                }

                balance += daemon->monitor()->balance(*chainKey, false);
            }

        bool previousUpdate = pEnvironment->GetLongField(pWallet, sWalletLastUpdatedID) != 0;
        NextCash::HashList previousHashList, previousConfirmedHashList;

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

        for(std::vector<BitCoin::Monitor::SPVTransactionData *>::iterator transaction =
          transactions.begin(); transaction != transactions.end(); ++transaction)
            (*transaction)->announceTime = transTime(*transaction, daemon);

        std::sort(transactions.begin(), transactions.end(), transGreater);

        setupTransactionClass(pEnvironment);

        // Set transactions
        std::vector<BitCoin::Monitor::SPVTransactionData *> newUpdatedTransactions;
        jobjectArray newTransactions = pEnvironment->NewObjectArray((jsize)transactions.size(),
          sTransactionClass, NULL);
        unsigned int index = 0;

        for(std::vector<BitCoin::Monitor::SPVTransactionData *>::iterator transaction =
          transactions.begin(); transaction != transactions.end(); ++transaction, ++index)
        {
            // Set value in array
            pEnvironment->SetObjectArrayElement(newTransactions, index,
              createTransaction(pEnvironment, daemon, *transaction));

            if(previousUpdate)
            {
                // Check if this is a new transaction
                if(!previousHashList.contains((*transaction)->transaction->hash))
                    newUpdatedTransactions.push_back(*transaction); // New transaction
                else if(!(*transaction)->blockHash.isEmpty() &&
                  !previousConfirmedHashList.contains((*transaction)->transaction->hash))
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
            for(std::vector<BitCoin::Monitor::SPVTransactionData *>::iterator transaction =
              newUpdatedTransactions.begin(); transaction != newUpdatedTransactions.end();
                 ++transaction, ++index)
            {
                // Set value in array
                pEnvironment->SetObjectArrayElement(updatedTransactions, index,
                  createTransaction(pEnvironment, daemon, *transaction));
            }

            pEnvironment->SetObjectField(pWallet, sWalletUpdatedTransactionsID,
              updatedTransactions);
        }

        pEnvironment->SetBooleanField(pWallet, sWalletIsPrivateID,
          (jboolean)daemon->keyStore()->hasPrivate((unsigned int)pOffset));
        pEnvironment->SetObjectField(pWallet, sWalletNameID,
          pEnvironment->NewStringUTF(daemon->keyStore()->name((unsigned int)pOffset).text()));
        pEnvironment->SetLongField(pWallet, sWalletBalanceID, (jlong)balance);
        pEnvironment->SetIntField(pWallet, sWalletBlockHeightID, blockHeight);
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
            jobject wallet = pEnvironment->GetObjectArrayElement(wallets, pOffset);
            pEnvironment->SetObjectField(wallet, sWalletNameID, pName);
        }

        return result;
    }

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_generateMnemonicSeed(JNIEnv *pEnvironment,
                                                                                             jobject pObject,
                                                                                             jint pEntropy)
    {
        return pEnvironment->NewStringUTF(BitCoin::Key::generateMnemonicSeed(BitCoin::Mnemonic::English, pEntropy).text());
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_addSeed(JNIEnv *pEnvironment,
                                                                             jobject pObject,
                                                                             jstring pPassCode,
                                                                             jstring pSeed,
                                                                             jint pDerivationMethod,
                                                                             jstring pName)
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
        int result = daemon->keyStore()->addSeed(seed, method);
        pEnvironment->ReleaseStringUTFChars(pSeed, seed);

        if(result != 0)
        {
            daemon->keyStore()->unloadPrivate();
            return (jint)result;
        }

        const char *name = pEnvironment->GetStringUTFChars(pName, NULL);
        daemon->keyStore()->setName(daemon->keyStore()->size() - 1, name);
        pEnvironment->ReleaseStringUTFChars(pName, name);

        if(savePrivateKeys(pEnvironment, daemon, pPassCode) && savePublicKeys(daemon))
        {
            daemon->monitor()->setKeyStore(daemon->keyStore());
            daemon->saveMonitor();
            daemon->keyStore()->unloadPrivate();
            return (jint)0;
        }
        else
        {
            daemon->keyStore()->unloadPrivate();
            return (jint)1;
        }
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

        bool result = daemon->keyStore()->remove((unsigned int)pOffset);

        if(!result)
        {
            daemon->keyStore()->unloadPrivate();
            return (jint)1;
        }

        if(savePrivateKeys(pEnvironment, daemon, pPassCode) && savePublicKeys(daemon))
        {
            daemon->monitor()->resetKeyStore();
            daemon->saveMonitor();
            daemon->keyStore()->unloadPrivate();
            return (jint)0;
        }
        else
        {
            daemon->keyStore()->unloadPrivate();
            return (jint)1;
        }
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
                                                                                              jint pOffset,
                                                                                              jint pIndex)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pOffset)
            return NULL;

        std::vector<BitCoin::Key *> *chainKeys = daemon->keyStore()->chainKeys((unsigned int)pOffset);

        if(chainKeys != NULL && chainKeys->size() != 0)
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

        return NULL;
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

        BitCoin::PaymentRequest::Format format = BitCoin::PaymentRequest::Format::INVALID;

        switch(pFormat)
        {
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
          BitCoin::MAINNET);
        pEnvironment->ReleaseStringUTFChars(pAddress, addressHashHex);

        return pEnvironment->NewStringUTF(result.text());
    }

    JNIEXPORT jobject JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_decodePaymentCode(JNIEnv *pEnvironment,
                                                                                          jobject pObject,
                                                                                          jstring pPaymentCode)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return NULL;

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
        pEnvironment->SetObjectField(result, sPaymentRequestCodeID, pPaymentCode);

        // Set format
        switch(request.format)
        {
            case BitCoin::PaymentRequest::Format::INVALID:
                pEnvironment->SetIntField(result, sPaymentRequestFormatID, (jint)0);
                break;
            case BitCoin::PaymentRequest::Format::LEGACY:
                pEnvironment->SetIntField(result, sPaymentRequestFormatID, (jint)1);
                break;
            case BitCoin::PaymentRequest::Format::CASH:
                pEnvironment->SetIntField(result, sPaymentRequestFormatID, (jint)2);
                break;
        }

        // Set protocol
        switch(request.protocol)
        {
            case BitCoin::PaymentRequest::Protocol::NONE:
                pEnvironment->SetIntField(result, sPaymentRequestProtocolID, (jint)0);
                break;
            case BitCoin::PaymentRequest::Protocol::ADDRESS:
                pEnvironment->SetIntField(result, sPaymentRequestProtocolID, (jint)1);
                break;
            case BitCoin::PaymentRequest::Protocol::REQUEST_AMOUNT:
                pEnvironment->SetIntField(result, sPaymentRequestProtocolID, (jint)2);
                break;
        }

        // Set address
        if(!request.address.isEmpty())
            pEnvironment->SetObjectField(result, sPaymentRequestAddressID,
              pEnvironment->NewStringUTF(request.address.hex()));

        // Set amount
        pEnvironment->SetLongField(result, sPaymentRequestAmountID, (jlong)request.amount);

        // Set description
        if(request.description)
            pEnvironment->SetObjectField(result, sPaymentRequestDescriptionID,
              pEnvironment->NewStringUTF(request.description));

        // Set secure
        pEnvironment->SetBooleanField(result, sPaymentRequestSecureID, (jboolean)request.secure);

        return result;
    }
}
