---
navigation:
  title: Large Integrated Working Station
  icon: neoecoae:integrated_working_station
  position: 21
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:large_integrated_working_station_casing
  - neoecoae:large_integrated_working_station_input_hatch
  - neoecoae:large_integrated_working_station_smart_cooling_module
  - neoecoae:large_integrated_working_station_output_hatch
  - neoecoae:large_integrated_working_station_interface
  - neoecoae:integrated_working_station_module_empty
---

# Large Integrated Working Station

<GameScene interactive={true} fullWidth={true} zoom="3">
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="1" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" x="1" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_output_hatch" p:formed="true" x="0" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_interface" p:formed="true" x="1" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_input_hatch" p:formed="true" x="2" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="1" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="1"></Block>
</GameScene>

The Large Integrated Working Station expands an [Integrated Working Station](integrated_working_station.md) into a 3×2×2 multiblock. Once formed, it joins the ME network as a pattern provider, processes tens of thousands of recipe operations in parallel and, with an upgrade module, replaces processing machines from other AE add-ons.

Compared with the single-block station, the large workstation:

- accepts jobs, pulls materials and delivers products through the ME network, with no manual loading;
- runs 256 parallel operations per batch normally, and up to 262,144 once heated;
- can process recipes from Advanced AE, Extended AE, Crystal Science, Lightning Tech and more with the matching upgrade module.

## Building the structure

<ItemGrid>
  <ItemIcon id="neoecoae:integrated_working_station" />
  <ItemIcon id="neoecoae:large_integrated_working_station_casing" />
  <ItemIcon id="neoecoae:large_integrated_working_station_interface" />
  <ItemIcon id="neoecoae:large_integrated_working_station_input_hatch" />
  <ItemIcon id="neoecoae:large_integrated_working_station_output_hatch" />
</ItemGrid>

You need 1 <ItemLink id="neoecoae:integrated_working_station" /> as the controller, 8 <ItemLink id="neoecoae:large_integrated_working_station_casing" />, 1 <ItemLink id="neoecoae:large_integrated_working_station_interface" />, 1 <ItemLink id="neoecoae:large_integrated_working_station_input_hatch" /> and 1 <ItemLink id="neoecoae:large_integrated_working_station_output_hatch" />. All parts are crafted in the Integrated Working Station; their recipes are listed at the end of this section.

The structure has a front and back row on two layers. Drag the scenes below to rotate them, and hover the colored blocks for details.

**Step 1: lower layer.** Three casings in front. In the back row, viewed from behind the structure, left to right: the **coolant output hatch, communication interface and coolant input hatch**.

<GameScene interactive={true} zoom="3">
  <Block id="neoecoae:large_integrated_working_station_casing" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="1" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="2" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_output_hatch" x="0" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_interface" x="1" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_input_hatch" x="2" y="0" z="1"></Block>
  <IsometricCamera yaw="45" pitch="30" />
  <BlockAnnotation x="0" y="0" z="1" color="#ffa726">
    **Coolant output hatch**: on the left when viewed from behind; receives coolant byproducts.
  </BlockAnnotation>
  <BlockAnnotation x="1" y="0" z="1" color="#4fc3f7">
    **Communication interface**: in the middle of the back row; connects to the ME network and holds patterns.
  </BlockAnnotation>
  <BlockAnnotation x="2" y="0" z="1" color="#66bb6a">
    **Coolant input hatch**: on the right when viewed from behind; can be replaced by a smart cooling module.
  </BlockAnnotation>
</GameScene>

**Step 2: upper layer.** Place the Integrated Working Station **in the middle of the upper front row**, facing outward, with one casing on each side and three casings behind it.

<GameScene interactive={true} zoom="3">
  <Block id="neoecoae:large_integrated_working_station_casing" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="1" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="2" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_output_hatch" x="0" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_interface" x="1" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_input_hatch" x="2" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="0" y="1" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" x="1" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="2" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="0" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="1" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="2" y="1" z="1"></Block>
  <BlockAnnotation x="1" y="1" z="0" color="#ffee58">
    **Controller**: the Integrated Working Station, in the middle of the upper front row, facing outward.
  </BlockAnnotation>
</GameScene>

A correct structure forms automatically; the casings hide and the whole machine switches to the large workstation model.

Alternatively, place only the controller, open its screen and click **Auto-build Large Integrated Working Station** on the side. If your inventory holds the materials (not needed in Creative) and nothing is in the way, every part is placed at once according to the controller's facing.

### Part recipes

<Row>
  <RecipeFor id="neoecoae:large_integrated_working_station_casing" />
  <RecipeFor id="neoecoae:large_integrated_working_station_interface" />
</Row>

<Row>
  <RecipeFor id="neoecoae:large_integrated_working_station_input_hatch" />
  <RecipeFor id="neoecoae:large_integrated_working_station_output_hatch" />
</Row>

## Connecting to the network and adding patterns

Connect a cable to the **communication interface** and the workstation appears on the ME network as a pattern provider. Open the interface and insert processing patterns that list every item and fluid input and output of the recipe. Then request crafts as with any pattern provider; materials and products move through the ME network.

The workstation's own recipes and compatible recipes appear in JEI / EMI under **Large Integrated Working Station**. The single-block station cannot process these compatible recipes.

The communication interface starts with **4 rows / 36 pattern slots**. Each AE2 **Capacity Card** in the right-hand upgrade slots unlocks one more row, up to 4 cards for **8 rows / 72 slots**. Empty the last row before removing a capacity card. Patterns outside the unlocked capacity in older saves can still be retrieved, but reinstall cards to insert patterns into those rows again.

## Upgrade modules and compatible recipes

The controller screen has one **Upgrade module** slot in the top-right corner. Each module unlocks recipes from one mod:

| Upgrade module | Unlocked recipes |
| --- | --- |
| Blank | None; used as a crafting material for the other modules |
| Advanced AE | Advanced AE Reaction Chamber, including fluid products |
| Extended AE | Extended AE and EAEP crystal assembler recipes |
| Crystal Science | Crystal growth, aggregation, circuit etching, pulverizing and entropy variation |
| Lightning Tech | Overload Processing Factory, Lightning Assembly Chamber, Lightning Simulation Chamber |
| Data Energistics | Data reassembly, charging and pressurizing |
| Applied Generators | Genesis Synthesizer, including fluid products |

A module only exists when its mod is installed. All modules are crafted in the large workstation for 16,384,000 AE each, using the blank module as a base. The workstation's own recipes and compatible recipes without a dedicated module are always available.

<RecipeFor id="neoecoae:integrated_working_station_module_empty" />

An installed module switches the controller lights, screen and interface circuits to that mod's theme color. From left to right below: normal, Advanced AE, Crystal Science and Applied Generators.

<GameScene interactive={true} fullWidth={true} zoom="2">
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" p:working="true" p:module="regular" x="1" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="4" y="0" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" p:working="true" p:module="advanced_ae" x="5" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="6" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="8" y="0" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" p:working="true" p:module="ae2cs" x="9" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="10" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="12" y="0" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" p:working="true" p:module="appgen" x="13" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="14" y="1" z="1"></Block>
  <BlockAnnotation x="1" y="1" z="0" color="#878fa5">Normal (no module or the blank module)</BlockAnnotation>
  <BlockAnnotation x="5" y="1" z="0" color="#b668d0">Advanced AE</BlockAnnotation>
  <BlockAnnotation x="9" y="1" z="0" color="#7dcbe8">Crystal Science</BlockAnnotation>
  <BlockAnnotation x="13" y="1" z="0" color="#f5a23c">Applied Generators</BlockAnnotation>
</GameScene>

- **Any one matching module works.** Identical products from several mods share one JEI / EMI entry with every usable module listed on the right; install any of them. Hover a module to see that route's batch output, processing time and cost. Each route keeps its own minimum batch: if Extended AE makes 4 per batch and Crystal Science 32, the Crystal Science module requires batches of 32.
- **Changing modules keeps your tasks.** The communication interface refreshes its available patterns and the machine changes to the module's theme color. Accepted tasks keep their materials and progress and can finish.
- **Servers can ignore modules.** `largeWorkstationIgnoreUpgradeCards` defaults to `false`. Set it to `true` and reopen the world or restart the server to allow every compatible recipe without a module.

Route-specific rules:

- **Crystal growth** (Crystal Science): each seed produces one matching purified crystal without consuming water; partially grown seeds work too. Each seed pays for its full growth cycle, by default 6,000 AE over 200 ticks.
- **Lightning** (Lightning Tech): recipes keep their original lightning tier and amount. The workstation pulls lightning from the ME network, so it does not belong in the pattern; missing lightning pauses the batch. High-voltage lightning cannot replace extreme-high-voltage lightning. Cancelling an unfinished crafting job returns lightning already withdrawn.
- **Genesis Synthesizer** (Applied Generators): supports Applied Generators 0.2.x and 0.3.0. Charged ember crystals are consumed only when the recipe lists them.

## Parallelism and temperature

Temperature mode is enabled by default: turn on overclocking and heat the machine to unlock more parallelism.

| Tier | Required temperature | Batch limit |
| --- | ---: | ---: |
| Normal | None | 256 |
| 4 | ≥ 20% | 16,384 |
| 6 | ≥ 50% | 65,536 |
| 9 | ≥ 80% | 262,144 |

Energy is paid per recipe operation actually processed: a batch of 100 operations pays for 100 recipes. Higher tiers process more at once but never reduce total energy.

The tier is read-only and follows the current temperature. **Left-click** the Overclock button to toggle overclocking. Overclocking alone generates no heat; recipes must actually be processing.

The three lower rows of the controller screen show tier and temperature, coolant and actual cooling rate, then overclock status and parallel capacity. Hover for details, including last-second processing heat, preheating, cooling and net change.

**Tier up.** When temperature rises and more parallelism becomes available, the running batch immediately pulls in following queued tasks from the same crafting job and recipe with identical per-operation inputs and outputs; what doesn't fit stays queued. Paid progress is never charged twice.

**Tier down.** If temperature falls below a batch's threshold, its remaining work continues in portions of at most 256 operations, with no reheating and no double charge. Once overclocking is on and temperature returns to the original threshold, the original parallelism is restored automatically.

## Heating and preheating

Temperature ranges from 0% to 100%. With overclocking and full load, temperature rises by `0.65 + 3.35 × (1 − temperature/100)²` percentage points per second: **fast when cold, slower near 100%**. Lighter load heats more slowly.

Approximate times at full load without cooling:

| Range | Time |
| --- | ---: |
| 0% → 20% | ~6 s |
| 20% → 50% | ~15 s |
| 50% → 80% | ~29 s |
| 80% → 100% | ~29 s |

**Electric preheating.** **Right-click** the Overclock button to start or stop preheating. At full power it costs 1,000 AE/t and heats twice as fast as full-load processing; a weaker supply slows it proportionally. Preheating stops at the next threshold (20% / 50% / 80%) until you right-click again, and also stops once processing advances. Reaching 20% / 50% / 80% from 0% takes about 3 / 10 / 25 seconds of preheating in total.

**Natural cooling.** Without overclocked processing or preheating, temperature falls by 0.1 percentage points per second down to 0%.

## Cooling

Reaching 100% overheats the machine, so sustained full load needs active cooling. Coolant enters through the **coolant input hatch** and byproducts go to the **coolant output hatch**; each holds 1,024,000 mB.

With **Active Cooling** enabled on the controller screen, the machine consumes 100 mB of coolant per tick (2 B per second). Active cooling is independent of overclocking: even while idle it keeps consuming and cooling while temperature is above 0% and coolant is available.

| Coolant | Cooling per second | Byproduct |
| --- | ---: | --- |
| Water | 0.1 points | Steam with Mekanism installed; otherwise none |
| Sodium | 0.3 points | Superheated sodium (Mekanism) |
| Cryotheum Solution | 0.6 points | None |

Stronger coolant removes more heat, which can also push temperature below a tier threshold:

- At 80% and full load, heating is about 0.784 per second; Cryotheum removes 0.6, a net gain of about 0.184, so 80% → 100% takes about 4 minutes 10 seconds.
- At half load, heating is about 0.392, and Cryotheum produces a net loss of about 0.208 per second, eventually dropping out of tier 9.

With manual control, aim to keep temperature within the tier you need and below 100%. For automatic regulation, use the smart cooling module below.

Cooling does nothing, and consumes nothing, while coolant is missing or the output hatch is full. Keep the output hatch drained when using a coolant with byproducts.

## Overheating and recovery

At **100%**, the machine locks: processing stops, no new jobs are accepted and preheating turns off. Accepted materials and progress are kept.

The only way out is forced cooling with **Cryotheum Solution**:

1. Keep Active Cooling enabled.
2. Supply Cryotheum Solution continuously and keep the output hatch clear.
3. Cool without interruption for **15 seconds**, using 30,000 mB.

Temperature then drops to 0% and the lock clears; hover the information button on the right of the controller screen to watch progress. Existing high-tier tasks resume in 256-operation portions and regain their parallelism once reheated to their original tier.

Recovery must be **uninterrupted**. Disabling cooling, running out of coolant, a blocked output, breaking the structure or reloading resets the timer to 100%. Water, sodium, other coolants, toggling overclocking or simply waiting cannot clear the lock.

## Smart cooling module

The <ItemLink id="neoecoae:large_integrated_working_station_smart_cooling_module" /> takes the place of the coolant input hatch, and the workstation switches to its cooling-module model:

<GameScene interactive={true} zoom="3">
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="1" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" p:smart_cooling="true" x="1" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_output_hatch" p:formed="true" x="0" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_interface" p:formed="true" x="1" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_smart_cooling_module" p:formed="true" p:facing="south" x="2" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="1" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="1"></Block>
  <IsometricCamera yaw="45" pitch="30" />
  <BlockAnnotation x="2" y="0" z="1" color="#66bb6a">
    **Smart cooling module**: replaces the coolant input hatch; right-click it to set a target temperature.
  </BlockAnnotation>
</GameScene>

It makes the workstation hold a temperature automatically:

- **Right-click the module** to set a target from 0 to 99% (default 75%), saved with the world. It takes over preheating and active cooling while installed.
- It heats below the target, cools above it, and then offsets processing heat and natural cooling. Regulation continues while idle, paused or waiting to deliver products.
- It draws the strongest available coolant from the same ME network, keeping about one second of demand buffered. Byproducts still go to the output hatch.

The trade-off is cost: every regulated tick consumes a fixed **100,000 AE** (heating included, recipe energy separate), and coolant circulation uses at least **300 mB/t**, three times a normal hatch, rising automatically under heavy processing heat. Missing power or coolant, or a blocked output hatch, pauses regulation until resources return. Overheating still requires the Cryotheum recovery above, after which the module returns to its target.

This mod provides no crafting recipe for the smart cooling module by default; obtain it in Creative or through a recipe added by your modpack.

## Disabling temperature: coolant mode

The server option `largeWorkstationTemperatureEnabled` defaults to `true`. Set it to `false` and reopen the world or restart the server to use **coolant mode**: no heating, no overheating, and the batch limit comes directly from the coolant.

| Coolant | Batch limit |
| --- | ---: |
| None, or overclocking and active cooling not both enabled | 256 |
| Water | 16,384 |
| Sodium | 65,536 |
| Cryotheum Solution | 262,144 |

Both **Overclock** and **Active Cooling** must be on. An overclocked batch consumes 100 mB of coolant per processing tick that actually advances (300 mB with the smart cooling module); idle, unpowered and paused machines consume none. Insufficient coolant, a coolant tier below the batch's requirement or blocked byproducts pause the batch without losing materials or progress. Higher-tier coolant can keep lower-tier batches running.

Switching modes never discards accepted batches. Saved temperature and overheat state are kept while disabled and checked again when temperature mode returns, so an overheated machine still needs Cryotheum recovery.

## Processing time and energy

- **Processing time.** Each route uses its own duration; anything over 200 ticks (10 seconds) is capped at 200 ticks with total energy unchanged. Multiplied patterns scale materials, time and energy together. Waiting for output space charges no processing energy; missing power or cooling pauses work and keeps progress.
- **FE recipes.** Lightning Tech and other FE-priced routes keep their original FE cost. With Applied Flux installed the workstation draws FE from ME storage; any shortfall, or a missing Applied Flux, is paid with equivalent AE using AE2's current conversion settings.
- **Advanced AE Reaction Chamber** routes can pay with AE or FE, preferring AE when accepted; they pause only when neither can pay.
- Preheating and smart regulation always use AE. Energy never substitutes for lightning or other additional resources.

JEI / EMI list energy, processing time and any additional requirements below each recipe; FE recipes show FE when Applied Flux is installed and equivalent AE otherwise.

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| Blocks are placed but the structure doesn't form | Check that the controller is in the middle of the upper front row and that the lower back row reads "output hatch, interface, input hatch" from behind; or use auto-build |
| Overclocking is on but capacity is still 256 | Temperature is below 20%; let recipes run or right-click Overclock to preheat |
| A task only uses 256 after cooling down | Temporary portion mode; it recovers once overclocked and back at its original threshold |
| Temperature keeps rising with cooling on | Heating exceeds cooling; use a stronger coolant or check the coolant level and output hatch |
| With Cryotheum, temperature keeps falling and drops a tier | Cooling exceeds heating; use a weaker coolant or the smart cooling module |
| Compatible recipe patterns are unavailable | Check the installed upgrade module and that the request meets the route's minimum batch |
| Overheat recovery doesn't advance or resets | Supply real Cryotheum Solution with Active Cooling on and a clear output hatch, without interruptions or reloads |
