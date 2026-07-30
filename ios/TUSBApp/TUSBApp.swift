import SwiftUI
import TUSBCore

@main
struct TUSBApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .preferredColorScheme(.dark)
                .tint(model.theme.accent)
        }
    }
}

