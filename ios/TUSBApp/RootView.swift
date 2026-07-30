import SwiftUI

struct RootView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        Group {
            if model.isConnected {
                ConnectedView()
            } else {
                ConnectView()
            }
        }
        .background(Color.tusbBackground.ignoresSafeArea())
        .alert("connect.usb.title", isPresented: $model.directUSBNotice) {
            Button("common.ok", role: .cancel) {}
        } message: {
            Text("connect.usb.message")
        }
    }
}

private struct ConnectView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        ScrollView {
            VStack(spacing: 26) {
                Spacer(minLength: 36)
                ZStack {
                    Circle().fill(model.theme.accent.opacity(0.16)).frame(width: 138, height: 138)
                    Image(systemName: "cable.connector")
                        .font(.system(size: 54, weight: .thin))
                        .foregroundStyle(model.theme.accent)
                }
                VStack(spacing: 6) {
                    Text("TUSB").font(.system(size: 38, weight: .black, design: .rounded))
                    Text("connect.subtitle").font(.caption.monospaced()).foregroundStyle(.secondary)
                }
                TUSBCard {
                    Label("connect.status", systemImage: "info.circle.fill")
                        .font(.headline)
                        .foregroundStyle(model.theme.accent)
                    Text("connect.truth").font(.subheadline).foregroundStyle(.secondary)
                }
                Button {
                    model.connectDemo()
                } label: {
                    Label("connect.demo", systemImage: "play.circle.fill")
                        .frame(maxWidth: .infinity).padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .accessibilityIdentifier("connect.demo")

                Button("connect.usb.action") {
                    model.directUSBNotice = true
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("connect.usb")
                Text("connect.footer").font(.caption).foregroundStyle(.tertiary)
                Spacer(minLength: 20)
            }
            .padding(24)
            .frame(maxWidth: 520)
            .frame(maxWidth: .infinity)
        }
        .navigationTitle("TUSB")
    }
}

private struct ConnectedView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        TabView(selection: $model.tab) {
            NavigationStack { EditorView() }
                .tag(AppTab.editor)
                .tabItem { Label(AppTab.editor.titleKey, systemImage: AppTab.editor.icon) }
                .accessibilityIdentifier("tab.editor")
            NavigationStack { PresetsView() }
                .tag(AppTab.presets)
                .tabItem { Label(AppTab.presets.titleKey, systemImage: AppTab.presets.icon) }
                .accessibilityIdentifier("tab.presets")
            NavigationStack { ToolsView() }
                .tag(AppTab.tools)
                .tabItem { Label(AppTab.tools.titleKey, systemImage: AppTab.tools.icon) }
                .accessibilityIdentifier("tab.tools")
            NavigationStack { MenuView() }
                .tag(AppTab.menu)
                .tabItem { Label(AppTab.menu.titleKey, systemImage: AppTab.menu.icon) }
                .accessibilityIdentifier("tab.menu")
        }
    }
}

struct TUSBCard<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) { content }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.tusbSurface, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(.white.opacity(0.08)))
    }
}

extension Color {
    static let tusbBackground = Color(red: 0.035, green: 0.037, blue: 0.045)
    static let tusbSurface = Color(red: 0.075, green: 0.078, blue: 0.09)
}
