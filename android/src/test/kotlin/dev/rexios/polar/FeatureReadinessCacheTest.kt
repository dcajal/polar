package dev.rexios.polar

import com.polar.sdk.api.PolarBleApi.PolarBleSdkFeature
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureReadinessCacheTest {
    private val feature = PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING

    @Test
    fun nativeReadyIsReady() {
        val cache = FeatureReadinessCache()

        assertTrue(cache.isReady("A", feature, nativeReady = true))
    }

    @Test
    fun observedCallbackOverridesFalseNativeState() {
        val cache = FeatureReadinessCache()
        cache.record("A", feature)

        assertTrue(cache.isReady("A", feature, nativeReady = false))
    }

    @Test
    fun devicesHaveIndependentState() {
        val cache = FeatureReadinessCache()
        cache.record("A", feature)

        assertFalse(cache.isReady("B", feature, nativeReady = false))
    }

    @Test
    fun clearingDeviceStartsANewSession() {
        val cache = FeatureReadinessCache()
        cache.record("A", feature)

        cache.clear("A")

        assertFalse(cache.isReady("A", feature, nativeReady = false))
    }

    @Test
    fun clearingAllRemovesShutdownState() {
        val cache = FeatureReadinessCache()
        cache.record("A", feature)
        cache.record("B", feature)

        cache.clearAll()

        assertFalse(cache.isReady("A", feature, nativeReady = false))
        assertFalse(cache.isReady("B", feature, nativeReady = false))
    }
}
