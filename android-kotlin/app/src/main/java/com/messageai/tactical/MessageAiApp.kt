/**
 * MessageAI – Application class for Hilt initialization.
 *
 * Serves as the DI root for the Android application. Keep lightweight.
 */
package com.messageai.tactical

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MessageAiApp : Application()
