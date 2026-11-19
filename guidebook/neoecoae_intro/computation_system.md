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

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../scenes/comp_min.nbt" />
  <IsometricCamera yaw="45" pitch="30" />
</GameScene>

The ECO Computation System is a high-performance crafting CPU for your ME network. It provides **threads**, **accelerators**, and **crafting storage**. Add threads to run more jobs at once, or add storage to take on larger jobs.

The scene above shows the smallest build (length 1) with a normal network exchange module.

## Building the Structure

<ItemGrid>
  <ItemIcon id="neoecoae:computation_system_l4" />
  <ItemIcon id="neoecoae:computation_system_l6" />
  <ItemIcon id="neoecoae:computation_system_l9" />
  <ItemIcon id="neoecoae:computation_drive" />
  <ItemIcon id="neoecoae:computation_transmitter" />
  <ItemIcon id="neoecoae:computation_threading_core_l4" />
  <ItemIcon id="neoecoae:computation_threading_core_l6" />
  <ItemIcon id="neoecoae:computation_threading_core_l9" />
  <ItemIcon id="neoecoae:computation_parallel_core_l4" />
  <ItemIcon id="neoecoae:computation_parallel_core_l6" />
  <ItemIcon id="neoecoae:computation_parallel_core_l9" />
  <ItemIcon id="neoecoae:computation_cooling_controller_l4" />
  <ItemIcon id="neoecoae:computation_cooling_controller_l6" />
  <ItemIcon id="neoecoae:computation_cooling_controller_l9" />
  <ItemIcon id="neoecoae:computation_interface" />
  <ItemIcon id="neoecoae:computation_casing" />
</ItemGrid>

- **Controller**: Sets the maximum tier. Threading and parallel cores may be of the same or a lower tier.
- **Transmitters and drives**: The <ItemLink id="neoecoae:computation_transmitter" /> connects the internal modules. The <ItemLink id="neoecoae:computation_drive" /> holds flash arrays.
- **Threading and parallel cores**: Threading cores determine how many jobs can run at once. The upper and lower rows of parallel cores provide crafting accelerators.
- **Interface and cooling controller**: The <ItemLink id="neoecoae:computation_interface" /> connects to the ME network. The cooling controller handles heat at the far end of the structure.
- **Casing**: The <ItemLink id="neoecoae:computation_casing" /> forms the outer frame.

Build it as follows, or use the [Multiblock Auto Builder](multiblock_builder.md) to preview and place it:

1. Place the controller facing outward and build the casing frame around it.
2. Place the interface behind and to the left of the controller.
3. Start a horizontal row of transmitters just beyond the casing to the controller's right.
4. Place threading cores behind the transmitters, and a row of drives above and below the transmitters.
5. Place a row of parallel cores above and below the threading cores.
6. Stand to the right of the build, face the controller, and place the cooling controller at the end of the transmitter row.
7. Fill the remaining spaces with casing.

To extend the build, add matching sections of transmitters, drives, threading cores, and parallel cores.

## Tiers and Flash Arrays

| Tier | Controller | Accelerators per Parallel Core | Threads per Threading Core | Storage per Flash Array |
|------|------------|--------------------------------|---------------------------|-------------------------|
| C4 | <ItemLink id="neoecoae:computation_system_l4" /> | 64 | 4 | 64 MiB |
| C6 | <ItemLink id="neoecoae:computation_system_l6" /> | 192 | 16 | 256 MiB |
| C9 | <ItemLink id="neoecoae:computation_system_l9" /> | 576 | 64 | 1 GiB |

Install CE4, CE6, or CE9 flash arrays in the drives to provide CPU storage:

<ItemGrid>
  <ItemIcon id="neoecoae:eco_computation_cell_l4" />
  <ItemIcon id="neoecoae:eco_computation_cell_l6" />
  <ItemIcon id="neoecoae:eco_computation_cell_l9" />
</ItemGrid>

Once formed and connected to the ME network, the system can serve as a crafting CPU. The controller screen shows threads, storage, and parallelism, and lets you choose which requests to accept:

| Selection Mode | Accepted Requests |
|----------------|-------------------|
| Any | Players and automation |
| Player Only | Manual player requests only |
| Machine Only | Automated requests only |

The controller must be online and allow the request source.

## C9 Networking and Ultimate Mode

<Row>
  <BlockImage id="neoecoae:computation_network_switch" scale="2"></BlockImage>
  <BlockImage id="neoecoae:computation_high_energy_network_switch" scale="2"></BlockImage>
</Row>

<ItemLink id="neoecoae:computation_network_switch" /> and <ItemLink id="neoecoae:computation_high_energy_network_switch" /> link multiple **C9 hosts** into one logical computation network.

- **Placement**: Facing the front of the controller, replace the adjacent middle casing on the right, or on the left for a mirrored build. Only C9 is supported.
- **Grouping**: Hosts must be in the same dimension, on the same online ME network, and set to the same logical frequency (1–4). A group holds up to **8 hosts**. Computation and crafting hosts group separately.
- **Activation**: At least **2 hosts** with modules are required; a lone host stays at x1. The two module types can be mixed. Each host applies its own multiplier before the resources are pooled into one logical CPU.

| Module | Thread / Accelerator / Storage Multiplier | Power Multiplier |
|--------|-------------------------------------------|------------------|
| Normal Network Exchange | **x2** | **x4** |
| High-Energy Network Exchange | **x8** | **x16** |

### Unlocking Ultimate Mode

Meet all of the following conditions to unlock ultimate capacity for the entire computation network. No additional cooling controller upgrade is required.

1. One logical group contains **8 C9 hosts**, each built to the configured maximum length.
2. All 8 hosts have a <ItemLink id="neoecoae:computation_high_energy_network_switch" />.
3. **Every slot in both drive rows on all 8 hosts** contains a <ItemLink id="neoecoae:eco_computation_cell_l9" />. An empty slot, invalid item, or lower-tier array (CE4/CE6) returns the whole group to ordinary pooled capacity.

| Resource | Ultimate Capacity |
|----------|-------------------|
| CPU storage | 9,223,372,036,854,775,807 bytes (signed 64-bit maximum) |
| Threads | 2,147,483,647 (signed 32-bit maximum) |
| Parallelism | 2,147,483,647 (signed 32-bit maximum) |

Active jobs still take up space and reduce the available bytes. Hover over the information icon on the controller screen to see unmet conditions.

## Planning Modes and Tools

### Default and Deep Planning

- **Default (fast planning)**: Uses one fixed pattern per material without retrying alternative recipes or substituting inputs. An individually encoded input explicitly returned unchanged only needs enough stock for all relevant slots of one craft. Consumed ingredients still count per craft, and actual byproducts can meet later demands.
- **Deep Planning Mode**: Enable it on the controller screen. It searches alternative routes, considers substitutions and container or tool returns, and shows full diagnostics and estimates. Use it for complex recipes when a longer calculation is acceptable.

### Self-Multiplying Cycles and Seed Retention

For recipes such as crystal seed multiplication, turn on **Cycle** in the planner to solve the loop and retain its startup seeds:

- **Enough in stock**: If stock already covers the startup seeds plus the order, fill the order from stock and leave the seeds untouched.
- **Not enough in stock**: Temporarily borrow the seeds to grow the shortfall, then return them to the network.
- **Missing seeds or ingredients**: If no startup seeds are available, or fuel or other ingredients cannot cover the growth, the planner reports the shortage rather than using up the seeds.

### Ignoring NBT Differences

Hold a sample and **left-click** the <ItemLink id="neoecoae:computation_interface" /> to mark that item as ignoring component/NBT differences. **Right-click** to clear the marks. The sample is not consumed.

- Marks apply to supported processing pattern inputs, and the machine must still accept the variant. Crafting recipes and custom patterns keep their own validation, and final products still require an exact match.
- Compatible **AE2LT / Thunderbolt** planners can use these marks without switching planners. The ECO planner also reads the ignore-NBT settings built into AE2LT overload patterns.
- Matching rules are saved when you place the order. Changing the interface or reloading the world does not alter existing jobs.

### Continuous Pattern Dispatch

Unlike base AE2's smoothed scheduling, the ECO CPU keeps dispatching the current pattern while it has inputs and providers can accept it. This reduces idle time for high-speed workers in the [ECO Crafting System](crafting_system.md).

Ordinary single-copy dispatch is capped at **64 per CPU per tick**; the server can set a lower cap. Processing-pattern batch dispatch has a separate budget of **64 attempts per CPU per tick**, with at most **32 per pattern**. A whole batch counts as one attempt, not one per copy. The ECO workers' fast batch path does not spend the ordinary single-copy budget. When only the budget runs out, dispatch resumes on the next tick without treating the provider as blocked.

### Crafting Graph and List Export

- **Exact quantities**: Hold **Shift** while hovering over materials or nodes on the confirmation screen or graph to see full values. Missing startup seeds and ordinary ingredients are reported separately.
- **Recursive crafting graph**: Open it from the left toolbar and expand a recursive module to inspect materials added at each layer, startup seeds, and remaining outputs. Large cycles fold repeated steps; **Unknown** means the branch is not fully calculated.
- **PNG list export**: Once the material list has loaded, use the image export button on the left toolbar to save it in `screenshots`. Long lists use multiple columns, and the filename in chat opens the image.
