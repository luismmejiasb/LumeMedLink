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
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.MainViewController() }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
