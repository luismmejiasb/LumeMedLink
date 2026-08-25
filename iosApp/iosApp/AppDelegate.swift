import UIKit

/// The UIKit-level security decisions of the iOS host.
///
/// Both of the things it does were declared as *owed* while no host existed (F1 and F3 tails).
/// Neither is reachable from Compose, which is exactly why they are here and not in `commonMain`.
final class AppDelegate: NSObject, UIApplicationDelegate {

    /// The privacy cover, in its own window (F1, mirror of ADR-0028 of LumeMed).
    ///
    /// iOS snapshots the screen when the app leaves the foreground, and that snapshot is what the
    /// app switcher shows and what is written to disk. There is no `FLAG_SECURE` on iOS — Apple
    /// exposes no API to refuse the snapshot — so the only defence is to make sure the snapshot has
    /// nothing on it. The moment to act is `willResignActive`, which fires *before* the snapshot is
    /// taken; `didEnterBackground` is already too late.
    ///
    /// Its own `UIWindow` rather than a view inside the app's window on purpose: a view can be
    /// covered, reordered or removed by whatever is presented on top (an alert, a share sheet, a
    /// system prompt). A separate window at a higher level is above all of that, including anything
    /// Compose presents.
    private var privacyWindow: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        return true
    }

    /// Refuses third-party keyboards app-wide (F3, §8.10).
    ///
    /// This is the asymmetry the constitution declares in this app's favour: a custom keyboard is a
    /// process that sees every keystroke and may hold network access, and iOS lets an app refuse
    /// them for its own text fields. **Android has no equivalent and the constitution says so
    /// rather than pretending parity.**
    ///
    /// It costs the user their preferred keyboard inside this app and nowhere else. For an app
    /// whose fields carry a RUT, a phone number and a credential, that trade is the easy direction.
    func application(
        _ application: UIApplication,
        shouldAllowExtensionPointIdentifier extensionPointIdentifier: UIApplication.ExtensionPointIdentifier
    ) -> Bool {
        return extensionPointIdentifier != .keyboard
    }

    func applicationWillResignActive(_ application: UIApplication) {
        showPrivacyCover()
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        hidePrivacyCover()
    }

    private func showPrivacyCover() {
        guard privacyWindow == nil,
              let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }

        let window = UIWindow(windowScene: scene)
        // Above every window this app or the system puts up, alerts included.
        window.windowLevel = .alert + 1
        let controller = UIViewController()
        // An opaque system background, not an image or a logo: whatever is drawn here is what a
        // person holding the phone sees in the switcher, and a brand mark is still a statement that
        // this doctor uses this app. Plain is the private choice.
        controller.view.backgroundColor = .systemBackground
        window.rootViewController = controller
        window.isHidden = false
        privacyWindow = window
    }

    private func hidePrivacyCover() {
        privacyWindow?.isHidden = true
        privacyWindow = nil
    }
}
