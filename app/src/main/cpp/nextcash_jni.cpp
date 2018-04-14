
#include "nextcash_jni.hpp"

#include "base.hpp"
#include "daemon.hpp"

#define NEXTCASH_JNI_LOG_NAME "JNI"


extern "C"
{
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

    BitCoin::Daemon *getDaemon(JNIEnv *pEnvironment, jobject pObject, bool pCreate = false)
    {
        jclass bitcoinClass = pEnvironment->GetObjectClass(pObject);
        jfieldID bitcoinHandleFieldID = pEnvironment->GetFieldID(bitcoinClass, "mHandle", "J");
        jlong handle = pEnvironment->GetLongField(pObject, bitcoinHandleFieldID);

        if(handle != 0)
            return reinterpret_cast<BitCoin::Daemon *>(handle);

        if(!pCreate)
            return NULL;

        // Create handle
        // NOTE : Daemon object apparently needs to be created in the thread running it. So make
        //   sure the functions with pCreate=true are only called on that Java thread.
        BitCoin::Daemon *daemon = new BitCoin::Daemon();
        handle = jlong(daemon);

        pEnvironment->SetLongField(pObject, bitcoinHandleFieldID, handle);
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
        jclass bitcoinClass = pEnvironment->GetObjectClass(pObject);
        jfieldID bitcoinHandleFieldID = pEnvironment->GetFieldID(bitcoinClass, "mHandle", "J");
        pEnvironment->SetLongField(pObject, bitcoinHandleFieldID, 0);

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
                NextCash::Log::add(NextCash::Log::WARNING, NEXTCASH_JNI_LOG_NAME, "Bitcoin failed to load");
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

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isLoaded(JNIEnv *pEnvironment,
                                                                                  jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return JNI_FALSE;

        return (jboolean)daemon->isLoaded();
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isRunning(JNIEnv *pEnvironment,
                                                                                   jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return JNI_FALSE;

        return (jboolean)daemon->isRunning();
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

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_stop(JNIEnv *pEnvironment,
                                                                          jobject pObject)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return;

        daemon->requestStop();
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

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_keyIsPrivate(JNIEnv *pEnvironment,
                                                                                      jobject pObject,
                                                                                      jint pKeyOffset)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return JNI_FALSE;

        BitCoin::KeyStore *keyStore = daemon->keyStore();
        if(keyStore != NULL && keyStore->size() > pKeyOffset)
            return (jboolean)keyStore->at(pKeyOffset)->isPrivate();
        else
            return JNI_FALSE;
    }

    JNIEXPORT jlong JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_keyBalance(JNIEnv *pEnvironment,
                                                                                 jobject pObject,
                                                                                 jint pKeyOffset,
                                                                                 jboolean pIncludePending)
    {
        BitCoin::Daemon *daemon = getDaemon(pEnvironment, pObject);
        if(daemon == NULL)
            return (jlong)0L;

        if(pKeyOffset >= daemon->keyStore()->size())
            return 0L;
        BitCoin::Key *key = (*daemon->keyStore())[pKeyOffset];
        return (jlong)daemon->monitor()->balance(key, pIncludePending);
    }
}
