import SwiftUI

/// Reusable export sheet: DPI stepper, live preview thumbnail, and
/// Save to Photos / Share actions. Shared by the 2D Layout and 3D Preview tabs.
@MainActor
struct ExportSheetView: View {
    let title: String
    let renderContent: @MainActor (CGFloat) -> UIImage?  // closure that renders at given DPI

    @Environment(\.dismiss) private var dismiss
    @AppStorage("exportDPI") private var dpi: Double = 200
    @State private var previewImage: UIImage?
    @State private var isSaving = false
    @State private var saveError: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                // DPI stepper (150, 200, 250, 300)
                Stepper("DPI: \(Int(dpi))", value: $dpi, in: 150...300, step: 50)
                    .onChange(of: dpi) { _, _ in refreshPreview() }

                // Preview thumbnail
                if let img = previewImage {
                    Image(uiImage: img)
                        .resizable()
                        .scaledToFit()
                        .frame(maxHeight: 200)
                        .background(Color.white)
                        .border(Color.gray.opacity(0.3))
                } else {
                    Text("Preview unavailable")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .frame(maxHeight: 200)
                }

                // Action buttons
                HStack(spacing: 16) {
                    Button { saveToPhotos() } label: {
                        Label("Save to Photos", systemImage: "photo")
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isSaving)

                    Button { shareImage() } label: {
                        Label("Share", systemImage: "square.and.arrow.up")
                    }
                    .buttonStyle(.bordered)
                    .disabled(previewImage == nil)
                }

                if let error = saveError {
                    Text(error).font(.caption).foregroundStyle(.red)
                }

                Spacer()
            }
            .padding()
            .navigationTitle("Export \(title)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cancel") { dismiss() }
                }
            }
            .onAppear { refreshPreview() }
        }
    }

    private func refreshPreview() {
        previewImage = renderContent(CGFloat(dpi))
    }

    private func saveToPhotos() {
        guard let image = previewImage else { return }
        isSaving = true
        Task {
            do {
                try await ExportService.saveToPhotos(image)
                dismiss()
            } catch {
                saveError = error.localizedDescription
            }
            isSaving = false
        }
    }

    private func shareImage() {
        guard let image = previewImage else { return }
        ExportService.shareImage(image)
    }
}
