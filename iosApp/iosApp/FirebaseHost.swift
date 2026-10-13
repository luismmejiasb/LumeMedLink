import FirebaseAnalytics
import FirebaseCore
import FirebaseCrashlytics
import FirebaseRemoteConfig
import Foundation
import LumeMedLink

/// Firebase for the Kotlin app, and the only Swift that touches it (ADR-0052).
///
/// Kotlin cannot import a SwiftPM package, so this implements the Kotlin `FirebaseBridge` and is handed to
/// `MainViewController`. The catalog is enforced on the Kotlin side before a name gets here; this file only carries
/// it, and `check-firebase-posture.sh` asserts that nothing else in the host calls Firebase.
final class FirebaseHost: NSObject, FirebaseBridge {
    /// Firebase, configured — or nil when the build carried no `GoogleService-Info.plist` (ADR-0052 §8). The file stays
    /// out of git; the "Copy Firebase configuration" phase puts it in the bundle only when it exists.
    static func configured() -> FirebaseHost? {
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else { return nil }
        if FirebaseApp.app() == nil { FirebaseApp.configure() }
        return FirebaseHost()
    }

    private let remoteConfig = RemoteConfig.remoteConfig()

    /// Both off from the factory by the Info.plist; on only while the person's consent says so (§2). Turning them off
    /// deletes what the device still holds.
    func setCollectionEnabled(enabled: Bool) {
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(enabled)
        Analytics.setAnalyticsCollectionEnabled(enabled)
        if !enabled {
            Crashlytics.crashlytics().deleteUnsentReports()
            Analytics.resetAnalyticsData()
        }
    }

    /// A catalog name, and never a parameter (§3).
    func logEvent(name: String) {
        Analytics.logEvent(name, parameters: nil)
    }

    /// The remote value once fetched, the code's default until then (§5).
    func flag(key: String, defaultValue: Bool) -> Bool {
        let value = remoteConfig.configValue(forKey: key)
        return value.source == .static ? defaultValue : value.boolValue
    }

    func refreshFlags() {
        remoteConfig.fetchAndActivate { _, _ in }
    }

    /// An unhandled Kotlin exception: its type and frames, and an empty reason — the message never travels (§4).
    func recordCrash(typeName: String, frames: [String]) {
        let model = ExceptionModel(name: typeName, reason: "")
        model.stackTrace = frames.map { StackFrame(symbol: $0, file: "", line: 0) }
        Crashlytics.crashlytics().record(exceptionModel: model)
    }
}
