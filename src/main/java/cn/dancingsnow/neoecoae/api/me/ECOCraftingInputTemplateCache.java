package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** CPU-local candidate snapshots. Validity and quantities are deliberately not cached. */
final class ECOCraftingInputTemplateCache {
    private static final int MAX_INPUTS = 256;
    private static final int MAX_TEMPLATES = 4096;
    private final Map<IPatternDetails.IInput, List<InputTemplate>> entries = new IdentityHashMap<>();
    private final Set<AEKey> knownKeys = new HashSet<>();
    private ICraftingInventory source;
    private long reloadGeneration = Long.MIN_VALUE;
    private int templateCount;

    List<InputTemplate> get(ICraftingInventory inventory, IPatternDetails.IInput input) {
        long generation = AE2PatternIntrospection.reloadGeneration();
        if (source != inventory || reloadGeneration != generation) {
            clear();
            source = inventory;
            reloadGeneration = generation;
        }
        var cached = entries.get(input);
        if (cached != null) return cached;

        var templates = new ArrayList<InputTemplate>();
        for (var stack : input.getPossibleInputs()) {
            for (var key : inventory.findFuzzyTemplates(stack.what())) {
                templates.add(new InputTemplate(key, stack.amount()));
            }
        }
        var result = List.copyOf(templates);
        if (result.size() <= MAX_TEMPLATES) {
            if (entries.size() >= MAX_INPUTS || templateCount + result.size() > MAX_TEMPLATES) {
                entries.clear();
                knownKeys.clear();
                templateCount = 0;
            }
            entries.put(input, result);
            for (var template : result) knownKeys.add(template.key());
            templateCount += result.size();
        }
        return result;
    }

    void clear() {
        if (!entries.isEmpty()) entries.clear();
        knownKeys.clear();
        templateCount = 0;
        source = null;
    }

    void inventoryChanged(AEKey key) {
        // Updating the amount of a surviving key leaves AE2's candidate membership and order unchanged.
        if (key != null && source instanceof ListCraftingInventory inventory
                && knownKeys.contains(key) && inventory.list.get(key) > 0L) return;
        clear();
    }
}
