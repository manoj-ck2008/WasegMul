import SwiftUI
import shared

// MARK: - Version
// Keep in sync with the Android single source of truth:
// gradle.properties VERSION_NAME (currently 3.0.0) and the :shared framework
// consumed via the KMP Gradle build. There is no auto-read of the Gradle
// version from Swift; bump this string in the same commit as VERSION_NAME.
private let kAppVersionString = "3.0.0"

struct ContentView: View {
    @Environment(\.colorScheme) private var colorScheme

    let bridge = IOSBridge.shared

    // iOS parity roadmap (§3.48 — explicit scope, no dead buttons ship):
    // TODO(parity-1): AVFoundation capture session (photo + live frames). No full
    //   AVFoundation in this batch — out of scope; scanner button below is a
    //   shared-logic probe only, NOT a camera.
    // TODO(parity-2): on-device inference bridge (TFLite C-API / CoreML) for the
    //   15-class waste detector + 30-subclass classifier.
    // TODO(parity-3): Barcode Resolution parity (camera scan + Open Food Facts via
    //   shared Ktor client) and offline barcode DB seeding.
    // TODO(parity-4): History / Eco Impact / Settings screens backed by shared
    //   domain models (WasteRecord, EcoImpactCalculator, IOSBridge).
    // TODO(parity-5): wire wasegmul:// deep links (see onOpenURL below) to real
    //   destinations once screens land; today they log + no-op.

    var body: some View {
        ZStack {
            // Theme tokens only: system background adapts to light/dark/contrast.
            Color(.systemBackground)
                .ignoresSafeArea()

            VStack(spacing: 20) {
                Image(systemName: "leaf.fill")
                    .font(.system(size: 80))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [.green, .mint],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .shadow(color: .green.opacity(colorScheme == .dark ? 0.3 : 0.15), radius: 20)
                    .accessibilityHidden(true)

                Text("WasegMul AI")
                    .font(.system(size: 34, weight: .black, design: .rounded))
                    .foregroundColor(.primary)

                Text("NEURAL WASTE SEGREGATION")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(2)
                    .foregroundColor(.secondary)

                Spacer()
                    .frame(height: 40)

                VStack(alignment: .leading, spacing: 15) {
                    // Only claims the shared Kotlin bridge can prove: a live call
                    // runs when the probe button is tapped (see console).
                    InfoRow(icon: "cpu", text: "Shared Kotlin Logic Available")
                    InfoRow(icon: "shield.fill", text: "ML Arbitrator via Shared Bridge")
                    InfoRow(icon: "camera.fill", text: "On-device Scanner: iOS Roadmap")
                }
                .padding(30)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(24)
                .overlay(
                    RoundedRectangle(cornerRadius: 24)
                        .stroke(Color(.separator), lineWidth: 1)
                )

                Spacer()

                Button(action: {
                    // Shared-logic probe (real bridged call, no camera).
                    let info = bridge.getWasteKnowledge(category: "Recyclable", subclass: "Plastic")
                    print("Bridge Test: \(info.disposalGuide)")
                }) {
                    Text("PROBE SHARED BRIDGE")
                        .font(.headline)
                        .foregroundColor(Color(.systemBackground))
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.accentColor)
                        .cornerRadius(16)
                }
                .padding(.horizontal, 40)
                .accessibilityHint("Runs a shared Kotlin lookup without opening a camera.")

                Text("V \(kAppVersionString) • iOS PROTOTYPE — scanner ships on Android first")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .padding(.bottom, 20)
            }
            .padding()
        }
        // Deep-link ready: wasegmul://result/<id>, wasegmul://history.
        // No destinations exist yet (see TODO parity-5); handler logs and no-ops
        // so links never crash or dead-end once advertised.
        .onOpenURL { url in
            print("Deep link received (no destination yet): \(url.absoluteString)")
        }
    }
}

struct InfoRow: View {
    let icon: String
    let text: String
    var body: some View {
        HStack(spacing: 15) {
            Image(systemName: icon)
                .foregroundColor(.green)
                .frame(width: 20)
                .accessibilityHidden(true)
            Text(text)
                .font(.subheadline)
                .foregroundColor(.primary)
        }
        .accessibilityElement(children: .combine)
    }
}
