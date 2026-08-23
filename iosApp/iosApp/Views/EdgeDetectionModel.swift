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
                self.isStable = false
                self.stableCount = 0
                self.detectedRect = nil
                self.latestObservation = nil   // snapshotBuffer deliberately retained
            }
            return
        }
        // Publish snapshot atomically
        _procObservation = best
        let vr = normalizedRect(best)

        DispatchQueue.main.async {
            self.latestObservation = best
            self.detectedRect = vr
            if let last = self.lastRect, self.rectsClose(last, vr) {
                self.stableCount += 1
                self.isStable = self.stableCount >= self.stableFrameThreshold
            } else {
                self.stableCount = 0
                self.isStable = false
            }
            self.lastRect = vr
        }
    }

    private func normalizedRect(_ obs: VNRectangleObservation) -> CGRect {
        // Vision [0,1] bottom-left origin → UIKit top-left origin
        let invY = 1.0 - obs.boundingBox.origin.y - obs.boundingBox.height
        return CGRect(
            x: obs.boundingBox.origin.x * viewSize.width,
            y: invY * viewSize.height,
            width: obs.boundingBox.width * viewSize.width,
            height: obs.boundingBox.height * viewSize.height
        )
    }

    private func rectsClose(_ a: CGRect, _ b: CGRect) -> Bool {
        abs(a.midX - b.midX) < 15 && abs(a.midY - b.midY) < 15 &&
        abs(a.width - b.width) < 30 && abs(a.height - b.height) < 30
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
        DispatchQueue.main.async { self.snapshotBuffer = pb }
        try? VNImageRequestHandler(cvPixelBuffer: pb, orientation: .up, options: [:])
            .perform([rectRequest])
    }
}
