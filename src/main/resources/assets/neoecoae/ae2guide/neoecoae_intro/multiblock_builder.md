---
navigation:
  title: Multiblock Auto Builder
  icon: neoecoae:crafting_system_l9
  parent: neoecoae_intro/index.md
---

# Multiblock Auto Builder

Neo ECO AE provides an automatic building feature for its main multiblock systems, making large structures much easier to assemble.

## Supported Systems

The auto builder currently supports the following multiblock systems:

- [ECO Storage System](storage_system.md)
- [ECO Computation System](computation_system.md)
- [ECO Crafting System](crafting_system.md)

## Opening the Builder Panel

When you open a multiblock controller GUI, a structure builder button appears on the side of the interface. Clicking it opens a separate floating panel.

The panel can be dragged, allowing you to inspect system information and builder controls at the same time.

## Panel Features

The auto builder panel provides the following features:

- Length adjustment: selects the current multiblock length to build
- Preview: checks missing blocks, conflicting blocks, reusable blocks, and required item counts
- Build: starts automatic placement after confirming the required materials
- Status display: shows preview results, build progress, and interruption states

## Preview Behavior

When you click Preview, the system generates a structure plan based on the controller orientation and selected length, then reports:

- Missing blocks
- Conflicting blocks
- Reusable blocks
- Required item count

If the world already contains blocks that should not be replaced, they are marked as conflicts and the auto builder will not overwrite them automatically.

## Auto Build Behavior

### Creative Mode

In creative mode, if the structure has no conflicts, all missing blocks are placed instantly.

### Survival Mode

In survival mode, the structure is built step by step on server ticks instead of being placed all at once.

This behavior exists to:

- Avoid large world changes in a single instant
- Keep material consumption aligned with actual block placement
- Allow the build process to stop cleanly when blocked

## Material Consumption Rules

The auto builder does not consume all materials up front. Instead, it consumes items only when a block is successfully placed.

The system can count and consume materials from accessible inventory sources, including container items with item handler support, such as:

- Shulker-like containers
- Backpack-type items
- Other container items that expose an item handler capability

## Conflicts and Interruptions

The auto builder may fail to start or may stop midway in cases such as:

- Conflicting blocks in the planned structure
- Missing materials
- The player leaving or becoming unavailable for the build session
- Target positions becoming blocked during the build process

The panel will show the corresponding state so you can preview again and adjust the structure.

## Tips

- Place the controller first and confirm its facing before using the auto builder
- For large structures, preview first to confirm the selected length and available space
- Watch the preview counts before changing structure scale
- In survival mode, keep enough materials available in your inventory or supported container items
