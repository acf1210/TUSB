import CoreMIDI
import Foundation
import SwiftUI
import TUSBCore

struct MenuView: View {
    @EnvironmentObject private var model: AppModel
    @StateObject private var midi = MIDIInputSource()

    var body: some View {
        Form {
            Section("menu.device") {
                LabeledContent("menu.firmware", value: "DEMO 1.2.0")
                LabeledContent("menu.slot", value: model.activeSlot.rawValue)
                Button {
                    model.toggleCapture()
                } label: {
                    Text(LocalizedStringKey(model.isCapturing ? "capture.stop" : "capture.start"))
                }
                .accessibilityIdentifier("capture.toggle")
                if let lastCapture = model.lastCapture {
                    LabeledContent("capture.last", value: lastCapture.lastPathComponent)
                }
            }
            Section("menu.settings") {
                LabeledContent("menu.volume") {
                    Slider(value: $model.masterVolume).frame(maxWidth: 180)
                }
                Stepper("A4: \(model.a4Reference) Hz", value: $model.a4Reference, in: 430...450)
                Picker("menu.theme", selection: $model.theme) {
                    ForEach(AppTheme.allCases) { Text($0.name).tag($0) }
                }
            }
            Section {
                LabeledContent("midi.sources", value: midi.sourceCount.formatted())
                LabeledContent("midi.last", value: midi.lastMessage)
                Picker("midi.channel", selection: $midi.channel) {
                    ForEach(0..<16, id: \.self) { channel in
                        Text("\(channel + 1)").tag(channel)
                    }
                }
                ForEach(midi.sources) { source in
                    Button {
                        midi.connect(source)
                    } label: {
                        HStack {
                            Text(source.label)
                            Spacer()
                            if midi.selectedSourceID == source.id {
                                Image(systemName: "checkmark")
                            }
                        }
                    }
                }
                Button("midi.refresh") { midi.refresh() }
                    .accessibilityIdentifier("midi.refresh")
                Text("midi.note").font(.caption).foregroundStyle(.secondary)
            } header: {
                Label("MIDI", systemImage: "pianokeys")
            }
            Section {
                Text("menu.about")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Button("menu.disconnect", role: .destructive) { model.disconnect() }
                    .accessibilityIdentifier("menu.disconnect")
            }
        }
        .navigationTitle("nav.menu")
        .onChange(of: midi.lastBatch) { _, batch in
            batch?.messages.forEach(model.handleMIDI)
        }
    }
}

struct MIDISourceOption: Identifiable {
    let id: MIDIEndpointRef
    let label: String
}

struct MIDIInputBatch: Equatable {
    let id = UUID()
    let messages: [TUSBCore.MIDIMessage]
}

@MainActor
final class MIDIInputSource: ObservableObject {
    @Published private(set) var sourceCount = 0
    @Published private(set) var sources: [MIDISourceOption] = []
    @Published private(set) var selectedSourceID: MIDIEndpointRef?
    @Published private(set) var lastMessage = "—"
    @Published private(set) var lastBatch: MIDIInputBatch?
    @Published var channel = 0
    private var client = MIDIClientRef()
    private var inputPort = MIDIPortRef()
    private var parser = TUSBCore.MIDIParser()
    private var connectionGeneration = 0

    init() {
        MIDIClientCreateWithBlock("TUSB" as CFString, &client) { _ in }
        MIDIInputPortCreateWithBlock(client, "TUSB Input" as CFString, &inputPort) {
            [weak self] packetList, sourceConnectionRefCon in
            let packets = packetList.unsafeSequence().map { Array($0.bytes()) }
            let generation = sourceConnectionRefCon.map { Int(bitPattern: $0) }
            Task { @MainActor in
                self?.receive(packets, generation: generation)
            }
        }
        refresh()
    }

    deinit {
        MIDIPortDispose(inputPort)
        MIDIClientDispose(client)
    }

    func refresh() {
        connectionGeneration &+= 1
        if let selectedSourceID {
            MIDIPortDisconnectSource(inputPort, selectedSourceID)
        }
        selectedSourceID = nil
        parser = TUSBCore.MIDIParser()
        sources = (0..<MIDIGetNumberOfSources()).compactMap {
            let source = MIDIGetSource($0)
            return source == 0 ? nil : MIDISourceOption(id: source, label: "MIDI \($0 + 1)")
        }
        sourceCount = sources.count
    }

    func connect(_ source: MIDISourceOption) {
        connectionGeneration &+= 1
        if let selectedSourceID {
            MIDIPortDisconnectSource(inputPort, selectedSourceID)
        }
        parser = TUSBCore.MIDIParser()
        let connectionToken = UnsafeMutableRawPointer(bitPattern: connectionGeneration)
        guard MIDIPortConnectSource(inputPort, source.id, connectionToken) == noErr else {
            selectedSourceID = nil
            return
        }
        selectedSourceID = source.id
    }

    private func receive(_ packets: [[UInt8]], generation: Int?) {
        guard generation == connectionGeneration else { return }
        let messages = packets
            .flatMap { parser.parse($0) }
            .filter { $0.channel == channel }
        guard let last = messages.last else { return }
        lastMessage = last.label
        lastBatch = MIDIInputBatch(messages: messages)
    }
}

private extension TUSBCore.MIDIMessage {
    var channel: Int {
        switch self {
        case let .programChange(channel, _), let .controlChange(channel, _, _): channel
        }
    }

    var label: String {
        switch self {
        case let .programChange(channel, program):
            "PC ch\(channel + 1) \(program)"
        case let .controlChange(channel, controller, value):
            "CC ch\(channel + 1) \(controller)=\(value)"
        }
    }
}
