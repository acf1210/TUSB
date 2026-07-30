import XCTest
@testable import TUSBCore

final class PedalModelTests: XCTestCase {
    func testParameterNormalizationAndImmutableStateMutations() {
        XCTAssertEqual(TonexParameter.modelVolume.denormalize(0.82), 8.2, accuracy: 0.0001)
        XCTAssertEqual(TonexParameter.modelVolume.normalize(8.2), 0.82, accuracy: 0.0001)

        let original = PedalState.simulated
        let updated = original
            .withActiveSlot(.b)
            .withPedalMode(.stomp)
            .withCabSimBypass(true)
            .withActivePresetName("Crunch Deluxe")
        XCTAssertEqual(original.activeSlot, .a)
        XCTAssertEqual(updated.activeSlot, .b)
        XCTAssertEqual(updated.pedalMode, .stomp)
        XCTAssertTrue(updated.cabSimBypass)
        XCTAssertEqual(updated.slots[1].name, "Crunch Deluxe")
    }

    func testFakeControllerSupportsSimulatorFlow() throws {
        let controller = FakePedalController()
        XCTAssertEqual(controller.handshake().firmware.version, "SIM-1.0.0")
        controller.select(slot: .c)
        controller.setParameter(index: TonexParameter.modelVolume.index, value: 8.2)
        XCTAssertEqual(controller.state.activeSlot, .c)
        XCTAssertEqual(controller.state.parameterValue(.modelVolume), 8.2)
        XCTAssertEqual(controller.parameterWriteCounts[TonexParameter.modelVolume.index], 1)
        XCTAssertThrowsError(try controller.loadPreset(20, into: .a, select: true))
    }

    func testModelDerivedValuesAndRawStateMutations() {
        var values = [Float](repeating: 0, count: 109)
        values[TonexParameter.modelAmpEnable.index] = 1
        values[TonexParameter.cabinetType.index] = 1
        values[TonexParameter.virCabinetModel.index] = 3
        let state = PedalState(
            activeSlot: .a,
            inputTrim: 0,
            a4Reference: 440,
            tempo: 120,
            slots: PedalState.simulated.slots,
            rawState: [UInt8](repeating: 0, count: 40)
        ).withActivePresetParameters(values)

        XCTAssertEqual(state.rigModels(), RigModels(ampEnabled: true, cabinetType: .vir, virCabinetModel: 3))
        XCTAssertEqual(state.rigModels().cabinetLabel(cabSimBypass: false), "VIR 4")
        XCTAssertEqual(state.rigModels().cabinetLabel(cabSimBypass: true), "OFF")
        XCTAssertEqual(state.withPedalMode(.stomp).rawState[27], 1)
        XCTAssertEqual(state.withCabSimBypass(true).rawState[28], 1)
        XCTAssertEqual(state.withBypassMode(true).rawState[28], 1)
        XCTAssertEqual(state.withPresetIDs([1, 2, 3]).presetIDs, [1, 2, 3])
        for slot in Slot.allCases {
            let loaded = state.withPresetInSlot(4 + slot.rawValue, slot: slot, selectSlot: true)
            let endOffset = [18, 16, 14][slot.rawValue]
            XCTAssertEqual(loaded.rawState[loaded.rawState.count - endOffset], UInt8(4 + slot.rawValue))
            XCTAssertEqual(loaded.rawState[loaded.rawState.count - 11], UInt8(slot.rawValue))
        }

        values[TonexParameter.cabinetType.index] = .nan
        values[TonexParameter.virCabinetModel.index] = .infinity
        XCTAssertEqual(
            state.withActivePresetParameters(values).rigModels(),
            RigModels(ampEnabled: true, cabinetType: nil, virCabinetModel: nil)
        )
    }

    func testFakeControllerAdditionalSimulatorActions() throws {
        let controller = FakePedalController()
        controller.connect()
        XCTAssertTrue(controller.isConnected)
        controller.select(presetID: 0x08)
        XCTAssertEqual(controller.state.activeSlot, .b)
        controller.setMode(.stomp)
        controller.setCabSimBypass(true)
        controller.setBypass(true)
        XCTAssertEqual(controller.state.pedalMode, .stomp)
        XCTAssertTrue(controller.state.cabSimBypass)
        XCTAssertTrue(controller.state.bypassMode)
        XCTAssertFalse(controller.setParameter(index: -1, value: .nan))
        try controller.loadPreset(3, into: .a, select: true)
        XCTAssertEqual(controller.state.presetIDs[0], 3)
        controller.disconnect()
        XCTAssertFalse(controller.isConnected)
    }
}
