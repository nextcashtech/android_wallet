/**************************************************************************
 * Copyright 2018 NextCash, LLC                                            *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.com>                                    *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
#include "nextcash_jni.hpp"

#include "base.hpp"
#include "daemon.hpp"


extern "C"
{
    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_NextCash_destroy(JNIEnv *pEnvironment)
    {
        NextCash::Log::destroy();
    }

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_userAgent(JNIEnv *pEnvironment)
    {
        return pEnvironment->NewStringUTF(BITCOIN_USER_AGENT);
    }

    JNIEXPORT jstring JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_networkName(JNIEnv *pEnvironment)
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

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setPath(JNIEnv *pEnvironment,
                                                                             jclass pClass,
                                                                             jstring pPath)
    {
        const char *newPath = pEnvironment->GetStringUTFChars(pPath, NULL);
        BitCoin::Info::instance().setPath(newPath);
        pEnvironment->ReleaseStringUTFChars(pPath, newPath);
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_load(JNIEnv *pEnvironment)
    {
        return (jboolean)BitCoin::Daemon::instance().load();
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isLoaded(JNIEnv *pEnvironment)
    {
        return (jboolean)BitCoin::Daemon::instance().isLoaded();
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_isRunning(JNIEnv *pEnvironment)
    {
        return (jboolean)BitCoin::Daemon::instance().isRunning();
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_run(JNIEnv *pEnvironment, jclass pClass, jint pMode)
    {
        BitCoin::Daemon::instance().setFinishMode(pMode);
        BitCoin::Daemon::instance().run(false);
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_destroy(JNIEnv *pEnvironment)
    {
        BitCoin::Daemon::destroy();
        BitCoin::Info::destroy();
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_stopDaemon(JNIEnv *pEnvironment)
    {
        BitCoin::Daemon::instance().requestStop();
    }

    JNIEXPORT void JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_setFinishMode(JNIEnv *pEnvironment, jclass pClass, jint pMode)
    {
        BitCoin::Daemon::instance().setFinishMode(pMode);
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_peerCount(JNIEnv *pEnvironment)
    {
        return BitCoin::Daemon::instance().peerCount();
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_status(JNIEnv *pEnvironment)
    {
        switch(BitCoin::Daemon::instance().status())
        {
            default:
            case BitCoin::Daemon::INACTIVE:
                return 0;
            case BitCoin::Daemon::LOADING:
                return 1;
            case BitCoin::Daemon::FINDING_PEERS:
                return 2;
            case BitCoin::Daemon::CONNECTING_TO_PEERS:
                return 3;
            case BitCoin::Daemon::SYNCHRONIZING:
                return 4;
            case BitCoin::Daemon::SYNCHRONIZED:
                return 5;
            case BitCoin::Daemon::FINDING_TRANSACTIONS:
                return 6;
        }
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_blockHeight(JNIEnv *pEnvironment)
    {
        return BitCoin::Daemon::instance().chain()->height();
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_merkleHeight(JNIEnv *pEnvironment)
    {
        return BitCoin::Daemon::instance().monitor()->height();
    }

    JNIEXPORT jlong JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_balance(JNIEnv *pEnvironment)
    {
        return BitCoin::Daemon::instance().monitor()->balance();
    }

    // Keys
    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_addKey(JNIEnv *pEnvironment,
                                                                                jclass pClass,
                                                                                jstring pKey,
                                                                                jint pDerivationMethod)
    {
        bool success = false;
        const char *newPath = pEnvironment->GetStringUTFChars(pKey, NULL);
        NextCash::Buffer buffer;
        buffer.writeString(newPath);

        BitCoin::Daemon &daemon = BitCoin::Daemon::instance();
        if(daemon.keyStore()->loadKeys(&buffer) && daemon.saveKeyStore())
        {
            daemon.monitor()->setKeyStore(daemon.keyStore());
            daemon.saveMonitor();
            success = true;
        }

        pEnvironment->ReleaseStringUTFChars(pKey, newPath);
        return (jboolean)success;
    }

    JNIEXPORT jint JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_keyCount(JNIEnv *pEnvironment)
    {
        return (jint)BitCoin::Daemon::instance().keyStore()->size();
    }

    JNIEXPORT jboolean JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_keyIsPrivate(JNIEnv *pEnvironment,
                                                                                      jclass pClass,
                                                                                      jint pKeyOffset)
    {
        BitCoin::KeyStore *keyStore = BitCoin::Daemon::instance().keyStore();
        if(keyStore != NULL && keyStore->size() > pKeyOffset)
            return (jboolean)keyStore->at(pKeyOffset)->isPrivate();
        else
            return JNI_FALSE;
    }

    JNIEXPORT jlong JNICALL Java_tech_nextcash_nextcashwallet_Bitcoin_keyBalance(JNIEnv *pEnvironment,
                                                                                 jclass pClass,
                                                                                 jint pKeyOffset,
                                                                                 jboolean pIncludePending)
    {
        BitCoin::Daemon &daemon = BitCoin::Daemon::instance();
        if(pKeyOffset >= daemon.keyStore()->size())
            return 0L;
        BitCoin::Key *key = (*daemon.keyStore())[pKeyOffset];
        return (jlong)daemon.monitor()->balance(key, pIncludePending);
    }
}
