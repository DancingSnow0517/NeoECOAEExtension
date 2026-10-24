---
navigation:
  title: Compatibility Storage Matrices
  icon: neoecoae:eco_drive
  position: 10
  parent: neoecoae_intro/storage_system.md
---

# Compatibility Storage Matrices

Bigger AE2 Quantum matrices are L6 with 128 MiB shared capacity, 65,536 bytes overhead per type and 20 AE/t idle power. Quantum and L9 Digital Singularity each offer items, fluids, chemicals, FE, experience and source: 12 cells total. The first three channels support five types per cell, the others one. Assemble an ECO housing with the upstream component; empty-cell disassembly returns both. Digital Singularity preserves exact stored amounts, visible in terminal hover text.

## Digital Singularity Matrices: Network Power Tiers

ECO Digital Singularity matrices require **Bigger AE2**. Item, fluid and chemical matrices each store five resource types with unlimited quantities. FE, Experience and Source matrices each store one type.

All usable ECO Digital Singularity matrices in one ME network share a count `N`, which sets the idle drain of **each matrix**. Different resource types, drives and storage controllers share this count, including empty matrices. Ordinary and Quantum matrices do not contribute.

| Digital Singularity Matrices | Idle Drain per Matrix | Combined Idle Drain |
|-----------------------------|-----------------------|---------------------|
| 1 | 1,024 AE/t | 1,024 AE/t |
| 2 | 4,096 AE/t | 8,192 AE/t |
| 3 | 16,384 AE/t | 49,152 AE/t |
| 4 | 65,535 AE/t | 262,140 AE/t |
| 5 | 262,144 AE/t | 1,310,720 AE/t |
| 6 | 1,048,576 AE/t | 6,291,456 AE/t |
| 7 | 1,048,576 AE/t | 7,340,032 AE/t |

At six or more matrices, the rate stays capped at **1,048,576 AE/t per matrix**. Total demand then grows linearly with the matrix count. Inserting or removing a matrix updates the rate for every Digital Singularity matrix in the network.

Controller, drive and channel power are additional. Insufficient power takes the ME network offline while preserving stored contents. Losing power does not reset the count; removing matrices reduces demand. Separate ME networks count independently. Each storage controller displays its proportional share of the network surcharge.

Compatibility storage matrices extend the <ItemLink id="neoecoae:eco_drive" /> to key types provided by other mods. A storage subsystem controller can operate matrices of its own tier or any lower tier.

These matrices are registered only when their companion mod is installed. **AE2 Omni Cells** provides the Omni families, **AE2 Lightning Tech** the Lightning family, **AppFlux** the FE family, **Ars Energistique** the Source family, **AppBot** the Mana family, and **AppMek** the Chemical family.

The `16M`, `64M`, and `256M` labels identify the recipe component tier and do not always equal the actual storage-byte capacity. The tables below list each matrix's actual AE storage-byte capacity.

## Omni Storage Matrices

Omni matrices accept every AE key type registered in the current game, allowing items, fluids, energy, chemicals, and other supported resources to share one matrix.

### Omni (AE2 Omni Cells)

The standard Omni family is intended for compact mixed storage. Every tier can hold up to 63 distinct types.

| Tier | Capacity | Type Limit | Idle Drain |
|--------|----------|------------|------------|
| LE4 (16M) | 256 MiB | 63 | 8 AE/t |
| LE6 (64M) | 1 GiB | 63 | 9 AE/t |
| LE9 (256M) | 4 GiB | 63 | 10 AE/t |

The Omni storage matrix housing is made with Ender Ingots. Finished matrices are assembled in the Integrated Working Station.

### Complex Omni (AE2 Omni Cells)

Complex Omni matrices trade much higher idle drain for a greatly expanded type limit.

| Tier | Capacity | Type Limit | Idle Drain |
|--------|----------|------------|------------|
| LE4 (16M) | 256 MiB | 1,600 | 256 AE/t |
| LE6 (64M) | 1 GiB | 3,200 | 512 AE/t |
| LE9 (256M) | 4 GiB | 6,400 | 1,024 AE/t |

The Complex Omni storage matrix housing requires Charged Ender Ingots and a Complex Link Processor. Finished matrices are assembled in the Integrated Working Station.

### Quantum Omni (AE2 Omni Cells)

Quantum Omni matrices have no type limit and the same capacity as the corresponding standard Omni matrix. Their exceptional flexibility carries a substantial idle drain.

| Tier | Capacity | Type Limit | Idle Drain |
|--------|----------|------------|------------|
| LE4 (16M) | 256 MiB | Unlimited | 6,561 AE/t |
| LE6 (64M) | 1 GiB | Unlimited | 19,683 AE/t |
| LE9 (256M) | 4 GiB | Unlimited | 59,049 AE/t |

The Quantum Omni storage matrix housing requires a Multidimensional Expansion Processor. Finished matrices are assembled in the Integrated Working Station.

### Integrated Working Station Recipes

Every recipe requires one housing from the matching family and 10 matching-tier Omni storage components. The remaining materials and energy are:

| Family | Tier | Link/Expansion Processors | Singularities | Energy |
|--------|------|---------------------------|---------------|--------|
| Omni | LE4 / LE6 / LE9 | 2 / 4 / 8 | 1 / 4 / 16 | 1,000 / 12,000 / 144,000 AE |
| Complex Omni | LE4 / LE6 / LE9 | 2 / 4 / 8 | 2 / 8 / 32 | 1,000 / 12,000 / 144,000 AE |
| Quantum Omni | LE4 / LE6 / LE9 | 2 / 4 / 8 | 4 / 16 / 64 | 1,000 / 12,000 / 144,000 AE |

Only empty matrices can be disassembled with alternate-use. Standard and Complex Omni matrices return their housing and one matching component; Quantum Omni matrices return their housing and all 10 matching components. Processors and singularities are not recovered.

## Other Compatibility Storage Matrices

### FE Storage Matrices (AppFlux)

FE storage matrices require **AppFlux** and store Forge Energy (FE) in the ME network. They are crafted in the Integrated Working Station from matching AppFlux energy cores, energy processors, singularities, and an FE housing.

Empty FE storage matrices can be disassembled with alternate-use to recover 10 matching AppFlux energy cores.

| Tier | Capacity | Type Limit | Idle Drain |
|--------|----------|------------|------------|
| LE4 (16M) | 256 MiB | 1 | 256 AE/t |
| LE6 (64M) | 1 GiB | 1 | 1,024 AE/t |
| LE9 (256M) | 4 GiB | 1 | 4,096 AE/t |

### Source Storage Matrices (Ars Energistique)

Source storage matrices require **Ars Energistique** and store Ars Nouveau Source. Their housing is made in an Enchanting Apparatus, then combined with the matching ECO storage component.

| Tier | Capacity | Type Limit | Idle Drain |
|--------|----------|------------|------------|
| LE4 (16M) | 16 MiB | 1 | 16 AE/t |
| LE6 (64M) | 64 MiB | 1 | 64 AE/t |
| LE9 (256M) | 256 MiB | 1 | 256 AE/t |

### Mana Storage Matrices (AppBot)

Mana storage matrices require **AppBot** and store Botania Mana. Their housing is made by infusing an item-matrix housing with 100,000 Mana, then combined with the matching ECO storage component.

| Tier | Capacity | Type Limit | Idle Drain |
|--------|----------|------------|------------|
| LE4 (16M) | 16 MiB | 1 | 16 AE/t |
| LE6 (64M) | 64 MiB | 1 | 64 AE/t |
| LE9 (256M) | 256 MiB | 1 | 256 AE/t |

### Chemical Storage Matrices (AppMek)

Chemical storage matrices require **AppMek** and store valid Mekanism chemicals. They are assembled from a chemical housing and the matching ECO storage component; chemicals that fail Mekanism's attribute requirements cannot be stored.

| Tier | Capacity | Type Limit | Idle Drain |
|--------|----------|------------|------------|
| LE4 (16M) | 16 MiB | 25 | 16 AE/t |
| LE6 (64M) | 64 MiB | 25 | 64 AE/t |
| LE9 (256M) | 256 MiB | 25 | 256 AE/t |

Experience, air, soul, matter and stress cells require their resource integrations. Standard tiers use 16 / 64 / 256 MiB, normally one type or three for matter. Data Energistics cells use 256 MiB / 1 GiB / 4 GiB and four types. Resource units, assembly and empty-cell disassembly follow the current JEI / EMI recipes.

Regular ECO MEGA cells require MEGA Cells and are L9, 4 GiB, with 1,024 AE/t base idle power. Item and fluid cells each support 315 types; external channels also require their integrations. Radioactive chemical cells form a separate L4 / L6 / L9 single-type family. The bulk matrix has 25 configuration slots, or 50 with an ECO MEGA Upgrade Card. Compression variants are currently disabled; use actual quantities rather than theoretical compression-chain capacity.

## Lightning Storage Matrices

Lightning matrices store the High Voltage and Extreme High Voltage lightning keys from AE2 Lightning Tech. Their LE level indicates progression rather than a capacity suffix.

| Tier | Effective Capacity | Types | Idle Drain | Processing Machine |
|--------|--------------------|-------|------------|--------------------|
| LE4 (16M) | 1,048,576 | 2 | 32,768 AE/t | Lightning Simulation Room |
| LE6 (64M) | 4,194,304 | 2 | 131,072 AE/t | Lightning Assembly Chamber |
| LE9 (256M) | 16,777,216 | 2 | 524,288 AE/t | Overload Processing Factory |

### LE4 Processing

The Lightning Simulation Room processes one housing, one 16M ECO storage component, and one Lightning Cell Component V. Processing consumes 1,000,000 FE and 32 High Voltage lightning.

### LE6 Processing

The Lightning Assembly Chamber processes one housing, one 64M ECO storage component, two Lightning Cell Components V, two Overload Alloy Plates, and one Overload Singularity. Processing consumes 4,000,000 FE and 64 Extreme High Voltage lightning.

### LE9 Processing

The Overload Processing Factory processes one housing, one 256M ECO storage component, four Lightning Cell Components V, two Ultimate Overload Cores, two Firmament Alloy Ingots, four Overload Alloy Plates, four Superconducting Processors, one Lightning Collapse Matrix, and 64,000 mB of Cryotheum Solution. Processing consumes 16,000,000 FE and 128 Extreme High Voltage lightning.

Empty Lightning matrices can be disassembled with alternate-use. The housing, ECO storage component, and all Lightning Cell Components V are recovered; auxiliary processing materials are consumed.
