---
navigation:
  title: ECO Crafting System
  icon: neoecoae:crafting_system_l9
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:crafting_system_l4
  - neoecoae:crafting_system_l6
  - neoecoae:crafting_system_l9
  - neoecoae:crafting_worker
  - neoecoae:crafting_pattern_bus
  - neoecoae:crafting_parallel_core_l4
  - neoecoae:crafting_parallel_core_l6
  - neoecoae:crafting_parallel_core_l9
  - neoecoae:crafting_interface
  - neoecoae:crafting_casing
  - neoecoae:crafting_vent
  - neoecoae:input_hatch
  - neoecoae:output_hatch
---

# ECO Crafting System

The ECO Crafting System is an advanced multiblock pattern provider that enables parallel processing of crafting patterns, dramatically increasing crafting throughput.

## Overview

Unlike the computation system which handles crafting jobs, the crafting subsystem is a pattern provider that can execute multiple patterns simultaneously. It supports overclocking and active cooling for enhanced performance.

## Tiers

There are three tiers of crafting systems available:

| Tier | Controller | Base Batch / Slot | Overclocked Batch / Slot |
|------|------------|-------------------|--------------------------|
| F4 | <ItemLink id="neoecoae:crafting_system_l4" /> | 32 | 128 |
| F6 | <ItemLink id="neoecoae:crafting_system_l6" /> | 32 | 256 |
| F9 | <ItemLink id="neoecoae:crafting_system_l9" /> | 32 | 512 |

## Structure Components

### Controller

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_system_l4" />
  <ItemIcon id="neoecoae:crafting_system_l6" />
  <ItemIcon id="neoecoae:crafting_system_l9" />
</ItemGrid>

The crafting system controller (<ItemLink id="neoecoae:crafting_system_l4" />, <ItemLink id="neoecoae:crafting_system_l6" />, or <ItemLink id="neoecoae:crafting_system_l9" />) manages all pattern processing operations and determines the tier of the system.

### Worker

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_worker" />
</ItemGrid>

The <ItemLink id="neoecoae:crafting_worker" /> provides one independent physical execution lane. Network exchange increases the batch carried by that lane; it never creates additional physical lanes.

### Pattern Bus

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_pattern_bus" />
</ItemGrid>

The <ItemLink id="neoecoae:crafting_pattern_bus" /> holds crafting patterns. In an exchange group, every member publishes the union of all member pattern buses to the ME network.

### Parallel Core

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_parallel_core_l4" />
  <ItemIcon id="neoecoae:crafting_parallel_core_l6" />
  <ItemIcon id="neoecoae:crafting_parallel_core_l9" />
</ItemGrid>

Parallel cores (<ItemLink id="neoecoae:crafting_parallel_core_l4" />, <ItemLink id="neoecoae:crafting_parallel_core_l6" />, or <ItemLink id="neoecoae:crafting_parallel_core_l9" />) provide structural processing capacity. Capacity beyond what the FX Workers can use increases overflow overclock; it does not set the batch size of an FX thread.

### Interface

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_interface" />
</ItemGrid>

The <ItemLink id="neoecoae:crafting_interface" /> connects the system to your ME Network.

### Fluid Input Hatch

<ItemGrid>
  <ItemIcon id="neoecoae:input_hatch" />
</ItemGrid>

The <ItemLink id="neoecoae:input_hatch" /> accepts coolant fluids for active cooling mode.

### Fluid Output Hatch

<ItemGrid>
  <ItemIcon id="neoecoae:output_hatch" />
</ItemGrid>

The <ItemLink id="neoecoae:output_hatch" /> expels used coolant from the system.

### Heat Sink

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_vent" />
</ItemGrid>

The <ItemLink id="neoecoae:crafting_vent" /> provides passive thermal management for the crafting system.

### Casing

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_casing" />
</ItemGrid>

The <ItemLink id="neoecoae:crafting_casing" /> blocks form the frame of the multiblock structure.

### Network Exchange Modules

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_network_switch" />
  <ItemIcon id="neoecoae:crafting_high_energy_network_switch" />
</ItemGrid>

#### Normal Network Exchange Structure (Length 1)

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../scenes/craft_min.nbt" />
  <IsometricCamera yaw="45" pitch="30" />
</GameScene>

<ItemLink id="neoecoae:crafting_network_switch" /> and <ItemLink id="neoecoae:crafting_high_energy_network_switch" /> link F9 crafting hosts on the same ME network. While facing the controller front, replace the adjacent central casing on the right for a normal structure or on the left for a mirrored structure. At least two linked F9 hosts are required; a single host remains at **x1**.

#### Exchange Multipliers

Every normal switch contributes 2 and every high-energy switch contributes 8 to one network-wide multiplier: **M = 2a + 8b**. Every physical FX lane in the logical network uses the same **512 x M** batch size; switches are not split into separate multiplier pools.

#### Shared Exchange Rules

| Topic | Behavior |
|-------|----------|
| Activation | At least two linked F9 hosts are required. A single host remains at **x1**. |
| Patterns | All member pattern buses are combined into one pattern set; work is routed to a member host with a free physical FX lane. |
| Power and UI | Each host continuously draws its full rated power for all available FX threads while exchange is active. Insufficient power pauses affected tasks, and the shared UI reports aggregate network energy use. |
| Shared controls | The network shares one GUI for exchange state, overclocking, active cooling, and aggregate energy use. The shared UI also controls active cooling. |
| Cooling pool | When active cooling is enabled, cached coolant from every member forms one pool. Ordinary finite batches pay the existing per-craft coolant cost when accepted. Coolant quality limits effective overclock, not the network multiplier. |
| Virtual tick cooling | Only final virtual mode uses a flat tick cost: each active physical FX lane pays 10000 coolant per tick when active cooling is enabled. Craft count never participates in this virtual cost. |

#### Complete Eight-host Virtual Crafting

Virtual crafting mode requires a logical exchange network of **exactly 8 F9 hosts**, with every host using a high-energy network switch and actually formed at the configured maximum length. By default this is **11 physical FX lanes per host, 88 total**. Ordinary switches, any non-F9 host, or even one host with fewer than 11 actual FX lanes keeps the network on finite crafting behavior.

- One FX Worker task thread carries one recipe task and accepts its complete remaining craft count.
- Inputs and outputs are aggregated as item keys with 64-bit quantities instead of being expanded into physical item stacks inside the FX Worker.
- The virtual task completes and returns its aggregated outputs on its first worker tick.
- When active cooling is enabled, each active virtual lane consumes a fixed **10000 coolant per tick** from the shared pool. If the pool cannot pay the full amount, that lane waits. Without active cooling, virtual execution still uses its explicit one-tick path.
- The supported task quantity follows the signed 64-bit range. Only true virtual mode is displayed as **infinite**; large finite capacities always display their real number.

## Building the Structure

1. Place the **Controller** facing outward
2. Build the structural frame using **Crafting Casing** blocks around the controller
3. Place the **Interface** at the designated position (back-left of controller)
4. Add the **Fluid Input Hatch** above the interface
5. Add the **Fluid Output Hatch** below the interface
6. Place **Workers** in a horizontal row extending from the right side of the Crafting Casing on the right side of the controller
7. Add **Parallel Cores** in upper and lower rows (above and below workers)
8. Place **Heat Sinks** behind the workers
9. Add **Pattern Buses** in upper and lower rows (above and below heat sinks)
10. Complete the structure with remaining casing blocks

The structure is extensible - add more workers, parallel cores, pattern buses, and heat sinks to increase capacity.

If you want to assemble the structure more quickly, see [Multiblock Auto Builder](multiblock_builder.md) for automatic preview and building tools.

## Usage

Once formed, the crafting system acts as a pattern provider in your ME Network. Insert patterns into the pattern buses to enable automated crafting.

### Configuration Options

The GUI provides the following settings:

#### Overclocking
Enable overclocking to increase batch capacity per task slot at the cost of higher energy consumption. It does not add task slots.
- Normal mode: Each FX Worker thread handles a base batch of 32 crafts
- Overclocked mode: The controller tier multiplies the FX batch by x4, x8, or x16 (see tier table)

#### Active Cooling
Enable active cooling to further enhance performance and eliminate extra energy costs from overclocking.
- Requires coolant fluids in the input hatch
- Coolant recipes can be viewed in JEI
- The system stores coolant as a buffer. Ordinary finite batches, including x2/x8 exchange batches, are charged per craft when work starts. Virtual mode uses the flat physical-lane tick cost described above.
- If the output hatch is full, coolant cannot be converted and the buffer cannot be replenished

### Cooling and Effective Overclock

The crafting system now separates structural overclock capability from coolant quality.

- The structure determines theoretical overflow overclock from parallel-core processing capacity that exceeds FX worker capacity. Every 5% overflow adds one speed level, up to level 9.
- Overflow overclock shortens task duration only. It neither adds task slots nor participates in the x2/x8 per-slot batch multiplier.
- Active coolant determines the effective overclock that can actually be used
- If the coolant tier is lower than the structure's capability, the system does not stop refilling coolant; instead, it runs at the lower effective overclock
- The GUI shows both the theoretical overclock and the currently effective overclock
- The GUI also shows the maximum overclock supported by the current coolant and includes a button to clear the coolant buffer when switching fluids

Current default coolant tiers are:

| Coolant | Cooling Value | Max Overclock |
|---------|---------------|---------------|
| Water | 1500 per 100 mB | 2 |
| Water to Steam | 1500 per 100 mB | 2 |
| Sodium | 5000 per 100 mB | 6 |
| Cryotheum Solution | 12000 per 100 mB | 9 |

The controller refills coolant in batches based on the current deficit instead of converting exactly one recipe per tick. This greatly increases refill throughput for large systems.

For the underlying ECO crafting CPU scheduling behavior, see [ECO Computation System](neoecoae:neoecoae_intro/computation_system.md).

### GUI Information

The interface displays:
- Worker count
- Pattern bus count
- Parallel core count
- Physical and active FX lane counts
- Network composition, multiplier, batch per FX, and total batch capacity
- FT parallel capacity
- Maximum energy usage
- Theoretical overclock and effective overclock
- Maximum overclock supported by the current coolant

## Tips

- Use overclocking for faster processing when power is abundant
- Enable active cooling in combination with overclocking for best efficiency
- Upgrade coolant quality if the effective overclock is lower than the theoretical overclock
- Use the clear coolant button before switching from a lower-tier coolant to a higher-tier coolant
- Every FX Worker is exactly one physical lane. Exchange changes its batch size through the shared `M = 2a + 8b` multiplier and does not create more lanes.
- Higher-tier parallel cores increase structural processing capacity and can raise overflow overclock
- Ensure the output hatch has space for used coolant to avoid system shutdown
