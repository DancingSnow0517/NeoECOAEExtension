---
navigation:
  title: Structure Terminal
  icon: neoecoae:structure_terminal
  parent: neoecoae_intro/index.md
---

# Structure Terminal

The Forge 1.20.1 edition uses the <ItemLink id="neoecoae:structure_terminal" /> to preview and build multiblocks.

<RecipeFor id="neoecoae:structure_terminal" />

## Supported Systems

The auto builder currently supports the following multiblock systems:

- [ECO Storage System](storage_system.md)
- [ECO Computation System](computation_system.md)
- [ECO Crafting System](crafting_system.md)
- Large Integrated Working Station

Storage, computation and crafting systems retain all L4/L6/L9 tiers. The large workstation has a fixed structure.

## Configuring the Terminal

Hold the terminal and right-click to open its configuration. Right-click a controller to link it, then select the structure type, tier and length. Use the preview controls to inspect mirrored layouts, formed appearance and individual layers.

Select Build, Mirrored Build or Dismantle, then Shift-right-click the target controller to execute that operation. Machine screens have no automatic-building button or floating builder window.

## Panel Features

The auto builder panel provides the following features:

- Length adjustment: selects the current multiblock length to build
- Preview: inspect structure layers, missing blocks, conflicts, reusable blocks and required materials
- Build: Shift-right-click the controller to start the selected operation
- Status display: shows preview results, build progress, and interruption states

## Preview Behavior

The terminal generates a structure plan from the controller orientation and selected length, then reports:

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

Reopen the terminal to check the target and material state before retrying.

## Tips

- Place the controller first and confirm its facing before using the auto builder
- For large structures, preview first to confirm the selected length and available space
- Watch the preview counts before changing structure scale
- In survival mode, keep enough materials available in your inventory or supported container items
