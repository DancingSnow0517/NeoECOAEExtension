# ECO MEGA basic-unit storage

Each compression chain has an immutable denomination table and one `long` balance.
For iron the table is nugget = 1, ingot = 9, block = 81. The limit is
`Long.MAX_VALUE` **basic units per chain**, not that many items at every level.
Different materials and different item components do not share balances.

Insertion first divides remaining capacity by the requested denomination. Extraction
first divides the balance by the denomination. Only the accepted quantity is then
multiplied; inventory arithmetic never clamps or saturates. Denominations too large
to represent are rejected. Aggregate tooltip statistics may saturate across chains.

## Display and controls

- Markers select accepted chains; they do not select the display denomination.
- New cells display the highest representable denomination plus lower remainders.
- In the storage host's marking panel, **Shift + left-click** raises and **Shift + right-click**
  lowers that chain's display cutoff. The marker item changes to the selected denomination,
  which is reported in the action bar.
- Changing a cutoff does not transfer items, alter the marker or change the balance.
- Removing a marker stops insertion but retains existing inventory and conversion paths.
- Without a compression card, insertion accepts only the exact configured item.
  Previously stored smaller remainders remain visible and extractable. Extraction of
  those remainders cannot convert the larger stock while the card is absent.
- CPU conversion patterns use the same cutoff as the published inventory. Equivalent
  totals are never published as simultaneous copies of the same stock.

## Persistence and recipe changes

Format version 1 stores the representative's full item key, the balance, a complete
table of full keys and factors, and independent per-chain cutoff settings. After a
recipe change, incompatible occupied accounts reject new deposits and expose only
their original basic item for recovery. They do not apply the new ratios to old units.
Once emptied, a new account can use the new recipe table.

Legacy version 0 did not store historical ratios. Migration therefore uses the
currently available chain and preserves the previous effective marker cutoff. **It
cannot establish whether a recipe changed before migration.** Back up old saves and
migrate with their original recipe set. An unknown legacy basis is preserved as an
unresolved entry rather than assumed to be a one-unit ordinary item.

Unresolved entries keep their original data and format version, occupy type slots,
prevent treating the cell as empty, and produce a tooltip notice. They are retried
when the inventory is reopened; normal writes and clearing resolved stock preserve
them. Missing items/components, duplicate accounts and invalid denomination tables
are not silently discarded.

## Integration boundaries

`BulkUnits` owns denomination validation, bounded quantity calculations and inventory
decomposition without referencing MEGA. `MegaBulkUnits` imports MEGA's recipe chains.
The existing cell retains host lifecycle/batch persistence, and the existing MEGA
conversion-pattern execution bridge remains in use. This change does not replace
recipe discovery, redesign pending-output ownership, or add cross-subnet crafting.

Configuration caching now compares the component-backed marker list rather than
constructing `ConfigInventory` for every lookup. Content writes retain compiled
denominations; recipe object changes invalidate them. The decompression service
still polls hosts, but refreshes AE2's provider only when patterns or priority change.

## Verification

Regression tests cover mixed denominations and simulation, independent cutoffs,
component-sensitive ordinary items, long boundaries, denominations above long range,
save/reopen, legacy migration, unresolved-data retention, card removal, recipe changes,
and ECO planning from both compressed and basic stock with cycle handling on/off.
Live GUI, ordinary AE2 CPU execution, IO-port lifecycle and subnet behavior require
in-game verification; JVM planning tests do not establish those results.
