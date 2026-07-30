import XCTest
@testable import TUSBCore

final class ProtocolCoreTests: XCTestCase {
    func testCRC16X25GoldenVectors() {
        XCTAssertEqual(CRC16X25.compute([]), 0x0000)
        XCTAssertEqual(CRC16X25.compute(Array("123456789".utf8)), 0x906E)
        XCTAssertEqual(CRC16X25.compute([0x41]), 0xA3F5)
    }

    func testHDLCOfficialPresetFramesAndErrors() throws {
        let arm = try TonexMessages.selectPresetPayload(presetID: 0x0C, phase: .arm)
        XCTAssertEqual(
            HDLC.encode(arm),
            hex("7E B9 03 81 00 03 82 06 00 80 0B 03 B9 04 0B 01 0C 00 68 8E 7E")
        )
        let escaped: [UInt8] = [0x10, 0x7E, 0x7D, 0x20]
        XCTAssertEqual(HDLC.decode(HDLC.encode(escaped)), .valid(escaped))
        XCTAssertEqual(HDLC.decode([0x7E, 0x01]), .incomplete)
        XCTAssertEqual(HDLC.decode([0x7E, 0x01, 0x00, 0x00, 0x7E]), .crcError)
    }

    func testTaggedValuesRoundTripAndValidateBounds() throws {
        XCTAssertEqual(TaggedValue.encodeU16(6, tag: 0x81), [0x81, 0x06, 0x00])
        XCTAssertEqual(try TaggedValue.decodeU16([0x81, 0x06, 0x00]), .init(value: 6, nextOffset: 3))
        let encoded = TaggedValue.encodeFloat(0.75)
        XCTAssertEqual(encoded.first, 0x88)
        XCTAssertEqual(try TaggedValue.decodeFloat(encoded).value, 0.75)
        XCTAssertThrowsError(try TaggedValue.decodeFloat([0x88, 0x00]))
    }

    func testEssentialMessageGoldenVectors() throws {
        XCTAssertEqual(TonexMessages.wakePayload, hex("B9 03 00 82 04 00 80 0B 01 B9 02 02 0B"))
        XCTAssertEqual(TonexMessages.helloPayload, hex("B9 03 81 03 00"))
        XCTAssertEqual(
            TonexMessages.requestStatePayload,
            hex("B9 03 00 82 06 00 80 0B 03 B9 02 81 06 03 0B")
        )
        XCTAssertEqual(
            try TonexMessages.buildSetParameterPayload(index: 21, value: 8.2),
            hex("B9 03 81 09 03 82 0A 00 80 0B 03 B9 04 02 00 15 88 33 33 03 41")
        )
        let change = try XCTUnwrap(TonexMessages.parseParameterChange(
            hex("B9 03 81 09 03 0A 02 B9 04 02 00 15 88 33 33 03 41")
        ))
        XCTAssertEqual(change.index, 21)
        XCTAssertEqual(change.value, 8.2, accuracy: 0.0001)
    }

    func testPresetDetailAndStateParsing() throws {
        let detail = hex("B9 03 81 04 03 00 BA 03 BA 6D 88 00 00 80 3F 88 33 33 03 41 00")
        XCTAssertEqual(TonexMessages.parsePresetParameters(detail), [1.0, 8.2])
        let named = hex("B9 03 81 04 03 00 00 BC 0D") + Array("Crunch Deluxe".utf8)
        XCTAssertEqual(try TonexMessages.parsePresetName(fromDetail: named), "Crunch Deluxe")

        let payload = syntheticState(activeSlot: 1)
        let state = try TonexMessages.parseState(payload, fieldsOffset: 22)
        XCTAssertEqual(state.activeSlot, .b)
        XCTAssertEqual(state.inputTrim, 1.5)
        XCTAssertEqual(state.a4Reference, 440)
        XCTAssertEqual(state.tempo, 120)
        XCTAssertEqual(state.slots.map(\.color), [
            RGB(red: 255, green: 0, blue: 0),
            RGB(red: 0, green: 255, blue: 0),
            RGB(red: 0, green: 0, blue: 255),
        ])
        XCTAssertEqual(state.presetIDs, [0x0C, 0x08, 0x07])
        XCTAssertEqual(state.pedalMode, .ab)
        XCTAssertTrue(state.cabSimBypass)
        XCTAssertEqual(state.rawState, payload)
    }

    func testStateCommandsPreserveBodyAndMutateRequestedFields() throws {
        let state = syntheticState(activeSlot: 1)
        let switched = try TonexMessages.buildSetStatePayload(state, fieldsOffset: 22, newSlot: .c)
        XCTAssertEqual(switched.count, state.count + 3)
        XCTAssertEqual(switched[0..<5], state[0..<5])
        XCTAssertEqual(switched[switched.count - 11], 2)

        let stomp = try TonexMessages.buildSwitchModePayload(state, targetMode: .stomp)
        XCTAssertEqual(stomp[11 + 19], 1)
        XCTAssertEqual(stomp[stomp.count - 11], 2)

        let cab = try TonexMessages.buildSetCabSimBypassPayload(state, bypass: false)
        XCTAssertEqual(cab[11 + 20], 0)
    }

    func testAdditionalMessageBuildersAndFirmwareParsing() throws {
        XCTAssertEqual(try TonexMessages.rawPresetSelectPayload(presetID: 13), hex("F0 0D F7 05 00 01"))
        XCTAssertEqual(
            try TonexMessages.presetBridgePayload(stage: TonexMessages.presetBridgeStage),
            hex("B9 03 00 82 06 00 80 0B 03 B9 02 81 0A 03 0B")
        )
        XCTAssertEqual(try TonexMessages.selectPresetPayloads(presetID: 7).map(\.last), [0, 1])
        XCTAssertThrowsError(try TonexMessages.selectPresetPayload(presetID: 256, phase: .arm))
        XCTAssertThrowsError(try TonexMessages.buildSetParameterPayload(index: -1, value: 1))
        XCTAssertThrowsError(try TonexMessages.buildSetParameterPayload(index: 21, value: .nan))
        XCTAssertThrowsError(try TonexMessages.buildSetParameterPayload(index: 21, value: .infinity))
        XCTAssertEqual(TonexMessages.parseFirmware([0x81, 0x0A, 0] + Array("1.2.3".utf8)).version, "1.2.3")
        XCTAssertEqual(
            TonexMessages.parseFirmware(hex("B9 03 81 06 03 00 4C 42 4C 47")).version,
            "ToneX One (versao nao mapeada)"
        )
    }

    func testAdditionalStateCommandsAndValidation() throws {
        let state = syntheticState(activeSlot: 1)
        let bypassed = try TonexMessages.buildSetBypassPayload(state, fieldsOffset: 22, bypass: true)
        XCTAssertEqual(bypassed[bypassed.count - 12], 1)
        let loaded = try TonexMessages.buildLoadPresetToSlotPayload(
            state,
            presetID: 3,
            slot: .c,
            selectSlot: true
        )
        XCTAssertEqual(loaded[11 + 19], 1)
        XCTAssertEqual(loaded[loaded.count - 14], 3)
        XCTAssertEqual(loaded[loaded.count - 11], 2)
        XCTAssertThrowsError(
            try TonexMessages.buildLoadPresetToSlotPayload(state, presetID: 20, slot: .a, selectSlot: false)
        )
        XCTAssertThrowsError(try TonexMessages.messageType([]))
        XCTAssertNil(TonexMessages.parseParameterChange(hex("B9 03 81 06 03 00")))
        XCTAssertThrowsError(try TonexMessages.parseState(syntheticState(activeSlot: 3), fieldsOffset: 22))
        XCTAssertThrowsError(
            try TonexMessages.buildSlotChangePayload(state, activeSlotOffset: state.count - 11, newSlotValue: 3)
        )
        var wrongType = state
        wrongType[3] = 0x04
        XCTAssertThrowsError(try TonexMessages.parseState(wrongType, fieldsOffset: 22))
        XCTAssertThrowsError(try TonexMessages.buildSwitchModePayload(wrongType, targetMode: .stomp))
    }

    func testExternalFloatsRejectNonFiniteValues() {
        let nanChange = hex("B9 03 81 09 03 0A 02 B9 04 02 00 15") + TaggedValue.encodeFloat(.nan)
        XCTAssertNil(TonexMessages.parseParameterChange(nanChange))

        let nanParameters = hex("B9 03 81 04 03 00 BA 6D") + TaggedValue.encodeFloat(.nan)
        XCTAssertTrue(TonexMessages.parsePresetParameters(nanParameters).isEmpty)

        var state = syntheticState(activeSlot: 1)
        state.replaceSubrange(22..<27, with: TaggedValue.encodeFloat(.infinity))
        XCTAssertThrowsError(try TonexMessages.parseState(state, fieldsOffset: 22))

        state = syntheticState(activeSlot: 1)
        state.replaceSubrange((state.count - 5)..<state.count, with: TaggedValue.encodeFloat(.nan))
        XCTAssertThrowsError(try TonexMessages.parseState(state, fieldsOffset: 22))
    }
}

private func hex(_ text: String) -> [UInt8] {
    text.split(separator: " ").compactMap { UInt8($0, radix: 16) }
}

private func encodedColor(_ red: Int, _ green: Int, _ blue: Int) -> [UInt8] {
    func component(_ value: Int) -> [UInt8] {
        value >= 0x80 ? [0x80, UInt8(value)] : [UInt8(value)]
    }
    return [0xB9, 3] + component(red) + component(green) + component(blue)
}

private func syntheticState(activeSlot: UInt8) -> [UInt8] {
    var payload = [UInt8](repeating: 0, count: 22)
    payload[3] = 0x06
    payload[4] = 0x03
    payload += TaggedValue.encodeFloat(1.5)
    payload += [0, 1, 0, 0xBA, 3]
    payload += encodedColor(255, 0, 0)
    payload += encodedColor(0, 255, 0)
    payload += encodedColor(0, 0, 255)
    payload += [0xBC, 6, 0x0C, 0, 0x08, 0, 0x07, 0, 0, activeSlot]
    payload += TaggedValue.encodeU16(440, tag: 0x81)
    payload += [0, 0]
    payload += TaggedValue.encodeFloat(120)
    return payload
}
