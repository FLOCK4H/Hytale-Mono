# Findings: UI (Brightness Tweaks / Hytale)

## Reality check (Hytale UI is server-driven)
- Hytale UI is rendered client-side, but interaction logic is server-side. Every button click / slider change can incur network latency.
- UI markup currently uses legacy `.ui` files; NoesisGUI/XAML is the planned future, so keep UI logic decoupled from the markup as much as possible.

## Modern, UX-friendly UI principles that map well to Hytale

### 1) Match native visuals
- Prefer using built-in UI assets/styles from `Common.ui` instead of shipping custom textures for basic panels/buttons.
- Reuse native patterns like `@Container`, separators, `@TextButton`, and `@DefaultSliderStyle` so the mod UI “feels first-party”.

### 2) Be latency-aware
- Don’t bind server callbacks to high-frequency events unless you truly need them.
- Prefer “commit” style events (e.g., `MouseButtonReleased` on sliders) instead of `ValueChanged` for heavy actions, so the UI doesn’t feel laggy on real servers.
- When you do need continuous feedback, keep the server response lightweight (e.g., update text labels only).

### 3) Always acknowledge UI events
- For `InteractiveCustomUIPage`, always call `sendUpdate(...)` (or switch pages) after handling an event.
- If you don’t, the client can get stuck showing “Loading…” and the UI becomes unusable.

### 4) Make state obvious and reversible
- Always show the current effective configuration in the UI (what will happen *right now*).
- Provide a safe “Reset/Disable” action that returns the player to a known-good state.
- Prefer explicit “this clears that” UX for mutually exclusive settings (example: custom tint vs warmth).

### 5) Keep layout responsive
- Use anchors and avoid hard-coding pixel-perfect positioning where possible.
- Avoid huge panels; favor a readable width and short vertical stacking, like native pages.

### 6) Accessibility basics
- Avoid tiny fonts for important values, keep contrast high, and don’t encode meaning only by color.
- Provide short helper text for non-obvious behavior (e.g., “requires torch in utility belt”).

## Implementation decisions for this repo (v0.2.x UI baseline)
- Use a single `InteractiveCustomUIPage` for “settings” rather than a HUD (HUDs are non-interactive).
- Keep UI markup in `src/main/resources/Common/UI/Custom/...` and load it with `UICommandBuilder.append(...)`.
- Use `Common.ui` for styling; ship only the mod-specific `.ui` file.
- Bind sliders with `ValueChanged` for preview feedback and commit on `MouseButtonReleased` for persistence-heavy actions.
- Keep persistence server-side (`BrightnessTweaksConfigStore`) and treat UI as a view/controller over persisted state.
- Avoid setting slider input values (e.g., `#SomeSlider.Value`) from the server; this can crash/disconnect clients. Prefer showing the current setting in adjacent labels, and only use server `Set` commands for safe properties like `.Text`.

## Known limitations / future work
- Until NoesisGUI becomes the public, stable path, treat `.ui` markup as volatile and keep it simple.
- If/when NoesisGUI becomes available, port the layout first (visual parity), then iterate on richer interactions client-side (if supported).

## Debug note: sliders “stuck at min”
- Symptom: slider handle doesn’t move, and the server only receives the minimum value.
- Primary cause (in this repo): calling `InteractiveCustomUIPage.sendUpdate(UICommandBuilder, boolean)` cleared the page’s event bindings, so after the first interaction the client no longer had slider bindings and the slider appeared stuck.
- Secondary cause (in this repo): sending `sendUpdate(...)` while a slider is being dragged can make the slider *feel* stuck (the UI update resets the control state to the markup default). If the server acks `ValueChanged` with a page update, the slider can snap back to min and the release event value is read as 0.
- Fix: always send updates with event bindings (`sendUpdate(uiCommands, uiEventBuilderWithBindings, ...)`) and avoid updating the page while the slider is dragging. Bind `MouseButtonReleased` and read `#Slider.Value` on release.
- Note: slider `ValueChanged` events can be high-frequency; prefer coarser `Step` values (and/or debouncing) to keep server-side UI responsive.
- Practical tip: avoid `Min: 0; Max: 1;` sliders for “real” settings; in practice it behaves like a binary toggle. Use `0..100` (step `1`) and normalize in code.
- Persistence tip: when reopening a page, initialize slider positions from the persisted config in `build(...)` (set `#Slider.Value` once after `append(...)`), while keeping interaction commits on `MouseButtonReleased`.
- Input tip: when “Clear” actions modify persisted state, also explicitly update the input field value with a UI `Set` on `#TintInput.Value` so the screen reflects the cleared state immediately.

### Important: don’t clear event bindings
- `InteractiveCustomUIPage.sendUpdate(UICommandBuilder, boolean)` calls `sendUpdate(..., null, ...)`, which clears all event bindings on the client.
- Symptom: after the first interaction, widgets stop responding (sliders appear stuck, values go missing, buttons stop firing).
- Fix: always send updates with event bindings, e.g. `sendUpdate(uiCommands, uiEventBuilderWithBindings, false)` (or `rebuild()` if you want to regenerate the page).
