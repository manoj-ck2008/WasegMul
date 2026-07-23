import SwiftUI
import shared

struct ContentView: View {
    let bridge = IOSBridge.shared

    var body: some View {
        ZStack {
            Color(red: 0.01, green: 0.02, blue: 0.03) // DarkBackground
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
                    .shadow(color: .green.opacity(0.3), radius: 20)

                Text("WasegMul AI")
                    .font(.system(size: 34, weight: .black, design: .rounded))
                    .foregroundColor(.white)

                Text("NEURAL WASTE SEGREGATION")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(2)
                    .foregroundColor(Color(red: 0.0, green: 1.0, blue: 0.58)) // EmeraldVibrant

                Spacer()
                    .frame(height: 40)

                VStack(alignment: .leading, spacing: 15) {
                    InfoRow(icon: "cpu", text: "Shared Kotlin Logic Active")
                    InfoRow(icon: "shield.fill", text: "ML Arbitrator Ready")
                    InfoRow(icon: "clock.arrow.circlepath", text: "16 KB Page Aligned")
                }
                .padding(30)
                .background(.white.opacity(0.05))
                .cornerRadius(24)
                .overlay(
                    RoundedRectangle(cornerRadius: 24)
                        .stroke(.white.opacity(0.1), lineWidth: 1)
                )

                Spacer()

                Button(action: {
                    // Logic bridge test
                    let info = bridge.getWasteKnowledge(category: "Recyclable", subclass: "Plastic")
                    print("Bridge Test: \(info.disposalGuide)")
                }) {
                    Text("LAUNCH SCANNER")
                        .font(.headline)
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color(red: 0.0, green: 1.0, blue: 0.58))
                        .cornerRadius(16)
                }
                .padding(.horizontal, 40)

                Text("V 1.1.0 • XCODE READY")
                    .font(.caption2)
                    .foregroundColor(.white.opacity(0.4))
                    .padding(.bottom, 20)
            }
            .padding()
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
            Text(text)
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.8))
        }
    }
}
