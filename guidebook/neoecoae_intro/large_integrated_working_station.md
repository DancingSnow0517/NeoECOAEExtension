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

## Parallelism and energy

The normal batch limit is **256**. Enable overclocking and maintain the required temperature to use higher parallelism.

| Tier | Continuous operating temperature | Batch limit | Energy per recipe |
| --- | ---: | ---: | ---: |
| Normal | None | 256 | 1× |
| 2 | ≥20% | 16,384 | 1× |
| 6 | ≥50% | 65,536 | 1× |
| 9 | ≥80% | 262,144 | 1× |

Energy scales with actual base-recipe operations. **1× does not mean an entire batch costs only one recipe's energy**: a batch of 100 operations pays 100 base recipe costs. Electrical preheating is charged separately.

## Starting and controlling temperature

1. Select a maximum tier on the controller's side rail. The button cycles through **2 / 6 / 9**; hover over it to check the selection.
2. Start preheating. This enables overclocking and disables active cooling. At full power it uses **1,000 AE/t** and adds **2 percentage points/s**; insufficient power reduces heating proportionally.
3. Wait for the selected tier's temperature threshold. Preheating stops automatically. Submit high-parallelism crafting tasks after the machine is ready.
4. Watch temperature and actual workload, then supply a suitable coolant and enable active cooling when needed. Excessive cooling can remove the temperature needed for high parallelism.
5. If temperature falls below a batch's requirement, reduce or disable cooling and preheat manually again. Heating never restarts automatically to counter cooling.

New batches use the highest temperature-ready tier within the selected maximum. The requirement is checked **throughout processing**, not just when a batch is accepted. Low temperature or disabled overclocking pauses the batch while retaining inputs and progress. Processing resumes when the conditions are restored. Changing the selected maximum affects new batches without rewriting accepted batches.

## Heating and coolant selection

Temperature ranges from **0% to 100%**. Full-load overclocked heat generation is `0.2 + 0.8 × (temperature / 100)²` percentage points/s, scaled by actual base-recipe work including multiplied patterns. **Higher temperatures generate heat faster.** Normal and paused batches generate no processing heat.

Active cooling works independently of the overclock switch and consumes **100 mB/server tick (2 B/s)**:

| Coolant | Temperature removed per second | Byproduct |
| --- | ---: | --- |
| Water | 0.1 percentage points | Steam with Mekanism installed; otherwise none |
| Sodium | 0.3 percentage points | Superheated sodium (Mekanism) |
| Cryotheum Solution | 0.6 percentage points | None |

Stronger coolant can lower temperature. At 20% temperature, full-load heating adds **0.232 percentage points/s**, but Cryotheum Solution removes 0.6, giving approximately **−0.368 percentage points/s**. Temperature then falls below tier 2's requirement. Lower workloads produce less heat and are even easier to overcool.

Keep temperature within the desired operating range and below 100%; there is no automatic thermostat. Enabled active cooling continues while paused or idle, provided temperature is above 0 and coolant is available. Consumption stops at 0. Insufficient coolant or blocked byproducts provide no cooling and do not consume an unprocessable coolant transaction.

Each hatch holds **1,024,000 mB**. Keep the output hatch clear when using a coolant with byproducts.

## Overheating and forced recovery

At **100%**, the machine locks processing and new batch admission, stops preheating and retains accepted inputs and progress. To recover:

1. Replace the input coolant with **Cryotheum Solution**. Prepare at least **30,000 mB (30 B)** or maintain a continuous supply.
2. Enable active cooling and keep coolant delivery and byproduct output unobstructed.
3. Wait until the displayed cryotheum recovery timer completes **15 seconds (300 server ticks)**. This forced recovery brings temperature to **0%**; the machine remains locked until the entire process finishes.
4. After unlocking, select the desired tier and preheat manually before resuming high-parallelism work.

Recovery requires **uninterrupted successful Cryotheum Solution consumption**. Disabled cooling, interrupted delivery, blocked output, disassembly or reloading clears progress and returns temperature to 100%, requiring a fresh 15 seconds. Water, sodium, custom high-tier coolants, toggling overclocking and simply waiting cannot clear the latch.

## Troubleshooting

| Symptom | Action |
| --- | --- |
| Overclocking is enabled but capacity is still 256 | Check the selected tier and current temperature; finish preheating first |
| A batch pauses due to low temperature | Reduce or disable cooling and preheat manually; inputs and progress are retained |
| Stronger coolant lowers temperature | Cooling exceeds actual heating; use weaker coolant or temporarily disable cooling |
| Temperature still rises with cooling enabled | Cooling only slows the rise when heating exceeds cooling; also check the switch, available amount, supported recipe and byproduct output capacity |
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

Recipe energy is paid in AE. AE2LT's FE costs are converted using AE2's FE/AE ratio; the other integrations retain their AE cost. Super Crystal Assembler recipes use 2,000 AE per craft (200 progress × 10 AE). The existing overclock and coolant rules also apply to these recipes.
