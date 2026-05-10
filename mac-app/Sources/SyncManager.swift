import Foundation
import AppKit
import CryptoKit

class SyncManager: ObservableObject {
    @Published var isConfigured = false
    private var pollTimer: Timer?
    private var sseTask: Task<Void, Never>?
    private var lastLocalHash = ""
    private var lastRemoteHash = ""
    private var lastRemoteId = ""
    
    init() {
        restartSync()
        
        // Timer to poll local clipboard (lightweight check every 1s)
        pollTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.checkLocalClipboard()
        }
    }
    
    func restartSync() {
        let apiUrl = UserDefaults.standard.string(forKey: "apiUrl") ?? ""
        let token = UserDefaults.standard.string(forKey: "token") ?? ""
        
        isConfigured = !apiUrl.isEmpty && !token.isEmpty
        
        sseTask?.cancel()
        
        if isConfigured {
            connectSSE()
            Task { await fetchLatest() }
        }
    }
    
    private func checkLocalClipboard() {
        guard isConfigured else { return }
        
        let pasteboard = NSPasteboard.general
        // Only trigger if it's text for now
        guard let text = pasteboard.string(forType: .string) else { return }
        
        let hash = SHA256.hash(data: Data(text.utf8)).compactMap { String(format: "%02x", $0) }.joined()
        
        if hash == lastLocalHash || hash == lastRemoteHash {
            return
        }
        
        lastLocalHash = hash
        Task { await uploadClip(text: text, hash: hash) }
    }
    
    private func uploadClip(text: String, hash: String) async {
        let apiUrl = UserDefaults.standard.string(forKey: "apiUrl") ?? ""
        let token = UserDefaults.standard.string(forKey: "token") ?? ""
        let deviceId = UserDefaults.standard.string(forKey: "deviceId") ?? "macbook"
        
        guard let url = URL(string: "\(apiUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/v1/clips") else { return }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: String] = [
            "text": text,
            "sourceDeviceId": deviceId,
            "sourceDeviceName": Host.current().localizedName ?? "Mac"
        ]
        
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                print("Uploaded clip to server")
            }
        } catch {
            print("Upload failed: \(error)")
        }
    }
    
    private func connectSSE() {
        sseTask = Task {
            let apiUrl = UserDefaults.standard.string(forKey: "apiUrl") ?? ""
            let token = UserDefaults.standard.string(forKey: "token") ?? ""
            let deviceId = UserDefaults.standard.string(forKey: "deviceId") ?? ""
            
            guard let url = URL(string: "\(apiUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/v1/events") else { return }
            
            var request = URLRequest(url: url)
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            
            do {
                if #available(macOS 12.0, *) {
                    let (bytes, _) = try await URLSession.shared.bytes(for: request)
                    for try await line in bytes.lines {
                        if Task.isCancelled { break }
                        if line.hasPrefix("data: ") {
                            let jsonString = String(line.dropFirst(6))
                            if let data = jsonString.data(using: .utf8),
                               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                               let clip = json["clip"] as? [String: Any],
                               let text = clip["text"] as? String,
                               let sourceDeviceId = clip["sourceDeviceId"] as? String,
                               let id = clip["id"] as? String {
                                
                                let deviceId = UserDefaults.standard.string(forKey: "deviceId") ?? ""
                                if sourceDeviceId != deviceId && id != self.lastRemoteId {
                                    await applyRemoteClip(text: text, id: id, sha256: clip["sha256"] as? String)
                                }
                            }
                        }
                    }
                }
            } catch {
                print("SSE stream disconnected: \(error)")
                try? await Task.sleep(nanoseconds: 5_000_000_000)
                if !Task.isCancelled { connectSSE() }
            }
        }
    }
    
    private func fetchLatest() async {
        let apiUrl = UserDefaults.standard.string(forKey: "apiUrl") ?? ""
        let token = UserDefaults.standard.string(forKey: "token") ?? ""
        let deviceId = UserDefaults.standard.string(forKey: "deviceId") ?? ""
        
        guard let url = URL(string: "\(apiUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/v1/clips/latest") else { return }
        
        var request = URLRequest(url: url)
        request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        
        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let clip = json["clip"] as? [String: Any],
               let text = clip["text"] as? String,
               let sourceDeviceId = clip["sourceDeviceId"] as? String,
               let id = clip["id"] as? String {
                
                if sourceDeviceId != deviceId && id != self.lastRemoteId {
                    await applyRemoteClip(text: text, id: id, sha256: clip["sha256"] as? String)
                }
            }
        } catch {
            print("Fetch latest failed: \(error)")
        }
    }
    
    @MainActor
    private func applyRemoteClip(text: String, id: String, sha256: String?) async {
        self.lastRemoteId = id
        self.lastRemoteHash = sha256 ?? SHA256.hash(data: Data(text.utf8)).compactMap { String(format: "%02x", $0) }.joined()
        self.lastLocalHash = self.lastRemoteHash
        
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)
        print("Applied remote clip from server")
    }
}
