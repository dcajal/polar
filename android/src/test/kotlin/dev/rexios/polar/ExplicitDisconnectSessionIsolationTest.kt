package dev.rexios.polar

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExplicitDisconnectSessionIsolationTest {
    @Test
    fun explicitDisconnectIsRegisteredUntilItsCallback() {
        val isolation = ExplicitDisconnectSessionIsolation()

        isolation.request("A") {}

        assertTrue(isolation.isPending("A"))
    }

    @Test
    fun matchingCallbackConsumesTheRegistration() {
        val isolation = ExplicitDisconnectSessionIsolation()
        isolation.request("A") {}

        val wasExplicit = isolation.complete(
            "A",
            cleanupClosedSessions = {},
            publishDisconnected = {},
        )

        assertTrue(wasExplicit)
        assertFalse(isolation.isPending("A"))
    }

    @Test
    fun cleanupRunsBeforeTheDisconnectedEventIsPublished() {
        val isolation = ExplicitDisconnectSessionIsolation()
        val order = mutableListOf<String>()
        isolation.request("A") {}

        isolation.complete(
            "A",
            cleanupClosedSessions = { order.add("cleanup") },
            publishDisconnected = { order.add("publish") },
        )

        assertEquals(listOf("cleanup", "publish"), order)
    }

    @Test
    fun spontaneousDisconnectDoesNotCleanSdkSessions() {
        val isolation = ExplicitDisconnectSessionIsolation()
        var cleanupCalls = 0
        var publishCalls = 0

        val wasExplicit = isolation.complete(
            "A",
            cleanupClosedSessions = { cleanupCalls++ },
            publishDisconnected = { publishCalls++ },
        )

        assertFalse(wasExplicit)
        assertEquals(0, cleanupCalls)
        assertEquals(1, publishCalls)
    }

    @Test
    fun anotherDeviceCallbackDoesNotConsumeTheRegistration() {
        val isolation = ExplicitDisconnectSessionIsolation()
        isolation.request("A") {}

        val wasExplicit = isolation.complete(
            "B",
            cleanupClosedSessions = {},
            publishDisconnected = {},
        )

        assertFalse(wasExplicit)
        assertTrue(isolation.isPending("A"))
        assertFalse(isolation.isPending("B"))
    }

    @Test
    fun twoDevicesKeepIndependentRegistrations() {
        val isolation = ExplicitDisconnectSessionIsolation()
        isolation.request("A") {}
        isolation.request("B") {}

        assertTrue(
            isolation.complete("A", cleanupClosedSessions = {}, publishDisconnected = {}),
        )
        assertFalse(isolation.isPending("A"))
        assertTrue(isolation.isPending("B"))

        assertTrue(
            isolation.complete("B", cleanupClosedSessions = {}, publishDisconnected = {}),
        )
        assertEquals(0, isolation.totalPendingCount())
    }

    @Test
    fun synchronousDisconnectFailureRollsBackAndPropagates() {
        val isolation = ExplicitDisconnectSessionIsolation()
        val failure = IllegalStateException("disconnect failed")

        try {
            isolation.request("A") { throw failure }
            fail("Expected the synchronous disconnect failure")
        } catch (error: IllegalStateException) {
            assertSame(failure, error)
        }

        assertFalse(isolation.isPending("A"))
    }

    @Test
    fun streamsAreCancelledAfterDisconnectIsRequested() {
        val isolation = ExplicitDisconnectSessionIsolation()
        val order = mutableListOf<String>()

        isolation.request(
            identifier = "A",
            disconnect = { order.add("disconnect") },
            afterDisconnectRequested = { order.add("cancel-streams") },
        )

        assertEquals(listOf("disconnect", "cancel-streams"), order)
        assertTrue(isolation.isPending("A"))
    }

    @Test
    fun synchronousDisconnectFailureDoesNotCancelStreams() {
        val isolation = ExplicitDisconnectSessionIsolation()
        val failure = IllegalStateException("disconnect failed")
        var cancelCalls = 0

        try {
            isolation.request(
                identifier = "A",
                disconnect = { throw failure },
                afterDisconnectRequested = { cancelCalls++ },
            )
            fail("Expected the synchronous disconnect failure")
        } catch (error: IllegalStateException) {
            assertSame(failure, error)
        }

        assertEquals(0, cancelCalls)
        assertFalse(isolation.isPending("A"))
    }

    @Test
    fun newConnectionClearsAStaleRegistrationForThatDeviceOnly() {
        val isolation = ExplicitDisconnectSessionIsolation()
        isolation.request("A") {}
        isolation.request("B") {}

        assertTrue(isolation.clearForNewConnection("A"))

        assertFalse(isolation.isPending("A"))
        assertTrue(isolation.isPending("B"))
    }

    @Test
    fun shutdownClearsAllRegistrations() {
        val isolation = ExplicitDisconnectSessionIsolation()
        isolation.request("A") {}
        isolation.request("B") {}

        isolation.clearAll()

        assertEquals(0, isolation.totalPendingCount())
    }

    @Test
    fun concurrentRequestsAndCallbacksAreSafe() {
        val isolation = ExplicitDisconnectSessionIsolation()
        val workerCount = 8
        val iterationsPerWorker = 250
        val start = CountDownLatch(1)
        val cleanupCalls = AtomicInteger()
        val publishCalls = AtomicInteger()
        val failures = ConcurrentLinkedQueue<Throwable>()
        val workers = (0 until workerCount).map { worker ->
            thread(start = true) {
                try {
                    start.await()
                    repeat(iterationsPerWorker) {
                        val identifier = "device-$worker"
                        isolation.request(identifier) {}
                        isolation.complete(
                            identifier,
                            cleanupClosedSessions = { cleanupCalls.incrementAndGet() },
                            publishDisconnected = { publishCalls.incrementAndGet() },
                        )
                    }
                } catch (error: Throwable) {
                    failures.add(error)
                }
            }
        }

        start.countDown()
        workers.forEach { it.join(5_000) }

        assertTrue(workers.none { it.isAlive })
        assertTrue(failures.isEmpty())
        assertEquals(0, isolation.totalPendingCount())
        assertEquals(workerCount * iterationsPerWorker, cleanupCalls.get())
        assertEquals(workerCount * iterationsPerWorker, publishCalls.get())
    }
}
