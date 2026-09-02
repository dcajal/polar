import Foundation
import PolarBleSdk

/// Thread-safe record of definitive feature-ready callbacks seen by the plugin.
final class FeatureReadinessCache {
  private let lock = NSLock()
  private var featuresByDevice: [String: Set<PolarBleSdkFeature>] = [:]

  func record(_ identifier: String, feature: PolarBleSdkFeature) {
    lock.lock()
    defer { lock.unlock() }
    featuresByDevice[identifier, default: []].insert(feature)
  }

  func isReady(
    _ identifier: String,
    feature: PolarBleSdkFeature,
    nativeReady: Bool
  ) -> Bool {
    if nativeReady { return true }
    lock.lock()
    defer { lock.unlock() }
    return featuresByDevice[identifier]?.contains(feature) == true
  }

  func clear(_ identifier: String) {
    lock.lock()
    defer { lock.unlock() }
    featuresByDevice.removeValue(forKey: identifier)
  }

  func clearAll() {
    lock.lock()
    defer { lock.unlock() }
    featuresByDevice.removeAll()
  }
}
