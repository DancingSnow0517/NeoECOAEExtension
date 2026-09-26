# Storage cell asset registration

All imported storage cell artwork is now connected to registered items or drive models. Items,
recipes, and MEGA variants remain conditional on the matching integration mod being loaded.

## Registered resource families

| Family | Integration | Standard cells | MEGA cells |
| --- | --- | --- | --- |
| Item, Fluid | NeoECOAE / MEGA Cells | 16M, 64M, 256M | 4G when MEGA Cells is loaded |
| Bulk Item | NeoECOAE | 16M, 64M, 256M; one item type | — |
| Energy | Applied Flux | Existing 16M, 64M, 256M FE cells | 4G with MEGA Cells |
| Chemical | Applied Mekanistics | Existing 16M, 64M, 256M cells | 4G with MEGA Cells |
| Mana | Applied Botanics | Existing 16M, 64M, 256M cells | 4G with MEGA Cells |
| Source | Ars Énergistique | Existing 16M, 64M, 256M cells | 4G with MEGA Cells |
| Lightning | AE2 Lightning Tech | Existing 16M, 64M, 256M cells | — |
| Omni, Complex Omni, Quantum Omni | AE2 Omni Cells | Existing 16M, 64M, 256M cells | — |
| Air | Applied Pneumatics | 16M, 64M, 256M | 4G with MEGA Cells |
| Experience | Applied Experienced | 16M, 64M, 256M | 4G with MEGA Cells |
| Soul | Applied Soul + Soulplied Energistics | 16M, 64M, 256M | 4G with MEGA Cells |

Cells use the corresponding AE key type. Recipes and cell registrations for mod-specific resources
are guarded by their mod-loaded conditions. The MEGA Air, Experience, Mana, Source, and Soul cells
also require the resource integration shown above. Mana uses `appbot:mana`; Source uses
`arseng:source`; Soul uses `soulplied_energistics:soul`.

## Imported asset status

- **42/42 drive models:** standard Air, Bulk Item, Energy, Experience, and Soul models are assigned;
  MEGA L9 models are assigned to their matching family cells.
- **92/92 nested item models:** each housing and 16M/64M/256M icon is used by a registered item.
  MEGA 4G items use the imported 256M tier icon with the cell status overlay.
- **12/12 newly imported compatibility textures:** each is referenced by its matching nested item
  model.

## Online compatibility references checked

Versions below were checked against the project’s 1.21.1 NeoForge target on 2026-09-23.

- [Applied Pneumatics](https://www.curseforge.com/minecraft/mc-mods/applied-pneumatics): 1.0.9 for
  1.21.1 NeoForge; provides AE air keys and storage cells.
- [Applied Experienced](https://www.curseforge.com/minecraft/mc-mods/applied-experienced): 1.3.2
  for 1.21.1 NeoForge; provides experience storage cells.
- [Applied Soul](https://www.curseforge.com/minecraft/mc-mods/applied-soul): 2.1.0 hotfix for
  1.21.1 NeoForge. It uses the soul key supplied by
  [Soulplied Energistics](https://www.curseforge.com/minecraft/mc-mods/soulplied-energistics)
  1.0.3.
- [Applied Flux](https://www.curseforge.com/minecraft/mc-mods/applied-flux): 2.1.5 for 1.21.1
  NeoForge; the project already registers its FE storage cells.
- [Applied Botanics Addon](https://www.curseforge.com/minecraft/mc-mods/applied-botanics-addon):
  1.6.0-alpha.3 for 1.21.1 NeoForge. MEGA Cells 4.11.0 release notes confirm its Botanics
  integration is enabled.
- [Ars Énergistique](https://www.curseforge.com/minecraft/mc-mods/ars-energistique): 2.1.1-beta
  for 1.21.1 NeoForge; provides Source storage cells.
- [MEGA Cells](https://www.curseforge.com/minecraft/mc-mods/mega-cells): 4.11.0 NeoForge 1.21.1,
  already configured by the project. Its release notes confirm Applied Soul and Applied Botanics
  compatibility.
