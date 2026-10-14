import SceneKit
import SharedLogic
import SwiftUI
import UIKit

/// Lets UI outside the 3D view (e.g. export) reach the live renderer.
@MainActor
final class RoomRendererHandle {
    fileprivate(set) var renderer: SceneKitRoomRenderer?
    fileprivate(set) weak var view: SCNView?

    func snapshot(size: CGSize) -> UIImage? {
        renderer?.snapshot(device: view?.device, size: size)
    }
}

/// SceneKit-backed 3D view of the room. Gestures are UIKit recognisers on
/// the SCNView so pinch, one/two-finger drag, trackpad/mouse scroll and taps
/// all work together on iPhone and iPad.
struct RoomSceneView: UIViewRepresentable {
    let scene: RoomScene
    let camera: OrbitCamera
    let hiddenSurfaceIds: Set<String>
    let selectedSurfaceId: String?
    let handle: RoomRendererHandle
    var onOrbit: (_ deltaYaw: Double, _ deltaPitch: Double) -> Void
    var onZoom: (_ factor: Double) -> Void
    /// Tap location and viewport size, both in points.
    var onTap: (_ location: CGPoint, _ viewport: CGSize) -> Void

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> SCNView {
        let renderer = SceneKitRoomRenderer()
        let view = LayoutReportingSCNView(frame: .zero, options: nil)
        view.scene = renderer.scene
        view.pointOfView = renderer.cameraNode
        view.antialiasingMode = .multisampling4X
        view.rendersContinuously = false
        view.preferredFramesPerSecond = 60
        view.backgroundColor = .clear
        view.allowsCameraControl = false
        view.onLayout = { [weak renderer] size in renderer?.setViewportSize(size) }
        view.accessibilityIdentifier = "preview-3d"

        context.coordinator.renderer = renderer
        context.coordinator.attachGestures(to: view)
        handle.renderer = renderer
        handle.view = view
        return view
    }

    func updateUIView(_ view: SCNView, context: Context) {
        context.coordinator.callbacks = (onOrbit, onZoom, onTap)
        context.coordinator.renderer?.update(
            scene: scene,
            camera: camera,
            hidden: hiddenSurfaceIds,
            selected: selectedSurfaceId
        )
    }

    @MainActor
    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var renderer: SceneKitRoomRenderer?
        var callbacks: (
            orbit: (Double, Double) -> Void,
            zoom: (Double) -> Void,
            tap: (CGPoint, CGSize) -> Void
        )?

        func attachGestures(to view: UIView) {
            let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
            pan.maximumNumberOfTouches = 2
            pan.delegate = self

            let pinch = UIPinchGestureRecognizer(target: self, action: #selector(handlePinch(_:)))
            pinch.delegate = self

            // Touch-less pan recogniser: only receives trackpad / mouse-wheel scrolling.
            let scroll = UIPanGestureRecognizer(target: self, action: #selector(handleScroll(_:)))
            scroll.allowedScrollTypesMask = .all
            scroll.maximumNumberOfTouches = 0

            let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))

            let recognizers: [UIGestureRecognizer] = [pan, pinch, scroll, tap]
            recognizers.forEach(view.addGestureRecognizer)
        }

        @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
            guard let view = gesture.view else { return }
            let t = gesture.translation(in: view)
            gesture.setTranslation(.zero, in: view)
            let k = OrbitCamera.companion.DEGREES_PER_POINT
            callbacks?.orbit(-Double(t.x) * k, Double(t.y) * k)
        }

        @objc private func handlePinch(_ gesture: UIPinchGestureRecognizer) {
            guard gesture.scale > 0 else { return }
            callbacks?.zoom(Double(gesture.scale))
            gesture.scale = 1
        }

        @objc private func handleScroll(_ gesture: UIPanGestureRecognizer) {
            guard let view = gesture.view else { return }
            let t = gesture.translation(in: view)
            gesture.setTranslation(.zero, in: view)
            if t.y != 0 { callbacks?.zoom(exp(Double(t.y) * Self.scrollZoomRate)) }
        }

        @objc private func handleTap(_ gesture: UITapGestureRecognizer) {
            guard let view = gesture.view else { return }
            callbacks?.tap(gesture.location(in: view), view.bounds.size)
        }

        nonisolated func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
        ) -> Bool { true }

        private static let scrollZoomRate = 0.005
    }
}

private final class LayoutReportingSCNView: SCNView {
    var onLayout: ((CGSize) -> Void)?

    override func layoutSubviews() {
        super.layoutSubviews()
        onLayout?(bounds.size)
    }
}
