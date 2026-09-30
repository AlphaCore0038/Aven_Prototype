// JNI bridge between Aven (Kotlin) and llama.cpp for on-device generation.
// Only greedy sampling is used; stop tokens end generation.

#include <jni.h>
#include <algorithm>
#include <android/log.h>
#include <atomic>
#include <cstring>
#include <ctime>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "llama.h"

namespace {

constexpr int MAX_TOKENS = 1024;

std::atomic<bool> g_stop(false);

// Route llama.cpp / ggml logs (LLAMA_LOG_*, GGML_ABORT reasons) into logcat;
// they go to stdout by default, which Android discards.
void android_log_callback(ggml_log_level level, const char * text, void * /*user_data*/) {
    android_LogPriority prio = ANDROID_LOG_INFO;
    if (level == GGML_LOG_LEVEL_WARN) prio = ANDROID_LOG_WARN;
    else if (level == GGML_LOG_LEVEL_ERROR || level == GGML_LOG_LEVEL_CONT) prio = ANDROID_LOG_ERROR;
    // ggml emits line fragments for CONT levels; pass through as-is.
    __android_log_print(prio, "aven-llama", "%s", text);
}

// llama_backend_init must run once per process; repeated calls leak.
std::once_flag g_backend_once;

void init_backend_once() {
    std::call_once(g_backend_once, [] {
        // llama_log_set covers libllama; ggml_log_set covers the ggml kernels
        // (abort reasons, backend errors). Both land in logcat under
        // the "aven-llama" tag.
        llama_log_set(android_log_callback, nullptr);
        ggml_log_set(android_log_callback, nullptr);
        llama_backend_init();
        llama_numa_init(GGML_NUMA_STRATEGY_DISABLED);
    });
}

/**
 * Mobile big.LITTLE heuristic: use half of the reported cores, clamped to a
 * sane range. Pure little-core scheduling hurts more than fewer threads.
 */
int detect_thread_count() {
    const unsigned int hw = std::thread::hardware_concurrency();
    const unsigned int half = hw == 0 ? 4u : hw / 2u;
    return static_cast<int>(std::clamp(half, 2u, 6u));
}

struct AvenModel {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    llama_sampler * sampler = nullptr;
};

jclass g_listener_class = nullptr;
jmethodID g_listener_on_token = nullptr;

void ensure_listener(JNIEnv * env) {
    if (g_listener_class != nullptr) {
        return;
    }
    jclass local = env->FindClass("com/aven/ai/engine/local/LlamaTokenListener");
    if (local == nullptr) {
        return;
    }
    g_listener_class = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    g_listener_on_token = env->GetMethodID(g_listener_class, "onToken", "(Ljava/lang/String;)V");
}

void throw_java(JNIEnv * env, const char * message) {
    jclass ex = env->FindClass("java/lang/IllegalStateException");
    if (ex != nullptr) {
        env->ThrowNew(ex, message);
    }
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_aven_ai_engine_local_LlamaNative_nativeLoad(JNIEnv * env, jobject /*thiz*/, jstring path) {
    const char * path_c = env->GetStringUTFChars(path, nullptr);
    if (path_c == nullptr) {
        return 0;
    }

    init_backend_once();

    llama_model_params model_params = llama_model_default_params();
    llama_model * model = llama_model_load_from_file(path_c, model_params);
    env->ReleaseStringUTFChars(path, path_c);
    if (model == nullptr) {
        throw_java(env, "Failed to load the local model");
        return 0;
    }

    const int n_threads = detect_thread_count();
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_batch = 1024;
    ctx_params.n_ubatch = 512;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    ctx_params.no_perf = true; // Mobile-friendly: disable sampling performance timers

    llama_context * ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        llama_model_free(model);
        throw_java(env, "Failed to create the inference context");
        return 0;
    }

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    // Pure greedy rambles and loops on small models; light sampling gives
    // shorter, more natural replies.
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(
            llama_vocab_n_tokens(llama_model_get_vocab(model)), 64, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist((uint32_t) time(nullptr)));

    AvenModel * handle = new AvenModel();
    handle->model = model;
    handle->ctx = ctx;
    handle->sampler = sampler;

    ensure_listener(env);

    return reinterpret_cast<jlong>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_aven_ai_engine_local_LlamaNative_nativeUnload(JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) {
        return;
    }
    AvenModel * aven = reinterpret_cast<AvenModel *>(handle);
    if (aven->sampler != nullptr) {
        llama_sampler_free(aven->sampler);
    }
    if (aven->ctx != nullptr) {
        llama_free(aven->ctx);
    }
    if (aven->model != nullptr) {
        llama_model_free(aven->model);
    }
    delete aven;
}

extern "C" JNIEXPORT void JNICALL
Java_com_aven_ai_engine_local_LlamaNative_nativeCancel(JNIEnv * /*env*/, jobject /*thiz*/) {
    g_stop.store(true);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aven_ai_engine_local_LlamaNative_nativeGenerate(JNIEnv * env, jobject /*thiz*/, jlong handle,
                                                         jstring prompt, jobject listener) {
    if (handle == 0) {
        throw_java(env, "Local model is not loaded");
        return nullptr;
    }
    AvenModel * aven = reinterpret_cast<AvenModel *>(handle);

    const char * prompt_c = env->GetStringUTFChars(prompt, nullptr);
    if (prompt_c == nullptr) {
        return nullptr;
    }

    const llama_vocab * vocab = llama_model_get_vocab(aven->model);
    const llama_token eos = llama_vocab_eos(vocab);
    const llama_token eot = llama_vocab_eot(vocab);

    std::vector<llama_token> tokens(llama_vocab_n_tokens(vocab));
    // The vendored llama.cpp has no "-1 means strlen" convention; passing -1
    // builds std::string(text, SIZE_MAX) and throws std::length_error.
    const int32_t n_prompt = llama_tokenize(vocab, prompt_c, (int32_t) strlen(prompt_c), tokens.data(), tokens.size(), true, false);
    env->ReleaseStringUTFChars(prompt, prompt_c);
    if (n_prompt < 0) {
        throw_java(env, "Failed to tokenize the prompt");
        return nullptr;
    }
    tokens.resize(n_prompt);

    const int32_t n_ctx = llama_n_ctx(aven->ctx);
    if (n_prompt >= n_ctx - 1) {
        throw_java(env, "The message is too long for the on-device context window");
        return nullptr;
    }

    g_stop.store(false);

    // Stateless sessions: each request starts from KV position 0. The
    // conversation history arrives as prompt text, so nothing is lost;
    // without this, positions accumulate across sends until the context
    // fills up and llama_decode aborts on a later message.
    llama_memory_clear(llama_get_memory(aven->ctx), true);

    // decode() hard-aborts when a single call carries more than n_batch
    // tokens, so feed the prompt in n_batch-sized slices; positions advance
    // automatically across calls.
    constexpr int32_t DECODE_CHUNK = 512;  // keep <= ctx_params.n_batch
    for (int32_t done = 0; done < n_prompt && !g_stop.load(); done += DECODE_CHUNK) {
        const int32_t take = std::min(DECODE_CHUNK, n_prompt - done);
        llama_batch chunk = llama_batch_get_one(tokens.data() + done, take);
        if (llama_decode(aven->ctx, chunk) != 0) {
            throw_java(env, "Failed to process the message");
            return nullptr;
        }
    }

    std::string output;
    for (int step = 0; step < MAX_TOKENS && n_prompt + step < n_ctx - 1; ++step) {
        if (g_stop.load()) {
            break;
        }
        const llama_token token = llama_sampler_sample(aven->sampler, aven->ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }
        llama_sampler_accept(aven->sampler, token);

        char piece_buf[256];
        const int n_piece = llama_token_to_piece(vocab, token, piece_buf, sizeof(piece_buf), 0, false);
        if (n_piece < 0) {
            break;
        }

        // CRITICAL FIX: Ensure null-termination before creating Java string
        if (n_piece < (int)sizeof(piece_buf)) {
            piece_buf[n_piece] = '\0';
        } else {
            piece_buf[sizeof(piece_buf) - 1] = '\0';
        }

        output.append(piece_buf, n_piece);

        if (listener != nullptr && g_listener_on_token != nullptr) {
            jstring piece = env->NewStringUTF(piece_buf);
            if (env->ExceptionCheck()) {
                // NewStringUTF failed (e.g. OOM). Clearing here keeps the JVM
                // from aborting on the next JNI call; generation stops.
                env->ExceptionClear();
                break;
            }
            env->CallVoidMethod(listener, g_listener_on_token, piece);
            env->DeleteLocalRef(piece);
            if (env->ExceptionCheck()) {
                // The Kotlin collector threw inside the callback (typically a
                // cancellation when the user stops generation). Continuing to
                // call JNI with a pending exception is a fatal runtime abort,
                // so stop cleanly instead.
                env->ExceptionClear();
                break;
            }
        }

        llama_token one = token;
        llama_batch one_batch = llama_batch_get_one(&one, 1);
        if (llama_decode(aven->ctx, one_batch) != 0) {
            break;
        }
    }

    // Never return from JNI with a pending exception — that aborts the
    // process. The Kotlin layer surfaces an empty/failed stream instead.
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aven_ai_engine_local_LlamaNative_nativeGenerateChat(JNIEnv * env, jobject /*thiz*/, jlong handle,
                                                              jstring system,
                                                              jobjectArray historyRoles,
                                                              jobjectArray historyContents,
                                                              jstring prompt,
                                                              jobject listener) {
    if (handle == 0) {
        throw_java(env, "Local model is not loaded");
        return nullptr;
    }
    AvenModel * aven = reinterpret_cast<AvenModel *>(handle);

    const char * system_c = env->GetStringUTFChars(system, nullptr);
    const char * prompt_c = env->GetStringUTFChars(prompt, nullptr);
    if (system_c == nullptr || prompt_c == nullptr) {
        if (system_c) env->ReleaseStringUTFChars(system, system_c);
        if (prompt_c) env->ReleaseStringUTFChars(prompt, prompt_c);
        return nullptr;
    }

    jsize historyLen = 0;
    if (historyRoles && historyContents) {
        jsize rlen = env->GetArrayLength(historyRoles);
        jsize clen = env->GetArrayLength(historyContents);
        historyLen = rlen < clen ? rlen : clen;
    }

    std::vector<std::string> roleStorage;
    std::vector<std::string> contentStorage;
    roleStorage.reserve(1 + historyLen + 1);
    contentStorage.reserve(1 + historyLen + 1);
    std::vector<const char *> rolePtrs;
    std::vector<const char *> contentPtrs;
    std::vector<jstring> historyRoleStrs;
    std::vector<jstring> historyContentStrs;
    std::vector<const char *> historyRoleCs;
    std::vector<const char *> historyContentCs;

    // Keep history jstrings and their UTF chars alive during template apply
    for (jsize i = 0; i < historyLen; ++i) {
        jstring r = (jstring) env->GetObjectArrayElement(historyRoles, i);
        jstring c = (jstring) env->GetObjectArrayElement(historyContents, i);
        historyRoleStrs.push_back(r);
        historyContentStrs.push_back(c);
        const char * rc = r ? env->GetStringUTFChars(r, nullptr) : "";
        const char * cc = c ? env->GetStringUTFChars(c, nullptr) : "";
        historyRoleCs.push_back(rc);
        historyContentCs.push_back(cc);
    }

    std::vector<llama_chat_message> chat;
    chat.reserve(1 + historyLen + 1);
    // system
    chat.push_back({system_c, system_c ? system_c + strlen(system_c) : system_c});
    // Actually llama_chat_message expects role and content as const char*; we need to set correctly
    // Rebuild with proper role/content split
    chat.clear();
    chat.push_back({"system", system_c});
    for (jsize i = 0; i < historyLen; ++i) {
        chat.push_back({historyRoleCs[i], historyContentCs[i]});
    }
    chat.push_back({"user", prompt_c});

    const char * tmpl = llama_model_chat_template(aven->model, nullptr);
    std::string formatted;
    bool usedTemplate = false;
    if (tmpl) {
        // Estimate buffer: 2 * total chars + 512
        size_t totalLen = 0;
        for (auto & m : chat) {
            totalLen += strlen(m.role) + strlen(m.content) + 32;
        }
        std::vector<char> buf(totalLen * 2 + 512);
        int32_t res = llama_chat_apply_template(tmpl, chat.data(), chat.size(), true, buf.data(), (int32_t) buf.size());
        if (res < 0) {
            // Buffer too small - retry with exact size
            buf.resize(-res);
            res = llama_chat_apply_template(tmpl, chat.data(), chat.size(), true, buf.data(), (int32_t) buf.size());
        }
        if (res >= 0) {
            formatted.assign(buf.data(), res);
            usedTemplate = true;
        }
    }
    // Fallback to manual if no template or apply failed
    if (!usedTemplate) {
        // Manual fallback uses same logic as before but now with parse_special=true in tokenization
        // For simplicity, reuse Llama manual if model is Llama, else Gemma
        // Detect via template name or model type - fallback to Llama style
        formatted = "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n";
        formatted += system_c;
        formatted += "<|eot_id|>";
        for (jsize i = 0; i < historyLen; ++i) {
            const char * r = historyRoleCs[i];
            const char * c = historyContentCs[i];
            std::string role = r ? r : "user";
            if (role == "model") role = "assistant";
            formatted += "<|start_header_id|>" + role + "<|end_header_id|>\n\n";
            formatted += c ? c : "";
            formatted += "<|eot_id|>";
        }
        formatted += "<|start_header_id|>user<|end_header_id|>\n\n";
        formatted += prompt_c;
        formatted += "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n\n";
    }

    // Release history UTF chars and system/prompt after template (keep formatted)
    for (jsize i = 0; i < historyLen; ++i) {
        if (historyRoleStrs[i]) {
            env->ReleaseStringUTFChars(historyRoleStrs[i], historyRoleCs[i]);
            env->DeleteLocalRef(historyRoleStrs[i]);
        }
        if (historyContentStrs[i]) {
            env->ReleaseStringUTFChars(historyContentStrs[i], historyContentCs[i]);
            env->DeleteLocalRef(historyContentStrs[i]);
        }
    }
    env->ReleaseStringUTFChars(system, system_c);
    env->ReleaseStringUTFChars(prompt, prompt_c);

    // Now tokenize the formatted prompt with parse_special=true
    const llama_vocab * vocab = llama_model_get_vocab(aven->model);
    const llama_token eos = llama_vocab_eos(vocab);
    const llama_token eot = llama_vocab_eot(vocab);

    std::vector<llama_token> tokens(llama_vocab_n_tokens(vocab));
    const int32_t n_prompt = llama_tokenize(vocab, formatted.c_str(), (int32_t) formatted.size(), tokens.data(), tokens.size(), true, true);
    if (n_prompt < 0) {
        throw_java(env, "Failed to tokenize the prompt");
        return nullptr;
    }
    tokens.resize(n_prompt);

    const int32_t n_ctx = llama_n_ctx(aven->ctx);
    if (n_prompt >= n_ctx - 1) {
        throw_java(env, "The message is too long for the on-device context window");
        return nullptr;
    }

    g_stop.store(false);
    llama_memory_clear(llama_get_memory(aven->ctx), true);

    constexpr int32_t DECODE_CHUNK = 512;
    for (int32_t done = 0; done < n_prompt && !g_stop.load(); done += DECODE_CHUNK) {
        const int32_t take = std::min(DECODE_CHUNK, n_prompt - done);
        llama_batch chunk = llama_batch_get_one(tokens.data() + done, take);
        if (llama_decode(aven->ctx, chunk) != 0) {
            throw_java(env, "Failed to process the message");
            return nullptr;
        }
    }

    std::string output;
    for (int step = 0; step < MAX_TOKENS && n_prompt + step < n_ctx - 1; ++step) {
        if (g_stop.load()) break;
        const llama_token token = llama_sampler_sample(aven->sampler, aven->ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        llama_sampler_accept(aven->sampler, token);
        char piece_buf[256];
        const int n_piece = llama_token_to_piece(vocab, token, piece_buf, sizeof(piece_buf), 0, false);
        if (n_piece < 0) break;
        if (n_piece < (int)sizeof(piece_buf)) piece_buf[n_piece] = '\0';
        else piece_buf[sizeof(piece_buf) - 1] = '\0';
        output.append(piece_buf, n_piece);
        if (listener != nullptr && g_listener_on_token != nullptr) {
            jstring piece = env->NewStringUTF(piece_buf);
            if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
            env->CallVoidMethod(listener, g_listener_on_token, piece);
            env->DeleteLocalRef(piece);
            if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
        }
        llama_token one = token;
        llama_batch one_batch = llama_batch_get_one(&one, 1);
        if (llama_decode(aven->ctx, one_batch) != 0) break;
    }

    if (env->ExceptionCheck()) env->ExceptionClear();
    return env->NewStringUTF(output.c_str());
}