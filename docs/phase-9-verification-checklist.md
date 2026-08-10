# Phase 9 Integration Verification Checklist

Manual verification checklist for the Phase 9 integration pipeline (compute → layout → preview → cut list → export) on both iOS and Android.

## How to use

- Run every step in order; each step is a single, verifiable action.
- **Prerequisites:** Build and install the latest versions of both apps (iOS: run `iosApp` scheme in Xcode on a simulator or device; Android: `./gradlew :androidApp:installDebug` on an emulator or device). Have a clean app install or reset app data before starting so the step-by-step walkthrough reproduces exactly.
- Record: device/simulator model + OS version, and tick/untick each checkbox. Unticked boxes should be reported as failures with the step number.
- Quote labels exactly as shown. Where a label differs on screen, note it — the checklist is the source of truth for this phase.

---

## Section 1: Shared Pipeline Verification

Run these from the project root before touching either app UI.

- [ ] `./gradlew :sharedLogic:jvmTest` passes (2 pre-existing debounce flakiness allowed)
- [ ] New `ComputePipelineIntegrationTest` passes (it exercises the full pipeline: room/surface setup → tile group assignment → layout compute → cut list, end-to-end in the shared module)

## Section 2: iOS Verification

Walkthrough on the iOS simulator/device.

- [ ] Launch app — Home screen shows the empty state "Tap + to create your first project" with a **+** button in the top-right
- [ ] Tap **+** → Create Project sheet opens → name it "Bathroom Renovation" → tap **Create** → project appears in the list; open it
- [ ] Project opens with tabs **Rooms** and **Tile Library** — Rooms tab is selected; tap **+** to add a room, enter **Width** 3000, **Depth** 2500, **Height** 2400 → save → room appears showing "3000 × 2500 × 2400 mm"; open the room
- [ ] Room Editor opens with segmented tabs **Surfaces | Layout | Preview | Cut List** — Surfaces tab is selected; tap **Generate** and confirm → five surfaces are listed: Front, Back, Left, Right, Floor (each labeled by type and dimensions, e.g. "Wall 3000×2400")
- [ ] Switch to the **Tile Library** tab → tap **Add** → create a tile group named "Ceramic White" with tile **Width** 300, **Height** 200 → tap **Add** → group appears in the library listing "300 × 200 mm"
- [ ] Switch back to **Rooms** → open "Bathroom Renovation" → open the room → **Surfaces** tab → tap any surface → Surface Detail shows "Tile Groups" with "No tile groups assigned" → tap **Add** → assign "Ceramic White" → a tile group card appears showing the region (e.g. "Region: (0, 0) 3000×2400") covering the full surface
- [ ] Go to **Layout** tab → select the surface (tap it in the surface list) → tiles render on the Canvas at the assigned region
- [ ] Drag on the Layout Canvas → tiles shift in real time as you drag (offset preview), and settle after release
- [ ] Tap the **Lock:** chip for a second surface (locks with the purple lock icon) → drag the first surface again → the locked surface's tiles shift with it (lock propagation)
- [ ] Tap **Undo** (arrow icon button, top-right action row) → tiles snap back to their pre-drag offsets
- [ ] Switch to **Preview** tab → 3D isometric room renders with tiled surfaces
- [ ] Tap on surfaces in the 3D view → selection changes, "Selected: …" label updates at the top
- [ ] Tap **◀** / **▶** buttons → perspective/view angle rotates, angle readout updates (e.g. "45°")
- [ ] Switch to **Cut List** tab → cut entries are shown (each with tile group name, "Size: W × H mm", and count "N×", plus "N cuts" per location); if no cuts exist, the "all tiles fit" empty state shows
- [ ] Go back to **Layout** → adjust a surface's offsets (drag) → switch to **Cut List** → cut entries and counts reflect the updated layout

## Section 3: iOS Export Verification

- [ ] On **Layout** tab → tap **Export** → export sheet opens
- [ ] Adjust the **DPI** stepper (150, 200, 250, 300) → preview thumbnail updates to the new resolution
- [ ] Tap **Save to Photos** → grant photo access when prompted → "Saved!" confirmation appears
- [ ] Open the Photos app → verify the exported image is there (2D tile layout at the chosen DPI)
- [ ] Re-open the export sheet → tap **Share** → system share sheet opens (AirDrop, Messages, Files, etc.); dismiss it
- [ ] Switch to **Preview** tab → tap **Export** → export sheet opens with a 3D isometric preview thumbnail → DPI stepper works → tap **Save to Photos** → "Saved!" → verify the 3D render is in Photos

## Section 4: Android Verification

Same flow as iOS, with Android-specific labels.

- [ ] Launch app — Home shows "Projects" with a **+** floating action button → tap **+** → "New Project" dialog → enter "Bathroom Renovation" in **Project name** → tap **Create** → open the project
- [ ] Project opens with tabs **Rooms** and **Tile Library** → add a room (e.g. 3000×2500×2400 mm) → open it → **Generate Surfaces** dialog → verify **Front wall / Back wall / Left wall / Right wall / Floor** toggles → generate → five surfaces listed
- [ ] Add a tile group in **Tile Library** ("New Tile Group" dialog: "Tile name", Width 300, Height 200, tap **Add**) → open a surface in **Surfaces** tab → tap **Add Tile Group** → assign the group → region covers the surface
- [ ] **Layout** tab: tiles render on the Canvas; drag shifts tiles in real time; lock a surface (lock chip) and drag another — locked surface propagates; **Undo** snaps tiles back
- [ ] **Preview** tab: 3D room renders; tapping a surface selects it ("Selected: …" updates); **◀** / **▶** buttons rotate the view; **Top-Down** toggle works
- [ ] **Cut List** tab: cut entries shown with tile group name, dimensions, and counts; empty state reads "No cut tiles found. Compute a layout to see the cut list."; entries update after layout changes (drag in Layout → back to Cut List); **More** / **Less** expand/collapse per entry

## Section 5: Android Export Verification

- [ ] On **Layout** tab → tap **Export** → "Export Layout" dialog opens with **DPI** readout and **− / +** stepper buttons (150–300)
- [ ] Preview image is shown in the dialog (spinner while rendering, then the rendered layout thumbnail); adjusting DPI re-renders the preview
- [ ] Tap **Save** → "Saved to gallery!" message appears → open the gallery/Pictures app → verify the image was saved under Pictures/TileLayout/
- [ ] Tap **Share** → Android share sheet opens (targets the same file via FileProvider); dismiss it
- [ ] On **Preview** tab → tap **Export** → "Export 3D Preview" dialog opens → DPI stepper and preview work → **Save** → "Saved to gallery!" → verify the 3D render appears in Pictures/TileLayout/

## Section 6: Cross-Cutting Concerns

- [ ] Export at different DPIs (150, 200, 250, 300) on both platforms: each produces an image at the correct resolution — pixel dimensions scale with DPI (e.g. the 300 DPI export is 2× the pixel width of the 150 DPI export of the same view); higher DPI is visibly sharper
- [ ] Export with an empty layout (no tile groups assigned to any surface): both platforms show an appropriate message or disabled export rather than crashing or exporting a blank image
- [ ] Export with no surface selected on the Layout tab: appropriate message/disabled state, no crash
- [ ] Rapid drag on the Layout Canvas immediately followed by Export: no crash, export completes with the last-rendered layout
- [ ] After multiple layout adjustments (drag, undo, re-drag, offset changes), the Cut List shows correct counts consistent with the final layout on both platforms
