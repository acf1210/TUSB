import Foundation

public struct PedalHandshake: Equatable, Sendable {
    public let firmware: FirmwareInfo
    public let state: PedalState
}

public enum FakePedalError: Error, Equatable, Sendable {
    case invalidPresetID(Int)
}

public final class FakePedalController {
    public private(set) var state: PedalState
    public private(set) var parameterWriteCounts: [Int: Int] = [:]
    public private(set) var isConnected = false

    public init(state: PedalState = .simulated) {
        self.state = state
    }

    public func connect() { isConnected = true }
    public func disconnect() { isConnected = false }

    public func handshake() -> PedalHandshake {
        isConnected = true
        return PedalHandshake(firmware: FirmwareInfo(version: "SIM-1.0.0"), state: state)
    }

    public func select(slot: Slot) {
        state = state.withActiveSlot(slot)
    }

    public func select(presetID: Int) {
        guard let index = state.presetIDs.firstIndex(of: presetID),
              let slot = Slot(rawValue: index) else { return }
        select(slot: slot)
    }

    public func setMode(_ mode: PedalMode) {
        state = state.withPedalMode(mode)
    }

    public func setCabSimBypass(_ enabled: Bool) {
        state = state.withCabSimBypass(enabled)
    }

    public func setBypass(_ enabled: Bool) {
        state = state.withBypassMode(enabled)
    }

    @discardableResult
    public func setParameter(index: Int, value: Float) -> Bool {
        guard (0...255).contains(index), value.isFinite else { return false }
        state = state.withParameterValue(index: index, value: value)
        parameterWriteCounts[index, default: 0] += 1
        return true
    }

    public func loadPreset(_ presetID: Int, into slot: Slot, select: Bool) throws {
        guard (0..<20).contains(presetID) else { throw FakePedalError.invalidPresetID(presetID) }
        state = state.withPresetInSlot(presetID, slot: slot, selectSlot: select)
    }
}
