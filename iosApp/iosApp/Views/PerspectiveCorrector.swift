import UIKit
import CoreImage
import Vision
import SharedLogic

struct PerspectiveCorrector {
    /// Compute output size preserving the tile's aspect ratio.
    /// Fits within maxDimension on the longest side.
    static func outputSize(tileWidth: Double, tileHeight: Double, maxDimension: CGFloat = 512) -> CGSize {
        let aspect = tileWidth / tileHeight
        if aspect >= 1.0 {
            return CGSize(width: maxDimension, height: maxDimension / aspect)
        } else {
            return CGSize(width: maxDimension * aspect, height: maxDimension)
        }
    }

    /// Correct using a VNRectangleObservation (auto-detected corners).
    /// IMPORTANT: Vision corners are in normalized [0,1] coordinates.
    /// CIPerspectiveCorrection expects pixel coordinates in the image extent.
    static func correct(
        image: CIImage,
        observation: VNRectangleObservation,
        tileWidth: Double, tileHeight: Double,
        outputSize: CGSize
    ) -> UIImage? {
        let w = image.extent.width
        let h = image.extent.height
        return correctWithCorners(
            image: image,
            topLeft: CGPoint(x: observation.topLeft.x * w, y: observation.topLeft.y * h),
            topRight: CGPoint(x: observation.topRight.x * w, y: observation.topRight.y * h),
            bottomLeft: CGPoint(x: observation.bottomLeft.x * w, y: observation.bottomLeft.y * h),
            bottomRight: CGPoint(x: observation.bottomRight.x * w, y: observation.bottomRight.y * h),
            tileWidth: tileWidth, tileHeight: tileHeight,
            outputSize: outputSize
        )
    }

    /// Correct using explicit pixel-coordinate corners (from manual adjustment).
    ///
    /// When the tile was framed 90° from its natural orientation, the corner ORDER is
    /// rotated back rather than the output image — the warp already resamples, so the
    /// rotation costs nothing and outputSize stays natural (tileWidth × tileHeight).
    /// Rotation is derived from the corners themselves, so it covers both the
    /// auto-detected and guide-seeded paths.
    static func correctWithCorners(
        image: CIImage,
        topLeft: CGPoint, topRight: CGPoint,
        bottomLeft: CGPoint, bottomRight: CGPoint,
        tileWidth: Double, tileHeight: Double,
        outputSize: CGSize
    ) -> UIImage? {
        guard let filter = CIFilter(name: "CIPerspectiveCorrection") else { return nil }

        let ordered = [topLeft, topRight, bottomRight, bottomLeft]   // tl, tr, br, bl
        let quadW = max(abs(topRight.x - topLeft.x), abs(bottomRight.x - bottomLeft.x))
        let quadH = max(abs(bottomLeft.y - topLeft.y), abs(bottomRight.y - topRight.y))
        let rotated = quadW > 0 && quadH > 0 && ScanGuideGeometry.shared.isRotated(
            quadWidth: Double(quadW), quadHeight: Double(quadH),
            tileWidth: tileWidth, tileHeight: tileHeight
        )
        // Still tl, tr, br, bl — now in the tile's natural orientation.
        let c = ScanGuideGeometry.shared.unrotationOrder(rotated: rotated)
            .map { ordered[$0.intValue] }

        filter.setValue(image, forKey: kCIInputImageKey)
        // CIPerspectiveCorrection uses bottom-left origin (same as CIImage)
        filter.setValue(CIVector(cgPoint: c[0]), forKey: "inputTopLeft")
        filter.setValue(CIVector(cgPoint: c[1]), forKey: "inputTopRight")
        filter.setValue(CIVector(cgPoint: c[2]), forKey: "inputBottomRight")
        filter.setValue(CIVector(cgPoint: c[3]), forKey: "inputBottomLeft")
        guard let corrected = filter.outputImage else { return nil }
        let scaleX = outputSize.width / corrected.extent.width
        let scaleY = outputSize.height / corrected.extent.height
        let scaled = corrected.transformed(by: CGAffineTransform(scaleX: scaleX, y: scaleY))
        let ctx = CIContext()
        guard let cg = ctx.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}
