import SwiftUI
import Foundation
import TUSBCore

enum AppTab: String, CaseIterable, Identifiable {
    case editor, presets, tools, menu

    var id: Self { self }
    var icon: String {
        switch self {
        case .editor: "slider.horizontal.3"
        case .presets: "square.grid.2x2"
        case .tools: "tuningfork"
        case .menu: "ellipsis.circle"
        }
    }
    var titleKey: LocalizedStringKey {
        switch self {
        case .editor: "nav.editor"
        case .presets: "nav.presets"
        case .tools: "nav.tools"
        case .menu: "nav.menu"
        }
    }
}

enum PedalMode: String, CaseIterable, Identifiable {
    case stomp = "STOMP"
    case ab = "A/B"
    var id: Self { self }
}

enum PedalSlot: String, CaseIterable, Identifiable {
    case a = "A", b = "B", c = "C"
    var id: Self { self }
}

enum AppTheme: String, CaseIterable, Identifiable {
    case classic, ocean, violet
    var id: Self { self }
    var name: String {
        switch self {
        case .classic: String(localized: "theme.classic")
        case .ocean: String(localized: "theme.ocean")
        case .violet: String(localized: "theme.violet")
        }
    }
    var accent: Color {
        switch self {
        case .classic: Color(red: 0.95, green: 0.62, blue: 0.18)
        case .ocean: .cyan
        case .violet: .purple
        }
    }
}

struct EffectBlock: Identifiable, Equatable {
    let id: String
    let symbol: String
    var enabled: Bool
    var mix: Double

    static let demo: [Self] = [
        .init(id: "COMP", symbol: "waveform.path", enabled: true, mix: 0.42),
        .init(id: "GATE", symbol: "rectangle.compress.vertical", enabled: true, mix: 0.30),
        .init(id: "AMP", symbol: "guitars", enabled: true, mix: 0.75),
        .init(id: "EQ", symbol: "slider.horizontal.3", enabled: true, mix: 0.50),
        .init(id: "CAB", symbol: "hifispeaker", enabled: true, mix: 0.68),
        .init(id: "MOD", symbol: "water.waves", enabled: false, mix: 0.35),
        .init(id: "DELAY", symbol: "repeat", enabled: true, mix: 0.28),
        .init(id: "REVERB", symbol: "sparkles", enabled: true, mix: 0.22)
    ]
}

struct Preset: Identifiable, Equatable {
    let id: Int
    let name: String
    let rig: String

    static let demo = (1...20).map {
        Preset(
            id: $0,
            name: ["Clean Studio", "Edge Breakup", "British Lead", "Modern High Gain", "Ambient Glass"][($0 - 1) % 5] + " \($0)",
            rig: ["American Clean · 2x12", "Class A · 1x12", "British 800 · 4x12", "Rectifier · 4x12"][($0 - 1) % 4]
        )
    }
}

@MainActor
final class AppModel: ObservableObject {
    private let demoPedal = TUSBCore.FakePedalController()
    private lazy var midiDispatcher = TUSBCore.MIDICommandDispatcher(handler: self)
    private var activeCapture: URL?

    @Published var isConnected = false
    @Published var tab: AppTab = .editor
    @Published var directUSBNotice = false
    @Published var activeSlot: PedalSlot = .a {
        didSet { demoPedal.select(slot: activeSlot.coreSlot) }
    }
    @Published var mode: PedalMode = .stomp {
        didSet { demoPedal.setMode(mode == .stomp ? .stomp : .ab) }
    }
    @Published var bypass = false {
        didSet { demoPedal.setBypass(bypass) }
    }
    @Published var theme: AppTheme = .classic
    @Published var effects = EffectBlock.demo
    @Published var selectedEffect: EffectBlock?
    @Published var presets = Preset.demo
    @Published var slotPresets: [PedalSlot: Preset] = [
        .a: Preset.demo[0], .b: Preset.demo[1], .c: Preset.demo[2]
    ]
    @Published var ampValues: [String: Double] = [
        "GAIN": 0.56, "BASS": 0.48, "MID": 0.62, "TREBLE": 0.58, "VOLUME": 0.72
    ]
    @Published var masterVolume = 0.75
    @Published var a4Reference = 440
    @Published var isCapturing = false
    @Published var lastCapture: URL?

    init(arguments: [String] = ProcessInfo.processInfo.arguments) {
        isConnected = arguments.contains("--uitest-fake-pedal")
        if isConnected { demoPedal.connect() }
    }

    var activePreset: Preset { slotPresets[activeSlot] ?? presets[0] }

    func connectDemo() {
        _ = demoPedal.handshake()
        isConnected = true
    }

    func disconnect() {
        demoPedal.disconnect()
        isConnected = false
        tab = .editor
    }

    func load(_ preset: Preset) {
        slotPresets = slotPresets.merging([activeSlot: preset]) { _, new in new }
        try? demoPedal.loadPreset(preset.id - 1, into: activeSlot.coreSlot, select: true)
    }

    func setAmp(_ name: String, normalizedValue: Double) {
        ampValues = ampValues.merging([name: normalizedValue]) { _, new in new }
        let parameter: TUSBCore.TonexParameter? = switch name {
        case "GAIN": .modelGain
        case "BASS": .eqBass
        case "MID": .eqMid
        case "TREBLE": .eqTreble
        case "VOLUME": .modelVolume
        default: nil
        }
        if let parameter {
            _ = demoPedal.setParameter(
                index: parameter.index,
                value: parameter.denormalize(Float(normalizedValue))
            )
        }
    }

    func toggle(_ effect: EffectBlock) {
        effects = effects.map {
            $0.id == effect.id
                ? EffectBlock(id: $0.id, symbol: $0.symbol, enabled: !$0.enabled, mix: $0.mix)
                : $0
        }
    }

    func setMix(_ value: Double, for effect: EffectBlock) {
        effects = effects.map {
            $0.id == effect.id
                ? EffectBlock(id: $0.id, symbol: $0.symbol, enabled: $0.enabled, mix: value)
                : $0
        }
        selectedEffect = effects.first { $0.id == effect.id }
    }

    func handleMIDI(_ message: TUSBCore.MIDIMessage) {
        midiDispatcher.dispatch(message)
    }

    func toggleCapture() {
        if isCapturing {
            lastCapture = activeCapture
            activeCapture = nil
            isCapturing = false
            return
        }
        let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let url = directory.appendingPathComponent("tusb-capture-\(UUID().uuidString.lowercased()).jsonl")
        let marker = #"{"event":"capture_started","source":"ios_demo"}"# + "\n"
        guard FileManager.default.createFile(
            atPath: url.path,
            contents: Data(marker.utf8),
            attributes: [.protectionKey: FileProtectionType.complete]
        ) else {
            return
        }
        activeCapture = url
        isCapturing = true
    }
}

extension AppModel: TUSBCore.MIDIActionHandling {
    var activePresetID: Int? { activePreset.id - 1 }

    func selectSlot(_ slot: TUSBCore.Slot) {
        if slot == .c {
            mode = .stomp
        }
        activeSlot = switch slot {
        case .a: .a
        case .b: .b
        case .c: .c
        }
    }

    func loadPreset(_ presetID: Int) {
        guard presets.indices.contains(presetID) else { return }
        load(presets[presetID])
    }

    func toggleBypass() {
        bypass.toggle()
    }

    func toggleCab() {
        toggleEffect(named: "CAB")
    }

    func toggleEffect(_ effect: TUSBCore.EffectBlock) {
        let name = switch effect {
        case .gate: "GATE"
        case .comp: "COMP"
        case .eq: "EQ"
        case .mod: "MOD"
        case .delay: "DELAY"
        case .reverb: "REVERB"
        }
        toggleEffect(named: name)
    }

    func setAmpKnob(_ knob: TUSBCore.AmpKnob, normalized: Float) {
        let name = switch knob {
        case .bass: "BASS"
        case .mid: "MID"
        case .treble: "TREBLE"
        case .gain: "GAIN"
        case .volume: "VOLUME"
        }
        setAmp(name, normalizedValue: Double(normalized))
    }

    private func toggleEffect(named name: String) {
        guard let effect = effects.first(where: { $0.id == name }) else { return }
        toggle(effect)
    }
}

private extension PedalSlot {
    var coreSlot: TUSBCore.Slot {
        switch self {
        case .a: .a
        case .b: .b
        case .c: .c
        }
    }
}
