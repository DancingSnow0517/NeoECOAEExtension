package cn.dancingsnow.neoecoae.integration.ae2pattern;

import appeng.api.inventories.InternalInventory;
import appeng.api.util.IConfigManager;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;

import io.github.lounode.ae2pattern.api.IPatternDiskHost;
import io.github.lounode.ae2pattern.api.PatternDiskApi;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
/**
 * Pattern-disk aware storage for NEO ECO machines, backed by AE2 Pattern Disk's public api.
 *
 * <p>AE2 Pattern Disk is an optional dependency: {@code PatternDiskApi} is compiled against, but the mod
 * need not be present at runtime. The isolation is the integration manager's, not this class's -
 * {@code compileContent} only records the annotated class name, and {@code IntegrationInstance.newInstance}
 * loads it with {@code Class.forName} after {@code loadAll} confirmed the mod is loaded. That is why the
 * import above is safe: this class is never touched when the mod is absent.</p>
 *
 * <p>Both of this mod's pattern hosts come through here: the large workstation interface and the pattern bus.
 * The bus used to be served the other way round - this mod exposed a static store for the purpose and AE2
 * Pattern Disk reached into it - and that spi is gone. What stays on the other side is that mod's own listing
 * of the bus, which needs nothing from here.</p>
 */
@Integration("ae2_pattern_disk")
public class PatternDiskIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae.integration.ae2pattern");

    /**
     * The api entry points this integration needs, each with its parameter count.
     *
     * <p>{@code removeAt} is in the list as a version floor rather than a call site: it arrived with the newest
     * revision of this api, and any build that has it has the shapes the other entries are checked for. The rest
     * are the methods this integration actually calls.</p>
     *
     * <p>Checked by name and arity rather than by constant: the version is inlined by javac, and a build new
     * enough for one method says nothing about the others. Arity catches an entry point being replaced by one
     * with a different shape, which a name-only check would wave through.</p>
     */
    private static final Map<String, Integer> REQUIRED_ENTRY_POINTS = Map.of(
            "isPatternDisk", 1,
            "contents", 1,
            "removeAt", 2,
            "decodePatterns", 2,
            "canAccept", 3,
            "insert", 4,
            "terminalView", 5,
            "registerDiskHost", 1
    );

    public void apply() {
        // Probing for the methods this integration calls, rather than comparing version numbers. The constant
        // is inlined by javac, so reading API_VERSION would report what this build compiled against, not what
        // is installed - and a version enough for one method says nothing about the others.
        String missing = firstMissingEntryPoint();
        if (missing != null) {
            LOGGER.warn(
                    "[NEO ECO] AE2 Pattern Disk is present but its api has no {}(); the pattern-disk integration "
                            + "stands down. This build needs a newer AE2 Pattern Disk.",
                    missing);
            return;
        }
        LOGGER.info("[NEO ECO] AE2 Pattern Disk present; pattern-disk storage is available to hosts that opt in");
        // Hand the facade its backend. Until this runs, a host asking for one gets null and stays plain.
        PatternDiskSupport.install(new AepdPatternDiskBackend());
        registerDiskHosts();
    }

    /**
     * @return the name of the first api entry point this integration needs and cannot find, or {@code null}
     *         when they are all there
     */
    @Nullable
    private static String firstMissingEntryPoint() {
        for (var required : REQUIRED_ENTRY_POINTS.entrySet()) {
            boolean found = false;
            for (Method method : PatternDiskApi.class.getDeclaredMethods()) {
                if (method.getName().equals(required.getKey())
                        && method.getParameterCount() == required.getValue()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return required.getKey();
            }
        }
        return null;
    }

    /**
     * Lets AE2 Pattern Disk's terminals list the disks inside this mod's machines.
     *
     * <p>Both machines that hold pattern disks are registered here. The bus used to be listed by AE2 Pattern
     * Disk on its own, which reached into it through a store this mod exposed for the purpose; that spi is gone,
     * and that mod's listing stands down without it - so leaving the bus out here would have taken its disks off
     * every terminal.</p>
     *
     * <p>Adapters are cached by position because both terminals de-duplicate hosts by object identity: a fresh
     * adapter per query would make one machine's disks appear once per call.</p>
     */
    private void registerDiskHosts() {
        // Cached by position, but validated on every query.
        //
        // The terminals de-duplicate hosts by object identity, so a fresh adapter per query would make one
        // machine's disks appear once per host - hence the cache. Caching purely by position would be wrong
        // the other way: after a chunk reload the block entity at that position is a different object, and
        // handing out the old adapter would let the terminal read and write an inventory that is no longer in
        // the level. Entries whose machine is gone are dropped, so the map does not hold block entities alive.
        Map<Long, MachineDiskHostAdapter> adapters = new HashMap<>();
        PatternDiskApi.registerDiskHost(grid -> {
            List<IPatternDiskHost> hosts = new ArrayList<>();
            Set<Long> present = new HashSet<>();
            for (var bus : grid.getActiveMachines(ECOCraftingPatternBusBlockEntity.class)) {
                addHost(adapters, hosts, present, bus, bus::getPatternSlotInventory);
            }
            for (var interfaceBlock : grid.getActiveMachines(
                    ECOLargeIntegratedWorkingStationInterfaceBlockEntity.class)) {
                addHost(adapters, hosts, present, interfaceBlock,
                        () -> interfaceBlock.getWorkstationProvider().getPatternInv());
            }
            adapters.keySet().retainAll(present);
            return hosts;
        });
    }

    /**
     * Adds one machine to {@code hosts}, reusing its adapter unless the one on file speaks for a block entity
     * that is gone.
     */
    private static void addHost(
            Map<Long, MachineDiskHostAdapter> adapters,
            List<IPatternDiskHost> hosts,
            Set<Long> present,
            BlockEntity machine,
            Supplier<InternalInventory> diskSlots) {
        long position = machine.getBlockPos().asLong();
        present.add(position);
        MachineDiskHostAdapter adapter = adapters.get(position);
        if (adapter == null || !adapter.speaksFor(machine)) {
            adapter = new MachineDiskHostAdapter(machine, diskSlots,
                    () -> machine instanceof PatternProviderLogicHost provider ? provider.getLogic().getConfigManager()
                            : null);
            adapters.put(position, adapter);
        }
        hosts.add(adapter);
    }
}
