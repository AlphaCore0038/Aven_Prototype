package com.aven.ai

import android.app.Application
import com.aven.ai.di.AppContainer

/**
 * Aven application entry point. Owns the manual dependency-injection container
 * so that repositories, the model manager, and providers can share a single
 * lifetime across the whole process.
 */
class AvenApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.keyManager.seedDefaultKeysIfNeeded()
    }
}