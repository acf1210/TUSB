import Foundation

public enum MIDIMessage: Equatable, Sendable {
    case programChange(channel: Int, program: Int)
    case controlChange(channel: Int, controller: Int, value: Int)
}

public final class MIDIParser {
    private var runningStatus = 0
    private var firstDataByte: Int?
    private var inSysEx = false

    public init() {}

    public func parse(_ bytes: [UInt8], offset: Int = 0, count: Int? = nil) -> [MIDIMessage] {
        guard offset >= 0, offset <= bytes.count else { return [] }
        let length = min(max(count ?? bytes.count - offset, 0), bytes.count - offset)
        var messages: [MIDIMessage] = []
        for byte in bytes[offset..<(offset + length)] {
            switch byte {
            case 0xF8...0xFF:
                break
            case 0xF0:
                inSysEx = true
                runningStatus = 0
                firstDataByte = nil
            case 0xF7:
                inSysEx = false
            case 0xF1...0xF6:
                runningStatus = 0
                firstDataByte = nil
            case 0x80...0xEF:
                runningStatus = Int(byte)
                firstDataByte = nil
                inSysEx = false
            default:
                if !inSysEx { consume(Int(byte), into: &messages) }
            }
        }
        return messages
    }

    private func consume(_ data: Int, into messages: inout [MIDIMessage]) {
        let channel = runningStatus & 0x0F
        switch runningStatus & 0xF0 {
        case 0xC0:
            messages.append(.programChange(channel: channel, program: data))
        case 0xB0:
            if let controller = firstDataByte {
                messages.append(.controlChange(channel: channel, controller: controller, value: data))
                firstDataByte = nil
            } else {
                firstDataByte = data
            }
        case 0x80, 0x90, 0xA0, 0xE0:
            firstDataByte = firstDataByte == nil ? data : nil
        default:
            break
        }
    }
}

public enum MIDIAction: String, CaseIterable, Sendable {
    case selectSlotA = "SELECT_SLOT_A"
    case selectSlotB = "SELECT_SLOT_B"
    case selectSlotC = "SELECT_SLOT_C"
    case nextPreset = "NEXT_PRESET"
    case previousPreset = "PREV_PRESET"
    case toggleBypass = "TOGGLE_BYPASS"
    case toggleCab = "TOGGLE_CAB"
    case toggleGate = "TOGGLE_GATE"
    case toggleComp = "TOGGLE_COMP"
    case toggleEQ = "TOGGLE_EQ"
    case toggleMod = "TOGGLE_MOD"
    case toggleDelay = "TOGGLE_DELAY"
    case toggleReverb = "TOGGLE_REVERB"
    case ampBass = "AMP_BASS"
    case ampMid = "AMP_MID"
    case ampTreble = "AMP_TREBLE"
    case ampGain = "AMP_GAIN"
    case ampVolume = "AMP_VOLUME"

    public var isContinuous: Bool {
        switch self {
        case .ampBass, .ampMid, .ampTreble, .ampGain, .ampVolume: true
        default: false
        }
    }
}

public struct MIDIMapping: Equatable, Sendable {
    public let actions: [Int: MIDIAction]

    public init(actions: [Int: MIDIAction]) {
        self.actions = actions
    }

    public func action(for key: Int) -> MIDIAction? { actions[key] }

    public func key(for action: MIDIAction) -> Int? {
        actions.first { $0.value == action }?.key
    }

    public func withLearned(_ action: MIDIAction, key: Int) -> Self {
        let cleaned = actions.filter { $0.key != key && $0.value != action }
        return Self(actions: cleaned.merging([key: action]) { _, new in new })
    }

    public static let `default` = Self(actions: [
        20: .selectSlotA, 21: .selectSlotB, 22: .selectSlotC,
        23: .nextPreset, 24: .previousPreset, 25: .toggleBypass,
        26: .toggleCab, 27: .toggleGate, 28: .toggleComp, 29: .toggleEQ,
        30: .toggleMod, 31: .toggleDelay, 32: .toggleReverb,
        102: .ampBass, 103: .ampMid, 104: .ampTreble, 105: .ampGain, 106: .ampVolume,
    ])
}

public enum MIDIMappingCodec {
    public static func encode(_ mapping: MIDIMapping) -> String {
        mapping.actions.keys.sorted().map { "\($0)=\(mapping.actions[$0]!.rawValue)" }.joined(separator: ";")
    }

    public static func decode(_ encoded: String?) -> MIDIMapping {
        guard let encoded, !encoded.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return .default
        }
        var result: [Int: MIDIAction] = [:]
        for entry in encoded.split(separator: ";", omittingEmptySubsequences: false) {
            let pair = entry.split(separator: "=", omittingEmptySubsequences: false)
            guard pair.count == 2,
                  let key = Int(pair[0]),
                  (0...255).contains(key),
                  let action = MIDIAction(rawValue: String(pair[1])) else {
                return .default
            }
            result[key] = action
        }
        return MIDIMapping(actions: result)
    }
}

let programChangeOffset = 128

public func programChangeKey(_ program: Int) -> Int { programChangeOffset + program }

public func midiMappingLabel(_ key: Int) -> String {
    key >= programChangeOffset ? "PC \(key - programChangeOffset)" : "CC \(key)"
}

public enum EffectBlock: Sendable {
    case gate, comp, eq, mod, delay, reverb
}

public enum AmpKnob: Sendable {
    case bass, mid, treble, gain, volume
}

public protocol MIDIActionHandling: AnyObject {
    var activePresetID: Int? { get }
    func selectSlot(_ slot: Slot)
    func loadPreset(_ presetID: Int)
    func toggleBypass()
    func toggleCab()
    func toggleEffect(_ effect: EffectBlock)
    func setAmpKnob(_ knob: AmpKnob, normalized: Float)
}

public struct MIDILearnedMapping: Equatable, Sendable {
    public let action: MIDIAction
    public let key: Int

    public init(action: MIDIAction, key: Int) {
        self.action = action
        self.key = key
    }
}

public final class MIDICommandDispatcher {
    private weak var handler: MIDIActionHandling?
    private let mappingProvider: () -> MIDIMapping
    public private(set) var learnTarget: MIDIAction?
    public private(set) var lastMessage: MIDIMessage?
    public private(set) var learned: MIDILearnedMapping?
    public var onLearned: ((MIDILearnedMapping) -> Void)?

    public init(handler: MIDIActionHandling, mappingProvider: @escaping () -> MIDIMapping = { .default }) {
        self.handler = handler
        self.mappingProvider = mappingProvider
    }

    public func startLearn(_ action: MIDIAction) { learnTarget = action }
    public func cancelLearn() { learnTarget = nil }

    public func dispatch(_ message: MIDIMessage) {
        lastMessage = message
        if let action = learnTarget {
            let key = switch message {
            case let .controlChange(_, controller, _): controller
            case let .programChange(_, program): programChangeKey(program)
            }
            let learned = MIDILearnedMapping(action: action, key: key)
            self.learned = learned
            learnTarget = nil
            onLearned?(learned)
            return
        }

        switch message {
        case let .programChange(_, program):
            if let action = mappingProvider().action(for: programChangeKey(program)) {
                perform(action, value: 127)
            } else if (0..<20).contains(program) {
                handler?.loadPreset(program)
            }
        case let .controlChange(_, controller, value):
            guard let action = mappingProvider().action(for: controller) else { return }
            perform(action, value: value)
        }
    }

    private func perform(_ action: MIDIAction, value: Int) {
        if action.isContinuous {
            let normalized = Float(value) / 127
            switch action {
            case .ampBass: handler?.setAmpKnob(.bass, normalized: normalized)
            case .ampMid: handler?.setAmpKnob(.mid, normalized: normalized)
            case .ampTreble: handler?.setAmpKnob(.treble, normalized: normalized)
            case .ampGain: handler?.setAmpKnob(.gain, normalized: normalized)
            case .ampVolume: handler?.setAmpKnob(.volume, normalized: normalized)
            default: break
            }
            return
        }
        guard value >= 64 else { return }
        switch action {
        case .selectSlotA: handler?.selectSlot(.a)
        case .selectSlotB: handler?.selectSlot(.b)
        case .selectSlotC: handler?.selectSlot(.c)
        case .nextPreset: stepPreset(by: 1)
        case .previousPreset: stepPreset(by: -1)
        case .toggleBypass: handler?.toggleBypass()
        case .toggleCab: handler?.toggleCab()
        case .toggleGate: handler?.toggleEffect(.gate)
        case .toggleComp: handler?.toggleEffect(.comp)
        case .toggleEQ: handler?.toggleEffect(.eq)
        case .toggleMod: handler?.toggleEffect(.mod)
        case .toggleDelay: handler?.toggleEffect(.delay)
        case .toggleReverb: handler?.toggleEffect(.reverb)
        default: break
        }
    }

    private func stepPreset(by delta: Int) {
        guard let current = handler?.activePresetID else { return }
        handler?.loadPreset((current + delta + 20) % 20)
    }
}
