import Foundation
import PolarBleSdk
import RxSwift

enum PmdNotificationWaitError: Error, Equatable, LocalizedError {
  case timeout
  case disconnected

  var errorDescription: String? {
    switch self {
    case .timeout:
      return "Timed out waiting for PMD notifications to be enabled"
    case .disconnected:
      return "Device disconnected while waiting for PMD notifications to be enabled"
    }
  }
}

final class PmdNotificationWaiter {
  private let retryDelay: RxTimeInterval
  private let timeout: RxTimeInterval
  private let scheduler: SchedulerType
  private let disconnectedDevices = PublishSubject<String>()

  init(
    retryDelay: RxTimeInterval = .milliseconds(100),
    timeout: RxTimeInterval = .seconds(5),
    scheduler: SchedulerType = MainScheduler.instance
  ) {
    self.retryDelay = retryDelay
    self.timeout = timeout
    self.scheduler = scheduler
  }

  func run<Element>(
    identifier: String,
    operation: @escaping () -> Single<Element>
  ) -> Single<Element> {
    let retryDelay = self.retryDelay
    let scheduler = self.scheduler

    let retryingOperation = Observable.deferred {
      operation().asObservable()
    }.retry { errors in
      errors.flatMap { error -> Observable<Void> in
        guard let polarError = error as? PolarErrors,
          case .notificationNotEnabled = polarError
        else {
          return .error(error)
        }

        return .just(())
          .delay(retryDelay, scheduler: scheduler)
      }
    }

    let disconnection: Observable<Element> = disconnectedDevices
      .filter { $0 == identifier }
      .take(1)
      .flatMap { _ in
        Observable<Element>.error(PmdNotificationWaitError.disconnected)
      }

    return retryingOperation
      .amb(disconnection)
      .timeout(
        timeout,
        other: Observable<Element>.error(PmdNotificationWaitError.timeout),
        scheduler: scheduler
      )
      .take(1)
      .asSingle()
  }

  func deviceDisconnected(_ identifier: String) {
    disconnectedDevices.onNext(identifier)
  }
}
