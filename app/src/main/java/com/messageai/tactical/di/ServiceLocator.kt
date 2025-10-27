package com.messageai.tactical.di

import javax.inject.Singleton

/**
 * Simple static access for cross-VM communication where Compose scopes make DI awkward.
 * Use sparingly. Here we expose the AIBuddyRouter to allow ChatViewModel to set last-open chat.
 */
object ServiceLocator


