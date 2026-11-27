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
  - neoecoae:network_pull_card
  - neoecoae:output_hatch
  - neoecoae:crafting_network_switch
  - neoecoae:crafting_high_energy_network_switch
---

# ECO Crafting System

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../scenes/craft_min.nbt" />
  <IsometricCamera yaw="45" pitch="30" />
</GameScene>

The ECO Crafting System is a multiblock machine that combines high-capacity pattern storage with fast molecular assemblers. Load patterns into the buses and the workers will process crafting jobs from the network in parallel. Overclocking and active cooling can increase throughput further.

The scene above shows the smallest build (length 1) with a normal network exchange module.

## Building the Structure

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_system_l4" />
  <ItemIcon id="neoecoae:crafting_system_l6" />
  <ItemIcon id="neoecoae:crafting_system_l9" />
  <ItemIcon id="neoecoae:crafting_worker" />
  <ItemIcon id="neoecoae:crafting_pattern_bus" />
  <ItemIcon id="neoecoae:crafting_parallel_core_l4" />
  <ItemIcon id="neoecoae:crafting_parallel_core_l6" />
  <ItemIcon id="neoecoae:crafting_parallel_core_l9" />
  <ItemIcon id="neoecoae:crafting_interface" />
  <ItemIcon id="neoecoae:input_hatch" />
  <ItemIcon id="neoecoae:output_hatch" />
  <ItemIcon id="neoecoae:crafting_vent" />
  <ItemIcon id="neoecoae:crafting_casing" />
</ItemGrid>

- **Controller**: Sets the system tier and manages scheduling.
- **Workers (FX)**: Each <ItemLink id="neoecoae:crafting_worker" /> provides one independent execution lane.
- **Pattern bus**: The <ItemLink id="neoecoae:crafting_pattern_bus" /> stores crafting patterns. Each page has 63 slots. The default is one page; the server can set `craftingPatternBusPages` to 1–8.
- **Parallel cores (FT)**: Provide structural parallel capacity. Capacity beyond the workers' batch limit can shorten craft time through overflow acceleration.
- **Interface and cooling components**: The <ItemLink id="neoecoae:crafting_interface" /> connects to the ME network. The <ItemLink id="neoecoae:crafting_vent" /> provides passive cooling; the <ItemLink id="neoecoae:input_hatch" /> takes in coolant and the <ItemLink id="neoecoae:output_hatch" /> drains used fluid.
- **Casing**: The <ItemLink id="neoecoae:crafting_casing" /> forms the outer frame.

Build it as follows, or use the [Multiblock Auto Builder](multiblock_builder.md) to preview and fill out the structure:

1. Place the controller facing outward and build the casing frame around it.
2. Place the interface behind and to the left of the controller, with the fluid input hatch directly above it and the output hatch directly below it.
3. Start a horizontal row of workers just beyond the casing to the controller's right.
4. Place a row of parallel cores above and below the workers, and heat sinks behind them.
5. Place a row of pattern buses above and below the heat sinks.
6. Fill the remaining spaces with casing.

To extend the build, add matching sections of workers, parallel cores, pattern buses, and heat sinks. Once formed and connected to the ME network, load crafting patterns into the buses to begin autocrafting.

> **Pattern management**: Use the interface to search and organize all patterns. A pattern appears if it matches either the input or output search field. All space-separated terms within a field must match. While holding a pattern, **left-click** the list to fill an empty slot, or **right-click** an existing pattern to replace it. See the [Crafting Controller walkthrough](crafting_controller.md) for controls and cooling troubleshooting.

Click **Transfer Network Patterns** at the destination interface to move crafting patterns from active providers and physical bus slots in other ECO crafting groups on the same ME network. A standalone host is its own group. Linked hosts share one destination, and patterns already in that group stay in their slots.

## Batches, Overclocking, and Overflow Acceleration

Neither speed boost adds task slots:

1. **Controller overclocking (larger batches)**: A standalone unit's base batch is **32** per FX lane. Turning on overclocking increases the batch by a tier-specific multiplier and raises power use.
2. **Parallel overflow (shorter craft time)**: When total FT capacity exceeds the FX batch limit (lane count × current batch per slot), the excess is measured as a share of total FT capacity. Each **5 percentage points** is roughly one acceleration level, up to **level 9**. With active cooling on, the coolant tier caps the effective level.

| Tier | Controller | Base Batch per Slot | Overclocked Batch per Slot |
|------|------------|---------------------|----------------------------|
| F4 | <ItemLink id="neoecoae:crafting_system_l4" /> | 32 | 128 (x4) |
| F6 | <ItemLink id="neoecoae:crafting_system_l6" /> | 32 | 256 (x8) |
| F9 | <ItemLink id="neoecoae:crafting_system_l9" /> | 32 | 512 (x16) |

## Active Cooling and Automatic Refills

Enable **Active Cooling** on the controller and supply coolant to the <ItemLink id="neoecoae:input_hatch" /> to remove the extra power cost of overclocking. If effective acceleration is lower than the theoretical level, use a coolant rated for a higher level.

- Coolant is converted into buffered cooling points. Finite batches spend those points per craft when accepted.
- Used fluid leaves through the <ItemLink id="neoecoae:output_hatch" />. A full output hatch prevents the system from replenishing cooling points.
- Hover over the parallel capacity area to compare theoretical and effective overclock. To change coolant, stop the old supply, empty the input hatch, and let ordinary crafting use up the old cooling buffer before supplying the new fluid.

| Default Coolant | Cooling Points per 100 mB | Maximum Overclock Level |
|-----------------|---------------------------|-------------------------|
| Water / Water to Steam | 1500 | 2 |
| Sodium | 5000 | 6 |
| Cryotheum Solution | 12000 | 9 |

Coolant recipes are available in JEI / EMI.

### Automatic Refills With a Network Pull Card

<RecipeFor id="neoecoae:network_pull_card" />

Insert the <ItemLink id="neoecoae:network_pull_card" /> into the right-hand slot of the <ItemLink id="neoecoae:input_hatch" />. The hatch will switch to a two-slot layout like an ME Interface. Removing the card restores the original layout without discarding stored fluid.

- **Top slot (filter)**: Right-click with a fluid container or drag in a coolant from JEI/EMI. Setting the filter does not consume fluid; left-click with an empty hand to clear it. Click the gear above the slot to set a target in buckets (B). Fractions are supported, and the target can exceed the original 16-bucket limit.
- **Bottom slot (buffer)**: The hatch draws from its connected ME network every 20 ticks to meet the target. Reducing the target or changing the filter returns excess or outdated fluid. If the network cannot accept it, the hatch keeps it and retries.

## F9 Networking and Virtual Crafting

<Row>
  <BlockImage id="neoecoae:crafting_network_switch" scale="2"></BlockImage>
  <BlockImage id="neoecoae:crafting_high_energy_network_switch" scale="2"></BlockImage>
</Row>

<ItemLink id="neoecoae:crafting_network_switch" /> and <ItemLink id="neoecoae:crafting_high_energy_network_switch" /> link multiple **F9 hosts** into one logical crafting network.

- **Placement**: Facing the front of the controller, replace the adjacent middle casing on the right, or on the left for a mirrored build. Only F9 is supported.
- **Grouping**: Hosts must be in the same dimension, on the same online ME network, and set to the same logical frequency (1–4). A group can have up to **8 hosts**. Computation and crafting hosts group separately. At least **2 hosts** with modules are required to activate exchange; a lone host stays at x1.

### Shared Resources and Network Multiplier

- **Shared multiplier**: Each normal exchange host contributes `2` and each high-energy host contributes `8`, giving the group **`M = 2a + 8b`** (`a` and `b` are the counts of each host type). In finite exchange mode every FX lane has a batch size of **`512 × M`** and a craft time of **1 tick**. The number of lanes does not change.
- **Shared resources**: The network pools member patterns and distributes jobs to members with suitable free lanes. All members share one control screen, the overclocking and active cooling switches, and a pool of cooling points.
- **Power draw**: In finite exchange mode each host continuously draws its rated power for all available workers. Jobs pause if power is insufficient.

### Unlocking Virtual Crafting

Meet all of the following conditions to enter **Virtual Crafting Mode**:

1. One logical group contains **8 F9 hosts**, each built to the configured maximum length. The default is 11 FX lanes per host, for 88 total.
2. All 8 hosts have a <ItemLink id="neoecoae:crafting_high_energy_network_switch" />.
3. Both **Overclocking** and **Active Cooling** are enabled.

- **Unlimited batch size, 1-tick completion**: The UI displays **Unlimited**. With an ECO exact order, a verified pattern without reusable or durability-changing inputs can use batches and outputs larger than the signed 64-bit limit. Each FX lane can complete its accepted batch on the first working tick with sufficient power and cooling.
- **Power and cooling**: The group pays **10,000,000 AE** once per working tick, subject to the AE2 power multiplier, instead of per craft or per occupied slot. Each working lane uses **10,000 cooling points per tick** from the shared pool, supplied by a coolant rated for **level 9**. The default is Cryotheum Solution; hover over the information icon on the right to see supported fluids. Lanes wait if cooling is insufficient without reverting to finite mode.

Fast workers still need timely dispatch from the CPU. See the [ECO Computation System](neoecoae:neoecoae_intro/computation_system.md) for scheduling details.
