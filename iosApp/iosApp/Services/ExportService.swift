import SwiftUI
import Photos

/// Renders SwiftUI views (canvases) to UIImage for PNG export, and handles
/// saving to the Photos library and presenting the system share sheet.
@MainActor
struct ExportService {
    static let defaultDPI: CGFloat = 200
    static let minDPI: CGFloat = 150
    static let maxDPI: CGFloat = 300

    /// Canvas size in points used for offscreen exports.
    /// Final pixel size = size × (dpi / 72).
    static let exportCanvasSize = CGSize(width: 1024, height: 1024)

    /// Render a SwiftUI view to a UIImage at the given DPI.
    ///
    /// The content is laid out at `size` points; the output pixel size is
    /// `size` scaled by `dpi / 72`. Pass a `Canvas` (e.g. one that calls
    /// `SurfaceCanvas.drawTiles` or `IsometricCanvas.drawIsometricRoom`) and
    /// add a `.background(.white)` so the exported PNG is opaque.
    static func renderToImage<Content: View>(
        content: Content,
        size: CGSize = CGSize(width: 1024, height: 1024),  // = exportCanvasSize
        dpi: CGFloat = 200  // = defaultDPI
    ) -> UIImage? {
        let clampedDPI = min(max(dpi, minDPI), maxDPI)
        let scale = clampedDPI / 72.0  // 72 points per inch base
        let renderer = ImageRenderer(
            content: content.frame(width: size.width, height: size.height)
        )
        renderer.scale = scale
        return renderer.uiImage
    }

    /// Save image to Photos library.
    static func saveToPhotos(_ image: UIImage) async throws {
        try await PHPhotoLibrary.shared().performChanges {
            PHAssetChangeRequest.creationRequestForAsset(from: image)
        }
    }

    /// Present the system share sheet for the image.
    ///
    /// Presented from the topmost presented view controller so it works while
    /// an export sheet is on screen; popover source configured for iPad.
    static func shareImage(_ image: UIImage) {
        let activityVC = UIActivityViewController(
            activityItems: [image],
            applicationActivities: nil
        )
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootVC = windowScene.windows.first?.rootViewController else { return }

        var presenter = rootVC
        while let presented = presenter.presentedViewController {
            presenter = presented
        }

        if let popover = activityVC.popoverPresentationController {
            popover.sourceView = presenter.view
            popover.sourceRect = presenter.view.bounds
        }

        presenter.present(activityVC, animated: true)
    }
}
