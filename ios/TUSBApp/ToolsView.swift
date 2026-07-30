import AVFoundation
import AudioToolbox
import SwiftUI

struct ToolsView: View {
    @EnvironmentObject private var model: AppModel
    @StateObject private var metronome = MetronomeSource()
    @StateObject private var tuner = TunerSource()

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                TUSBCard {
                    Label("tools.metronome", systemImage: "metronome").font(.headline)
                    HStack {
                        Button { metronome.bpm = max(30, metronome.bpm - 1) } label: {
                            Image(systemName: "minus.circle")
                        }.accessibilityLabel("bpm.decrease")
                        Spacer()
                        Text("\(metronome.bpm) BPM").font(.system(size: 32, weight: .bold, design: .monospaced))
                        Spacer()
                        Button { metronome.bpm = min(240, metronome.bpm + 1) } label: {
                            Image(systemName: "plus.circle")
                        }.accessibilityLabel("bpm.increase")
                    }
                    Slider(value: Binding(
                        get: { Double(metronome.bpm) },
                        set: { metronome.bpm = Int($0) }
                    ), in: 30...240, step: 1)
                    Button {
                        metronome.toggle()
                    } label: {
                        Label {
                            Text(LocalizedStringKey(metronome.running ? "common.stop" : "common.play"))
                        } icon: {
                            Image(systemName: metronome.running ? "stop.fill" : "play.fill")
                        }
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("metronome.toggle")
                }

                TUSBCard {
                    Label("tools.tuner", systemImage: "tuningfork").font(.headline)
                    VStack(spacing: 4) {
                        Text(tuner.note).font(.system(size: 54, weight: .bold, design: .rounded))
                        Text(tuner.frequency > 0 ? "\(tuner.frequency, specifier: "%.1f") Hz" : "— Hz")
                            .font(.title3.monospacedDigit())
                        Gauge(value: tuner.cents, in: -50...50) {
                            Text("tools.tuner.offset")
                        } currentValueLabel: {
                            Text("\(Int(tuner.cents)) ¢").font(.caption.monospacedDigit())
                        }
                        .tint(abs(tuner.cents) < 5 ? .green : .orange)
                    }
                    Button {
                        tuner.toggle()
                    } label: {
                        Label {
                            Text(LocalizedStringKey(tuner.listening ? "common.stop" : "tuner.start"))
                        } icon: {
                            Image(systemName: tuner.listening ? "mic.slash" : "mic")
                        }
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("tuner.toggle")
                    if let error = tuner.error {
                        Text(error).font(.caption).foregroundStyle(.red)
                    }
                    Text("tuner.note").font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding()
        }
        .navigationTitle("nav.tools")
        .onAppear { tuner.a4Reference = Double(model.a4Reference) }
        .onChange(of: model.a4Reference) { _, value in
            tuner.a4Reference = Double(value)
        }
        .onDisappear {
            metronome.stop()
            tuner.stop()
        }
    }
}

@MainActor
final class MetronomeSource: ObservableObject {
    @Published var bpm = 120 {
        didSet { if running { restart() } }
    }
    @Published private(set) var running = false
    private var timer: Timer?

    deinit {
        timer?.invalidate()
    }

    func toggle() {
        running ? stop() : start()
    }

    private func start() {
        running = true
        playClick()
        timer = .scheduledTimer(withTimeInterval: 60 / Double(bpm), repeats: true) { _ in
            AudioServicesPlaySystemSound(1104)
        }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
        running = false
    }

    private func restart() {
        stop()
        start()
    }

    private func playClick() {
        AudioServicesPlaySystemSound(1104)
    }
}

@MainActor
final class TunerSource: ObservableObject {
    @Published private(set) var listening = false
    @Published private(set) var frequency = 0.0
    @Published private(set) var note = "—"
    @Published private(set) var cents = 0.0
    @Published private(set) var error: String?
    var a4Reference = 440.0

    private let engine = AVAudioEngine()
    private var demoTimer: Timer?
    private var tapInstalled = false
    private var startTask: Task<Void, Never>?

    func toggle() {
        if listening || startTask != nil {
            stop()
        } else {
            startTask = Task { [weak self] in
                guard let self else { return }
                await start()
                startTask = nil
            }
        }
    }

    private func start() async {
        #if targetEnvironment(simulator)
        guard !Task.isCancelled else { return }
        listening = true
        var index = 0
        let tones = [82.41, 110.0, 146.83, 196.0, 246.94, 329.63]
        update(tones[index])
        demoTimer = .scheduledTimer(withTimeInterval: 1.2, repeats: true) { [weak self] _ in
            index = (index + 1) % tones.count
            Task { @MainActor in self?.update(tones[index]) }
        }
        #else
        guard await AVAudioApplication.requestRecordPermission() else {
            error = String(localized: "tuner.permission")
            return
        }
        guard !Task.isCancelled else { return }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement)
            try session.setActive(true)
            let input = engine.inputNode
            let format = input.outputFormat(forBus: 0)
            input.installTap(onBus: 0, bufferSize: 2048, format: format) { [weak self] buffer, _ in
                guard let channel = buffer.floatChannelData?[0] else { return }
                let value = Self.estimatePitch(channel, count: Int(buffer.frameLength), sampleRate: format.sampleRate)
                guard value > 0 else { return }
                Task { @MainActor in self?.update(value) }
            }
            tapInstalled = true
            try engine.start()
            listening = true
            error = nil
        } catch {
            if tapInstalled {
                engine.inputNode.removeTap(onBus: 0)
                tapInstalled = false
            }
            try? AVAudioSession.sharedInstance().setActive(false)
            self.error = error.localizedDescription
        }
        #endif
    }

    func stop() {
        startTask?.cancel()
        startTask = nil
        demoTimer?.invalidate()
        demoTimer = nil
        #if !targetEnvironment(simulator)
        if tapInstalled {
            engine.inputNode.removeTap(onBus: 0)
            tapInstalled = false
        }
        engine.stop()
        try? AVAudioSession.sharedInstance().setActive(false)
        #endif
        listening = false
    }

    private func update(_ value: Double) {
        frequency = value
        let midi = 69 + 12 * log2(value / a4Reference)
        let nearest = midi.rounded()
        let names = ["C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"]
        note = names[(Int(nearest) % 12 + 12) % 12]
        cents = (midi - nearest) * 100
    }

    nonisolated private static func estimatePitch(
        _ samples: UnsafePointer<Float>,
        count: Int,
        sampleRate: Double
    ) -> Double {
        guard count > 1 else { return 0 }
        let minLag = max(1, Int(sampleRate / 1_200))
        let maxLag = min(count / 2, Int(sampleRate / 55))
        guard minLag < maxLag else { return 0 }
        var bestLag = 0
        var bestScore: Float = 0
        for lag in minLag...maxLag {
            var score: Float = 0
            for index in 0..<(count - lag) {
                score += samples[index] * samples[index + lag]
            }
            if score > bestScore {
                bestScore = score
                bestLag = lag
            }
        }
        return bestLag == 0 ? 0 : sampleRate / Double(bestLag)
    }
}
