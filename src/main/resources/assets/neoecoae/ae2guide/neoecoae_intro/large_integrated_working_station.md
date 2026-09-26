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

The Large Integrated Working Station uses a large multiblock structure to process advanced recipes in batches. With overclocking off, it keeps the existing 1,024-craft batch limit and recipe energy cost.

The controller has **Overclock** and **Active Cooling** switches. Both must be on when a batch is accepted for that batch to use overclocking:

| Coolant | Maximum crafts in one batch | Energy per recipe |
| --- | ---: | ---: |
| Water | 16,384 | 8× |
| Sodium | 65,536 | 32× |
| Cryotheum Solution | 262,144 | 64× |

Each processing tick that advances an overclocked batch consumes 100 mB of coolant, regardless of batch size. The input and output hatches each hold 1,024,000 mB. Water produces steam and sodium produces superheated sodium when Mekanism is installed; without Mekanism, water has no byproduct. Cryotheum Solution has no byproduct.

An overclocked batch pauses if overclock or active cooling is turned off, coolant is missing or below the batch's locked tier, power is insufficient, or the byproduct output is full. It resumes when the missing condition is restored. Idle or paused ticks do not consume coolant.

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
