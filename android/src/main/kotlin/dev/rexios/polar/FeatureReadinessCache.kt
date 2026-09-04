package dev.rexios.polar

import com.polar.sdk.api.PolarBleApi.PolarBleSdkFeature

/** Thread-safe record of definitive feature-ready callbacks seen by the plugin. */
internal class FeatureReadinessCache {
    private val lock = Any()
    private val featuresByDevice = mutableMapOf<String, MutableSet<PolarBleSdkFeature>>()

    fun record(
        identifier: String,
        feature: PolarBleSdkFeature,
    ): Unit = synchronized(lock) {
        featuresByDevice.getOrPut(identifier) { mutableSetOf() }.add(feature)
        Unit
    }

    fun recordReadiness(
        identifier: String,
        ready: List<PolarBleSdkFeature>,
        unavailable: List<PolarBleSdkFeature>,
    ): Unit = synchronized(lock) {
        val features = featuresByDevice.getOrPut(identifier) { mutableSetOf() }
        features.removeAll(unavailable.toSet())
        features.addAll(ready)
        if (features.isEmpty()) featuresByDevice.remove(identifier)
        Unit
    }

    fun isReady(
        identifier: String,
        feature: PolarBleSdkFeature,
        nativeReady: Boolean,
    ): Boolean = nativeReady || synchronized(lock) {
        featuresByDevice[identifier]?.contains(feature) == true
    }

    fun clear(identifier: String) = synchronized(lock) {
        featuresByDevice.remove(identifier)
        Unit
    }

    fun clearAll() = synchronized(lock) {
        featuresByDevice.clear()
    }
}
