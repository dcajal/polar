package dev.rexios.polar

/**
 * Tracks explicit disconnect requests until their matching native callback.
 *
 * Polar's Android SDK may finish PMD teardown after a Dart subscription is
 * disposed. Cleaning closed SDK sessions at the explicit disconnect boundary
 * prevents that teardown from leaking into a later connection that reuses the
 * same SDK instance. Spontaneous disconnects are deliberately not tracked so
 * native automatic reconnection remains unaffected.
 */
internal class ExplicitDisconnectSessionIsolation {
    private val lock = Any()
    private val pendingDevices = mutableSetOf<String>()

    fun request(
        identifier: String,
        disconnect: () -> Unit,
    ) {
        val markerAdded = synchronized(lock) {
            pendingDevices.add(identifier)
        }

        try {
            disconnect()
        } catch (error: Throwable) {
            if (markerAdded) {
                consume(identifier)
            }
            throw error
        }
    }

    fun complete(
        identifier: String,
        cleanupClosedSessions: () -> Unit,
        publishDisconnected: () -> Unit,
    ): Boolean {
        val wasExplicit = consume(identifier)
        if (wasExplicit) {
            cleanupClosedSessions()
        }
        publishDisconnected()
        return wasExplicit
    }

    fun clearForNewConnection(identifier: String): Boolean = synchronized(lock) {
        pendingDevices.remove(identifier)
    }

    fun clearAll() {
        synchronized(lock) {
            pendingDevices.clear()
        }
    }

    internal fun isPending(identifier: String): Boolean = synchronized(lock) {
        pendingDevices.contains(identifier)
    }

    internal fun totalPendingCount(): Int = synchronized(lock) {
        pendingDevices.size
    }

    private fun consume(identifier: String): Boolean = synchronized(lock) {
        pendingDevices.remove(identifier)
    }
}
