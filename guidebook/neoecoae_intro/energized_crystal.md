---
navigation:
  title: Energized Budding Crystal
  icon: neoecoae:flawless_budding_energized_crystal
  position: 10
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:flawless_budding_energized_crystal
  - neoecoae:flawed_budding_energized_crystal
  - neoecoae:chipped_budding_energized_crystal
  - neoecoae:damaged_budding_energized_crystal
  - neoecoae:energized_crystal
---

# Obtaining Energized Budding Crystal

The Energized Budding Crystal is a key material for crafting many components in this mod. It functions similarly to AE2's Budding Certus Quartz but grows Energized Crystals instead.

## Crystal Tiers

<ItemGrid>
  <ItemIcon id="neoecoae:flawless_budding_energized_crystal" />
  <ItemIcon id="neoecoae:flawed_budding_energized_crystal" />
  <ItemIcon id="neoecoae:chipped_budding_energized_crystal" />
  <ItemIcon id="neoecoae:damaged_budding_energized_crystal" />
</ItemGrid>

There are four quality tiers of Energized Budding Crystal:
- <ItemLink id="neoecoae:flawless_budding_energized_crystal" /> - Best quality, does not degrade
- <ItemLink id="neoecoae:flawed_budding_energized_crystal" /> - High quality
- <ItemLink id="neoecoae:chipped_budding_energized_crystal" /> - Medium quality
- <ItemLink id="neoecoae:damaged_budding_energized_crystal" /> - Lowest quality

Higher quality crystals grow faster and have better durability.

## Method 1: Lightning Strike

Place AE2 Budding Certus Quartz blocks and strike them with lightning. The blocks have a chance to transform into Energized Budding Crystal.

### Conversion Table

| Source Block | Result |
|--------------|--------|
| <ItemLink id="ae2:flawless_budding_quartz" /> | <ItemLink id="neoecoae:flawless_budding_energized_crystal" /> |
| <ItemLink id="ae2:flawed_budding_quartz" /> | <ItemLink id="neoecoae:flawed_budding_energized_crystal" /> |
| <ItemLink id="ae2:chipped_budding_quartz" /> | <ItemLink id="neoecoae:chipped_budding_energized_crystal" /> |
| <ItemLink id="ae2:damaged_budding_quartz" /> | <ItemLink id="neoecoae:chipped_budding_energized_crystal" /> |

### Conversion Mechanics

- The conversion has a random chance based on distance from the lightning strike point
- Blocks directly hit by lightning have the highest conversion rate
- Blocks within a 3x3x3 area around the strike point may also convert
- Closer blocks have higher conversion rates (base chance: 1/2 at center, decreasing with distance)

### Tips
- Use a Lightning Rod or Channeling trident to control where lightning strikes
- Place multiple budding quartz blocks close together for efficient conversion
- Build during thunderstorms for natural lightning, or use a trident with Channeling

## Method 2: Crystal Fixer (Requires ExtendedAE)

If you have the ExtendedAE mod installed, you can use the Crystal Fixer to upgrade Energized Crystal blocks progressively.

<ItemGrid>
  <ItemIcon id="neoecoae:energized_crystal" />
</ItemGrid>

### Upgrade Path

| Input | Output | Success Rate |
|-------|--------|--------------|
| <ItemLink id="neoecoae:energized_crystal" /> | <ItemLink id="neoecoae:damaged_budding_energized_crystal" /> | 80% |
| <ItemLink id="neoecoae:damaged_budding_energized_crystal" /> | <ItemLink id="neoecoae:chipped_budding_energized_crystal" /> | 80% |
| <ItemLink id="neoecoae:chipped_budding_energized_crystal" /> | <ItemLink id="neoecoae:flawed_budding_energized_crystal" /> | 5% |

### Usage
- Use <ItemLink id="neoecoae:energized_crystal" /> as fuel for the Crystal Fixer
- The final upgrade to Flawed has only 5% success rate, so stock up on materials
- This method is more controlled than lightning but requires ExtendedAE

## Growing Energized Crystals

Once you have Energized Budding Crystal blocks, they will naturally grow Energized Crystal clusters over time, similar to how Budding Certus Quartz grows Certus Quartz crystals.

<ItemGrid>
  <ItemIcon id="neoecoae:energized_crystal" />
</ItemGrid>

The <ItemLink id="neoecoae:energized_crystal" /> harvested from these clusters is used to craft various components for the ECO multiblock systems.
