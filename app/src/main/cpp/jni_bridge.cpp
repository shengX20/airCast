// SPDX-License-Identifier: Apache-2.0
//
// jni_bridge.cpp -- JNI bridge between Kotlin and the C++ RAOP sender.
// ---------------------------------------------------------------------------
// Owns the RaopLoop host, RaopSender state machine, and the audio ring buffer.
// Runs the pump loop on a dedicated background thread. Audio PCM data is fed
// in from Kotlin (via MediaProjection / AudioRecord) into the ring buffer.

#include "raop_sender.h"
#include "raop_loop.h"
#include "ring_buffer.h"

#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <thread>
#include <string>

#define LOG_TAG "AirPlayCast"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace fxchain;

namespace {

// Singleton state held in native code.
struct NativeState {
    std::mutex mutex;
    std::unique_ptr<RaopLoop> loop;
    std::unique_ptr<RaopSender> sender;
    std::unique_ptr<RingBuffer<int16_t>> ring;
    std::unique_ptr<std::thread> pumpThread;
    std::atomic<bool> running{false};

    JavaVM* jvm = nullptr;
    jobject callbackObj = nullptr;  // global ref

    // Cached method IDs
    jmethodID onLaunched = nullptr;
    jmethodID onClosed = nullptr;
    jmethodID onPinRequired = nullptr;
    jmethodID onCredentials = nullptr;
};

NativeState g;

// Call a Kotlin callback on the JVM thread.
void callVoidCallback(JNIEnv* env, jobject obj, jmethodID method, ...) {
    if (!obj || !method) return;
    va_list args;
    va_start(args, method);
    env->CallVoidMethodV(obj, method, args);
    va_end(args);
}

JNIEnv* attachCurrentThread() {
    if (!g.jvm) return nullptr;
    JNIEnv* env = nullptr;
    jint rc = g.jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (rc == JNI_EDETACHED) {
        g.jvm->AttachCurrentThread(&env, nullptr);
    }
    return env;
}

void detachCurrentThread() {
    if (g.jvm) g.jvm->DetachCurrentThread();
}

void pumpLoopFunc() {
    JNIEnv* env = nullptr;
    g.jvm->AttachCurrentThread(&env, nullptr);

    while (g.running.load() && g.loop && g.sender) {
        g.loop->pump(*g.sender, std::chrono::milliseconds(50));
    }

    g.jvm->DetachCurrentThread();
}

} // anonymous namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_airplay_cast_native_NativeBridge_nativeInit(JNIEnv* env, jobject /*thiz*/, jobject callbackObj) {
    std::lock_guard<std::mutex> lock(g.mutex);

    env->GetJavaVM(&g.jvm);
    if (g.callbackObj) env->DeleteGlobalRef(g.callbackObj);
    g.callbackObj = env->NewGlobalRef(callbackObj);

    jclass cls = env->GetObjectClass(callbackObj);
    g.onLaunched    = env->GetMethodID(cls, "onLaunched", "(ZLjava/lang/String;)V");
    g.onClosed      = env->GetMethodID(cls, "onClosed", "()V");
    g.onPinRequired = env->GetMethodID(cls, "onPinRequired", "(Ljava/lang/String;)V");
    g.onCredentials = env->GetMethodID(cls, "onCredentialsObtained", "(Ljava/lang/String;Ljava/lang/String;)V");
    env->DeleteLocalRef(cls);

    LOGI("Native bridge initialized");
}

JNIEXPORT jboolean JNICALL
Java_com_airplay_cast_native_NativeBridge_nativeConnect(
        JNIEnv* env, jobject /*thiz*/,
        jstring ip, jint port,
        jstring deviceId, jint authType, jboolean airplay2,
        jstring credsJson, jstring deviceName) {

    std::lock_guard<std::mutex> lock(g.mutex);

    if (g.running.load()) {
        LOGW("Already running, stop first");
        return JNI_FALSE;
    }

    const char* ipC = env->GetStringUTFChars(ip, nullptr);
    const char* devIdC = env->GetStringUTFChars(deviceId, nullptr);
    const char* credsC = credsJson ? env->GetStringUTFChars(credsJson, nullptr) : "";
    const char* devNameC = deviceName ? env->GetStringUTFChars(deviceName, nullptr) : "AirPlay Cast";

    // Create the ring buffer (44100 * 2 * 4 = ~352KB, enough for 2 seconds of stereo)
    g.ring = std::make_unique<RingBuffer<int16_t>>(44100 * 2 * 4);

    g.loop = std::make_unique<RaopLoop>();

    RaopEvents events;
    events.launched = [](bool ok, const std::string& error) {
        JNIEnv* e = attachCurrentThread();
        if (!e || !g.callbackObj) return;
        jstring err = e->NewStringUTF(error.c_str());
        e->CallVoidMethod(g.callbackObj, g.onLaunched, ok ? JNI_TRUE : JNI_FALSE, err);
        e->DeleteLocalRef(err);
    };
    events.closed = []() {
        JNIEnv* e = attachCurrentThread();
        if (!e || !g.callbackObj) return;
        e->CallVoidMethod(g.callbackObj, g.onClosed);
    };
    events.pinRequired = [](const std::string& name) {
        JNIEnv* e = attachCurrentThread();
        if (!e || !g.callbackObj) return;
        jstring n = e->NewStringUTF(name.c_str());
        e->CallVoidMethod(g.callbackObj, g.onPinRequired, n);
        e->DeleteLocalRef(n);
    };
    events.credentialsObtained = [](const std::string& devId, const std::string& creds) {
        JNIEnv* e = attachCurrentThread();
        if (!e || !g.callbackObj) return;
        jstring d = e->NewStringUTF(devId.c_str());
        jstring c = e->NewStringUTF(creds.c_str());
        e->CallVoidMethod(g.callbackObj, g.onCredentials, d, c);
        e->DeleteLocalRef(d);
        e->DeleteLocalRef(c);
    };

    RaopLogSink logSink = [](RaopLogLevel lvl, const std::string& msg) {
        if (lvl == RaopLogLevel::Warn)
            LOGW("%s", msg.c_str());
        else
            LOGI("%s", msg.c_str());
    };

    g.sender = std::make_unique<RaopSender>(*g.loop, std::move(events), logSink);
    g.sender->attachRing(g.ring.get());
    g.sender->setInputFormat(44100);  // Android AudioRecord typically 48kHz

    // Set identity
    RaopIdentity identity;
    identity.name = devNameC;
    identity.deviceId = devIdC;
    g.sender->setIdentity(identity);

    // Set auth
    auto auth = static_cast<RaopDeviceInfo::Auth>(authType);
    g.sender->setAuth(auth, airplay2 == JNI_TRUE, devIdC, credsC ? credsC : "", "");

    // Start
    g.sender->start(ipC, static_cast<uint16_t>(port), devNameC);

    env->ReleaseStringUTFChars(ip, ipC);
    env->ReleaseStringUTFChars(deviceId, devIdC);
    if (credsJson) env->ReleaseStringUTFChars(credsJson, credsC);
    env->ReleaseStringUTFChars(deviceName, devNameC);

    g.running.store(true);
    g.pumpThread = std::make_unique<std::thread>(pumpLoopFunc);

    LOGI("Connecting to %s:%d", ipC, port);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_airplay_cast_native_NativeBridge_nativeDisconnect(JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g.mutex);

    if (!g.running.load()) return;

    g.running.store(false);
    g.loop->requestStop();

    if (g.sender) g.sender->stop();

    if (g.pumpThread && g.pumpThread->joinable()) {
        g.pumpThread->join();
    }
    g.pumpThread.reset();
    g.sender.reset();
    g.loop.reset();
    g.ring.reset();

    LOGI("Disconnected");
}

JNIEXPORT jint JNICALL
Java_com_airplay_cast_native_NativeBridge_nativeFeedAudio(
        JNIEnv* env, jobject /*thiz*/, jshortArray pcmData, jint numSamples) {

    if (!g.ring || !g.running.load()) return 0;

    jshort* samples = env->GetShortArrayElements(pcmData, nullptr);
    if (!samples) return 0;

    size_t pushed = 0;
    // RingBuffer expects spans; push in chunks
    size_t chunk = 4096;
    size_t remaining = static_cast<size_t>(numSamples);
    size_t offset = 0;
    while (remaining > 0) {
        size_t n = std::min(chunk, remaining);
        std::span<const int16_t> span(samples + offset, n);
        if (!g.ring->tryPush(span)) break;
        pushed += n;
        offset += n;
        remaining -= n;
    }

    env->ReleaseShortArrayElements(pcmData, samples, JNI_ABORT);
    return static_cast<jint>(pushed);
}

JNIEXPORT void JNICALL
Java_com_airplay_cast_native_NativeBridge_nativeSubmitPin(JNIEnv* env, jobject /*thiz*/, jstring pin) {
    std::lock_guard<std::mutex> lock(g.mutex);
    if (!g.sender) return;
    const char* pinC = env->GetStringUTFChars(pin, nullptr);
    g.sender->submitPin(pinC);
    env->ReleaseStringUTFChars(pin, pinC);
}

JNIEXPORT void JNICALL
Java_com_airplay_cast_native_NativeBridge_nativeSetVolume(JNIEnv* /*env*/, jobject /*thiz*/, jdouble pct) {
    std::lock_guard<std::mutex> lock(g.mutex);
    if (!g.sender) return;
    g.sender->setVolume(pct);
}

JNIEXPORT jboolean JNICALL
Java_com_airplay_cast_native_NativeBridge_nativeIsActive(JNIEnv* /*env*/, jobject /*thiz*/) {
    return g.running.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_airplay_cast_native_NativeBridge_nativeSetNowPlaying(
        JNIEnv* env, jobject /*thiz*/,
        jstring title, jstring artist, jstring album) {
    std::lock_guard<std::mutex> lock(g.mutex);
    if (!g.sender) return;
    const char* t = title ? env->GetStringUTFChars(title, nullptr) : "";
    const char* a = artist ? env->GetStringUTFChars(artist, nullptr) : "";
    const char* al = album ? env->GetStringUTFChars(album, nullptr) : "";
    g.sender->setNowPlaying(t, a, al);
    if (title) env->ReleaseStringUTFChars(title, t);
    if (artist) env->ReleaseStringUTFChars(artist, a);
    if (album) env->ReleaseStringUTFChars(album, al);
}

} // extern "C"
