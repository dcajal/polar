import PolarBleSdk
import RxSwift
import XCTest

@testable import polar

final class PmdNotificationWaiterTests: XCTestCase {
  private var disposeBag: DisposeBag!

  override func setUp() {
    super.setUp()
    disposeBag = DisposeBag()
  }

  func testImmediateSuccess() {
    let waiter = makeWaiter()
    var receivedValue: Int?

    waiter.run(identifier: "A") {
      .just(42)
    }.subscribe(onSuccess: {
      receivedValue = $0
    }).disposed(by: disposeBag)

    XCTAssertEqual(receivedValue, 42)
  }

  func testNotificationNotEnabledIsRetriedUntilSuccess() {
    let waiter = makeWaiter()
    let completed = expectation(description: "query succeeds")
    var attempts = 0

    waiter.run(identifier: "A") { () -> Single<Int> in
      attempts += 1
      if attempts == 1 {
        return .error(PolarErrors.notificationNotEnabled)
      }
      return .just(42)
    }.subscribe(onSuccess: { value in
      XCTAssertEqual(value, 42)
      completed.fulfill()
    }).disposed(by: disposeBag)

    wait(for: [completed], timeout: 1)
    XCTAssertEqual(attempts, 2)
  }

  func testNonTransientErrorIsPropagatedImmediately() {
    let waiter = makeWaiter()
    var receivedError: Error?
    var attempts = 0

    waiter.run(identifier: "A") { () -> Single<Int> in
      attempts += 1
      return .error(PolarErrors.serviceNotFound)
    }.subscribe(onFailure: {
      receivedError = $0
    }).disposed(by: disposeBag)

    XCTAssertEqual(attempts, 1)
    guard let polarError = receivedError as? PolarErrors,
      case .serviceNotFound = polarError
    else {
      return XCTFail("Expected PolarErrors.serviceNotFound")
    }
  }

  func testTimeout() {
    let waiter = makeWaiter(timeout: .milliseconds(50))
    let completed = expectation(description: "query times out")

    waiter.run(identifier: "A") { () -> Single<Int> in
      .error(PolarErrors.notificationNotEnabled)
    }.subscribe(onFailure: { error in
      XCTAssertEqual(error as? PmdNotificationWaitError, .timeout)
      completed.fulfill()
    }).disposed(by: disposeBag)

    wait(for: [completed], timeout: 1)
  }

  func testDisconnectionCancelsWait() {
    let waiter = makeWaiter(retryDelay: .seconds(1))
    var receivedError: Error?

    waiter.run(identifier: "A") { () -> Single<Int> in
      .error(PolarErrors.notificationNotEnabled)
    }.subscribe(onFailure: {
      receivedError = $0
    }).disposed(by: disposeBag)

    waiter.deviceDisconnected("A")

    XCTAssertEqual(receivedError as? PmdNotificationWaitError, .disconnected)
  }

  private func makeWaiter(
    retryDelay: RxTimeInterval = .milliseconds(10),
    timeout: RxTimeInterval = .milliseconds(100)
  ) -> PmdNotificationWaiter {
    PmdNotificationWaiter(
      retryDelay: retryDelay,
      timeout: timeout,
      scheduler: MainScheduler.instance
    )
  }
}
