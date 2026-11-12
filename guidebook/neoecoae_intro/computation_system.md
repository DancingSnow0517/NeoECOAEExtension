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
  - neoecoae:computation_network_switch
  - neoecoae:computation_high_energy_network_switch
---

# ECO Computation System

The ECO Computation System is a powerful multiblock crafting CPU cluster that provides massive parallel crafting capabilities for your ME Network.

## Default and Deep Planning Modes

Default planning prioritizes fast calculations without an extra mode label. The computation host's **Deep Planning Mode** switch is disabled by default. In default planning, each material keeps one fixed pattern, without alternative-route retries, substitutions, or tool and container returns. Physical byproducts are shared across ingredient demands. Materials without a direct producer can use one fixed byproduct source from the selected patterns; acyclic materials allow at most one supplemental expansion for late demands, and unresolved amounts remain missing. With **Cycle** enabled, cyclic orders solve growth, startup seeds, and external ingredient demands within that fixed pattern route, without searching other patterns. With cycle solving disabled, a cyclic material is expanded once; captured stock and verified output allocations must close its remaining demand, without borrowing outputs from a cycle that cannot start. Explicitly marked processing inputs match by item ID and retain the actual reserved NBT variants. The final product still matches exactly. Enabling Deep Planning Mode uses the original full planner, including alternative routes, substitutions, and tool and container returns. Calculations may take longer.

Default planning shows no mode label and hides the incomplete-calculation notice. Deep planning retains full diagnostics. Material shortages remain visible and detected cycles are shown as information.

## Continuous Pattern Dispatch

Within a crafting job, the current pattern keeps dispatch priority while its inputs are available and providers keep accepting it. There is no consecutive-success cap. Missing inputs, unavailable providers, task completion, suspension, or dependency and cycle constraints allow other legal patterns to run.

Per-tick execution and probe budgets still apply. Processing batches spend that budget per provider submission, not per copy: each CPU makes at most 64 submissions per tick and one pattern uses at most half of them. A provider that accepts whole batches receives every copy that materials, energy, cycle seeds and its own capacity allow in one submission. A budget pause is not a provider failure and retains the current pattern for the next tick. Batches consume inputs and record expected outputs according to the amount actually accepted.

## Crafting Reports and Recursive Graphs

After calculation, the confirmation page shows the final selected plan. The summary and material list arrive first, followed by the graph. Material cells show stock, amounts to craft, and shortages. In deep planning mode, a partial byte estimate is labelled as incomplete.

Open the graph from the left toolbar and expand a recursive module to inspect its startup seeds, material additions, inventory after each layer, final delivery, and retained materials. Large cycles fold repeated steps. An unfinished search displays unknown stages and does not imply that the plan can execute.

Hold **Shift** while hovering over a material or recursive node to see its full exact quantities. Values remain exact beyond the ordinary integer range, and missing startup seeds are distinguished from ingredients required for the whole order.

Once the material list has loaded, use the image export button on the left toolbar to save the complete list in the game's `screenshots` folder. Large lists use multiple columns in one PNG. Click the filename in chat to open it.

## Overview

The computation subsystem replaces standard AE2 crafting CPUs with a much more powerful and scalable solution. It provides multiple crafting threads and accelerators, allowing many crafting jobs to run simultaneously.

## Tiers

There are three tiers of computation systems available:

| Tier | Controller | Accelerators | Threads | Storage per Cell |
|------|------------|--------------|---------|------------------|
| C4 | <ItemLink id="neoecoae:computation_system_l4" /> | 64 | 4 | 64 MiB |
| C6 | <ItemLink id="neoecoae:computation_system_l6" /> | 192 | 16 | 256 MiB |
| C9 | <ItemLink id="neoecoae:computation_system_l9" /> | 576 | 64 | 1 GiB |

## Structure Components

### Controller

<Row>
  <BlockImage id="neoecoae:computation_system_l4" scale="2"></BlockImage>
  <BlockImage id="neoecoae:computation_system_l6" scale="2"></BlockImage>
  <BlockImage id="neoecoae:computation_system_l9" scale="2"></BlockImage>
</Row>

The computation system controller (<ItemLink id="neoecoae:computation_system_l4" />, <ItemLink id="neoecoae:computation_system_l6" />, or <ItemLink id="neoecoae:computation_system_l9" />) is the core of the multiblock. It determines the tier and manages all crafting operations.

### Computation Drive

<BlockImage id="neoecoae:computation_drive" scale="2"></BlockImage>

The <ItemLink id="neoecoae:computation_drive" /> holds computation cells that provide storage space for crafting operations. Drives are placed in upper and lower rows above and below the transmitter.

### Superconductive Transmitter

<BlockImage id="neoecoae:computation_transmitter" scale="2"></BlockImage>

The <ItemLink id="neoecoae:computation_transmitter" /> handles data transfer between drives and processing cores.

### Threading Core

<Row>
  <BlockImage id="neoecoae:computation_threading_core_l4" scale="2"></BlockImage>
  <BlockImage id="neoecoae:computation_threading_core_l6" scale="2"></BlockImage>
  <BlockImage id="neoecoae:computation_threading_core_l9" scale="2"></BlockImage>
</Row>

Threading cores (<ItemLink id="neoecoae:computation_threading_core_l4" />, <ItemLink id="neoecoae:computation_threading_core_l6" />, or <ItemLink id="neoecoae:computation_threading_core_l9" />) provide crafting threads. Each thread can handle one crafting job simultaneously. A controller accepts cores at its own tier or lower.

### Parallel Core

<Row>
  <BlockImage id="neoecoae:computation_parallel_core_l4" scale="2"></BlockImage>
  <BlockImage id="neoecoae:computation_parallel_core_l6" scale="2"></BlockImage>
  <BlockImage id="neoecoae:computation_parallel_core_l9" scale="2"></BlockImage>
</Row>

Parallel cores (<ItemLink id="neoecoae:computation_parallel_core_l4" />, <ItemLink id="neoecoae:computation_parallel_core_l6" />, or <ItemLink id="neoecoae:computation_parallel_core_l9" />) provide crafting accelerators that speed up crafting operations. They are placed in rows above and below the threading cores.

### Cooling Controller

<Row>
  <BlockImage id="neoecoae:computation_cooling_controller_l4" scale="2"></BlockImage>
  <BlockImage id="neoecoae:computation_cooling_controller_l6" scale="2"></BlockImage>
  <BlockImage id="neoecoae:computation_cooling_controller_l9" scale="2"></BlockImage>
</Row>

The cooling system controller (<ItemLink id="neoecoae:computation_cooling_controller_l4" />, <ItemLink id="neoecoae:computation_cooling_controller_l6" />, or <ItemLink id="neoecoae:computation_cooling_controller_l9" />) manages thermal output of the computation system. It is placed at the end of the structure.

### Interface

<BlockImage id="neoecoae:computation_interface" scale="2"></BlockImage>

The <ItemLink id="neoecoae:computation_interface" /> connects the system to your ME Network.

### Casing

<BlockImage id="neoecoae:computation_casing" scale="2"></BlockImage>

The <ItemLink id="neoecoae:computation_casing" /> blocks form the frame of the multiblock structure.

### Network Exchange Modules

<Row>
  <BlockImage id="neoecoae:computation_network_switch" scale="2"></BlockImage>
  <BlockImage id="neoecoae:computation_high_energy_network_switch" scale="2"></BlockImage>
</Row>

#### Normal Network Exchange Structure (Length 1)

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../scenes/comp_min.nbt" />
  <IsometricCamera yaw="45" pitch="30" />
</GameScene>

<ItemLink id="neoecoae:computation_network_switch" /> and <ItemLink id="neoecoae:computation_high_energy_network_switch" /> link C9 computation hosts on the same ME network. While facing the controller front, replace the adjacent central casing on the right for a normal structure or on the left for a mirrored structure. At least two linked C9 hosts are required; a single host remains at **x1**.

Groups require the same dimension, online ME network and logical frequency. Computation and crafting group separately. Select frequency 1–4; each group has at most eight hosts. Endgame mode has no additional cooling-controller requirement. Hover over the information tooltip for missing conditions.

#### Exchange Multipliers

| Topic | Normal | High-energy |
|-------|--------|-------------|
| Resource multiplier | **x2** threads, accelerators, and CPU storage | **x8** threads, accelerators, and CPU storage |
| Power draw | **x4** | **x16** |

#### Shared Exchange Rules

| Topic | Behavior |
|-------|----------|
| Activation | At least two linked C9 hosts are required. A single host remains at **x1**. |
| Resource aggregation | Each physical host is evaluated independently, then its effective threads, accelerators, and storage are added to the ME network. |
| Module mixing | Normal and high-energy modules can be mixed; each host contributes resources according to its installed module tier. |

#### Ultimate Mode Requirements

All of the following conditions must be met together:

1. One logical group contains exactly **8 C9 computation hosts**.
2. Every host has a <ItemLink id="neoecoae:computation_high_energy_network_switch" />.
3. Every host reaches the maximum build length allowed by the current configuration.
4. **Every computation cell slot in both drive rows on all 8 hosts** holds a <ItemLink id="neoecoae:eco_computation_cell_l9" />. All arrays must be **CE9**.

The entire computation network then enters ultimate mode:

| Resource | Ultimate Capacity |
|----------|-------------------|
| CPU storage | 9,223,372,036,854,775,807 bytes (64-bit integer maximum) |
| Threads | 2,147,483,647 (32-bit integer maximum) |
| Parallelism | 2,147,483,647 (32-bit integer maximum) |

For example, if every other slot holds CE9 but one host has a single CE4 array in its lower row, CPU storage, threads and parallelism for the entire group return to finite values calculated from the installed components and exchange multipliers. CE6 arrays, empty slots and invalid items also prevent ultimate mode. Fill every slot with CE9 and meet the remaining conditions to regain ultimate capacity.

Active jobs still reserve CPU storage, and their bytes are deducted from available capacity. Hover over the information icon in the host interface to see unmet conditions across the computation network.

## Building the Structure

1. Place the **Controller** facing outward
2. Build the structural frame using **Computation Casing** blocks around the controller
3. Place the **Interface** at the designated position (back-left of controller)
4. Add **Transmitters** in a horizontal row extending from the right side of the Computation Casing on the right side of the controller
5. Place **Threading Cores** behind the transmitters
6. Add **Drives** in upper and lower rows (above and below the transmitters)
7. Place **Parallel Cores** in upper and lower rows (above and below the threading cores)
8. Add the **Cooling Controller** at the end of the transmitter row (the player should place it on the right side of the structure, facing the controller)
9. Complete the structure with remaining casing blocks

The structure is extensible - add more threading cores, parallel cores, drives, and transmitters to increase capacity.

If you want to assemble the structure more quickly, see [Multiblock Auto Builder](multiblock_builder.md) for automatic preview and building tools.

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

Ordinary computation networks pool CPU storage from the arrays actually installed. Ultimate mode requires every upper and lower drive on every host in the logical group to hold CE9. A single CE4 or CE6 array, or one empty slot, prevents the entire group from qualifying.

## Usage

Once formed, the computation system appears as a crafting CPU in your ME Network. When starting a crafting job, you can select the ECO computation system as the target CPU.

The GUI displays:
- Used threads / Total threads
- Used storage / Available storage
- Parallelism count

### ECO CPU Scheduling

The ECO computation system uses a more aggressive crafting CPU scheduling model than the original AE-style smoothed scheduling.

This scheduling model is specifically meant to support the high-speed workers used by the ECO crafting system: when a worker can finish a pattern in only a few ticks at high overclock levels, the CPU must refill the next batch of work immediately to avoid idle gaps.

- Original AE-style scheduling smooths pattern submission across multiple ticks
- ECO CPU refills available work every tick based on the currently free providers and workers
- This is especially important for ECO crafting systems, where high overclock levels can complete patterns in very few ticks
- As a result, the system spends less time idle between completed pattern executions and maintains higher sustained throughput

In practice, the ECO computation system favors sustained throughput over conservative per-tick smoothing.

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


Fast planning requires an online host that accepts the request source. Cyclic routes also require Cycle Planning and actual startup seeds; an unfinished search is different from missing materials. In the computation interface, hold a sample and left-click to mark an item whose component differences may be ignored, or right-click to clear it. Samples are not consumed, and the physical pattern and machine must still accept the inputs.

These marks also apply to AE2LT / Thunderbolt planners with a compatible integration API, without switching to the ECO planner. Plain AE2 processing patterns accept the marked item's component variants; crafting recipes and custom patterns keep their own validation. Each task retains the marks captured when it was ordered, including after interface edits or a world reload. The ECO planner also reads the component-insensitive input settings encoded in AE2LT overload patterns.

Self-growing cycles (for example crystal seed loops) keep their minimal startup seed so later orders can still grow. When the stock covers the startup seed plus this order, the order uses the stock and the seed stays in the network. Otherwise the cycle grows the shortfall first: it may borrow the seed while it runs, but the seed must be back in the network when the order finishes. For example, a crystal dust order grinds existing crystals directly and leaves the crystals needed to restart the loop; with too few crystals it grows the shortfall and replaces the seed. If fuel or other materials cannot cover the growth, the plan lists them as missing instead of consuming the seed; with no seed in the network at all, the missing startup seed is reported. Catalysts and containers are still returned according to the pattern.
