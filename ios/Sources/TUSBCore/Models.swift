import Foundation

public enum Slot: Int, CaseIterable, Sendable {
    case a, b, c
}

public enum PedalMode: Sendable {
    case ab, stomp
}

public struct RGB: Equatable, Sendable {
    public let red: Int
    public let green: Int
    public let blue: Int

    public init(red: Int, green: Int, blue: Int) {
        self.red = red
        self.green = green
        self.blue = blue
    }
}

public enum ParameterType: Sendable {
    case float, int, byte
}

public struct Parameter: Equatable, Sendable {
    public let id: String
    public let label: String
    public let type: ParameterType
    public let value: Float
    public let min: Float
    public let max: Float

    public init(id: String, label: String, type: ParameterType, value: Float, min: Float, max: Float) {
        self.id = id
        self.label = label
        self.type = type
        self.value = value
        self.min = min
        self.max = max
    }
}

public struct PresetSlot: Equatable, Sendable {
    public let index: Int
    public let name: String
    public let color: RGB
    public let parameters: [String: Parameter]

    public init(index: Int, name: String, color: RGB, parameters: [String: Parameter] = [:]) {
        self.index = index
        self.name = name
        self.color = color
        self.parameters = parameters
    }

    func with(name: String? = nil, parameters: [String: Parameter]? = nil) -> Self {
        Self(
            index: index,
            name: name ?? self.name,
            color: color,
            parameters: parameters ?? self.parameters
        )
    }
}

public struct LibraryPreset: Equatable, Sendable {
    public let index: Int
    public let name: String
    public let color: RGB

    public init(index: Int, name: String, color: RGB) {
        self.index = index
        self.name = name
        self.color = color
    }
}

public enum CabinetType: Int, Sendable {
    case toneModel, vir, disabled
}

public struct RigModels: Equatable, Sendable {
    public let ampEnabled: Bool?
    public let cabinetType: CabinetType?
    public let virCabinetModel: Int?

    public func cabinetLabel(cabSimBypass: Bool) -> String {
        if cabSimBypass { return "OFF" }
        return switch cabinetType {
        case .toneModel: "TONE MODEL"
        case .vir: "VIR \((virCabinetModel ?? 0) + 1)"
        case .disabled: "OFF"
        case nil: "—"
        }
    }
}

public enum TonexParameter: CaseIterable, Sendable {
    case noiseGateEnable, compEnable, compMakeUp, eqBass, eqMid, eqTreble
    case modelAmpEnable, modelGain, modelVolume, cabinetType, virCabinetModel
    case reverbEnable, reverbModel, modulationEnable, modulationModel, delayEnable, delayModel

    public var index: Int {
        switch self {
        case .noiseGateEnable: 1
        case .compEnable: 6
        case .compMakeUp: 8
        case .eqBass: 11
        case .eqMid: 13
        case .eqTreble: 16
        case .modelAmpEnable: 18
        case .modelGain: 20
        case .modelVolume: 21
        case .cabinetType: 24
        case .virCabinetModel: 25
        case .reverbEnable: 37
        case .reverbModel: 38
        case .modulationEnable: 64
        case .modulationModel: 65
        case .delayEnable: 95
        case .delayModel: 96
        }
    }

    public var range: ClosedRange<Float> {
        switch self {
        case .compMakeUp: -30...10
        case .cabinetType: 0...2
        case .virCabinetModel, .modelGain, .modelVolume, .eqBass, .eqMid, .eqTreble: 0...10
        case .reverbModel: 0...5
        case .modulationModel: 0...4
        case .delayModel: 0...1
        default: 0...1
        }
    }

    public var parameterID: String {
        switch self {
        case .noiseGateEnable: "ParameterXNoiseGateEnable"
        case .compEnable: "ParameterXCompEnable"
        case .compMakeUp: "ParameterXCompMakeUp"
        case .eqBass: "ParameterXEqBass"
        case .eqMid: "ParameterXEqMid"
        case .eqTreble: "ParameterXEqTreble"
        case .modelAmpEnable: "ParameterXModelAmpEnable"
        case .modelGain: "ParameterXModelGain"
        case .modelVolume: "ParameterXModelVolume"
        case .cabinetType: "ParameterXCabinetType"
        case .virCabinetModel: "ParameterXVirCabinetModel"
        case .reverbEnable: "ParameterXReverbEnable"
        case .reverbModel: "ParameterXReverbModel"
        case .modulationEnable: "ParameterXModulationEnable"
        case .modulationModel: "ParameterXModulationModel"
        case .delayEnable: "ParameterXDelayEnable"
        case .delayModel: "ParameterXDelayModel"
        }
    }

    public func denormalize(_ normalized: Float) -> Float {
        range.lowerBound + min(max(normalized, 0), 1) * (range.upperBound - range.lowerBound)
    }

    public func normalize(_ value: Float) -> Float {
        let width = range.upperBound - range.lowerBound
        return width <= 0 ? 0 : min(max((value - range.lowerBound) / width, 0), 1)
    }

    public static func from(index: Int) -> Self? {
        allCases.first { $0.index == index }
    }
}

public struct ParameterBinding: Equatable, Sendable {
    public let index: Int
    public let min: Float
    public let max: Float

    public init(index: Int, min: Float, max: Float) {
        self.index = index
        self.min = min
        self.max = max
    }

    public func denormalize(_ normalized: Float) -> Float {
        min + Swift.min(Swift.max(normalized, 0), 1) * (max - min)
    }

    public func normalize(_ value: Float) -> Float {
        max <= min ? 0 : Swift.min(Swift.max((value - min) / (max - min), 0), 1)
    }
}

public struct FirmwareInfo: Equatable, Sendable {
    public let version: String
    public let serialNumber: String?

    public init(version: String, serialNumber: String? = nil) {
        self.version = version
        self.serialNumber = serialNumber
    }
}

public struct PedalState: Equatable, Sendable {
    public private(set) var activeSlot: Slot
    public let inputTrim: Float
    public let a4Reference: Int
    public let tempo: Int
    public private(set) var slots: [PresetSlot]
    public let libraryPresets: [LibraryPreset]
    public private(set) var presetIDs: [Int]
    public private(set) var rawState: [UInt8]
    public private(set) var pedalMode: PedalMode
    public private(set) var cabSimBypass: Bool
    public private(set) var bypassMode: Bool
    public private(set) var presetParameters: [Float]

    public init(
        activeSlot: Slot,
        inputTrim: Float,
        a4Reference: Int,
        tempo: Int,
        slots: [PresetSlot],
        libraryPresets: [LibraryPreset] = [],
        presetIDs: [Int] = [],
        rawState: [UInt8] = [],
        pedalMode: PedalMode = .ab,
        cabSimBypass: Bool = false,
        bypassMode: Bool = false,
        presetParameters: [Float] = []
    ) {
        self.activeSlot = activeSlot
        self.inputTrim = inputTrim
        self.a4Reference = a4Reference
        self.tempo = tempo
        self.slots = slots
        self.libraryPresets = libraryPresets
        self.presetIDs = presetIDs
        self.rawState = rawState
        self.pedalMode = pedalMode
        self.cabSimBypass = cabSimBypass
        self.bypassMode = bypassMode
        self.presetParameters = presetParameters
    }

    public func withActiveSlot(_ slot: Slot) -> Self {
        var result = self
        result.activeSlot = slot
        return result
    }

    public func withPresetIDs(_ IDs: [Int]) -> Self {
        var result = self
        result.presetIDs = IDs
        return result
    }

    public func withBypassMode(_ enabled: Bool) -> Self {
        var result = self
        result.bypassMode = enabled
        let offset = result.rawState.count - 12
        if result.rawState.count > 8, result.rawState.indices.contains(offset) {
            result.rawState[offset] = enabled ? 1 : 0
        }
        return result
    }

    public func withPedalMode(_ mode: PedalMode) -> Self {
        var result = self
        result.pedalMode = mode
        if result.rawState.indices.contains(27) {
            result.rawState[27] = mode == .stomp ? 1 : 0
        }
        return result
    }

    public func withCabSimBypass(_ enabled: Bool) -> Self {
        var result = self
        result.cabSimBypass = enabled
        if result.rawState.indices.contains(28) {
            result.rawState[28] = enabled ? 1 : 0
        }
        return result
    }

    public func withPresetInSlot(_ presetID: Int, slot: Slot, selectSlot: Bool) -> Self {
        var result = self
        if result.rawState.count > 8 {
            if result.rawState.indices.contains(27) {
                result.rawState[27] = slot == .c ? 1 : 0
            }
            let presetOffset = result.rawState.count - [18, 16, 14][slot.rawValue]
            if result.rawState.indices.contains(presetOffset) {
                result.rawState[presetOffset] = UInt8(truncatingIfNeeded: presetID)
                if result.rawState.indices.contains(presetOffset + 1) {
                    result.rawState[presetOffset + 1] = 0
                }
            }
            if selectSlot {
                let activeOffset = result.rawState.count - 11
                if result.rawState.indices.contains(activeOffset) {
                    result.rawState[activeOffset] = UInt8(slot.rawValue)
                }
            }
            let directMonitorOffset = result.rawState.count - 7
            if result.rawState.indices.contains(directMonitorOffset) {
                result.rawState[directMonitorOffset] = 1
            }
            let bypassOffset = result.rawState.count - 12
            if result.rawState.indices.contains(bypassOffset) {
                result.rawState[bypassOffset] = 0
            }
        }
        while result.presetIDs.count < Slot.allCases.count { result.presetIDs.append(0) }
        result.presetIDs[slot.rawValue] = presetID
        if result.slots.indices.contains(slot.rawValue) {
            let name = result.libraryPresets.first { $0.index == presetID }?.name ?? "Preset \(presetID + 1)"
            result.slots[slot.rawValue] = result.slots[slot.rawValue].with(name: name)
        }
        if selectSlot { result.activeSlot = slot }
        if slot == .c { result.pedalMode = .stomp }
        result.bypassMode = false
        return result
    }

    public func withActivePresetParameters(_ values: [Float]) -> Self {
        guard !values.isEmpty, slots.indices.contains(activeSlot.rawValue) else { return self }
        var result = self
        var mapped = result.slots[activeSlot.rawValue].parameters
        for parameter in TonexParameter.allCases where values.indices.contains(parameter.index) {
            mapped[parameter.parameterID] = Parameter(
                id: parameter.parameterID,
                label: parameter.parameterID,
                type: .float,
                value: values[parameter.index],
                min: parameter.range.lowerBound,
                max: parameter.range.upperBound
            )
        }
        result.slots[activeSlot.rawValue] = result.slots[activeSlot.rawValue].with(parameters: mapped)
        result.presetParameters = values
        return result
    }

    public func withParameterValue(index: Int, value: Float) -> Self {
        guard presetParameters.indices.contains(index) else { return self }
        var result = self
        result.presetParameters[index] = value
        guard let parameter = TonexParameter.from(index: index),
              result.slots.indices.contains(activeSlot.rawValue) else { return result }
        var mapped = result.slots[activeSlot.rawValue].parameters
        mapped[parameter.parameterID] = Parameter(
            id: parameter.parameterID,
            label: parameter.parameterID,
            type: .float,
            value: value,
            min: parameter.range.lowerBound,
            max: parameter.range.upperBound
        )
        result.slots[activeSlot.rawValue] = result.slots[activeSlot.rawValue].with(parameters: mapped)
        return result
    }

    public func withActivePresetName(_ name: String) -> Self {
        guard slots.indices.contains(activeSlot.rawValue) else { return self }
        var result = self
        result.slots[activeSlot.rawValue] = result.slots[activeSlot.rawValue].with(name: name)
        return result
    }

    public func parameterValue(_ parameter: TonexParameter) -> Float? {
        presetParameters.indices.contains(parameter.index) ? presetParameters[parameter.index] : nil
    }

    public func rigModels() -> RigModels {
        RigModels(
            ampEnabled: parameterValue(.modelAmpEnable).map { $0 >= 0.5 },
            cabinetType: parameterValue(.cabinetType).flatMap {
                $0.isFinite && abs($0) <= 10_000
                    ? CabinetType(rawValue: min(max(Int($0), 0), 2))
                    : nil
            },
            virCabinetModel: parameterValue(.virCabinetModel).flatMap {
                $0.isFinite && abs($0) <= 10_000 ? Int($0) : nil
            }
        )
    }

    public static let simulated: Self = {
        let colors = [
            RGB(red: 255, green: 80, blue: 56),
            RGB(red: 46, green: 204, blue: 113),
            RGB(red: 52, green: 152, blue: 219),
        ]
        return Self(
            activeSlot: .a,
            inputTrim: 0,
            a4Reference: 440,
            tempo: 120,
            slots: Slot.allCases.map {
                PresetSlot(index: $0.rawValue, name: "Preset \(String(describing: $0).uppercased())", color: colors[$0.rawValue])
            },
            libraryPresets: (0..<20).map {
                LibraryPreset(index: $0, name: "Preset \($0 + 1)", color: colors[$0 % colors.count])
            },
            presetIDs: [0x0C, 0x08, 0x07],
            presetParameters: [Float](repeating: 0, count: 109)
        )
    }()
}
