// JNI bridge between Aven (Kotlin) and llama.cpp for on-device generation.
// Only greedy sampling is used; stop tokens end generation.

#include <jni.h>
#include <atomic>
#include <cstring>
#include <string>
#include <vector>

#include "llama.h"

namespace {

constexpr int MAX_TOKENS = 1024;

std::atomic<bool> g_stop(false);

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

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    llama_model * model = llama_model_load_from_file(path_c, model_params);
    env->ReleaseStringUTFChars(path, path_c);
    if (model == nullptr) {
        throw_java(env, "Failed to load the local model");
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_batch = 512;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;

    llama_context * ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        llama_model_free(model);
        throw_java(env, "Failed to create the inference context");
        return 0;
    }

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

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
    const int32_t n_prompt = llama_tokenize(vocab, prompt_c, -1, tokens.data(), tokens.size(), true, false);
    env->ReleaseStringUTFChars(prompt, prompt_c);
    if (n_prompt < 0) {
        throw_java(env, "Failed to tokenize the prompt");
        return nullptr;
    }
    tokens.resize(n_prompt);

    g_stop.store(false);

    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    batch.logits[batch.n_tokens - 1] = 1;
    if (llama_decode(aven->ctx, batch) != 0) {
        throw_java(env, "Failed to process the prompt");
        return nullptr;
    }

    std::string output;
    for (int step = 0; step < MAX_TOKENS; ++step) {
        if (g_stop.load()) {
            break;
        }
        const llama_token token = llama_sampler_sample(aven->sampler, aven->ctx, -1);
        if (token == eos || token == eot) {
            break;
        }
        llama_sampler_accept(aven->sampler, token);

        char piece_buf[256];
        const int n_piece = llama_token_to_piece(vocab, token, piece_buf, sizeof(piece_buf), 0, false);
        if (n_piece < 0) {
            break;
        }
        output.append(piece_buf, n_piece);

        if (listener != nullptr && g_listener_on_token != nullptr) {
            jstring piece = env->NewStringUTF(piece_buf);
            env->CallVoidMethod(listener, g_listener_on_token, piece);
            env->DeleteLocalRef(piece);
        }

        llama_token one = token;
        llama_batch one_batch = llama_batch_get_one(&one, 1);
        one_batch.logits[0] = 1;
        if (llama_decode(aven->ctx, one_batch) != 0) {
            break;
        }
    }

    return env->NewStringUTF(output.c_str());
}