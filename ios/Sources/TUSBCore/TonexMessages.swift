import Foundation

public enum PresetSelectPhase: UInt8, Sendable {
    case arm = 0
    case commit = 1
}

public struct ParameterChange: Equatable, Sendable {
    public let index: Int
    public let value: Float
}

public enum TonexMessages {
    public static let stateResponseType = 0x0306
    public static let presetDetailType = 0x0304
    public static let wakeResponseType = 0x0B2B
    public static let presetSelectType = 0x0300
    public static let parameterChangeType = 0x0309
    public static let presetBridgeStage = 0x0A
    public static let presetSettleStage = 0x01

    public static let wakePayload: [UInt8] =
        [0xB9, 0x03, 0x00, 0x82, 0x04, 0x00, 0x80, 0x0B, 0x01, 0xB9, 0x02, 0x02, 0x0B]
    public static let helloPayload: [UInt8] = [0xB9, 0x03, 0x81, 0x03, 0x00]
    public static let requestStatePayload: [UInt8] =
        [0xB9, 0x03, 0x00, 0x82, 0x06, 0x00, 0x80, 0x0B, 0x03, 0xB9, 0x02, 0x81, 0x06, 0x03, 0x0B]

    private static let presetSelectPrefix: [UInt8] =
        [0xB9, 0x03, 0x81, 0x00, 0x03, 0x82, 0x06, 0x00, 0x80, 0x0B, 0x03, 0xB9, 0x04, 0x0B, 0x01]
    private static let presetBridgePrefix: [UInt8] =
        [0xB9, 0x03, 0x00, 0x82, 0x06, 0x00, 0x80, 0x0B, 0x03, 0xB9, 0x02, 0x81]
    private static let setParameterHeader: [UInt8] =
        [0xB9, 0x03, 0x81, 0x09, 0x03, 0x82, 0x0A, 0x00, 0x80, 0x0B, 0x03]
    private static let parameterPrefix: [UInt8] = [0xB9, 0x04, 0x02, 0x00]
    private static let parameterBlockMarker: [UInt8] = [0xBA, 0x03, 0xBA, 0x6D]
    private static let stateResponseHeaderLength = 8

    public static func messageType(_ payload: [UInt8]) throws -> Int {
        try TaggedValue.require(payload, offset: 0, count: 5)
        return Int(payload[3]) | Int(payload[4]) << 8
    }

    public static func selectPresetPayload(presetID: Int, phase: PresetSelectPhase) throws -> [UInt8] {
        guard (0...255).contains(presetID) else {
            throw ProtocolDecodingError.invalid("preset ID must be in 0...255")
        }
        return presetSelectPrefix + [UInt8(presetID), phase.rawValue]
    }

    public static func selectPresetPayloads(presetID: Int) throws -> [[UInt8]] {
        [
            try selectPresetPayload(presetID: presetID, phase: .arm),
            try selectPresetPayload(presetID: presetID, phase: .commit),
        ]
    }

    public static func rawPresetSelectPayload(presetID: Int) throws -> [UInt8] {
        guard (0...255).contains(presetID) else {
            throw ProtocolDecodingError.invalid("preset ID must be in 0...255")
        }
        return [0xF0, UInt8(presetID), 0xF7, 0x05, 0x00, 0x01]
    }

    public static func presetBridgePayload(stage: Int) throws -> [UInt8] {
        guard (0...255).contains(stage) else {
            throw ProtocolDecodingError.invalid("preset bridge stage must be in 0...255")
        }
        return presetBridgePrefix + [UInt8(stage), 0x03, 0x0B]
    }

    public static func buildSetParameterPayload(index: Int, value: Float) throws -> [UInt8] {
        guard (0...255).contains(index), value.isFinite else {
            throw ProtocolDecodingError.invalid("parameter index or value is invalid")
        }
        return setParameterHeader + parameterPrefix + [UInt8(index)] + TaggedValue.encodeFloat(value)
    }

    public static func parseParameterChange(_ payload: [UInt8]) -> ParameterChange? {
        guard payload.count >= 5,
              Int(payload[3]) | Int(payload[4]) << 8 == parameterChangeType,
              let start = payload.firstRange(of: parameterPrefix)?.lowerBound else {
            return nil
        }
        let indexOffset = start + parameterPrefix.count
        guard payload.count - indexOffset >= 6, payload[indexOffset + 1] == 0x88,
              let decoded = try? TaggedValue.decodeFloat(payload, offset: indexOffset + 1),
              decoded.value.isFinite else {
            return nil
        }
        return ParameterChange(index: Int(payload[indexOffset]), value: decoded.value)
    }

    public static func parsePresetParameters(_ payload: [UInt8]) -> [Float] {
        guard let marker = payload.firstRange(of: parameterBlockMarker) else { return [] }
        var values: [Float] = []
        var offset = marker.upperBound
        while payload.count - offset >= 5, payload[offset] == 0x88 {
            guard let value = try? TaggedValue.decodeFloat(payload, offset: offset).value,
                  value.isFinite else {
                return []
            }
            values.append(value)
            offset += 5
        }
        return values
    }

    public static func parsePresetName(fromDetail payload: [UInt8]) throws -> String? {
        guard try messageType(payload) == presetDetailType else { return nil }
        guard payload.count >= 3 else { return nil }
        for index in 0..<(payload.count - 2) where payload[index] == 0xBC {
            let length = Int(payload[index + 1])
            let start = index + 2
            guard length > 0, payload.count - start >= length else { continue }
            let nameBytes = Array(payload[start..<(start + length)])
            guard nameBytes.allSatisfy({ (0x20...0x7E).contains($0) }) else { continue }
            let name = String(decoding: nameBytes, as: UTF8.self).trimmingCharacters(in: .whitespaces)
            if !name.isEmpty { return name }
        }
        return nil
    }

    public static func parseFirmware(_ response: [UInt8]) -> FirmwareInfo {
        var runs: [String] = []
        var current: [UInt8] = []
        for byte in response {
            if (0x20...0x7E).contains(byte) {
                current.append(byte)
            } else if !current.isEmpty {
                runs.append(String(decoding: current, as: UTF8.self))
                current.removeAll(keepingCapacity: true)
            }
        }
        if !current.isEmpty { runs.append(String(decoding: current, as: UTF8.self)) }
        let semantic = runs.compactMap { run -> String? in
            run.firstMatch(of: /\d+\.\d+(?:\.\d+)?/)?.output.description
        }.max { $0.count < $1.count }
        let fallback = runs.map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { $0.count >= 3 && $0.contains(where: \.isNumber) }
            .max { $0.count < $1.count }
        return FirmwareInfo(version: semantic ?? fallback ?? "ToneX One (versao nao mapeada)")
    }

    public static func parseState(_ payload: [UInt8], fieldsOffset: Int) throws -> PedalState {
        guard try messageType(payload) == stateResponseType else {
            throw ProtocolDecodingError.invalid("payload is not a state response")
        }
        let fields = try walkFields(payload, fieldsOffset: fieldsOffset)
        guard let activeSlot = Slot(rawValue: Int(fields.activeSlot)) else {
            throw ProtocolDecodingError.invalid("active slot is outside 0...2")
        }
        let slots = fields.colors.prefix(3).enumerated().map {
            PresetSlot(index: $0.offset, name: "Preset \(Character(UnicodeScalar(65 + $0.offset)!))", color: $0.element)
        }
        let library = fields.colors.prefix(20).enumerated().map {
            LibraryPreset(
                index: $0.offset,
                name: "Preset \(String(format: "%02d", $0.offset + 1))",
                color: $0.element
            )
        }
        return PedalState(
            activeSlot: activeSlot,
            inputTrim: fields.inputTrim,
            a4Reference: Int(fields.a4Reference),
            tempo: Int(fields.tempo),
            slots: slots,
            libraryPresets: library,
            presetIDs: fields.presetIDs,
            rawState: payload,
            pedalMode: fields.stompMode ? .stomp : .ab,
            cabSimBypass: fields.cabSimBypass,
            bypassMode: fields.bypassMode
        )
    }

    public static func presetID(for slot: Slot, in state: PedalState) -> Int? {
        state.presetIDs.indices.contains(slot.rawValue) ? state.presetIDs[slot.rawValue] : nil
    }

    public static func activeSlotOffset(_ rawState: [UInt8], fieldsOffset: Int) throws -> Int {
        guard try messageType(rawState) == stateResponseType else {
            throw ProtocolDecodingError.invalid("payload is not a state response")
        }
        return try walkFields(rawState, fieldsOffset: fieldsOffset).activeSlotOffset
    }

    public static func buildSetStatePayload(
        _ rawState: [UInt8],
        fieldsOffset: Int,
        newSlot: Slot
    ) throws -> [UInt8] {
        try buildSlotChangePayload(
            rawState,
            activeSlotOffset: activeSlotOffset(rawState, fieldsOffset: fieldsOffset),
            newSlotValue: newSlot.rawValue
        )
    }

    public static func buildSlotChangePayload(
        _ rawState: [UInt8],
        activeSlotOffset: Int,
        newSlotValue: Int,
        bypass: Bool = false
    ) throws -> [UInt8] {
        guard rawState.indices.contains(activeSlotOffset), (0...2).contains(newSlotValue) else {
            throw ProtocolDecodingError.invalid("active slot offset or value is invalid")
        }
        return try rebuildStateCommand(rawState) { body in
            let offset = activeSlotOffset - stateResponseHeaderLength
            guard body.indices.contains(offset) else {
                throw ProtocolDecodingError.invalid("active slot is outside state body")
            }
            body[offset] = UInt8(newSlotValue)
            if body.count > 12 { body[body.count - 12] = bypass ? 1 : 0 }
        }
    }

    public static func buildSetBypassPayload(
        _ rawState: [UInt8],
        fieldsOffset: Int,
        bypass: Bool
    ) throws -> [UInt8] {
        let offset = try activeSlotOffset(rawState, fieldsOffset: fieldsOffset)
        return try buildSlotChangePayload(
            rawState,
            activeSlotOffset: offset,
            newSlotValue: Int(rawState[offset]),
            bypass: bypass
        )
    }

    public static func buildSwitchModePayload(_ rawState: [UInt8], targetMode: PedalMode) throws -> [UInt8] {
        try rebuildStateCommand(rawState) { body in
            guard body.indices.contains(19) else {
                throw ProtocolDecodingError.invalid("state body has no stomp mode")
            }
            body[19] = targetMode == .stomp ? 1 : 0
            let slotOffset = body.count - 11
            if body.indices.contains(slotOffset) {
                if targetMode == .stomp {
                    body[slotOffset] = UInt8(Slot.c.rawValue)
                } else if body[slotOffset] == Slot.c.rawValue {
                    body[slotOffset] = UInt8(Slot.a.rawValue)
                }
            }
        }
    }

    public static func buildSetCabSimBypassPayload(_ rawState: [UInt8], bypass: Bool) throws -> [UInt8] {
        try rebuildStateCommand(rawState) { body in
            guard body.indices.contains(20) else {
                throw ProtocolDecodingError.invalid("state body has no cab bypass field")
            }
            body[20] = bypass ? 1 : 0
        }
    }

    public static func buildLoadPresetToSlotPayload(
        _ rawState: [UInt8],
        presetID: Int,
        slot: Slot,
        selectSlot: Bool
    ) throws -> [UInt8] {
        guard (0..<20).contains(presetID) else {
            throw ProtocolDecodingError.invalid("preset ID must be in 0..<20")
        }
        return try rebuildStateCommand(rawState) { body in
            guard body.indices.contains(19) else {
                throw ProtocolDecodingError.invalid("state body has no mode field")
            }
            body[19] = slot == .c ? 1 : 0
            if body.count > 12 { body[body.count - 12] = 0 }
            let endOffset = [18, 16, 14][slot.rawValue]
            let presetOffset = body.count - endOffset
            guard body.indices.contains(presetOffset) else {
                throw ProtocolDecodingError.invalid("state body has no preset field")
            }
            body[presetOffset] = UInt8(presetID)
            if body.indices.contains(presetOffset + 1) { body[presetOffset + 1] = 0 }
            if selectSlot {
                let activeOffset = body.count - 11
                guard body.indices.contains(activeOffset) else {
                    throw ProtocolDecodingError.invalid("state body has no active slot")
                }
                body[activeOffset] = UInt8(slot.rawValue)
            }
        }
    }

    private static func rebuildStateCommand(
        _ rawState: [UInt8],
        mutate: (inout [UInt8]) throws -> Void
    ) throws -> [UInt8] {
        guard rawState.count > stateResponseHeaderLength else {
            throw ProtocolDecodingError.invalid("state response is too short")
        }
        guard try messageType(rawState) == stateResponseType else {
            throw ProtocolDecodingError.invalid("payload is not a state response")
        }
        var body = Array(rawState.dropFirst(stateResponseHeaderLength))
        try mutate(&body)
        if body.count > 7 { body[body.count - 7] = 1 }
        let length = body.count
        guard length <= 0xFFFF else {
            throw ProtocolDecodingError.invalid("state body is too large")
        }
        return Array(rawState.prefix(5))
            + [0x82, UInt8(truncatingIfNeeded: length), UInt8(truncatingIfNeeded: length >> 8), 0x80, 0x0B, 0x03]
            + body
    }

    private struct StateFields {
        let inputTrim: Float
        let colors: [RGB]
        let activeSlot: UInt8
        let activeSlotOffset: Int
        let a4Reference: UInt16
        let tempo: Float
        let presetIDs: [Int]
        let stompMode: Bool
        let cabSimBypass: Bool
        let bypassMode: Bool
    }

    private static func walkFields(_ payload: [UInt8], fieldsOffset: Int) throws -> StateFields {
        let trim = try TaggedValue.decodeFloat(payload, offset: fieldsOffset)
        var offset = trim.nextOffset
        try TaggedValue.require(payload, offset: offset, count: 3)
        let stomp = payload[offset] != 0
        let cab = payload[offset + 1] != 0
        offset += 3

        try TaggedValue.require(payload, offset: offset, count: 2)
        guard payload[offset] == 0xBA else {
            throw ProtocolDecodingError.invalid("missing RGB collection at \(offset)")
        }
        let colorCount = Int(payload[offset + 1])
        offset += 2
        var colors: [RGB] = []
        colors.reserveCapacity(colorCount)
        for _ in 0..<colorCount {
            try TaggedValue.require(payload, offset: offset, count: 2)
            guard payload[offset] == 0xB9, payload[offset + 1] >= 3 else {
                throw ProtocolDecodingError.invalid("invalid RGB item at \(offset)")
            }
            let componentCount = Int(payload[offset + 1])
            offset += 2
            var components: [Int] = []
            components.reserveCapacity(componentCount)
            for _ in 0..<componentCount {
                try TaggedValue.require(payload, offset: offset, count: 1)
                if payload[offset] == 0x80 {
                    try TaggedValue.require(payload, offset: offset, count: 2)
                    components.append(Int(payload[offset + 1]))
                    offset += 2
                } else {
                    components.append(Int(payload[offset]))
                    offset += 1
                }
            }
            guard components.count >= 3 else {
                throw ProtocolDecodingError.invalid("RGB item has fewer than three components")
            }
            colors.append(RGB(red: components[0], green: components[1], blue: components[2]))
        }

        try TaggedValue.require(payload, offset: offset, count: 2)
        guard payload[offset] == 0xBC else {
            throw ProtocolDecodingError.invalid("missing slot collection at \(offset)")
        }
        let byteCount = Int(payload[offset + 1])
        guard byteCount.isMultiple(of: 2) else {
            throw ProtocolDecodingError.invalid("slot collection has odd byte count")
        }
        offset += 2
        try TaggedValue.require(payload, offset: offset, count: byteCount + 2)
        var presetIDs: [Int] = []
        for _ in 0..<(byteCount / 2) {
            presetIDs.append(Int(payload[offset]) | Int(payload[offset + 1]) << 8)
            offset += 2
        }
        offset += 1
        let activeSlotOffset = offset
        let activeSlot = payload[offset]
        offset += 1

        let a4 = try TaggedValue.decodeU16(payload, offset: offset)
        offset = a4.nextOffset + 2
        let tempo = try TaggedValue.decodeFloat(payload, offset: offset)
        guard trim.value.isFinite,
              tempo.value.isFinite,
              abs(tempo.value) <= 10_000 else {
            throw ProtocolDecodingError.invalid("state contains invalid floating-point values")
        }
        let body = payload.count >= stateResponseHeaderLength
            ? Array(payload.dropFirst(stateResponseHeaderLength))
            : []
        let bypass = body.count > 12 ? body[body.count - 12] != 0 : false
        return StateFields(
            inputTrim: trim.value,
            colors: colors,
            activeSlot: activeSlot,
            activeSlotOffset: activeSlotOffset,
            a4Reference: a4.value,
            tempo: tempo.value,
            presetIDs: presetIDs,
            stompMode: stomp,
            cabSimBypass: cab,
            bypassMode: bypass
        )
    }
}

private extension Array where Element: Equatable {
    func firstRange(of sequence: [Element]) -> Range<Int>? {
        guard !sequence.isEmpty, sequence.count <= count else { return nil }
        for start in 0...(count - sequence.count)
        where self[start..<(start + sequence.count)].elementsEqual(sequence) {
            return start..<(start + sequence.count)
        }
        return nil
    }
}
