import Metal
import SceneKit
import SharedLogic
import UIKit

/// Owns the SceneKit scene for the 3D preview: one node per surface built
/// from the shared `RoomScene`, an orbit camera mirrored from the shared
/// `OrbitCamera`, per-wall visibility and the selection tint.
///
/// Lighting is baked into vertex colours by the shared module and every
/// material uses `.constant`, so iOS shades faces exactly like Android and
/// no per-pixel lighting runs on the GPU. The shade is a linear-light factor:
/// SceneKit's linear-space rendering (on by default; never set
/// `SCNDisableLinearSpaceRendering`) linearises sRGB `UIColor`s and textures
/// before multiplying by it, matching Filament's explicit sRGB → linear path.
@MainActor
final class SceneKitRoomRenderer {
    let scene = SCNScene()
    let cameraNode = SCNNode()

    private var roomScene: RoomScene = RoomScene.companion.EMPTY
    private var orbit = OrbitCamera(yawDegrees: OrbitCamera.companion.DEFAULT_YAW, pitchDegrees: OrbitCamera.companion.DEFAULT_PITCH, zoom: 1.0)
    private var hiddenIds: Set<String> = []
    private var selectedId: String?
    private var aspect: Double = 1

    /// Keyed by surface id; values are reused while the shared mesh instance is unchanged.
    private var surfaceNodes: [String: (mesh: SurfaceMesh, node: SCNNode)] = [:]
    private var textures: [String: UIImage] = [:]
    private var pendingTextures: Set<String> = []

    init() {
        scene.background.contents = uiColor(SceneColors.shared.BACKGROUND)
        let camera = SCNCamera()
        camera.projectionDirection = .vertical
        camera.fieldOfView = CGFloat(OrbitCamera.companion.FOV_Y_DEGREES)
        camera.wantsHDR = false
        cameraNode.camera = camera
        scene.rootNode.addChildNode(cameraNode)
    }

    func update(scene newScene: RoomScene, camera: OrbitCamera, hidden: Set<String>, selected: String?) {
        if newScene !== roomScene {
            roomScene = newScene
            syncSurfaces()
        }
        orbit = camera
        hiddenIds = hidden
        selectedId = selected
        applyVisibilityAndSelection()
        applyCamera()
    }

    func setViewportSize(_ size: CGSize) {
        guard size.width > 0, size.height > 0 else { return }
        aspect = Double(size.width / size.height)
        applyCamera()
    }

    /// Offscreen render with the current camera and hidden walls, without the
    /// selection tint. Renders a clone of the visible nodes so the live
    /// scene (and its highlight) is never touched.
    func snapshot(device: MTLDevice?, size: CGSize) -> UIImage? {
        let exportScene = SCNScene()
        exportScene.background.contents = scene.background.contents
        for (id, entry) in surfaceNodes where !hiddenIds.contains(id) {
            // clone() shares geometry and materials with the live node
            let node = entry.node.clone()
            if id == selectedId {
                for child in node.childNodes {
                    guard let geometry = child.geometry?.copy() as? SCNGeometry else { continue }
                    geometry.materials = geometry.materials.map { material in
                        let untinted = material.copy() as? SCNMaterial ?? material
                        untinted.multiply.contents = UIColor.white
                        return untinted
                    }
                    child.geometry = geometry
                }
            }
            exportScene.rootNode.addChildNode(node)
        }
        let exportCamera = SCNNode()
        exportCamera.camera = cameraNode.camera?.copy() as? SCNCamera
        pose(exportCamera, aspect: Double(size.width / size.height))
        exportScene.rootNode.addChildNode(exportCamera)

        let renderer = SCNRenderer(device: device ?? MTLCreateSystemDefaultDevice(), options: nil)
        renderer.scene = exportScene
        renderer.pointOfView = exportCamera
        return renderer.snapshot(atTime: 0, with: size, antialiasingMode: .multisampling4X)
    }

    // MARK: - Scene sync

    private func syncSurfaces() {
        var next: [String: (mesh: SurfaceMesh, node: SCNNode)] = [:]
        for mesh in roomScene.surfaces {
            if let existing = surfaceNodes[mesh.surfaceId], existing.mesh === mesh {
                next[mesh.surfaceId] = existing
            } else {
                surfaceNodes[mesh.surfaceId]?.node.removeFromParentNode()
                let node = makeNode(for: mesh)
                scene.rootNode.addChildNode(node)
                next[mesh.surfaceId] = (mesh, node)
            }
        }
        for (id, entry) in surfaceNodes where next[id] == nil {
            entry.node.removeFromParentNode()
        }
        surfaceNodes = next
        releaseUnusedTextures()
    }

    private func usedTexturePaths() -> Set<String> {
        var paths: Set<String> = []
        for entry in surfaceNodes.values {
            for part in entry.mesh.parts {
                if let path = (part.material as? SceneMaterialTile)?.texturePath { paths.insert(path) }
            }
        }
        return paths
    }

    /// Drop decoded images no remaining part uses, e.g. after a tile group's image was replaced.
    private func releaseUnusedTextures() {
        let used = usedTexturePaths()
        textures = textures.filter { used.contains($0.key) }
    }

    private func makeNode(for mesh: SurfaceMesh) -> SCNNode {
        let node = SCNNode()
        node.name = mesh.surfaceId
        for part in mesh.parts where part.indexCount > 0 {
            let child = SCNNode(geometry: makeGeometry(part))
            node.addChildNode(child)
        }
        return node
    }

    private func makeGeometry(_ part: MeshPart) -> SCNGeometry {
        let count = Int(part.vertexCount)
        let sources = [
            source(SceneKitBuffers.shared.positions(part: part), .vertex, count, components: 3),
            source(SceneKitBuffers.shared.normals(part: part), .normal, count, components: 3),
            source(SceneKitBuffers.shared.shadeColors(part: part), .color, count, components: 3),
            source(SceneKitBuffers.shared.uvs(part: part), .texcoord, count, components: 2),
        ]
        let element = SCNGeometryElement(
            data: SceneKitBuffers.shared.indices(part: part),
            primitiveType: .triangles,
            primitiveCount: Int(part.indexCount) / 3,
            bytesPerIndex: 4
        )
        let geometry = SCNGeometry(sources: sources, elements: [element])
        geometry.materials = [makeMaterial(part.material)]
        return geometry
    }

    private func source(_ data: Data, _ semantic: SCNGeometrySource.Semantic, _ count: Int, components: Int) -> SCNGeometrySource {
        SCNGeometrySource(
            data: data,
            semantic: semantic,
            vectorCount: count,
            usesFloatComponents: true,
            componentsPerVector: components,
            bytesPerComponent: 4,
            dataOffset: 0,
            dataStride: 4 * components
        )
    }

    // Kotlin/Native exports classes nested in an interface with concatenated
    // Swift names (`SceneMaterialSolid`), since Swift protocols cannot nest types.
    private func makeMaterial(_ sceneMaterial: SceneMaterial) -> SCNMaterial {
        let material = SCNMaterial()
        material.lightingModel = .constant
        material.isDoubleSided = false
        material.diffuse.wrapS = .clamp
        material.diffuse.wrapT = .clamp
        material.diffuse.mipFilter = .linear
        if let solid = sceneMaterial as? SceneMaterialSolid {
            material.diffuse.contents = uiColor(solid.color)
        } else if let tile = sceneMaterial as? SceneMaterialTile {
            if let path = tile.texturePath, let image = textures[path] {
                material.diffuse.contents = image
            } else {
                material.diffuse.contents = uiColor(tile.color)
                if let path = tile.texturePath { loadTexture(path) }
            }
        }
        return material
    }

    /// Decodes off the main thread, then swaps the texture into every material that uses it.
    private func loadTexture(_ path: String) {
        guard !pendingTextures.contains(path) else { return }
        pendingTextures.insert(path)
        Task { [weak self] in
            let image = await Task.detached(priority: .userInitiated) { Self.decodeTexture(path) }.value
            guard let self else { return }
            self.pendingTextures.remove(path)
            guard let image, self.usedTexturePaths().contains(path) else { return }
            self.textures[path] = image
            self.applyTexture(image, path: path)
        }
    }

    private func applyTexture(_ image: UIImage, path: String) {
        for entry in surfaceNodes.values {
            for (child, part) in zip(entry.node.childNodes, entry.mesh.parts.filter { $0.indexCount > 0 }) {
                guard let tile = part.material as? SceneMaterialTile, tile.texturePath == path else { continue }
                child.geometry?.firstMaterial?.diffuse.contents = image
            }
        }
    }

    /// Bounded like Android: tiles are small on screen, so 512 px keeps GPU memory low.
    nonisolated private static func decodeTexture(_ path: String) -> UIImage? {
        guard let image = UIImage(contentsOfFile: path) else { return nil }
        let maxSide: CGFloat = 512
        let longest = max(image.size.width, image.size.height)
        guard longest > maxSide else { return image }
        let scale = maxSide / longest
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
    }

    // MARK: - State

    private func applyVisibilityAndSelection() {
        let tint = uiColor(SceneColors.shared.SELECTION_TINT)
        for (id, entry) in surfaceNodes {
            entry.node.isHidden = hiddenIds.contains(id)
            let multiply: UIColor = id == selectedId ? tint : .white
            for child in entry.node.childNodes {
                child.geometry?.firstMaterial?.multiply.contents = multiply
            }
        }
    }

    private func applyCamera() {
        pose(cameraNode, aspect: aspect)
    }

    private func pose(_ node: SCNNode, aspect: Double) {
        let pose = orbit.pose(bounds: roomScene.bounds, aspect: aspect)
        node.camera?.zNear = pose.near
        node.camera?.zFar = pose.far
        node.position = vector(pose.eye)
        node.look(at: vector(pose.target), up: vector(pose.up), localFront: SCNVector3(0, 0, -1))
    }

    private func vector(_ v: Vec3) -> SCNVector3 {
        SCNVector3(Float(v.x), Float(v.y), Float(v.z))
    }

    private func uiColor(_ rgb: Int32) -> UIColor {
        UIColor(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}
