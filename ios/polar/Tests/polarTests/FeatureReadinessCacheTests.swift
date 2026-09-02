import PolarBleSdk
import XCTest

@testable import polar

final class FeatureReadinessCacheTests: XCTestCase {
  private var feature: PolarBleSdkFeature {
    PolarBleSdkFeature.allCases[0]
  }

  func testNativeReadyIsReady() {
    let cache = FeatureReadinessCache()

    XCTAssertTrue(cache.isReady("A", feature: feature, nativeReady: true))
  }

  func testObservedCallbackOverridesFalseNativeState() {
    let cache = FeatureReadinessCache()
    cache.record("A", feature: feature)

    XCTAssertTrue(cache.isReady("A", feature: feature, nativeReady: false))
  }

  func testDevicesHaveIndependentState() {
    let cache = FeatureReadinessCache()
    cache.record("A", feature: feature)

    XCTAssertFalse(cache.isReady("B", feature: feature, nativeReady: false))
  }

  func testClearingDeviceStartsANewSession() {
    let cache = FeatureReadinessCache()
    cache.record("A", feature: feature)

    cache.clear("A")

    XCTAssertFalse(cache.isReady("A", feature: feature, nativeReady: false))
  }

  func testClearingAllRemovesShutdownState() {
    let cache = FeatureReadinessCache()
    cache.record("A", feature: feature)
    cache.record("B", feature: feature)

    cache.clearAll()

    XCTAssertFalse(cache.isReady("A", feature: feature, nativeReady: false))
    XCTAssertFalse(cache.isReady("B", feature: feature, nativeReady: false))
  }
}
