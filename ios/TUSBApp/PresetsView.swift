import SwiftUI

struct PresetsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var query = ""

    private var filtered: [Preset] {
        query.isEmpty
            ? model.presets
            : model.presets.filter { $0.name.localizedCaseInsensitiveContains(query) || $0.rig.localizedCaseInsensitiveContains(query) }
    }

    var body: some View {
        VStack(spacing: 12) {
            TUSBCard {
                Picker("preset.mode", selection: $model.mode) {
                    ForEach(PedalMode.allCases) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)
                HStack {
                    ForEach(PedalSlot.allCases) { slot in
                        Button {
                            model.activeSlot = slot
                        } label: {
                            VStack {
                                Text(slot.rawValue).font(.title3.bold())
                                Text(model.slotPresets[slot]?.name ?? "—").font(.caption2).lineLimit(1)
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                        .tint(slot == model.activeSlot ? model.theme.accent : .secondary)
                        .disabled(model.mode == .ab && slot == .c)
                        .accessibilityIdentifier("slot.\(slot.rawValue.lowercased())")
                    }
                }
                Toggle("preset.bypass", isOn: $model.bypass)
                    .accessibilityIdentifier("presets.bypass")
            }
            .padding(.horizontal)

            List(filtered) { preset in
                Button {
                    model.load(preset)
                } label: {
                    HStack {
                        Text(preset.id.formatted()).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                        VStack(alignment: .leading) {
                            Text(preset.name).foregroundStyle(.primary)
                            Text(preset.rig).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if preset == model.activePreset {
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(model.theme.accent)
                        }
                    }
                }
                .accessibilityIdentifier("preset.\(preset.id)")
            }
            .listStyle(.plain)
            .searchable(text: $query, prompt: "preset.search")
        }
        .padding(.top)
        .navigationTitle("nav.presets")
    }
}
