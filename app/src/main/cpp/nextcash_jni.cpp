
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
        sTransactionConstructor = pEnvironment->GetMethodID(sTransactionClass, "<init>",
          "()V");
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
        sBlockConstructor = pEnvironment->GetMethodID(sBlockClass, "<init>",
          "()V");
        sBlockHeightID = pEnvironment->GetFieldID(sBlockClass, "height",
          "I");
        sBlockHashID = pEnvironment->GetFieldID(sBlockClass, "hash",
          "Ljava/lang/String;");
        sBlockTimeID = pEnvironment->GetFieldID(sBlockClass, "time",
          "J");
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

    // Keys
    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_addKey(JNIEnv *pEnvironment,
                                                                            jobject pObject,
                                                                            jstring pKey,
                                                                            jint pDerivationMethod)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return (jint)1;

        const char *newPath = pEnvironment->GetStringUTFChars(pKey, NULL);
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

        int result = daemon->keyStore()->loadKey(newPath, method);
        if(result == 0)
        {
            if(daemon->saveKeyStore())
            {
                daemon->monitor()->setKeyStore(daemon->keyStore());
                daemon->saveMonitor();
            }
            else
                result = 1; // Failed to save
        }

        pEnvironment->ReleaseStringUTFChars(pKey, newPath);
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
                                                                                      jobject pObject,
                                                                                      jint pOffset)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pOffset)
            return JNI_FALSE;

        jobjectArray wallets = (jobjectArray)pEnvironment->GetObjectField(pObject,
          sBitcoinWalletsID);
        jobject wallet = pEnvironment->GetObjectArrayElement(wallets, pOffset);

        BitCoin::Key *key = daemon->keyStore()->at(pOffset);

        // Get and sort transactions
        std::vector<BitCoin::Monitor::SPVTransactionData *> transactions;
        if(!daemon->monitor()->getTransactions(key, transactions, true))
            return JNI_FALSE;

        jint blockHeight = (jint)daemon->chain()->height();
        bool previousUpdate = pEnvironment->GetLongField(wallet, sWalletLastUpdatedID) != 0;
        NextCash::HashList previousHashList, previousConfirmedHashList;

        // Check for updated transactions since previous update
        if(previousUpdate)
        {
            jobjectArray previousTransactions = (jobjectArray)pEnvironment->GetObjectField(wallet,
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

        pEnvironment->SetObjectField(wallet, sWalletTransactionsID, newTransactions);

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

            pEnvironment->SetObjectField(wallet, sWalletUpdatedTransactionsID,
              updatedTransactions);
        }

        pEnvironment->SetBooleanField(wallet, sWalletIsPrivateID, (jboolean)key->isPrivate());

        jstring name = pEnvironment->NewStringUTF(daemon->keyStore()->data.at(pOffset).name.text());
        pEnvironment->SetObjectField(wallet, sWalletNameID, name);

        pEnvironment->SetLongField(wallet, sWalletBalanceID,
          (jlong)daemon->monitor()->balance(key, false));

        pEnvironment->SetIntField(wallet, sWalletBlockHeightID, blockHeight);

        pEnvironment->SetLongField(wallet, sWalletLastUpdatedID, (jlong)BitCoin::getTime());
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
        daemon->keyStore()->data[pOffset].name = newName;
        jboolean result = (jboolean)daemon->saveKeyStore();
        pEnvironment->ReleaseStringUTFChars(pName, newName);
        return result;
    }

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_seed(JNIEnv *pEnvironment,
                                                                             jobject pObject,
                                                                             jint pOffset)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pOffset)
            return JNI_FALSE;

        return pEnvironment->NewStringUTF(daemon->keyStore()->data.at(pOffset).seed.text());
    }

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_getNextReceiveAddress(JNIEnv *pEnvironment,
                                                                                              jobject pObject,
                                                                                              jint pOffset)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL || daemon->keyStore()->size() <= pOffset)
            return NULL;

        BitCoin::Key *chain = daemon->keyStore()->at(pOffset)->chainKey(0,
          daemon->keyStore()->data.at(pOffset).derivationPathMethod);

        if(chain == NULL)
            return NULL;

        BitCoin::Key *unused = chain->getNextUnused();

        if(unused == NULL)
            return NULL;

        NextCash::String result = unused->address();
        return pEnvironment->NewStringUTF(result.text());
    }
}
