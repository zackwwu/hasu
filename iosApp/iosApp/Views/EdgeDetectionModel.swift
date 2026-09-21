import AVFoundation
import Vision
import CoreImage

/// Handles camera session and rectangle detection on the proc queue.
/// Published properties are updated on MainActor.
/// latestPixelBuffer and latestObservation are accessed ONLY on procQueue
/// and copied to main-safe snapshots for the UI via captureSnapshot().
final class EdgeDetectionModel: NSObject, ObservableObject {
    let session = AVCaptureSession()
    @Published var detectedRect: CGRect?
    @Published var isStable = false
    @Published var flashOn = false
    @Published var viewSize: CGSize = .zero

    // Main-thread-safe snapshot for capture (set atomically from proc queue)
    @Published var latestObservation: VNRectangleObservation?
    private(set) var snapshotBuffer: CVPixelBuffer?

    private let videoOutput = AVCaptureVideoDataOutput()
    private let sessionQueue = DispatchQueue(label: "edge.session")
    private let procQueue = DispatchQueue(label: "edge.proc")

    private lazy var rectRequest: VNDetectRectanglesRequest = {
        let req = VNDetectRectanglesRequest { [weak self] r, e in
            self?.handleRectangles(r, e)
        }
        req.minimumAspectRatio = 0.3
        req.maximumAspectRatio = 1.0
        req.minimumSize = 0.15
        req.maximumObservations = 1
        req.minimumConfidence = 0.7
        return req
    }()

    private var lastRect: CGRect?
    private var stableCount = 0
    // ~330ms at 30fps — enough to confirm user is holding still
    private let stableFrameThreshold = 10

    // Overlay smoothing state (main-thread only).
    private var smoothedRect: CGRect?
    private var missedFrames = 0
    private let maxMissedFrames = 4
    private let smoothingAlpha: CGFloat = 0.35
    /// Pixel dimensions of the video stream, used to map Vision's normalized
    /// rects into the aspect-fill display space. Set from the sample buffer.
    private var bufferSize: CGSize = .zero

    // Proc-queue-only working state
    private var _procPixelBuffer: CVPixelBuffer?
    private var _procObservation: VNRectangleObservation?

    func start() {
        sessionQueue.async { [weak self] in
            guard let self, !self.session.isRunning else { return }
            self.session.beginConfiguration()

            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
                  let input = try? AVCaptureDeviceInput(device: device),
                  self.session.canAddInput(input),
                  self.session.canAddOutput(self.videoOutput)
            else {
                self.session.commitConfiguration()
                return
            }

            self.session.addInput(input)
            self.videoOutput.videoSettings = [
                String(kCVPixelFormatType_32BGRA): Int(kCVPixelFormatType_32BGRA)
            ]
            self.videoOutput.alwaysDiscardsLateVideoFrames = true
            self.videoOutput.setSampleBufferDelegate(self, queue: self.procQueue)
            self.session.addOutput(self.videoOutput)
            self.session.commitConfiguration()
            self.session.startRunning()
        }
    }

    func stop() {
        sessionQueue.async { [weak self] in
            self?.session.stopRunning()
        }
    }

    /// Freeze the frame stream while the corner review is open, so Accept warps
    /// exactly the frame the user aligned against — not a later one. Mirrors the
    /// Android scanner's paused analyzer.
    func pause() {
        sessionQueue.async { [weak self] in
            self?.session.stopRunning()
        }
    }

    func resume() {
        sessionQueue.async { [weak self] in
            guard let self, !self.session.isRunning else { return }
            self.session.startRunning()
        }
    }

    func toggleFlash() {
        sessionQueue.async { [weak self] in
            guard let self,
                  let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
                  device.hasTorch else { return }
            do {
                try device.lockForConfiguration()
                let turnOn = device.torchMode == .off
                device.torchMode = turnOn ? .on : .off
                device.unlockForConfiguration()
                DispatchQueue.main.async { self.flashOn = turnOn }
            } catch {
                // Torch unavailable (thermal, unsupported) — leave state untouched.
            }
        }
    }

    /// Snapshot the current frame for the corner review screen.
    /// The observation is OPTIONAL: when no rectangle was detected we still hand back
    /// the image, so review can seed its corners from the guide frame instead of
    /// dead-ending. Only a missing image buffer returns nil.
    func captureSnapshot() -> (image: CIImage, observation: VNRectangleObservation?)? {
        guard let buf = snapshotBuffer else { return nil }
        return (CIImage(cvPixelBuffer: buf), latestObservation)
    }

    private func handleRectangles(_ request: VNRequest, _ error: Error?) {
        guard let results = request.results as? [VNRectangleObservation],
              let best = results.first else {
            DispatchQueue.main.async {
                // Decay stability instead of hard-resetting — a brief missed
                // detection (glare, motion blur) shouldn't lose "Hold steady".
                self.stableCount = max(0, self.stableCount - 4)
                self.isStable = self.stableCount >= self.stableFrameThreshold
                // Hysteresis: hold the last cutout for a few frames so a single
                // missed detection doesn't make the overlay flash off/on.
                if self.missedFrames < self.maxMissedFrames, self.detectedRect != nil {
                    self.missedFrames += 1
                } else {
                    self.detectedRect = nil
                    self.smoothedRect = nil
                }
                self.latestObservation = nil   // snapshotBuffer deliberately retained
            }
            return
        }
        // Publish snapshot atomically
        _procObservation = best
        let vr = normalizedRect(best)

        DispatchQueue.main.async {
            self.latestObservation = best
            self.missedFrames = 0
            // EMA smoothing: the raw Vision rect jitters per frame (lighting,
            // hand shake). Smooth it so the cutout tracks instead of vibrating.
            if let current = self.smoothedRect {
                self.smoothedRect = CGRect(
                    x: current.origin.x + (vr.origin.x - current.origin.x) * self.smoothingAlpha,
                    y: current.origin.y + (vr.origin.y - current.origin.y) * self.smoothingAlpha,
                    width: current.width + (vr.width - current.width) * self.smoothingAlpha,
                    height: current.height + (vr.height - current.height) * self.smoothingAlpha
                )
            } else {
                self.smoothedRect = vr
            }
            self.detectedRect = self.smoothedRect
            if let last = self.lastRect, self.rectsClose(last, vr) {
                self.stableCount += 1
            } else {
                // Decay rather than reset: tiny hand movements stay "steady".
                self.stableCount = max(0, self.stableCount - 4)
            }
            self.isStable = self.stableCount >= self.stableFrameThreshold
            self.lastRect = vr
        }
    }

    /// Map Vision's normalized bounding box (full camera frame) into the view
    /// coordinates of the aspect-fill preview. Without this the cutout sits in
    /// the wrong place/size whenever the camera aspect ≠ screen aspect, and
    /// follows the full-frame rect — which reads as a flashing, resizing blob.
    private func normalizedRect(_ obs: VNRectangleObservation) -> CGRect {
        // Vision [0,1] bottom-left origin → UIKit top-left origin
        let normX = obs.boundingBox.origin.x
        let normY = 1.0 - obs.boundingBox.origin.y - obs.boundingBox.height
        let normW = obs.boundingBox.width
        let normH = obs.boundingBox.height

        let viewW = max(viewSize.width, 1)
        let viewH = max(viewSize.height, 1)
        guard bufferSize.width > 0, bufferSize.height > 0 else {
            // Buffer size unknown yet — fall back to the old direct mapping.
            return CGRect(x: normX * viewW, y: normY * viewH,
                          width: normW * viewW, height: normH * viewH)
        }

        // Video display rect under .resizeAspectFill (scale to cover, crop overflow).
        let scale = max(viewW / bufferSize.width, viewH / bufferSize.height)
        let displayW = bufferSize.width * scale
        let displayH = bufferSize.height * scale
        let originX = (viewW - displayW) / 2
        let originY = (viewH - displayH) / 2

        return CGRect(
            x: originX + normX * displayW,
            y: originY + normY * displayH,
            width: normW * displayW,
            height: normH * displayH
        )
    }

    /// Proportional closeness: tolerances scale with the rect size, so a large
    /// detected tile tolerates proportionally more hand movement before the
    /// "steady" state is lost.
    private func rectsClose(_ a: CGRect, _ b: CGRect) -> Bool {
        abs(a.midX - b.midX) < max(15, a.width * 0.10) &&
        abs(a.midY - b.midY) < max(15, a.height * 0.10) &&
        abs(a.width - b.width) < max(30, a.width * 0.15) &&
        abs(a.height - b.height) < max(30, a.height * 0.15)
    }
}

extension EdgeDetectionModel: AVCaptureVideoDataOutputSampleBufferDelegate {
    nonisolated func captureOutput(_ output: AVCaptureOutput,
                      didOutput sampleBuffer: CMSampleBuffer,
                      from connection: AVCaptureConnection) {
        guard let pb = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        _procPixelBuffer = pb
        // Publish EVERY frame, not just detected ones — capture must work when
        // detection finds nothing. This lands on main before the observation
        // below (both hop from procQueue in order), so they stay the same frame.
        let bufferW = CVPixelBufferGetWidth(pb)
        let bufferH = CVPixelBufferGetHeight(pb)
        DispatchQueue.main.async {
            self.snapshotBuffer = pb
            self.bufferSize = CGSize(width: bufferW, height: bufferH)
        }
        try? VNImageRequestHandler(cvPixelBuffer: pb, orientation: .up, options: [:])
            .perform([rectRequest])
    }
}
