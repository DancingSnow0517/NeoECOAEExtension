# ECO Host Unified UI Design

## Scope

Redesign the three ECO subsystem host UIs with one shared visual system:

- Storage system host
- Computation system host
- Crafting system host

The multiblock builder entry remains available from each host UI. The builder floating panel is not redesigned in this pass, but its launcher and surrounding shell should visually fit the new host panels.

## Goals

- Use one consistent host panel size and visual language across storage, computation, and crafting.
- Make storage status clearer than the current text-only list.
- Keep storage extensible for any number of registered `ECOCellType` entries.
- Keep computation and crafting single-screen with no scrolling in normal content.
- Avoid progress bars for values that do not have a meaningful maximum.
- Make layout localization-friendly, especially for longer English labels.
- Prefer reusable registered LowDragLib widgets so future LSS styling is practical.

## Visual Direction

Use the direction from `output/ui-mockups/eco-host-unified-ui-v2.html`:

- Modern light technical panel instead of thick retro borders.
- Thin low-contrast borders, subtle panel backgrounds, and restrained status colors.
- Unified panel target size: about `460 x 380` in the mockup. The exact LowDragLib size can be adjusted slightly if Minecraft scaling or font metrics require it.
- Shared structure:
  - Header: localized host name, system subtitle, online/running state.
  - Top metrics: three equal cards.
  - Details: system-specific cards.
  - Footer: small hint/status area and multiblock builder button.

Top metric cards share the same structure: label, large value, bottom spacer. If the metric has `used / max`, the spacer contains a progress bar. If the metric is scalar, the spacer is invisible and only preserves height.

## Shared Components

Create reusable UI components under `cn.dancingsnow.neoecoae.gui.widget` where appropriate. Components that should be stylable from LSS should use `@LDLRegister(..., registry = "ldlib2:ui_element")`, following `PatternItemSlot`.

Recommended components:

- `ECOHostPanel`
  - Shared fixed-size shell with header, metrics row, details area, footer, and builder launcher slot.
  - Can also be implemented as a Java helper if registering the whole container is not useful for LSS.

- `ECOHostMetric`
  - Label, value, optional progress.
  - Supports ratio metrics and scalar metrics without implying a false maximum.
  - Stable classes for LSS: host metric, metric label, metric value, metric bar.

- `ECOStatusBadge`
  - Compact status indicator for `ONLINE`, `RUNNING`, or future unavailable states.

- `ECOInfoTile`
  - Small value tile used in computation and crafting detail grids.

- `ECOStatLine`
  - Label, optional progress bar, value. Used inside detail cards.

- `ECOChannelCard`
  - Storage-specific card for a registered `ECOCellType`.
  - Shows type name, type usage, and byte usage.

LowDragLib registration should be used for widgets whose styling should be available in LSS. Pure factory helpers are acceptable for composition-only code.

## Storage Host

Top metrics:

- Type Usage: sum of all used types and total types.
- Storage Usage: sum of all used bytes and total bytes.
- Energy Buffer: stored AE and max AE.

Details:

- Scrollable `Storage Channels` list.
- Generate one card per `NERegistries.CELL_TYPE` entry.
- Each channel card shows:
  - Cell type description.
  - Type usage `used / total` with progress when total is greater than zero.
  - Byte usage `used / total` with progress when total is greater than zero.

Storage is the only host that should normally scroll, because `ECOCellType` is extensible.

## Computation Host

Top metrics:

- CPU Storage: used bytes and total bytes. This should be the first card.
- Thread Usage: used thread and total thread.
- Parallel Count: scalar value without a progress bar.

Details:

- Single-screen, no scrolling.
- Show compact tiles for:
  - Active vCPU.
  - Max vCPU.
  - Parallel accelerators.
  - Free CPU memory or available bytes.
- Show a CPU pool detail card with thread and storage stat lines if space permits.
- Do not include the previous generic `Runtime State` card unless it is replaced by actionable data.

## Crafting Host

Top metrics:

- Working Threads: running threads and available worker capacity.
- Total Parallelism: scalar value without a progress bar.
- Max Energy Usage: scalar estimate without a progress bar.

Details:

- Single-screen, no scrolling.
- Show compact tiles for:
  - Pattern buses.
  - Parallel cores.
  - Worker cores.
  - Overflow percentage or overflow threads.
- Show overclock and cooling detail:
  - Theoretical overclock.
  - Effective overclock.
  - Current coolant max overclock.
  - Coolant buffer if available.
  - Energy usage line.
- Keep `Enable Overclock` and `Enable Active Cooling` visible without scrolling.
- Switches should stay on the right side of their rows and must not wrap under labels.

## Localization

- Labels and titles use translatable components.
- Long labels should wrap or use `TextWrap.HOVER_ROLL` depending on the component.
- Numeric values should remain visible even when labels are long.
- English labels in the mockup are representative of the required space.

## Styling

Extend `assets/neoecoae/lss/eco.lss` with named styles for the new widgets. Prefer style classes over hard-coded colors where LowDragLib supports it.

Suggested classes:

- `eco-host-panel`
- `eco-host-header`
- `eco-host-status`
- `eco-host-metrics`
- `eco-host-metric`
- `eco-host-detail-card`
- `eco-host-tile`
- `eco-host-stat-line`
- `eco-host-footer`

Existing `panel_bg`, `panel_border`, button, and progress styles can be reused where they still fit.

## Data Flow

The three block entities keep their existing synced fields. UI construction should move most shared layout into helper/component classes so each block entity only assembles its data:

- Storage supplies aggregate metrics and a list of cell type channel models.
- Computation supplies CPU storage, thread usage, parallel count, and detail values.
- Crafting supplies thread usage, parallelism, energy estimate, component counts, overclock/cooling values, and switch bindings.

Where useful, introduce small immutable view model records in the GUI package. They should hold `Supplier<Component>`, `LongSupplier`, `IntSupplier`, or similar dynamic suppliers rather than copying stale values.

## Error And Empty States

- If a host is not formed or cluster data is unavailable, metrics should show zero values and the status badge should reflect the inactive state if that data is available.
- Progress ratio helpers must handle zero totals without division by zero.
- Storage channel cards with zero total should show `0 / 0` and an empty progress bar.
- Crafting thread percentage must not divide by zero when available threads are zero.

## Testing And Verification

- Compile with Gradle after implementation.
- Open each host UI in game or a dev client if practical.
- Verify storage with more than three cell types scrolls only the channel list.
- Verify computation and crafting fit in the shared panel without scrolling.
- Verify long English labels do not overlap numeric values.
- Verify switches remain aligned on the right.
- Verify multiblock builder button still opens the existing floating builder panel.

