import XCTest
@testable import TUSBCore

final class MidiTunerTests: XCTestCase {
    func testMIDIParserSupportsRunningStatusFragmentationAndRealtime() {
        let parser = MIDIParser()
        XCTAssertEqual(
            parser.parse([0xB0, 20, 127, 21, 0]),
            [.controlChange(channel: 0, controller: 20, value: 127),
             .controlChange(channel: 0, controller: 21, value: 0)]
        )
        XCTAssertTrue(parser.parse([0xB1, 25]).isEmpty)
        XCTAssertEqual(
            parser.parse([0xF8, 127]),
            [.controlChange(channel: 1, controller: 25, value: 127)]
        )
        XCTAssertEqual(
            MIDIParser().parse([0xF0, 0x7E, 0x7F, 0xF7, 0xC0, 3]),
            [.programChange(channel: 0, program: 3)]
        )
    }

    func testMIDIMappingLearningAndCodec() {
        let learned = MIDIMapping.default.withLearned(.toggleBypass, key: 40)
        XCTAssertEqual(learned.action(for: 40), .toggleBypass)
        XCTAssertNil(learned.action(for: 25))
        XCTAssertEqual(MIDIMappingCodec.decode(MIDIMappingCodec.encode(learned)), learned)
        XCTAssertEqual(MIDIMappingCodec.decode("garbage"), .default)
        XCTAssertEqual(midiMappingLabel(programChangeKey(3)), "PC 3")
    }

    func testMIDIDispatcherHandlesMappedAndContinuousActions() {
        let handler = RecordingMIDIHandler()
        let dispatcher = MIDICommandDispatcher(handler: handler)
        dispatcher.dispatch(.controlChange(channel: 0, controller: 25, value: 63))
        dispatcher.dispatch(.controlChange(channel: 0, controller: 25, value: 64))
        dispatcher.dispatch(.controlChange(channel: 0, controller: 105, value: 127))
        dispatcher.dispatch(.programChange(channel: 0, program: 7))
        XCTAssertEqual(handler.calls, ["bypass", "knob:gain:1.0", "preset:7"])

        dispatcher.startLearn(.toggleCab)
        dispatcher.dispatch(.controlChange(channel: 0, controller: 45, value: 127))
        XCTAssertEqual(dispatcher.learned, .init(action: .toggleCab, key: 45))
    }

    func testTunerGoldenReadingsAndPitchDetection() throws {
        XCTAssertEqual(TuningPreset.dropD.notes.first?.label, "D2")
        XCTAssertEqual(TuningPreset.dropD.notes.first?.frequencyHz ?? 0, 73.42, accuracy: 0.01)
        let reading = try tunerReading(frequencyHz: 110.2, preset: .standard)
        XCTAssertEqual(reading.target.label, "A2")
        XCTAssertLessThan(abs(reading.cents), 4)

        let samples = generateSineSamples(frequencyHz: 146.83, sampleRate: 44_100, count: 4096)
        XCTAssertEqual(try XCTUnwrap(detectPitchHz(samples: samples, sampleRate: 44_100)), 146.83, accuracy: 2)
        XCTAssertNil(detectPitchHz(samples: [Int16](repeating: 0, count: 4096), sampleRate: 44_100))
    }

    func testMIDIParserBoundsAndDispatcherBranches() {
        let parser = MIDIParser()
        XCTAssertEqual(
            parser.parse([0, 0xC2, 9, 0], offset: 1, count: 2),
            [.programChange(channel: 2, program: 9)]
        )
        XCTAssertTrue(parser.parse([20, 127], offset: -1).isEmpty)

        let handler = RecordingMIDIHandler()
        let dispatcher = MIDICommandDispatcher(handler: handler)
        for controller in [20, 21, 22, 26, 27, 28, 29, 30, 31, 32] {
            dispatcher.dispatch(.controlChange(channel: 0, controller: controller, value: 127))
        }
        XCTAssertEqual(handler.calls.count, 10)
        dispatcher.cancelLearn()
    }

    func testTunerValidationAndOtherPresets() {
        XCTAssertThrowsError(try nearestTarget(440, notes: []))
        XCTAssertThrowsError(try tunerReading(frequencyHz: 0, preset: .standard))
        XCTAssertEqual(TuningPreset.allCases.map(\.notes.count), [6, 6, 6, 6, 6, 6])
        XCTAssertTrue(generateSineSamples(frequencyHz: 0, sampleRate: 44_100, count: 10).isEmpty)
        XCTAssertNil(detectPitchHz(samples: [1], sampleRate: 0))
    }
}

private final class RecordingMIDIHandler: MIDIActionHandling {
    var calls: [String] = []
    var activePresetID: Int? = 4

    func selectSlot(_ slot: Slot) { calls.append("slot:\(slot)") }
    func loadPreset(_ presetID: Int) { calls.append("preset:\(presetID)") }
    func toggleBypass() { calls.append("bypass") }
    func toggleCab() { calls.append("cab") }
    func toggleEffect(_ effect: EffectBlock) { calls.append("effect:\(effect)") }
    func setAmpKnob(_ knob: AmpKnob, normalized: Float) {
        calls.append("knob:\(knob):\(normalized)")
    }
}
