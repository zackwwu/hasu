import SwiftUI
import AVFoundation
import Vision
import SharedLogic

/// Fullscreen camera with real-time rectangle detection (Vision), a guide frame at
/// the tile's aspect ratio, and a corner-review step before perspective correction.
struct EdgeDetectionCameraView: View {
    let tileGroup: TileGroup  // needed for aspect ratio
    let onCapture: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model = EdgeDetectionModel()
    @State private var showCornerReview = false

    var body: some View {
        GeometryReader { geo in
            ZStack {
                EdgeDetectionPreview(session: model.session)
                    .ignoresSafeArea()

                // Guide frame at the tile's aspect ratio — drawn UNDER the detection
                // overlay so a live detection visually wins when there is one.
                ScanGuideOverlay(
                    tileWidth: tileGroup.tileWidth,
                    tileHeight: tileGroup.tileHeight
                )
                .allowsHitTesting(false)

                // Semi-transparent overlay with rectangle cutout
                if let rect = model.detectedRect {
                    EdgeOverlayShape(detectedRect: rect)
                        .fill(Color.black.opacity(0.4), style: FillStyle(eoFill: true))
                        .allowsHitTesting(false)
                }

                VStack {
                    HStack {
                        Button { dismiss() } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.title).foregroundStyle(.white).shadow(radius: 4)
                        }.padding()
                        Spacer()
                        Button { model.toggleFlash() } label: {
                            Image(systemName: model.flashOn ? "bolt.fill" : "bolt.slash.fill")
                                .font(.title3).foregroundStyle(.white)
                        }.padding()
                    }
                    Spacer()
                    if model.isStable {
                        Text("Hold steady…").font(.caption).foregroundStyle(.green)
                            .padding(8).background(.black.opacity(0.5)).clipShape(Capsule())
                    } else if model.latestObservation == nil {
                        Text("Align the tile with the frame")
                            .font(.caption).foregroundStyle(.white)
                            .padding(8).background(.black.opacity(0.5)).clipShape(Capsule())
                    }
                    // NOT gated on a detection: with no rectangle found, Review Corners
                    // opens seeded from the guide frame instead of dead-ending.
                    Button { showCornerReview = true } label: {
                        ZStack {
                            Circle().stroke(.white, lineWidth: 4).frame(width: 72, height: 72)
                            Circle().fill(model.isStable ? Color.green : Color.white)
                                .frame(width: 60, height: 60)
                        }
                    }
                    .padding(.bottom, 40)
                }
            }
            .onAppear {
                model.start()
                model.viewSize = geo.size
            }
            .onDisappear { model.stop() }
            .onChange(of: geo.size) { _, newSize in model.viewSize = newSize }
        }
        .fullScreenCover(isPresented: $showCornerReview) {
            CornerReviewView(
                model: model,
                tileWidth: tileGroup.tileWidth,
                tileHeight: tileGroup.tileHeight,
                onAccept: { image in
                    onCapture(image)
                    dismiss()
                }
            )
        }
    }
}

// MARK: - Camera Preview (UIViewRepresentable)

/// Live AVCaptureVideoPreviewLayer wrapper — same pattern as the old CameraPreview.
struct EdgeDetectionPreview: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewView {
        PreviewView()
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {
        uiView.previewLayer.session = session
    }

    final class PreviewView: UIView {
        var previewLayer: AVCaptureVideoPreviewLayer {
            layer as! AVCaptureVideoPreviewLayer
        }

        override class var layerClass: AnyClass {
            AVCaptureVideoPreviewLayer.self
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            previewLayer.frame = bounds
            previewLayer.videoGravity = .resizeAspectFill
        }
    }
}

// MARK: - Detection Overlay Shape

/// Dims everything except the detected rectangle via an even-odd cutout.
struct EdgeOverlayShape: Shape {
    let detectedRect: CGRect

    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addRect(rect)
        path.addRect(detectedRect)
        return path
    }
}

#Preview {
    EdgeDetectionCameraView(tileGroup: TileGroup(
        id: "preview", projectId: "preview-project", name: "Preview",
        tileWidth: 300, tileHeight: 200, texturePath: nil, source: TileSource.imported
    )) { _ in }
}
