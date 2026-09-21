import UIKit

/// The UIKit-level security decisions of the iOS host.
///
/// Both of the things it does were declared as *owed* while no host existed (F1 and F3 tails).
/// Neither is reachable from Compose, which is exactly why they are here and not in `commonMain`.
final class AppDelegate: NSObject, UIApplicationDelegate {

    /// The privacy cover, in its own window (F1, mirror of ADR-0028 of LumeMed).
    ///
    /// iOS snapshots the screen when the app leaves the foreground, and that snapshot is what the
    /// app switcher shows and what is written to disk — in this app's own container, under
    /// `Library/SplashBoard/Snapshots`. There is no `FLAG_SECURE` on iOS — Apple exposes no API to
    /// refuse the snapshot — so the only defence is to make sure the snapshot has nothing on it.
    ///
    /// Its own `UIWindow` rather than a view inside the app's window on purpose: a view can be
    /// covered, reordered or removed by whatever is presented on top (an alert, a share sheet, a
    /// system prompt). A separate window at a higher level is above all of that, including anything
    /// Compose presents. That is its whole job — the Compose overlay in `PrivacyScreenScaffold`
    /// covers the Compose content, and this covers everything above it.
    private var privacyWindow: UIWindow?

    private var lifecycleObservers: [NSObjectProtocol] = []

    /// **Scene notifications, NOT the app delegate's lifecycle methods — and this is measured.**
    ///
    /// This app is a SwiftUI `App` with a `WindowGroup`, which means it adopts the UIScene
    /// lifecycle, and UIKit then does **not call** four `UIApplicationDelegate` methods:
    /// `applicationWillResignActive`, `applicationDidBecomeActive`,
    /// `applicationDidEnterBackground` and `applicationWillEnterForeground`.
    /// `@UIApplicationDelegateAdaptor` does not bring them back.
    ///
    /// The cover used to be armed in `applicationWillResignActive`. It therefore **never armed
    /// once**, from the day this host was written, and nothing said so: no error, no warning, no
    /// crash — code that reads as a control and is dead. It was found by putting an `NSLog` in the
    /// method and another in `didFinishLaunching` as a positive control: the control fired, the
    /// lifecycle method did not, and the scene notification did (ADR-0031).
    ///
    /// The trap in the middle: `UIApplication.willResignActiveNotification` is still **posted**.
    /// Only the delegate *method* stops being called. "It finds out anyway" is half true, through
    /// the channel nobody was listening on.
    ///
    /// `willDeactivateNotification` is the scene-lifecycle equivalent of `willResignActive`: it
    /// fires *before* the system takes its snapshot, which `didEnterBackground` is already too late
    /// for. The scene comes from the notification rather than from
    /// `UIApplication.shared.connectedScenes.first`, which was arbitrary ordering dressed as a
    /// lookup.
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let center = NotificationCenter.default
        lifecycleObservers = [
            center.addObserver(
                forName: UIScene.willDeactivateNotification, object: nil, queue: .main
            ) { [weak self] notification in
                self?.showPrivacyCover(on: notification.object as? UIWindowScene)
            },
            center.addObserver(
                forName: UIScene.didActivateNotification, object: nil, queue: .main
            ) { [weak self] _ in
                self?.hidePrivacyCover()
            },
        ]
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
    ///
    /// Unaffected by the scene lifecycle: this is not one of the four methods scenes replace, so it
    /// is still called. That is a real distinction and not a hopeful one — the replaced set is
    /// exactly those four.
    func application(
        _ application: UIApplication,
        shouldAllowExtensionPointIdentifier extensionPointIdentifier: UIApplication.ExtensionPointIdentifier
    ) -> Bool {
        return extensionPointIdentifier != .keyboard
    }

    private func showPrivacyCover(on scene: UIWindowScene?) {
        guard privacyWindow == nil else { return }
        // Fall back to the connected scene only if the notification carried none. A cover that
        // silently does not appear is the failure this whole file is about, so the fallback exists
        // rather than a `return`.
        guard let scene = scene ?? UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }

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
