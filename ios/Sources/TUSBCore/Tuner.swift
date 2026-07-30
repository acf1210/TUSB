import Foundation

public struct TuningNote: Equatable, Sendable {
    public let label: String
    public let frequencyHz: Double

    public init(label: String, frequencyHz: Double) {
        self.label = label
        self.frequencyHz = frequencyHz
    }
}

public enum TuningPreset: CaseIterable, Sendable {
    case standard, dropD, halfStepDown, dStandard, openG, dadgad

    public var notes: [TuningNote] {
        switch self {
        case .standard:
            notes(("E2", 82.41), ("A2", 110), ("D3", 146.83), ("G3", 196), ("B3", 246.94), ("E4", 329.63))
        case .dropD:
            notes(("D2", 73.42), ("A2", 110), ("D3", 146.83), ("G3", 196), ("B3", 246.94), ("E4", 329.63))
        case .halfStepDown:
            notes(("Eb2", 77.78), ("Ab2", 103.83), ("Db3", 138.59), ("Gb3", 185), ("Bb3", 233.08), ("Eb4", 311.13))
        case .dStandard:
            notes(("D2", 73.42), ("G2", 98), ("C3", 130.81), ("F3", 174.61), ("A3", 220), ("D4", 293.66))
        case .openG:
            notes(("D2", 73.42), ("G2", 98), ("D3", 146.83), ("G3", 196), ("B3", 246.94), ("D4", 293.66))
        case .dadgad:
            notes(("D2", 73.42), ("A2", 110), ("D3", 146.83), ("G3", 196), ("A3", 220), ("D4", 293.66))
        }
    }

    private func notes(_ values: (String, Double)...) -> [TuningNote] {
        values.map(TuningNote.init(label:frequencyHz:))
    }
}

public struct TunerReading: Equatable, Sendable {
    public let detectedFrequencyHz: Double
    public let target: TuningNote
    public let cents: Double
    public var isInTune: Bool { abs(cents) <= 5 }
}

public enum TunerError: Error, Equatable, Sendable {
    case invalidFrequency
    case noNotes
}

public func centsOff(_ frequencyHz: Double, targetFrequencyHz: Double) -> Double {
    1200 * log2(frequencyHz / targetFrequencyHz)
}

public func nearestTarget(_ frequencyHz: Double, notes: [TuningNote]) throws -> TuningNote {
    guard frequencyHz > 0 else { throw TunerError.invalidFrequency }
    guard let note = notes.min(by: {
        abs(centsOff(frequencyHz, targetFrequencyHz: $0.frequencyHz))
            < abs(centsOff(frequencyHz, targetFrequencyHz: $1.frequencyHz))
    }) else {
        throw TunerError.noNotes
    }
    return note
}

public func tunerReading(
    frequencyHz: Double,
    preset: TuningPreset,
    selectedStringIndex: Int? = nil
) throws -> TunerReading {
    guard frequencyHz > 0 else { throw TunerError.invalidFrequency }
    let notes = preset.notes
    let target: TuningNote
    if let selectedStringIndex, notes.indices.contains(selectedStringIndex) {
        target = notes[selectedStringIndex]
    } else {
        target = try nearestTarget(frequencyHz, notes: notes)
    }
    return TunerReading(
        detectedFrequencyHz: frequencyHz,
        target: target,
        cents: centsOff(frequencyHz, targetFrequencyHz: target.frequencyHz)
    )
}

public func detectPitchHz(
    samples: [Int16],
    sampleRate: Int,
    minFrequencyHz: Double = 60,
    maxFrequencyHz: Double = 1_200
) -> Double? {
    guard !samples.isEmpty, sampleRate > 0, minFrequencyHz > 0, maxFrequencyHz > minFrequencyHz else {
        return nil
    }
    let normalized = samples.map { Float($0) / Float(Int16.max) }
    let rms = sqrt(normalized.reduce(0) { $0 + Double($1 * $1) } / Double(samples.count))
    guard rms >= 0.01 else { return nil }

    let minLag = max(Int(Double(sampleRate) / maxFrequencyHz), 1)
    let maxLag = min(Int(Double(sampleRate) / minFrequencyHz), samples.count - 1)
    guard minLag <= maxLag else { return nil }
    var correlations = [Double](repeating: 0, count: maxLag + 1)
    var bestLag = 0
    var bestCorrelation = 0.0
    for lag in minLag...maxLag {
        var correlation = 0.0
        var leftEnergy = 0.0
        var rightEnergy = 0.0
        for index in 0..<(samples.count - lag) {
            let left = Double(normalized[index])
            let right = Double(normalized[index + lag])
            correlation += left * right
            leftEnergy += left * left
            rightEnergy += right * right
        }
        let normalizedCorrelation = correlation / sqrt(leftEnergy * rightEnergy + 1e-12)
        correlations[lag] = normalizedCorrelation
        if normalizedCorrelation > bestCorrelation {
            bestCorrelation = normalizedCorrelation
            bestLag = lag
        }
    }
    guard bestLag > 0, bestCorrelation >= 0.45 else { return nil }
    let earlyPeak = minLag + 1 < maxLag
        ? ((minLag + 1)..<maxLag).first {
            correlations[$0] >= bestCorrelation * 0.92
                && correlations[$0] >= correlations[$0 - 1]
                && correlations[$0] >= correlations[$0 + 1]
        }
        : nil
    return Double(sampleRate) / Double(earlyPeak ?? bestLag)
}

public func generateSineSamples(
    frequencyHz: Double,
    sampleRate: Int,
    count: Int,
    amplitude: Double = 0.8
) -> [Int16] {
    guard frequencyHz > 0, sampleRate > 0, count > 0 else { return [] }
    return (0..<count).map { index in
        let value = sin(2 * Double.pi * frequencyHz * Double(index) / Double(sampleRate))
        return Int16(clamping: Int(value * min(max(amplitude, 0), 1) * Double(Int16.max)))
    }
}
