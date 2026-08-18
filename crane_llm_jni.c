// JNI bridge to the LiteRT-LM v0.16 C-API for Crane Reader.
//
// Reference copy: this repo ships the prebuilt libcrane_llm_jni.so (Android/src/app/src/main/
// jniLibs/arm64-v8a/), built from this exact source, and is not rebuilt by the Gradle build.
// Its exported Java_com_google_ai_edge_gallery_llm_CraneLlm_* symbols must match the Kotlin
// declarations in shared/src/androidMain/kotlin/com/google/ai/edge/gallery/llm/CraneLlm.kt —
// renaming/moving that object requires recompiling this file (see build recipe below).
//
// Mirrors the proven ~/crane_deploy/phone_harness.c configuration:
//   - CPU backend, benchmark enabled
//   - system message applied via conversation config (template-safe)
//   - per-turn optional args: max_output_tokens + repetition_penalty +
//     no_repeat_ngram (the doom-loop guards)
//
// All strings cross the JNI boundary as UTF-8 byte arrays to avoid the
// modified-UTF-8 pitfalls of NewStringUTF/GetStringUTFChars.
//
// Build (arm64 Linux host, e.g. DGX Spark): cross-compile with the host clang against the NDK
// sysroot (the NDK's prebuilt clang is x86_64 and won't run on an arm64 host):
//
//   clang-18 --target=aarch64-linux-android26 \
//     --sysroot=$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot \
//     -shared -fPIC -O2 -fuse-ld=lld -nostdlib \
//     -o libcrane_llm_jni.so crane_llm_jni.c \
//     -I<litert-lm-c-api>/include -L<litert-lm-c-api>/lib/android_arm64 \
//     -llitert-lm -llog -lc -ldl -lm \
//     -L<ndk-clang-rt-dir> -lclang_rt.builtins-aarch64-android -Wl,-z,defs
//
// The C-API package (headers + liblitert-lm.so for android_arm64) is the LiteRT-LM v0.16
// release asset (see downloadLiteRtLmCApi in Android/src/app/build.gradle.kts for the pinned
// URL + sha256).

#include <jni.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#include "conversation.h"
#include "engine.h"

#define TAG "CraneLlmJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static JavaVM* g_vm = NULL;

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  (void)reserved;
  g_vm = vm;
  return JNI_VERSION_1_6;
}

// Copies a Java byte[] into a NUL-terminated malloc'd C string. NULL-safe.
static char* bytes_to_cstr(JNIEnv* env, jbyteArray arr) {
  if (arr == NULL) return NULL;
  jsize n = (*env)->GetArrayLength(env, arr);
  char* out = malloc((size_t)n + 1);
  if (out == NULL) return NULL;
  (*env)->GetByteArrayRegion(env, arr, 0, n, (jbyte*)out);
  out[n] = '\0';
  return out;
}

// ---------------------------------------------------------------------------
// Engine
// ---------------------------------------------------------------------------

JNIEXPORT jlong JNICALL
Java_com_google_ai_edge_gallery_llm_CraneLlm_nativeCreateEngine(
    JNIEnv* env, jclass clazz, jbyteArray model_path_bytes) {
  (void)clazz;
  char* model_path = bytes_to_cstr(env, model_path_bytes);
  if (model_path == NULL) return 0;

  LiteRtLmEngineSettings* settings =
      litert_lm_engine_settings_create(model_path, "cpu", NULL, NULL);
  if (settings == NULL) {
    LOGE("engine_settings_create failed for %s", model_path);
    free(model_path);
    return 0;
  }
  litert_lm_engine_settings_enable_benchmark(settings);

  LiteRtLmEngine* engine = litert_lm_engine_create(settings);
  litert_lm_engine_settings_delete(settings);
  if (engine == NULL) {
    LOGE("engine_create failed for %s", model_path);
  } else {
    LOGI("engine created for %s", model_path);
  }
  free(model_path);
  return (jlong)(intptr_t)engine;
}

JNIEXPORT void JNICALL
Java_com_google_ai_edge_gallery_llm_CraneLlm_nativeDeleteEngine(
    JNIEnv* env, jclass clazz, jlong engine_handle) {
  (void)env;
  (void)clazz;
  if (engine_handle != 0) {
    litert_lm_engine_delete((LiteRtLmEngine*)(intptr_t)engine_handle);
  }
}

// ---------------------------------------------------------------------------
// Conversation
// ---------------------------------------------------------------------------

// system_message_json: full JSON message like
// {"role":"system","content":"..."} or NULL for no system prompt.
JNIEXPORT jlong JNICALL
Java_com_google_ai_edge_gallery_llm_CraneLlm_nativeCreateConversation(
    JNIEnv* env, jclass clazz, jlong engine_handle,
    jbyteArray system_message_json_bytes) {
  (void)clazz;
  if (engine_handle == 0) return 0;
  char* system_message_json = bytes_to_cstr(env, system_message_json_bytes);

  LiteRtLmConversationConfig* config = litert_lm_conversation_config_create();
  if (config == NULL) {
    free(system_message_json);
    return 0;
  }
  if (system_message_json != NULL && system_message_json[0] != '\0') {
    litert_lm_conversation_config_set_system_message(config,
                                                     system_message_json);
  }

  LiteRtLmConversation* conversation = litert_lm_conversation_create(
      (LiteRtLmEngine*)(intptr_t)engine_handle, config);
  litert_lm_conversation_config_delete(config);
  free(system_message_json);
  if (conversation == NULL) {
    LOGE("conversation_create failed");
  }
  return (jlong)(intptr_t)conversation;
}

JNIEXPORT void JNICALL
Java_com_google_ai_edge_gallery_llm_CraneLlm_nativeDeleteConversation(
    JNIEnv* env, jclass clazz, jlong conversation_handle) {
  (void)env;
  (void)clazz;
  if (conversation_handle != 0) {
    litert_lm_conversation_delete(
        (LiteRtLmConversation*)(intptr_t)conversation_handle);
  }
}

JNIEXPORT void JNICALL
Java_com_google_ai_edge_gallery_llm_CraneLlm_nativeCancel(
    JNIEnv* env, jclass clazz, jlong conversation_handle) {
  (void)env;
  (void)clazz;
  if (conversation_handle != 0) {
    litert_lm_conversation_cancel_process(
        (LiteRtLmConversation*)(intptr_t)conversation_handle);
  }
}

JNIEXPORT jint JNICALL
Java_com_google_ai_edge_gallery_llm_CraneLlm_nativeLastPrefillTokenCount(
    JNIEnv* env, jclass clazz, jlong conversation_handle) {
  (void)env;
  (void)clazz;
  if (conversation_handle == 0) return 0;
  LiteRtLmBenchmarkInfo* info = litert_lm_conversation_get_benchmark_info(
      (LiteRtLmConversation*)(intptr_t)conversation_handle);
  if (info == NULL) return 0;
  int turns = litert_lm_benchmark_info_get_num_prefill_turns(info);
  if (turns <= 0) return 0;
  return litert_lm_benchmark_info_get_prefill_token_count_at(info, turns - 1);
}

// ---------------------------------------------------------------------------
// Streaming send (blocks the calling thread until the final chunk)
// ---------------------------------------------------------------------------

typedef struct {
  jobject callback;    // global ref to CraneLlm.ChunkCallback
  jmethodID on_chunk;  // onChunk(byte[] text, boolean done, byte[] error)
  pthread_mutex_t mutex;
  pthread_cond_t cond;
  int done;
} StreamCtx;

static void stream_callback(void* data, const LiteRtLmStreamChunk* chunk) {
  StreamCtx* ctx = (StreamCtx*)data;
  JNIEnv* env = NULL;
  int attached = 0;
  if ((*g_vm)->GetEnv(g_vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
    if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != JNI_OK) return;
    attached = 1;
  }

  const char* text = litert_lm_stream_chunk_get_text(chunk);
  const char* error = litert_lm_stream_chunk_get_error(chunk);
  // Treat an error chunk as terminal so the blocked caller always wakes up.
  int is_final = (litert_lm_stream_chunk_is_final(chunk) ||
                  (error != NULL && error[0] != '\0'))
                     ? 1
                     : 0;

  jbyteArray jtext = NULL;
  if (text != NULL && text[0] != '\0') {
    size_t n = strlen(text);
    jtext = (*env)->NewByteArray(env, (jsize)n);
    if (jtext != NULL) {
      (*env)->SetByteArrayRegion(env, jtext, 0, (jsize)n, (const jbyte*)text);
    }
  }
  jbyteArray jerror = NULL;
  if (error != NULL && error[0] != '\0') {
    size_t n = strlen(error);
    jerror = (*env)->NewByteArray(env, (jsize)n);
    if (jerror != NULL) {
      (*env)->SetByteArrayRegion(env, jerror, 0, (jsize)n,
                                 (const jbyte*)error);
    }
    LOGE("stream chunk error: %s", error);
  }

  (*env)->CallVoidMethod(env, ctx->callback, ctx->on_chunk, jtext,
                         (jboolean)is_final, jerror);
  if ((*env)->ExceptionCheck(env)) {
    (*env)->ExceptionDescribe(env);
    (*env)->ExceptionClear(env);
  }
  if (jtext != NULL) (*env)->DeleteLocalRef(env, jtext);
  if (jerror != NULL) (*env)->DeleteLocalRef(env, jerror);
  if (attached) (*g_vm)->DetachCurrentThread(g_vm);

  if (is_final) {
    pthread_mutex_lock(&ctx->mutex);
    ctx->done = 1;
    pthread_cond_signal(&ctx->cond);
    pthread_mutex_unlock(&ctx->mutex);
  }
}

// message_json: full JSON message like {"role":"user","content":"..."}.
// Blocks until the final chunk arrives. Returns 0 on success.
JNIEXPORT jint JNICALL
Java_com_google_ai_edge_gallery_llm_CraneLlm_nativeSendMessageStream(
    JNIEnv* env, jclass clazz, jlong conversation_handle,
    jbyteArray message_json_bytes, jint max_output_tokens,
    jfloat repetition_penalty, jint no_repeat_ngram_size, jobject callback) {
  (void)clazz;
  if (conversation_handle == 0 || callback == NULL) return -1;
  char* message_json = bytes_to_cstr(env, message_json_bytes);
  if (message_json == NULL) return -1;

  LiteRtLmConversationOptionalArgs* args =
      litert_lm_conversation_optional_args_create();
  if (max_output_tokens > 0) {
    litert_lm_conversation_optional_args_set_max_output_tokens(
        args, max_output_tokens);
  }
  if (repetition_penalty > 0.0f) {
    LiteRtLmRepetitionPenaltyConfig* rep =
        litert_lm_repetition_penalty_config_create();
    litert_lm_repetition_penalty_config_set_repetition_penalty(
        rep, repetition_penalty);
    litert_lm_conversation_optional_args_set_repetition_penalty_config(args,
                                                                       rep);
    litert_lm_repetition_penalty_config_delete(rep);  // deep-copied on set
  }
  if (no_repeat_ngram_size > 0) {
    LiteRtLmNoRepeatNgramConfig* ngram =
        litert_lm_no_repeat_ngram_config_create();
    litert_lm_no_repeat_ngram_config_set_no_repeat_ngram_size(
        ngram, no_repeat_ngram_size);
    litert_lm_conversation_optional_args_set_no_repeat_ngram_config(args,
                                                                    ngram);
    litert_lm_no_repeat_ngram_config_delete(ngram);  // deep-copied on set
  }

  StreamCtx ctx;
  ctx.callback = (*env)->NewGlobalRef(env, callback);
  jclass cb_class = (*env)->GetObjectClass(env, callback);
  ctx.on_chunk = (*env)->GetMethodID(env, cb_class, "onChunk", "([BZ[B)V");
  pthread_mutex_init(&ctx.mutex, NULL);
  pthread_cond_init(&ctx.cond, NULL);
  ctx.done = 0;

  int rc = -1;
  if (ctx.on_chunk != NULL) {
    rc = litert_lm_conversation_send_message_stream(
        (LiteRtLmConversation*)(intptr_t)conversation_handle, message_json,
        NULL, args, stream_callback, &ctx);
    if (rc == 0) {
      pthread_mutex_lock(&ctx.mutex);
      while (!ctx.done) pthread_cond_wait(&ctx.cond, &ctx.mutex);
      pthread_mutex_unlock(&ctx.mutex);
    } else {
      LOGE("send_message_stream failed rc=%d", rc);
    }
  } else {
    LOGE("onChunk([BZ[B)V not found on callback");
  }

  (*env)->DeleteGlobalRef(env, ctx.callback);
  pthread_mutex_destroy(&ctx.mutex);
  pthread_cond_destroy(&ctx.cond);
  litert_lm_conversation_optional_args_delete(args);
  free(message_json);
  return rc;
}
