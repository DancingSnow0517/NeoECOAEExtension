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

| Tier | Controller | Parallelism | Overclocked Parallelism |
|------|------------|-------------|-------------------------|
| F4 | <ItemLink id="neoecoae:crafting_system_l4" /> | 24 | 32 |
| F6 | <ItemLink id="neoecoae:crafting_system_l6" /> | 72 | 96 |
| F9 | <ItemLink id="neoecoae:crafting_system_l9" /> | 256 | 384 |

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

The <ItemLink id="neoecoae:crafting_worker" /> is the core processing unit that executes crafting patterns. It handles the actual item transformation based on patterns.

### Pattern Bus

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_pattern_bus" />
</ItemGrid>

The <ItemLink id="neoecoae:crafting_pattern_bus" /> holds crafting patterns. Multiple pattern buses can be added to store more patterns.

### Parallel Core

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_parallel_core_l4" />
  <ItemIcon id="neoecoae:crafting_parallel_core_l6" />
  <ItemIcon id="neoecoae:crafting_parallel_core_l9" />
</ItemGrid>

Parallel cores (<ItemLink id="neoecoae:crafting_parallel_core_l4" />, <ItemLink id="neoecoae:crafting_parallel_core_l6" />, or <ItemLink id="neoecoae:crafting_parallel_core_l9" />) provide additional parallelism for pattern processing. The tier must match the controller tier.

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

## Building the Structure

1. Place the **Controller** facing outward
2. Build the structural frame using **Crafting Casing** blocks around the controller
3. Place the **Interface** at the designated position (back-left of controller)
4. Add the **Fluid Input Hatch** above the interface
5. Add the **Fluid Output Hatch** below the interface
6. Place **Workers** in a horizontal row extending from the controller
7. Add **Parallel Cores** in upper and lower rows (above and below workers)
8. Place **Heat Sinks** behind the workers
9. Add **Pattern Buses** in upper and lower rows (above and below heat sinks)
10. Complete the structure with remaining casing blocks

The structure is extensible - add more workers, parallel cores, pattern buses, and heat sinks to increase capacity.

## Usage

Once formed, the crafting system acts as a pattern provider in your ME Network. Insert patterns into the pattern buses to enable automated crafting.

### Configuration Options

The GUI provides the following settings:

#### Overclocking
Enable overclocking to increase parallelism at the cost of higher energy consumption.
- Normal mode: Base parallelism
- Overclocked mode: Enhanced parallelism (see tier table)

#### Active Cooling
Enable active cooling to further enhance performance and eliminate extra energy costs from overclocking.
- Requires coolant fluids in the input hatch
- Coolant recipes can be viewed in JEI
- If coolant runs out during operation, the system will stop
- If the output hatch is full, coolant cannot be consumed

### GUI Information

The interface displays:
- Worker count
- Pattern bus count
- Parallel core count
- Total parallelism
- Working threads (active/total)
- Maximum energy usage

## Tips

- Use overclocking for faster processing when power is abundant
- Enable active cooling in combination with overclocking for best efficiency
- More workers allow more simultaneous pattern processing
- More parallel cores increase the number of items processed per operation
- Ensure the output hatch has space for used coolant to avoid system shutdown
