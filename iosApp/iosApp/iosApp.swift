import SwiftUI
import UIKit
import ComposeApp

struct ComposeViewController: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct EvidriloApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeViewController()
                .onOpenURL { url in
                    AccountAuthPlatformKt.submitAccountAuthRedirect(url: url.absoluteString)
                }
        }
    }
}
