import SwiftUI

@main
struct RayClipMacApp: App {
    @StateObject private var syncManager = SyncManager()
    @Environment(\.openWindow) var openWindow

    var body: some Scene {
        MenuBarExtra("RayClip", systemImage: syncManager.isConfigured ? "paperclip" : "paperclip.badge.ellipsis") {
            Button("Settings...") {
                NSApp.activate(ignoringOtherApps: true)
                openWindow(id: "settings")
            }
            Divider()
            Button("Quit") {
                NSApplication.shared.terminate(nil)
            }
        }
        
        WindowGroup("RayClip Settings", id: "settings") {
            SettingsView(syncManager: syncManager)
        }
        .windowResizability(.contentSize)
    }
}