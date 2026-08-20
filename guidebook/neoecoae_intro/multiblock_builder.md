---
navigation:
  title: Multiblock Auto Builder
  icon: neoecoae:crafting_system_l9
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:structure_terminal
---

# Multiblock Auto Builder

In 1.20.1, Neo ECO AE provides automatic multiblock construction through the **Structure Terminal**. The terminal selects a structure, shows its pattern and materials, and starts construction after confirmation.

## Supported Systems

The auto builder currently supports the following multiblock systems:

- [ECO Storage System](storage_system.md)
- [ECO Computation System](computation_system.md)
- [ECO Crafting System](crafting_system.md)

## Opening the Structure Terminal

When holding the <ItemLink id="neoecoae:structure_terminal" />:

- Normal right-click in the air or on a block opens the Structure Terminal panel.
- Normal right-click on a multiblock controller opens the panel and links that controller to the terminal. The system and tier are detected automatically.
- If you open the panel in the air, you can select the crafting, storage, or computation system and its tier manually. A controller must still be linked before construction can be executed.

The terminal remembers its linked controller. The controller must be in the same dimension and within interaction range.

## Panel Features

The Structure Terminal panel provides the following features:

- System and tier selection: chooses the crafting, storage, or computation system and its L4/C4/F4, L6/C6/F6, or L9/C9 tier
- Length adjustment: uses `-` and `+` to select the variable structure length
- Pattern view: switches between the prototype, formed, and mirrored pattern, with layer navigation
- Material list: shows the blocks and quantities required by the selected pattern
- Operation mode: selects standard build, mirrored build, or dismantle
- Status display: shows the linked controller's formed state, material state, and build progress

## Preview and Build Flow

1. Place the target system's controller and confirm that it is facing the intended direction.
2. Hold the Structure Terminal and normal right-click the controller to open the panel and link the target.
3. Select the system, tier, and length, then check the pattern, mirror direction, and material list.
4. Select **Build** or **Mirrored Build**, then close the terminal panel. The selected mode is stored in the terminal.
5. Hold Shift and right-click the linked controller with the same Structure Terminal to start construction.

The pattern view shows the construction plan. Selecting **Build** or **Mirrored Build** only arms the operation; it does not place blocks immediately. The world positions and materials are checked again when construction starts.

Blocks that should not be replaced are treated as conflicts and are not overwritten. Construction also does not run again when the controller is already formed.

## Auto Build Behavior

### Creative Mode

In creative mode, after the Shift-right-click, all missing blocks are placed instantly when the structure has no conflicts.

### Survival Mode

In survival mode, the structure is built step by step on server ticks instead of being placed all at once.

This behavior exists to:

- Avoid large world changes in a single instant
- Keep material consumption aligned with actual block placement
- Allow the build process to stop cleanly when blocked

## Material Consumption Rules

The Structure Terminal does not consume all materials up front. Instead, it consumes items only when a block is successfully placed.

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

The Structure Terminal panel shows the corresponding state so you can review the pattern, materials, and target position.

## Dismantling

Select **Dismantle**, close the panel, then hold Shift and right-click the linked controller. Confirm the linked controller and operation mode before dismantling.

## Tips

- Place the controller first and confirm its facing before linking the terminal
- For large structures, check the pattern and material list before confirming the selected length and available space
- Check the mirrored pattern before building a mirrored structure
- In survival mode, keep enough materials available in your inventory or supported container items
