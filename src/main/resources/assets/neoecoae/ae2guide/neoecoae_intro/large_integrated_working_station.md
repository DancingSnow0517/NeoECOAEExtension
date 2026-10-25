---
navigation:
  title: Large Integrated Working Station
  icon: neoecoae:integrated_working_station
  position: 21
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:large_integrated_working_station_casing
  - neoecoae:large_integrated_working_station_input_hatch
  - neoecoae:large_integrated_working_station_output_hatch
  - neoecoae:large_integrated_working_station_interface
---

# Large Integrated Working Station

## Communication block pattern storage

Each communication block starts with **4 rows / 36 pattern slots**. Insert up to **4 native AE2 capacity cards** in the right-hand upgrade panel. Each card immediately adds one row, up to **8 rows / 72 slots**.

The viewport shows up to 7 rows and scrolls smoothly with the mouse wheel and draggable scrollbar, clipping partial rows at both edges. Empty the last row before removing a capacity card. Existing patterns outside the currently unlocked capacity remain retrievable when loading older saves; reinstall capacity cards to insert patterns into those rows again.

## Upgrade cards and recipe compatibility

Supported compatible recipes load by default. Insert one card in the **Upgrade module** slot to unlock its recipe family: Advanced AE, Extended AE (including EAEP), Crystal Science, Lightning Tech, Data Energistics, or Applied Generators. The blank card adds no recipe family. Native recipes and adapters without a dedicated card remain available.

Equivalent recipes share one entry in JEI / EMI. The cards on the right are alternatives: install **any one** to use its supported route. Hover a card to see that route's original batch output, AE cost and additional resources. Proportional batches also share an entry, but keep their original minimum sizes: a Crystal Science route producing 32 items cannot run as a four-item batch. Routes with different materials, yields or byproducts remain separate.

The Applied Generators card supports Genesis Synthesizer recipes from versions 0.2.x and 0.3.0, including fluid products. Patterns must include every item and fluid required by that particular recipe; charged ember crystals are consumed only when listed. Multiplied patterns scale all materials and AE costs together. Installing the card gives the controller lights, screen and interface circuits an ember-orange theme. Craft the card in the large workstation for 16,384,000 AE.

Changing cards refreshes the communication interface's available patterns. Accepted tasks retain their materials and progress and can finish after a card is removed.

The server option `largeWorkstationIgnoreUpgradeCards` defaults to `false`. Set it to `true` and reopen the world or restart the server to unlock all supported compatible recipes without a card. This replaces the old recipe compatibility switch.

<GameScene interactive={true} fullWidth={true} zoom="3">
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="1" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" x="1" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_input_hatch" p:formed="true" x="0" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_interface" p:formed="true" x="1" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_output_hatch" p:formed="true" x="2" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="1" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="1"></Block>
</GameScene>

## Processing time and network FE

Base recipes longer than 200 ticks (10 seconds) are capped at 200 ticks; shorter recipes and total costs stay unchanged. Parallelism increases simultaneous operations. Crystal Science uses a 200 AE/t source reference, so a 57,600 AE recipe is capped from 288 to 200 ticks. The lightning overload factory defaults to 400,000 FE/t with a four-tick minimum; assembly and simulation use a 200 FE/t source reference. Data reassembly retains its duration within the cap. AdvancedAE, Applied Generators, ExtendedAE and EAEP keep their 100-tick reference.

Multiplied patterns scale duration and costs proportionally. Waiting for output space does not charge processing energy; energy or cooling shortages preserve progress. Tasks accepted before this update retain their previous processing contract.

Lightning routes retain their original FE budgets. **Applied Flux enables FE extraction from ME storage; missing FE can be paid with equivalent AE, including when Applied Flux is absent.** Only the FE deficit is substituted, using AE2's current conversion settings. Preheating and network operation still use AE. Lightning and other additional resources remain separate requirements.

AdvancedAE reaction routes can use either AE or FE. The FE budget uses AE2's current conversion ratio and power multiplier. A task prefers AE when accepted; if AE cannot pay its first processing tick and network FE can, it selects FE. That FE budget also allows AE substitution later; processing pauses only when the required energy cannot be paid.

JEI / EMI show energy, processing time and any additional resources. FE recipes prefer an FE value when Applied Flux is installed and an equivalent AE value otherwise. AE units are purple and FE units dark green. Hover the summary or cards for separate sections headed in each mod's theme color; different batch sizes remain explicit and FE supply requirements appear once at the bottom. JEI item counts use a smaller font and SI abbreviations from 1,000, with exact quantities in tooltips. Machine tooltips show actual AE/t and FE/t separately.

In coolant mode, an overclocked task consumes 100 mB per actual processing tick, so longer recipes need more coolant per batch. Temperature mode generates heat from occupied processing load; active cooling retains its thermal rules.

## Parallelism and energy

Temperature-band labels 4 / 6 / 9 correspond to coolant recipe tiers 2 / 6 / 9; the first labels differ.

The normal batch limit is **256**. Temperature mode is enabled by default: enable overclocking and maintain the required temperature for higher parallelism. The server configuration can also disable temperature and use coolant mode below.

| Tier | Continuous operating temperature | Batch limit | Energy per recipe |
| --- | ---: | ---: | ---: |
| Normal | None | 256 | 1× |
| 4 | ≥20% | 16,384 | 1× |
| 6 | ≥50% | 65,536 | 1× |
| 9 | ≥80% | 262,144 | 1× |

Energy scales with actual base-recipe operations. **1× does not mean an entire batch costs only one recipe's energy**: a batch of 100 operations pays 100 base recipe costs. Electrical preheating is charged separately.

## Configuration: disabling temperature

The server setting `largeWorkstationTemperatureEnabled` defaults to `true`. Set it to `false`, then reopen the world or restart the server to select parallelism directly from coolant tier. This mode requires no preheating or operating temperature and has no overheating or 15-second recovery requirement.

| Coolant | Batch limit | Energy per recipe |
| --- | ---: | ---: |
| No coolant, or overclock / active cooling disabled | 256 | 1× |
| Water (tier 2) | 16,384 | 1× |
| Sodium (tier 6) | 65,536 | 1× |
| Cryotheum Solution (tier 9) | 262,144 | 1× |

Coolant mode provides higher parallelism only while **Overclock** and **Active Cooling** are both enabled and the coolant meets the batch requirement. Coolant tier directly determines the limit for new batches; temperature thresholds do not apply. An overclocked batch consumes **100 mB per processing tick that actually advances work**. Idle, unpowered and paused machines consume none. Insufficient coolant, a coolant tier below the accepted batch's requirement or blocked byproducts pause the batch without losing inputs or progress. Higher-tier coolant can keep an existing lower-tier batch running.

Disabling temperature stops preheating and thermal updates. The screen indicates coolant mode. Saved temperature and overheat protection remain stored but do not block processing while disabled. Re-enabling temperature checks them again, so an already overheated machine still requires cryotheum recovery. Switching modes never discards accepted batches.

**The preheating, temperature and overheat rules below apply only while temperature mode is enabled.**

## Interface and temperature status

Each task card shows the product name, amount beside its status, and a separate progress bar. Hover for exact amounts and task details. The information button on the right summarizes idle, processing and paused states, pause reasons and queue information.

The three lower rows show temperature tier and temperature, coolant and actual temperature reduction per second, then overclock status and parallel capacity. These rows display information and provide Tooltips.

The control area retains the overclock switch. Temperature bands are read-only and follow current temperature automatically: **20% / 50% / 80%** unlock bands **4 / 6 / 9**. Enabling overclocking alone generates no heat; recipes must actually advance.

**Right-click the Overclock button** to start or stop electric preheating; left-click still toggles overclocking. Full power costs **1,000 AE/t** and heats at **twice the current full-load processing heat rate**, slowing as temperature rises. Partial power scales heat by actual payment, and the final tick charges only for heat needed to reach the target. Preheating stops at the next threshold (20%, 50% or 80%), or when processing advances. It never restarts automatically to counter cooling. With full power, cumulative active preheating from 0% to 20% / 50% / 80% takes about **3 / 10 / 25 seconds**; right-click again at each threshold to continue.

New batches use the highest temperature-ready tier. When parallelism becomes available, the running batch immediately fills its free slots from consecutive queued tasks belonging to the same crafting job and recipe with identical per-craft inputs and outputs. Any part that does not fit stays queued. This works in temperature and coolant modes without draining the old queue first. Paid work and fractional progress survive refilling and save/reload without charging twice; additional compatibility inputs must actually be acquired before joining progressed work. Different recipes, crafting jobs, already-progressed tasks and tasks awaiting output are not combined.

Temperature requirements are checked throughout processing. **Falling below the batch requirement switches its remaining work to portions of at most 256 crafts**, with a smaller final portion when needed. No further preheating is required to finish it. Already-paid work, including fractional progress, is credited toward the earliest portions without charging twice. Each portion consumes only its inputs, acquires only its required compatibility inputs and delivers its own products. Blocked products wait for delivery before another portion starts; returning a task first delivers completed products, then returns its unprocessed inputs.

The task keeps one queue entry, and its portion mode and remaining work survive save/reload. **With overclocking enabled, reaching the batch's original temperature threshold automatically clears the 256-craft fallback and restores its original parallelism.** Paid progress and fractional work remain credited. Completed portions must be delivered and all remaining compatibility inputs acquired before restoration; missing inputs preserve the fallback and its progress while waiting. Saved downgraded tasks can recover too, and switching to coolant mode restores them when their original coolant-tier requirements are met. New batches still use the limits for the current temperature band. Disabling overclocking still pauses high-tier tasks that have not fallen back; downgraded tasks can continue at the normal portion size.

## Heating and coolant selection

Temperature ranges from **0% to 100%**. While overclocking is enabled and recipes actually advance, full-load heat generation is `0.65 + 3.35 × (1 − temperature / 100)²` percentage points/s, scaled by actual base-recipe work including multiplied patterns. Normal batches below the first threshold can also warm up an overclocked machine. **Heating starts quickly and slows as temperature rises.** Disabled overclocking, idle, unpowered, paused and output-blocked machines generate no processing heat.

At 20 TPS with continuous standard full load and no cooling, 0% → 20% takes about 6 seconds, 20% → 50% about 15 seconds, 50% → 80% about 29 seconds, and 80% → 100% about 29 seconds. Actual times depend on load, power and processing scheduling. Hover over temperature to see actual processing heat, preheating, cooling and net changes over the last second, all in temperature percentage points/s.

Without overclocked processing or independent electric preheating, temperature naturally falls by **0.1 percentage points/s**, requiring no coolant or power and stopping at 0%. Processing uses net heat generation minus active cooling. Latched overheat still requires cryotheum recovery.

Active cooling works independently of the overclock switch and consumes **100 mB/server tick (2 B/s)**:

| Coolant | Temperature removed per second | Byproduct |
| --- | ---: | --- |
| Water | 0.1 percentage points | Steam with Mekanism installed; otherwise none |
| Sodium | 0.3 percentage points | Superheated sodium (Mekanism) |
| Cryotheum Solution | 0.6 percentage points | None |

Stronger coolant can lower temperature. At 80%, full-load heating adds **0.784 percentage points/s**; Cryotheum Solution removes 0.6, leaving a net gain of **0.184 percentage points/s**. Continuous full load with Cryotheum takes about **4 minutes 10 seconds** to go from 80% to 100%. At half load, heating drops to **0.392 percentage points/s**, producing a net loss of **0.208 percentage points/s** with the same cooling. Falling below the highest-tier requirement switches the existing task to 256-craft portions, while new batches follow the current temperature band.

Keep temperature within the desired operating range and below 100%; there is no automatic thermostat. Enabled active cooling continues while paused or idle, provided temperature is above 0 and coolant is available. Consumption stops at 0. Insufficient coolant or blocked byproducts provide no cooling and do not consume an unprocessable coolant transaction.

Each hatch holds **1,024,000 mB**. Keep the output hatch clear when using a coolant with byproducts.

## Overheating and forced recovery

At **100%**, the machine locks processing and new batch admission, stops preheating and retains accepted inputs and progress.

Recovery requires active cooling to be enabled, **Cryotheum Solution** in the input hatch, continuous coolant delivery and unobstructed byproduct output. A full recovery consumes **30,000 mB (30 B)** over **15 seconds (300 server ticks)** of uninterrupted cooling. Hover over the information button to check recovery status. Forced recovery brings temperature to **0%**; the machine stays locked until it finishes. Existing high-tier tasks initially resume in 256-craft portions and automatically regain their original parallelism once reheated to their original threshold.

Recovery requires **uninterrupted successful Cryotheum Solution consumption**. Disabled cooling, interrupted delivery, blocked output, disassembly or reloading clears progress and returns temperature to 100%, requiring a fresh 15 seconds. Water, sodium, custom high-tier coolants, toggling overclocking and simply waiting cannot clear the latch.

## Operating states

| Symptom | Action |
| --- | --- |
| Overclocking is enabled but capacity is still 256 | Current temperature is below 20%; advance recipes or use independent preheating to warm up |
| A task uses only 256 parallelism after cooling | The fallback is temporary; enable overclocking, restore its original temperature threshold, deliver completed products and supply its remaining compatibility inputs to regain its original parallelism |
| Stronger coolant lowers temperature | Cooling exceeds actual heating; a weaker coolant may be suitable |
| Temperature still rises with cooling enabled | Cooling only slows the rise when heating exceeds cooling; hover over cooling information to check its state, amount, recipe and byproduct output capacity |
| Recovery does not advance or resets | Supply real Cryotheum Solution continuously, allow successful cooling every tick and avoid interruptions or reloading |

## Additional machine recipes

A formed Large Integrated Working Station also processes the following recipe types from installed mods:

- AE2 Lightning Tech Reborn: all Overload Processing Factory, Lightning Assembly Chamber, and Lightning Simulation Chamber recipes.
- AE2 Crystal Science: Circuit Etcher and Crystal Aggregator recipes.
- Applied Generators: Genesis Synthesizer recipes.
- AdvancedAE: Reaction Chamber recipes, including fluid products.
- ExtendedAE Plus: all Super Crystal Assembler recipes, including inherited ExtendedAE Crystal Assembler recipes.

Encode a processing pattern with the complete item/fluid inputs and outputs, and place it in the large workstation's communication interface. JEI and EMI list these recipes under **Large Integrated Working Station**. The single-block workstation cannot process these additional recipes. The recipes follow the active datapacks, including additions, replacements, and removals on reload.

AE2LT recipes retain their original lightning tier and quantity. The controller draws the required lightning from the connected ME network; it does not need to be added to the processing pattern. If lightning is missing, the batch pauses until that tier is available. Lightning scales with the number of crafts, without the overclock energy multiplier, and high-voltage lightning does not substitute for extreme-high-voltage lightning. Pending withdrawals survive save/reload, and cancelling an unfinished tracked crafting job returns the resources already withdrawn.

Each route retains its AE or FE energy contract. Super Crystal Assembler recipes use 2,000 AE per craft (200 progress × 10 AE). Processing time and network FE requirements are described above; overclock and coolant rules also apply.
