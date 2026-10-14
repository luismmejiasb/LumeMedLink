import SwiftUI
import UIKit
// The Kotlin framework. The Swift module is named LumeMedLinkHost precisely so this import is
// possible: an app module named LumeMedLink could not import a framework of the same name.
import LumeMedLink

/// The iOS half of the shell. Everything it *shows* comes from `commonMain` through
/// `MainViewController()`; everything in this file is the part Compose cannot reach — the window,
/// the app lifecycle, and the UIKit-level decisions the constitution assigns to the host.
///
/// It is deliberately thin. A screen belongs in `commonMain`; only capabilities that do not exist
/// there belong here, and each one below names the rule it implements.
@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea(.all)
        }
    }
}

/// Bridges the Compose UI into SwiftUI. Nothing else: this is composition, not behaviour.
/// Firebase is configured here, once, or not at all when the build carried no configuration (ADR-0052 §8).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(firebase: FirebaseHost.configured(), environment: Self.environment)
    }

    /// Where the data comes from, by build configuration (ADR-0062). A Debug build talks to the platform on this
    /// Mac, which the simulator reaches as `localhost`; a Release build carries no local address at all, and
    /// `check-network-posture.sh` fails one that does.
    private static var environment: AppEnvironment {
        #if DEBUG
        return AppEnvironment.companion.localBackend(baseUrl: "http://localhost:3000")
        #else
        return AppEnvironment.companion.Script
        #endif
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
