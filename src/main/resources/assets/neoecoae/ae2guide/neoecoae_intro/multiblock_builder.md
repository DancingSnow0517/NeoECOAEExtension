---
navigation:
  title: Multiblock Auto Builder
  icon: neoecoae:structure_terminal
  parent: neoecoae_intro/index.md
---

# Multiblock Auto Builder

To build an [ECO Storage System](storage_system.md), [ECO Computation System](computation_system.md) or [ECO Crafting System](crafting_system.md), right-click its host with an empty hand and use the Build button inside the host's UI. Each system's L4/L6/L9 hosts use their own screens.

## Selecting the Length and Building

Place the host and confirm its facing, then click its Build button to open the settings window. The window has four rows: a length label, a length input, length adjustment buttons, and the mirror switch with the build button. Enter a length directly or adjust it with the −10, −1, +1 and +10 buttons. The input tooltip shows the allowed range.

Check the length, mirror setting and materials, then start building. The return button in the upper-right corner closes the window and retains its settings. Settings and building are unavailable while the structure is formed or a build is already in progress.

## Structure Terminal

Opening the <ItemLink id="neoecoae:structure_terminal" /> displays only this notice: “Please use the Build button in the corresponding host's UI.” Structure previews remain available on the machines' recipe viewer pages.

<RecipeFor id="neoecoae:structure_terminal" />

## Materials and Build Progress

Materials are taken from the player's accessible inventory, including container items that expose an item handler capability, such as supported shulker boxes and backpacks.

In survival mode, blocks are placed over server ticks and materials are consumed only when each block is placed. In creative mode, missing blocks are placed immediately if there are no conflicts.

The builder does not overwrite conflicting blocks. Missing materials, conflicts or blocked positions can prevent or interrupt a build. Replenish materials and clear blocked positions, then retry from the corresponding host's builder window.
