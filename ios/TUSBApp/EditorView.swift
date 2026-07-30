import SwiftUI

struct EditorView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                HStack {
                    VStack(alignment: .leading) {
                        Text(model.activeSlot.rawValue + " · " + model.activePreset.name)
                            .font(.title3.bold()).lineLimit(1)
                        Text(model.activePreset.rig).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Toggle("preset.bypass", isOn: $model.bypass).labelsHidden()
                        .accessibilityLabel("preset.bypass")
                        .accessibilityIdentifier("editor.bypass")
                }
                .padding(.horizontal)

                TUSBCard {
                    Label("editor.amp", systemImage: "guitars").font(.headline)
                    ForEach(["GAIN", "BASS", "MID", "TREBLE", "VOLUME"], id: \.self) { name in
                        HStack {
                            Text(name).font(.caption.monospaced()).frame(width: 64, alignment: .leading)
                            Slider(value: ampBinding(name))
                                .accessibilityLabel(name)
                                .accessibilityIdentifier("amp.\(name.lowercased())")
                            Text((model.ampValues[name] ?? 0).formatted(.number.precision(.fractionLength(1))))
                                .font(.caption.monospacedDigit()).frame(width: 32)
                        }
                    }
                }
                .padding(.horizontal)

                VStack(alignment: .leading, spacing: 10) {
                    Text("editor.chain").font(.headline).padding(.horizontal)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(model.effects) { effect in
                                Button {
                                    model.selectedEffect = effect
                                } label: {
                                    VStack(spacing: 10) {
                                        Image(systemName: effect.symbol).font(.title2)
                                        Text(effect.id).font(.caption2.monospaced())
                                        Circle()
                                            .fill(effect.enabled ? Color.green : Color.secondary)
                                            .frame(width: 9, height: 9)
                                    }
                                    .frame(width: 78, height: 88)
                                }
                                .buttonStyle(.bordered)
                                .opacity(effect.enabled ? 1 : 0.55)
                                .accessibilityLabel(effect.id)
                                .accessibilityIdentifier("effect.\(effect.id.lowercased())")
                            }
                        }
                        .padding(.horizontal)
                    }
                    Text("editor.chain.hint").font(.caption).foregroundStyle(.secondary).padding(.horizontal)
                }
            }
            .padding(.vertical)
        }
        .navigationTitle("nav.editor")
        .sheet(item: $model.selectedEffect) { effect in
            EffectDetailView(effect: effect)
        }
    }

    private func ampBinding(_ name: String) -> Binding<Double> {
        Binding(
            get: { model.ampValues[name] ?? 0 },
            set: { model.setAmp(name, normalizedValue: $0) }
        )
    }
}

private struct EffectDetailView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let effect: EffectBlock

    var current: EffectBlock {
        model.effects.first { $0.id == effect.id } ?? effect
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle("effect.active", isOn: Binding(
                        get: { current.enabled },
                        set: { _ in model.toggle(current) }
                    ))
                    Slider(value: Binding(
                        get: { current.mix },
                        set: { model.setMix($0, for: current) }
                    ), in: 0...1)
                    .accessibilityLabel("effect.mix")
                    LabeledContent("effect.value", value: current.mix.formatted(.percent.precision(.fractionLength(0))))
                } header: {
                    Label(effect.id, systemImage: effect.symbol)
                } footer: {
                    Text("effect.demo.note")
                }
            }
            .navigationTitle(effect.id)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium])
    }
}
