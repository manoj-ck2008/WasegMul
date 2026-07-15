import SwiftUI
import shared

struct ContentView: View {
    var body: some View {
        VStack {
            Image(systemName: "leaf.fill")
                .imageScale(.large)
                .foregroundColor(.green)
            Text("WasegMul KMP")
                .font(.title)
                .fontWeight(.bold)
            Text("Logic Shared via Kotlin")
                .font(.caption)
        }
        .padding()
    }
}
