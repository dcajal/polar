package dev.rexios.polar

import io.reactivex.rxjava3.disposables.Disposable

/** Owns one native streaming subscription and invalidates callbacks from prior owners. */
internal class StreamingSubscriptionSlot {
    internal data class Token(
        val identifier: String,
        val generation: Long,
    )

    private val lock = Any()
    private var generation = 0L
    private var identifier: String? = null
    private var subscription: Disposable? = null

    fun begin(identifier: String): Token {
        val (token, previous) = synchronized(lock) {
            generation++
            this.identifier = identifier
            val previous = subscription
            subscription = null
            Token(identifier, generation) to previous
        }
        previous?.dispose()
        return token
    }

    fun install(
        token: Token,
        subscription: Disposable,
    ) {
        val accepted = synchronized(lock) {
            if (isCurrentLocked(token)) {
                this.subscription = subscription
                true
            } else {
                false
            }
        }
        if (!accepted) subscription.dispose()
    }

    fun isCurrent(token: Token): Boolean = synchronized(lock) {
        isCurrentLocked(token)
    }

    fun clearIfCurrent(token: Token) {
        synchronized(lock) {
            if (isCurrentLocked(token)) {
                generation++
                identifier = null
                subscription = null
            }
        }
    }

    fun cancelIfOwnedBy(identifier: String): Boolean {
        val ownedSubscription = synchronized(lock) {
            if (this.identifier != identifier) return false
            generation++
            this.identifier = null
            val ownedSubscription = subscription
            subscription = null
            ownedSubscription
        }
        ownedSubscription?.dispose()
        return true
    }

    fun cancel() {
        val activeSubscription = synchronized(lock) {
            generation++
            identifier = null
            val activeSubscription = subscription
            subscription = null
            activeSubscription
        }
        activeSubscription?.dispose()
    }

    internal fun owner(): String? = synchronized(lock) { identifier }

    internal fun hasSubscription(): Boolean = synchronized(lock) { subscription != null }

    private fun isCurrentLocked(token: Token): Boolean =
        identifier == token.identifier && generation == token.generation
}
