---
navigation:
  title: ECO Storage System
  icon: neoecoae:storage_system_l9
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:storage_system_l4
  - neoecoae:storage_system_l6
  - neoecoae:storage_system_l9
  - neoecoae:eco_drive
  - neoecoae:storage_interface
  - neoecoae:storage_casing
  - neoecoae:storage_vent
  - neoecoae:energy_cell_l4
  - neoecoae:energy_cell_l6
  - neoecoae:energy_cell_l9
  - neoecoae:eco_item_storage_cell_16m
  - neoecoae:eco_item_storage_cell_64m
  - neoecoae:eco_item_storage_cell_256m
  - neoecoae:eco_fluid_storage_cell_16m
  - neoecoae:eco_fluid_storage_cell_64m
  - neoecoae:eco_fluid_storage_cell_256m
---

# ECO Storage System

The ECO Storage System is an extensible multiblock storage solution that provides massive storage capacity integrated with your ME Network.

## Overview

The storage subsystem acts as a high-capacity storage extension for your AE2 network. It consists of a controller, drives for storage cells, energy cells for power storage, and various structural components.

## Tiers

There are three tiers of storage systems available:

| Tier | Controller | Base Cell Capacity | Energy per Energy Cell |
|------|------------|------------------|---------------|
| L4 | <ItemLink id="neoecoae:storage_system_l4" /> | 16 MiB per cell | 10,000,000 AE |
| L6 | <ItemLink id="neoecoae:storage_system_l6" /> | 64 MiB per cell | 100,000,000 AE |
| L9 | <ItemLink id="neoecoae:storage_system_l9" /> | 256 MiB per cell | 1,000,000,000 AE |

## Structure Components

### Controller

<Row>
  <BlockImage id="neoecoae:storage_system_l4" scale="2"></BlockImage>
  <BlockImage id="neoecoae:storage_system_l6" scale="2"></BlockImage>
  <BlockImage id="neoecoae:storage_system_l9" scale="2"></BlockImage>
</Row>

The controller (<ItemLink id="neoecoae:storage_system_l4" />, <ItemLink id="neoecoae:storage_system_l6" />, or <ItemLink id="neoecoae:storage_system_l9" />) is the core of the storage system. It must be placed at a valid position in the multiblock structure and determines the tier of the entire system.

### Storage Drive

<BlockImage id="neoecoae:eco_drive" scale="2"></BlockImage>

The <ItemLink id="neoecoae:eco_drive" /> holds ECO storage cells. Multiple drives can be added to expand storage capacity. Drives are placed in a row extending from the controller.

### Energy Cells

<Row>
  <BlockImage id="neoecoae:energy_cell_l4" scale="2"></BlockImage>
  <BlockImage id="neoecoae:energy_cell_l6" scale="2"></BlockImage>
  <BlockImage id="neoecoae:energy_cell_l9" scale="2"></BlockImage>
</Row>

High-density energy cells (<ItemLink id="neoecoae:energy_cell_l4" />, <ItemLink id="neoecoae:energy_cell_l6" />, or <ItemLink id="neoecoae:energy_cell_l9" />) provide power storage for the system. The energy cell tier must match the controller tier.

### Interface

<BlockImage id="neoecoae:storage_interface" scale="2"></BlockImage>

The <ItemLink id="neoecoae:storage_interface" /> connects the storage system to your ME Network.

### Heat Sink

<BlockImage id="neoecoae:storage_vent" scale="2"></BlockImage>

The <ItemLink id="neoecoae:storage_vent" /> is required for thermal management of the storage system.

### Casing

<BlockImage id="neoecoae:storage_casing" scale="2"></BlockImage>

The <ItemLink id="neoecoae:storage_casing" /> blocks form the frame of the multiblock structure.

## Building the Structure

1. Place the **Controller** facing outward
2. Build the structural frame using **Storage Casing** blocks around the controller (excluding the right side and back-right side)
3. Place the **Interface** at the designated position (back-left of controller)
4. Add **Drives** in a horizontal row extending from the right side of the controller
5. For each vertical column of drives on the right side, place one Energy Cell above, one Heat Sink in the middle, and one Energy Cell below on the back of the drives
6. Complete the structure with remaining casing blocks

The structure is extensible - you can add more drives and energy cells to increase capacity.

If you want to assemble the structure more quickly, see [Multiblock Auto Builder](multiblock_builder.md) for automatic preview and building tools.

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../scenes/store_min.nbt" />
  <IsometricCamera yaw="45" pitch="30" />
</GameScene>

## Storage Cells

The following ECO storage cells can be used in the drives:

### Item Storage

<ItemGrid>
  <ItemIcon id="neoecoae:eco_item_storage_cell_16m" />
  <ItemIcon id="neoecoae:eco_item_storage_cell_64m" />
  <ItemIcon id="neoecoae:eco_item_storage_cell_256m" />
</ItemGrid>

- <ItemLink id="neoecoae:eco_item_storage_cell_16m" /> - 16 MiB capacity
- <ItemLink id="neoecoae:eco_item_storage_cell_64m" /> - 64 MiB capacity
- <ItemLink id="neoecoae:eco_item_storage_cell_256m" /> - 256 MiB capacity

### Fluid Storage

<ItemGrid>
  <ItemIcon id="neoecoae:eco_fluid_storage_cell_16m" />
  <ItemIcon id="neoecoae:eco_fluid_storage_cell_64m" />
  <ItemIcon id="neoecoae:eco_fluid_storage_cell_256m" />
</ItemGrid>

- <ItemLink id="neoecoae:eco_fluid_storage_cell_16m" /> - 16 MiB capacity
- <ItemLink id="neoecoae:eco_fluid_storage_cell_64m" /> - 64 MiB capacity
- <ItemLink id="neoecoae:eco_fluid_storage_cell_256m" /> - 256 MiB capacity

Storage matrices for AE2 Omni Cells and AE2 Lightning Tech are documented in [Compatibility Storage Matrices](compat_storage_matrices.md).

## Usage

Once formed, the storage system will automatically connect to the ME Network through the interface. All stored items and fluids will be accessible from any connected terminal.

The GUI displays:
- Current energy storage level
- Energy capacity percentage


The communication IO port defaults to Storage mode, exposing local matrices to the ME network. Input moves network stock into this host; Output moves host stock into other network storage. Its 27 filters allow everything when empty and otherwise match complete resource keys. Hold a sample and left-click to set, or right-click to clear; samples are not consumed. Fluid containers select their fluid unless Shift is held to select the container item.

Ignore Creative Storage Input defaults to on and affects Input only. Exclude Storage Bus Containers defaults to on and affects both directions. Check these switches, filters and destination capacity if nothing moves. Capacity utilization and resource distribution describe space usage and inventory composition separately; hover for exact amounts.

An L9 host can migrate from Storage mode with at least 12 eligible L9 matrices in that host and 64 Infinite Storage Components in its component slot. Missing original members pause domain access. Component removal requires restoring all stock to compatible normal matrices with enough capacity and type slots. Bigger AE2 Digital Singularity cells do not participate in finite-cell migration.
