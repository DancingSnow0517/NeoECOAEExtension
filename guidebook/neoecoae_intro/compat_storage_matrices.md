---
navigation:
  title: Compatibility Storage Matrices
  icon: neoecoae:eco_drive
  position: 10
  parent: neoecoae_intro/storage_system.md
item_ids:
  - neoecoae:eco_omni_cell_housing
  - neoecoae:eco_omni_cell_16m
  - neoecoae:eco_omni_cell_64m
  - neoecoae:eco_omni_cell_256m
  - neoecoae:eco_complex_omni_cell_housing
  - neoecoae:eco_complex_omni_cell_16m
  - neoecoae:eco_complex_omni_cell_64m
  - neoecoae:eco_complex_omni_cell_256m
  - neoecoae:eco_quantum_omni_cell_housing
  - neoecoae:eco_quantum_omni_cell_16m
  - neoecoae:eco_quantum_omni_cell_64m
  - neoecoae:eco_quantum_omni_cell_256m
  - neoecoae:eco_lightning_cell_housing
  - neoecoae:eco_lightning_cell_16m
  - neoecoae:eco_lightning_cell_64m
  - neoecoae:eco_lightning_cell_256m
---

# Compatibility Storage Matrices

Compatibility storage matrices extend the <ItemLink id="neoecoae:eco_drive" /> to key types provided by other mods. A storage subsystem controller can operate matrices of its own tier or any lower tier.

These matrices are registered only when their companion mod is installed. **AE2 Omni Cells** provides the Omni families, while **AE2 Lightning Tech** provides the Lightning family.

## Omni Storage Matrices

Omni matrices accept every AE key type registered in the current game, allowing items, fluids, energy, chemicals, and other supported resources to share one matrix.

### Omni

<ItemGrid>
  <ItemIcon id="neoecoae:eco_omni_cell_16m" />
  <ItemIcon id="neoecoae:eco_omni_cell_64m" />
  <ItemIcon id="neoecoae:eco_omni_cell_256m" />
</ItemGrid>

The standard Omni family is intended for compact mixed storage. Every tier can hold up to 63 distinct types.

| Matrix | Capacity | Type Limit | Idle Drain |
|--------|----------|------------|------------|
| <ItemLink id="neoecoae:eco_omni_cell_16m" /> | 16 MB | 63 | 8 AE/t |
| <ItemLink id="neoecoae:eco_omni_cell_64m" /> | 64 MB | 63 | 9 AE/t |
| <ItemLink id="neoecoae:eco_omni_cell_256m" /> | 256 MB | 63 | 10 AE/t |

The <ItemLink id="neoecoae:eco_omni_cell_housing" /> is made with Ender Ingots and is combined with the corresponding ECO storage component.

<RecipeFor id="neoecoae:eco_omni_cell_housing" />

### Complex Omni

<ItemGrid>
  <ItemIcon id="neoecoae:eco_complex_omni_cell_16m" />
  <ItemIcon id="neoecoae:eco_complex_omni_cell_64m" />
  <ItemIcon id="neoecoae:eco_complex_omni_cell_256m" />
</ItemGrid>

Complex Omni matrices trade much higher idle drain for a greatly expanded type limit.

| Matrix | Capacity | Type Limit | Idle Drain |
|--------|----------|------------|------------|
| <ItemLink id="neoecoae:eco_complex_omni_cell_16m" /> | 16 MB | 1,600 | 256 AE/t |
| <ItemLink id="neoecoae:eco_complex_omni_cell_64m" /> | 64 MB | 3,200 | 512 AE/t |
| <ItemLink id="neoecoae:eco_complex_omni_cell_256m" /> | 256 MB | 6,400 | 1,024 AE/t |

Its <ItemLink id="neoecoae:eco_complex_omni_cell_housing" /> requires Charged Ender Ingots and a Complex Link Processor.

<RecipeFor id="neoecoae:eco_complex_omni_cell_housing" />

### Quantum Omni

<ItemGrid>
  <ItemIcon id="neoecoae:eco_quantum_omni_cell_16m" />
  <ItemIcon id="neoecoae:eco_quantum_omni_cell_64m" />
  <ItemIcon id="neoecoae:eco_quantum_omni_cell_256m" />
</ItemGrid>

Quantum Omni matrices have no type limit. Their byte capacity still limits the total stored amount, and their exceptional flexibility carries a substantial idle drain.

| Matrix | Capacity | Type Limit | Idle Drain |
|--------|----------|------------|------------|
| <ItemLink id="neoecoae:eco_quantum_omni_cell_16m" /> | 16 MB | Unlimited | 6,561 AE/t |
| <ItemLink id="neoecoae:eco_quantum_omni_cell_64m" /> | 64 MB | Unlimited | 19,683 AE/t |
| <ItemLink id="neoecoae:eco_quantum_omni_cell_256m" /> | 256 MB | Unlimited | 59,049 AE/t |

The <ItemLink id="neoecoae:eco_quantum_omni_cell_housing" /> requires a Multidimensional Expansion Processor.

<RecipeFor id="neoecoae:eco_quantum_omni_cell_housing" />

Empty Omni matrices can be disassembled with alternate-use to recover their housing and ECO storage component.

## Lightning Storage Matrices

<ItemGrid>
  <ItemIcon id="neoecoae:eco_lightning_cell_16m" />
  <ItemIcon id="neoecoae:eco_lightning_cell_64m" />
  <ItemIcon id="neoecoae:eco_lightning_cell_256m" />
</ItemGrid>

Lightning matrices store the High Voltage and Extreme High Voltage lightning keys from AE2 Lightning Tech. Their LE level indicates progression rather than a capacity suffix.

| Matrix | Effective Capacity | Types | Idle Drain | Processing Machine |
|--------|--------------------|-------|------------|--------------------|
| <ItemLink id="neoecoae:eco_lightning_cell_16m" /> | 1,048,576 | 2 | 32,768 AE/t | Lightning Simulation Room |
| <ItemLink id="neoecoae:eco_lightning_cell_64m" /> | 4,194,304 | 2 | 131,072 AE/t | Lightning Assembly Chamber |
| <ItemLink id="neoecoae:eco_lightning_cell_256m" /> | 16,777,216 | 2 | 524,288 AE/t | Overload Processing Factory |

### LE4 Processing

The Lightning Simulation Room processes one housing, one 16M ECO storage component, and four Lightning Cell Components V. Processing consumes 8,000,000 FE and 256 High Voltage lightning.

### LE6 Processing

The Lightning Assembly Chamber processes one housing, one 64M ECO storage component, 16 Lightning Cell Components V, 16 Overload Alloy Plates, four Overload Singularities, and four Lightning Collapse Matrices. Processing consumes 32,000,000 FE and 256 Extreme High Voltage lightning.

### LE9 Processing

The Overload Processing Factory processes one housing, one 256M ECO storage component, 64 Lightning Cell Components V, 16 Ultimate Overload Cores, 16 Firmament Alloy Ingots, 64 Overload Alloy Plates, 64 Superconducting Processors, 16 Lightning Collapse Matrices, and 64,000 mB of Cryotheum Solution. Processing consumes 128,000,000 FE and 1,024 Extreme High Voltage lightning.

Empty Lightning matrices can be disassembled with alternate-use. The housing, ECO storage component, and all Lightning Cell Components V are recovered; auxiliary processing materials are consumed.
