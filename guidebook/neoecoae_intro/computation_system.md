---
navigation:
  title: ECO Computation System
  icon: neoecoae:computation_system_l9
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:computation_system_l4
  - neoecoae:computation_system_l6
  - neoecoae:computation_system_l9
  - neoecoae:computation_drive
  - neoecoae:computation_transmitter
  - neoecoae:computation_threading_core_l4
  - neoecoae:computation_threading_core_l6
  - neoecoae:computation_threading_core_l9
  - neoecoae:computation_parallel_core_l4
  - neoecoae:computation_parallel_core_l6
  - neoecoae:computation_parallel_core_l9
  - neoecoae:computation_cooling_controller_l4
  - neoecoae:computation_cooling_controller_l6
  - neoecoae:computation_cooling_controller_l9
  - neoecoae:computation_interface
  - neoecoae:computation_casing
  - neoecoae:eco_computation_cell_l4
  - neoecoae:eco_computation_cell_l6
  - neoecoae:eco_computation_cell_l9
---

# ECO Computation System

The ECO Computation System is a powerful multiblock crafting CPU cluster that provides massive parallel crafting capabilities for your ME Network.

## Overview

The computation subsystem replaces standard AE2 crafting CPUs with a much more powerful and scalable solution. It provides multiple crafting threads and accelerators, allowing many crafting jobs to run simultaneously.

## Tiers

There are three tiers of computation systems available:

| Tier | Controller | Accelerators | Threads | Storage per Cell |
|------|------------|--------------|---------|------------------|
| C4 | <ItemLink id="neoecoae:computation_system_l4" /> | 64 | 1 | 64MB |
| C6 | <ItemLink id="neoecoae:computation_system_l6" /> | 192 | 2 | 256MB |
| C9 | <ItemLink id="neoecoae:computation_system_l9" /> | 576 | 4 | 1GB |

## Structure Components

### Controller

<ItemGrid>
  <ItemIcon id="neoecoae:computation_system_l4" />
  <ItemIcon id="neoecoae:computation_system_l6" />
  <ItemIcon id="neoecoae:computation_system_l9" />
</ItemGrid>

The computation system controller (<ItemLink id="neoecoae:computation_system_l4" />, <ItemLink id="neoecoae:computation_system_l6" />, or <ItemLink id="neoecoae:computation_system_l9" />) is the core of the multiblock. It determines the tier and manages all crafting operations.

### Computation Drive

<ItemGrid>
  <ItemIcon id="neoecoae:computation_drive" />
</ItemGrid>

The <ItemLink id="neoecoae:computation_drive" /> holds computation cells that provide storage space for crafting operations. Drives are placed in upper and lower rows above and below the transmitter.

### Superconductive Transmitter

<ItemGrid>
  <ItemIcon id="neoecoae:computation_transmitter" />
</ItemGrid>

The <ItemLink id="neoecoae:computation_transmitter" /> handles data transfer between drives and processing cores.

### Threading Core

<ItemGrid>
  <ItemIcon id="neoecoae:computation_threading_core_l4" />
  <ItemIcon id="neoecoae:computation_threading_core_l6" />
  <ItemIcon id="neoecoae:computation_threading_core_l9" />
</ItemGrid>

Threading cores (<ItemLink id="neoecoae:computation_threading_core_l4" />, <ItemLink id="neoecoae:computation_threading_core_l6" />, or <ItemLink id="neoecoae:computation_threading_core_l9" />) provide crafting threads. Each thread can handle one crafting job simultaneously. The tier must match the controller tier.

### Parallel Core

<ItemGrid>
  <ItemIcon id="neoecoae:computation_parallel_core_l4" />
  <ItemIcon id="neoecoae:computation_parallel_core_l6" />
  <ItemIcon id="neoecoae:computation_parallel_core_l9" />
</ItemGrid>

Parallel cores (<ItemLink id="neoecoae:computation_parallel_core_l4" />, <ItemLink id="neoecoae:computation_parallel_core_l6" />, or <ItemLink id="neoecoae:computation_parallel_core_l9" />) provide crafting accelerators that speed up crafting operations. They are placed in rows above and below the threading cores.

### Cooling Controller

<ItemGrid>
  <ItemIcon id="neoecoae:computation_cooling_controller_l4" />
  <ItemIcon id="neoecoae:computation_cooling_controller_l6" />
  <ItemIcon id="neoecoae:computation_cooling_controller_l9" />
</ItemGrid>

The cooling system controller (<ItemLink id="neoecoae:computation_cooling_controller_l4" />, <ItemLink id="neoecoae:computation_cooling_controller_l6" />, or <ItemLink id="neoecoae:computation_cooling_controller_l9" />) manages thermal output of the computation system. It is placed at the end of the structure.

### Interface

<ItemGrid>
  <ItemIcon id="neoecoae:computation_interface" />
</ItemGrid>

The <ItemLink id="neoecoae:computation_interface" /> connects the system to your ME Network.

### Casing

<ItemGrid>
  <ItemIcon id="neoecoae:computation_casing" />
</ItemGrid>

The <ItemLink id="neoecoae:computation_casing" /> blocks form the frame of the multiblock structure.

## Building the Structure

1. Place the **Controller** facing outward
2. Build the structural frame using **Computation Casing** blocks around the controller
3. Place the **Interface** at the designated position (back-left of controller)
4. Add **Transmitters** in a horizontal row extending from the controller
5. Place **Threading Cores** behind the transmitters
6. Add **Drives** in upper and lower rows (above and below the transmitters)
7. Place **Parallel Cores** in upper and lower rows (above and below the threading cores)
8. Add the **Cooling Controller** at the end of the transmitter row
9. Complete the structure with remaining casing blocks

The structure is extensible - add more threading cores, parallel cores, drives, and transmitters to increase capacity.

## Computation Cells

<ItemGrid>
  <ItemIcon id="neoecoae:eco_computation_cell_l4" />
  <ItemIcon id="neoecoae:eco_computation_cell_l6" />
  <ItemIcon id="neoecoae:eco_computation_cell_l9" />
</ItemGrid>

The following computation cells provide storage for crafting jobs:

- <ItemLink id="neoecoae:eco_computation_cell_l4" /> - CE4 Flash Array, 64MB
- <ItemLink id="neoecoae:eco_computation_cell_l6" /> - CE6 Flash Array, 256MB
- <ItemLink id="neoecoae:eco_computation_cell_l9" /> - CE9 Flash Array, 1GB

## Usage

Once formed, the computation system appears as a crafting CPU in your ME Network. When starting a crafting job, you can select the ECO computation system as the target CPU.

The GUI displays:
- Used threads / Total threads
- Used storage / Available storage
- Parallelism count

### CPU Selection Modes

The computation system supports different CPU selection modes:
- **Any** - Can be selected by any crafting request
- **Player Only** - Only accepts manual player requests
- **Machine Only** - Only accepts automated requests

## Tips

- More threading cores = more simultaneous crafting jobs
- More parallel cores = faster individual crafting operations
- Ensure sufficient computation cell storage for large crafting jobs
- The total storage across all cells must meet the crafting job requirements
