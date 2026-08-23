import SwiftUI
import Vision
import CoreImage
import SharedLogic

/// Allows user to drag 4 corner handles to adjust detected rectangle
/// before perspective correction. Handles the case where auto-detection
/// is imperfect (tiles on busy floors, partial occlusion, etc.) — and the
/// case where it found nothing at all, seeding from the guide frame.
///
/// Corner state lives in IMAGE PIXEL space (what the perspective filter needs);
/// handles and lines are rendered in the fitted-image view space via pixelToView.
struct CornerReviewView: View {
    let model: EdgeDetectionModel
    let tileWidth: Double
    let tileHeight: Double
    let onAccept: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var topLeft: CGPoint = .zero
    @State private var topRight: CGPoint = .zero
    @State private var bottomLeft: CGPoint = .zero
    @State private var bottomRight: CGPoint = .zero
    @State private var previewImage: UIImage?

    var body: some View {
        VStack {
            Text("Adjust Corners").font(.headline).padding(.top)
            Text("Drag corners to align with tile edges")
                .font(.caption).foregroundStyle(.secondary)

            GeometryReader { geometry in
                let imageSize = previewImage?.size ?? .zero
                let fit = fittedRect(imageSize: imageSize, in: geometry.size)
                ZStack {
                    if let img = previewImage {
                        Image(uiImage: img).resizable().scaledToFit()
                            .frame(width: fit.width, height: fit.height)
                    }
                    // 4 draggable corner handles — mapped from image pixels into view space
                    CornerHandle(position: Binding(
                        get: { pixelToView(topLeft, fit: fit, imageSize: imageSize) },
                        set: { topLeft = viewToPixel($0, fit: fit, imageSize: imageSize) }
                    ))
                    CornerHandle(position: Binding(
                        get: { pixelToView(topRight, fit: fit, imageSize: imageSize) },
                        set: { topRight = viewToPixel($0, fit: fit, imageSize: imageSize) }
                    ))
                    CornerHandle(position: Binding(
                        get: { pixelToView(bottomLeft, fit: fit, imageSize: imageSize) },
                        set: { bottomLeft = viewToPixel($0, fit: fit, imageSize: imageSize) }
                    ))
                    CornerHandle(position: Binding(
                        get: { pixelToView(bottomRight, fit: fit, imageSize: imageSize) },
                        set: { bottomRight = viewToPixel($0, fit: fit, imageSize: imageSize) }
                    ))
                    // Lines connecting corners
                    CornerLinesShape(
                        tl: pixelToView(topLeft, fit: fit, imageSize: imageSize),
                        tr: pixelToView(topRight, fit: fit, imageSize: imageSize),
                        bl: pixelToView(bottomLeft, fit: fit, imageSize: imageSize),
                        br: pixelToView(bottomRight, fit: fit, imageSize: imageSize)
                    )
                    .stroke(Color.green, lineWidth: 2)
                }
            }

            HStack(spacing: 20) {
                Button("Re-scan") { dismiss() }
                    .buttonStyle(.bordered)
                Button("Accept") { applyCorrection() }
                    .buttonStyle(.borderedProminent)
            }
            .padding()
        }
        .onAppear { initializeCorners() }
    }

    // MARK: - Coordinate mapping

    /// The rect where the aspect-fitted image is actually rendered inside the view.
    private func fittedRect(imageSize: CGSize, in viewSize: CGSize) -> CGRect {
        guard imageSize.width > 0, imageSize.height > 0, viewSize.width > 0, viewSize.height > 0 else {
            return .zero
        }
        let scale = min(viewSize.width / imageSize.width, viewSize.height / imageSize.height)
        let w = imageSize.width * scale
        let h = imageSize.height * scale
        return CGRect(x: (viewSize.width - w) / 2, y: (viewSize.height - h) / 2,
                      width: w, height: h)
    }

    private func pixelToView(_ p: CGPoint, fit: CGRect, imageSize: CGSize) -> CGPoint {
        guard fit.width > 0, fit.height > 0, imageSize.width > 0, imageSize.height > 0 else { return p }
        return CGPoint(x: fit.minX + p.x * fit.width / imageSize.width,
                       y: fit.minY + p.y * fit.height / imageSize.height)
    }

    private func viewToPixel(_ p: CGPoint, fit: CGRect, imageSize: CGSize) -> CGPoint {
        guard fit.width > 0, fit.height > 0, imageSize.width > 0, imageSize.height > 0 else { return p }
        return CGPoint(x: (p.x - fit.minX) * imageSize.width / fit.width,
                       y: (p.y - fit.minY) * imageSize.height / fit.height)
    }

    private func initializeCorners() {
        guard let (ciImage, obs) = model.captureSnapshot() else { dismiss(); return }
        let ctx = CIContext()
        guard let cg = ctx.createCGImage(ciImage, from: ciImage.extent) else { return }
        previewImage = UIImage(cgImage: cg)
        let w = ciImage.extent.width
        let h = ciImage.extent.height

        if let obs {
            // Map observation normalized corners to image pixel coordinates
            topLeft = CGPoint(x: obs.topLeft.x * w, y: (1 - obs.topLeft.y) * h)
            topRight = CGPoint(x: obs.topRight.x * w, y: (1 - obs.topRight.y) * h)
            bottomLeft = CGPoint(x: obs.bottomLeft.x * w, y: (1 - obs.bottomLeft.y) * h)
            bottomRight = CGPoint(x: obs.bottomRight.x * w, y: (1 - obs.bottomRight.y) * h)
        } else {
            // No rectangle detected — seed from the guide frame so the user has
            // something to drag instead of a dead end.
            let guide = ScanGuideGeometry.shared.frame(
                viewportWidth: Double(w), viewportHeight: Double(h),
                tileWidth: tileWidth, tileHeight: tileHeight,
                insetFraction: 0.85
            )
            topLeft = CGPoint(x: CGFloat(guide.x), y: CGFloat(guide.y))
            topRight = CGPoint(x: CGFloat(guide.x + guide.width), y: CGFloat(guide.y))
            bottomRight = CGPoint(x: CGFloat(guide.x + guide.width), y: CGFloat(guide.y + guide.height))
            bottomLeft = CGPoint(x: CGFloat(guide.x), y: CGFloat(guide.y + guide.height))
        }
    }

    private func applyCorrection() {
        guard let (ciImage, _) = model.captureSnapshot() else { return }
        let outputSize = PerspectiveCorrector.outputSize(
            tileWidth: tileWidth, tileHeight: tileHeight, maxDimension: 512
        )

        // Rotation is derived inside the corrector from the corners themselves, so it
        // covers the auto-detected and guide-seeded paths alike.
        guard let result = PerspectiveCorrector.correctWithCorners(
            image: ciImage,
            topLeft: topLeft, topRight: topRight,
            bottomLeft: bottomLeft, bottomRight: bottomRight,
            tileWidth: tileWidth, tileHeight: tileHeight,
            outputSize: outputSize
        ) else { return }
        onAccept(result)
    }
}

// MARK: - Corner Handle

/// Draggable circle pinned to a position in the parent's coordinate space.
struct CornerHandle: View {
    @Binding var position: CGPoint
    @State private var dragStart: CGPoint?

    var body: some View {
        Circle()
            .fill(Color.green)
            .frame(width: 32, height: 32)
            .overlay(Circle().stroke(.white, lineWidth: 2))
            .shadow(radius: 2)
            .position(position)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if dragStart == nil { dragStart = position }
                        if let start = dragStart {
                            position = CGPoint(x: start.x + value.translation.width,
                                               y: start.y + value.translation.height)
                        }
                    }
                    .onEnded { _ in dragStart = nil }
            )
    }
}

// MARK: - Corner Lines Shape

/// Quad connecting the four corners in tl → tr → br → bl order.
struct CornerLinesShape: Shape {
    let tl: CGPoint
    let tr: CGPoint
    let bl: CGPoint
    let br: CGPoint

    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: tl)
        p.addLine(to: tr)
        p.addLine(to: br)
        p.addLine(to: bl)
        p.closeSubpath()
        return p
    }
}
