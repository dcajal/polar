package dev.rexios.polar

import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingSubscriptionSlotTest {
    @Test
    fun activeSubscriptionIsAssociatedWithItsDevice() {
        val slot = StreamingSubscriptionSlot()
        val token = slot.begin("A")

        slot.install(token, Disposable.empty())

        assertEquals("A", slot.owner())
        assertTrue(slot.hasSubscription())
        assertTrue(slot.isCurrent(token))
    }

    @Test
    fun matchingSelectiveCancellationDisposesAndClearsOwnership() {
        val slot = StreamingSubscriptionSlot()
        val disposeCalls = AtomicInteger()
        val token = slot.begin("A")
        slot.install(token, Disposable.fromAction { disposeCalls.incrementAndGet() })

        assertTrue(slot.cancelIfOwnedBy("A"))

        assertEquals(1, disposeCalls.get())
        assertNull(slot.owner())
        assertFalse(slot.hasSubscription())
        assertFalse(slot.isCurrent(token))
    }

    @Test
    fun anotherDeviceCannotCancelTheActiveSubscription() {
        val slot = StreamingSubscriptionSlot()
        val disposeCalls = AtomicInteger()
        val token = slot.begin("A")
        slot.install(token, Disposable.fromAction { disposeCalls.incrementAndGet() })

        assertFalse(slot.cancelIfOwnedBy("B"))

        assertEquals(0, disposeCalls.get())
        assertEquals("A", slot.owner())
        assertTrue(slot.hasSubscription())
    }

    @Test
    fun repeatedCancellationAndEmptySlotAreSafe() {
        val slot = StreamingSubscriptionSlot()
        val disposeCalls = AtomicInteger()

        assertFalse(slot.cancelIfOwnedBy("A"))
        val token = slot.begin("A")
        slot.install(token, Disposable.fromAction { disposeCalls.incrementAndGet() })
        assertTrue(slot.cancelIfOwnedBy("A"))
        assertFalse(slot.cancelIfOwnedBy("A"))

        assertEquals(1, disposeCalls.get())
    }

    @Test
    fun newSubscriptionCanBeInstalledAfterSelectiveCancellation() {
        val slot = StreamingSubscriptionSlot()
        val oldToken = slot.begin("A")
        slot.install(oldToken, Disposable.empty())
        slot.cancelIfOwnedBy("A")

        val newToken = slot.begin("A")
        slot.install(newToken, Disposable.empty())

        assertTrue(slot.isCurrent(newToken))
        assertTrue(slot.hasSubscription())
    }

    @Test
    fun callbackFromPreviousGenerationIsRejected() {
        val slot = StreamingSubscriptionSlot()
        val previousToken = slot.begin("A")
        slot.install(previousToken, Disposable.empty())
        var deliveries = 0
        val queuedCallback = {
            if (slot.isCurrent(previousToken)) deliveries++
        }

        val currentToken = slot.begin("A")
        slot.install(currentToken, Disposable.empty())
        queuedCallback()

        assertEquals(0, deliveries)
        assertFalse(slot.isCurrent(previousToken))
        assertTrue(slot.isCurrent(currentToken))
    }

    @Test
    fun subscriptionInstalledAfterCancellationIsDisposedAsStale() {
        val slot = StreamingSubscriptionSlot()
        val disposeCalls = AtomicInteger()
        val token = slot.begin("A")

        slot.cancelIfOwnedBy("A")
        slot.install(token, Disposable.fromAction { disposeCalls.incrementAndGet() })

        assertEquals(1, disposeCalls.get())
        assertNull(slot.owner())
        assertFalse(slot.hasSubscription())
    }

    @Test
    fun cancellationIsLimitedToMatchingDeviceAcrossChannels() {
        val channelA = StreamingSubscriptionSlot()
        val channelB = StreamingSubscriptionSlot()
        val disposeA = AtomicInteger()
        val disposeB = AtomicInteger()
        val tokenA = channelA.begin("A")
        val tokenB = channelB.begin("B")
        channelA.install(tokenA, Disposable.fromAction { disposeA.incrementAndGet() })
        channelB.install(tokenB, Disposable.fromAction { disposeB.incrementAndGet() })

        channelA.cancelIfOwnedBy("A")
        channelB.cancelIfOwnedBy("A")

        assertEquals(1, disposeA.get())
        assertEquals(0, disposeB.get())
        assertTrue(channelB.isCurrent(tokenB))
    }
}
