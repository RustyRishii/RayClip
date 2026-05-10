import SwiftUI
import ServiceManagement

struct SettingsView: View {
    @ObservedObject var syncManager: SyncManager
    @AppStorage("apiUrl") var apiUrl = ""
    @AppStorage("token") var token = ""
    @AppStorage("deviceId") var deviceId = "macbook-" + UUID().uuidString.prefix(4).lowercased()
    
    @State private var launchAtLogin = false
    @State private var showSaved = false
    @State private var showToken = false

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 16) {
                Image(systemName: "paperclip.circle.fill")
                    .resizable()
                    .frame(width: 48, height: 48)
                    .foregroundColor(.blue)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text("RayClip Sync")
                        .font(.title2)
                        .fontWeight(.semibold)
                    Text("Cross-device clipboard synchronization")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                Spacer()
            }
            .padding(20)
            .background(Color(NSColor.controlBackgroundColor))
            
            Divider()
            
            // Content
            VStack(alignment: .leading, spacing: 20) {
                
                VStack(alignment: .leading, spacing: 6) {
                    Text("Server URL")
                        .font(.headline)
                    TextField("https://rayclip.onrender.com", text: $apiUrl)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .controlSize(.large)
                    Text("The address of your RayClip server.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                VStack(alignment: .leading, spacing: 6) {
                    Text("Authentication Token")
                        .font(.headline)
                    ZStack(alignment: .trailing) {
                        if showToken {
                            TextField("Enter your secret token", text: $token)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                                .controlSize(.large)
                        } else {
                            SecureField("Enter your secret token", text: $token)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                                .controlSize(.large)
                        }
                        
                        Button(action: { showToken.toggle() }) {
                            Image(systemName: showToken ? "eye.slash.fill" : "eye.fill")
                                .foregroundColor(.secondary)
                        }
                        .buttonStyle(.plain)
                        .padding(.trailing, 10)
                    }
                }
                
                VStack(alignment: .leading, spacing: 6) {
                    Text("Device ID")
                        .font(.headline)
                    TextField("e.g. macbook-air", text: $deviceId)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .controlSize(.large)
                }
                
                if #available(macOS 13.0, *) {
                    Toggle("Launch RayClip at Login", isOn: $launchAtLogin)
                        .onChange(of: launchAtLogin, perform: updateLaunchAtLogin)
                        .padding(.top, 4)
                        .onAppear { launchAtLogin = SMAppService.mainApp.status == .enabled }
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 4) {
                        Image(systemName: "lock.shield.fill")
                            .foregroundColor(.green)
                        Text("Privacy & Security")
                            .font(.subheadline)
                            .fontWeight(.medium)
                    }
                    Text("RayClip is designed with privacy in mind. Your clipboard history is never saved, logged, or persistently stored. Data is transmitted securely and discarded immediately.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.top, 8)
            }
            .padding(24)
            
            Divider()
            
            // Footer
            HStack {
                // Status Indicator
                HStack(spacing: 6) {
                    Circle()
                        .fill(syncManager.isConfigured ? Color.green : Color.red)
                        .frame(width: 8, height: 8)
                    Text(syncManager.isConfigured ? "Sync Active" : "Not Configured")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                if showSaved {
                    Text("Saved!")
                        .font(.subheadline)
                        .foregroundColor(.green)
                        .transition(.opacity)
                }
                
                Button("Save & Restart Sync") {
                    syncManager.restartSync()
                    withAnimation { showSaved = true }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                        withAnimation { showSaved = false }
                    }
                }
                .keyboardShortcut(.defaultAction)
                .controlSize(.large)
            }
            .padding(20)
            .background(Color(NSColor.windowBackgroundColor))
        }
        .frame(width: 480)
    }
    
    private func updateLaunchAtLogin(_ newValue: Bool) {
        if #available(macOS 13.0, *) {
            do {
                if newValue { try SMAppService.mainApp.register() }
                else { try SMAppService.mainApp.unregister() }
            } catch {
                print("Failed to update login items: \(error)")
            }
        }
    }
}
