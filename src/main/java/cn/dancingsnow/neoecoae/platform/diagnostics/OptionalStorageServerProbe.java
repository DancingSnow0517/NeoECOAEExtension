package cn.dancingsnow.neoecoae.platform.diagnostics;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCellItem;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.recipe.StorageCellDisassemblyRecipe;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.ModList;
import org.slf4j.LoggerFactory;

/** Opt-in registration, recipe, storage and cold-reload acceptance in an isolated server world. */
public final class OptionalStorageServerProbe {
    private static final String[] SIZES = {"16m", "64m", "256m"};
    private static final BigInteger HUGE = BigInteger.TEN.pow(45).add(BigInteger.valueOf(17));
    private OptionalStorageServerProbe() {}

    private record Family(String channel, String housing, List<String> cells, List<String> mods) {
        boolean enabled() { return mods.stream().allMatch(ModList.get()::isLoaded); }
    }

    private static Family tiered(String channel, String prefix, String housing, String... mods) {
        return new Family(channel, housing, java.util.Arrays.stream(SIZES).map(size -> prefix + size).toList(), List.of(mods));
    }

    private static Family single(String channel, String id, String housing, String... mods) {
        return new Family(channel, housing, List.of(id), List.of(mods));
    }

    private static List<Family> families() {
        List<Family> families = new ArrayList<>(List.of(
            tiered("mana", "eco_mana_storage_cell_", "eco_mana_cell_housing", "appbot"),
            tiered("fe", "eco_fe_storage_cell_", "eco_fe_cell_housing", "appflux"),
            tiered("chemical", "eco_chemical_storage_cell_", "eco_chemical_cell_housing", "appmek"),
            tiered("source", "eco_source_storage_cell_", "eco_source_cell_housing", "arseng"),
            tiered("air", "eco_air_cell_", "eco_air_cell_housing", "appliedpneumatics"),
            tiered("experience", "eco_experience_cell_", "eco_experience_cell_housing", "appex"),
            tiered("soul", "eco_soul_cell_", "eco_soul_cell_housing", "appliedsoul"),
            tiered("matter", "eco_matter_cell_", "eco_matter_cell_housing", "apprep"),
            tiered("stress", "eco_stress_cell_", "eco_stress_cell_housing", "appliedcreate"),
            tiered("data", "eco_data_storage_cell_", "eco_data_cell_housing", "data_energistics"),
            tiered("lightning", "eco_lightning_cell_", "eco_lightning_cell_housing", "ae2lt"),
            tiered("item", "eco_omni_cell_", "eco_omni_cell_housing", "ae2omnicells"),
            tiered("item", "eco_complex_omni_cell_", "eco_complex_omni_cell_housing", "ae2omnicells"),
            tiered("item", "eco_quantum_omni_cell_", "eco_quantum_omni_cell_housing", "ae2omnicells"),
            tiered("spent_nuclear_waste", "eco_spent_nuclear_waste_cell_", "eco_spent_nuclear_waste_cell_housing", "ae2omnicells", "appmek"),
            single("item", "eco_mega_item_cell_4g", "mega_item_cell_housing", "megacells"),
            single("fluid", "eco_mega_fluid_cell_4g", "mega_fluid_cell_housing", "megacells"),
            single("item", "eco_mega_long_bulk_cell", "eco_bulk_item_cell_housing", "megacells"),
            single("fe", "eco_mega_energy_cell_4g", "mega_energy_cell_housing", "megacells", "appflux"),
            single("chemical", "eco_mega_chemical_cell_4g", "mega_chemical_cell_housing", "megacells", "appmek"),
            tiered("radioactive_chemical", "eco_radioactive_chemical_cell_", "eco_radioactive_chemical_cell_housing", "megacells", "appmek")
        ));
        String[][] external = {{"mana", "appbot"}, {"source", "arseng"}, {"air", "appliedpneumatics"},
            {"experience", "appex"}, {"soul", "appliedsoul"}, {"matter", "apprep"}, {"stress", "appliedcreate"}, {"data", "data_energistics"}};
        for (String[] entry : external) families.add(single(entry[0], "eco_mega_" + entry[0] + "_cell_4g",
            "mega_" + entry[0] + "_cell_housing", "megacells", entry[1]));
        for (String channel : List.of("item", "fluid", "chemical", "fe", "experience", "source")) {
            String required = switch (channel) { case "chemical" -> "appmek"; case "fe" -> "appflux";
                case "experience" -> "appex"; case "source" -> "arseng"; default -> null; };
            String[] mods = required == null ? new String[] {"bigger_ae2"} : new String[] {"bigger_ae2", required};
            for (String family : List.of("quantum", "digital_singularity")) families.add(single(channel,
                "eco_" + family + "_" + channel + "_storage_cell", "eco_" + channel + "_cell_housing", mods));
        }
        return families;
    }

    public static void started(ServerStartedEvent event) {
        String phase = System.getProperty("neoecoae.optionalStorageSmoke", "");
        if (phase.isEmpty()) return;
        MinecraftServer server = event.getServer();
        // A production installation needs an explicit marker in the selected acceptance world as well.
        if (net.minecraftforge.fml.loading.FMLEnvironment.production
            && !Files.isRegularFile(server.getWorldPath(LevelResource.ROOT)
                .resolve("neoecoae-optional-storage-acceptance"))) return;
        try {
            check(Set.of("write", "read").contains(phase), "Unknown optional storage phase");
            run(server, phase);
            LoggerFactory.getLogger(OptionalStorageServerProbe.class).info("OPTIONAL_STORAGE_PASS phase={}", phase);
        } catch (Throwable failure) {
            LoggerFactory.getLogger(OptionalStorageServerProbe.class).error("OPTIONAL_STORAGE_FAIL phase=" + phase, failure);
            throw new IllegalStateException("Optional storage acceptance failed", failure);
        } finally { server.halt(false); }
    }

    private static void run(MinecraftServer server, String phase) throws Exception {
        var world = server.overworld();
        var root = server.getWorldPath(LevelResource.ROOT);
        var fixture = root.resolve("optional-storage-fixture.dat");
        JsonObject report = new JsonObject();
        report.addProperty("phase", phase);
        JsonArray present = new JsonArray(), absent = new JsonArray();
        ListTag disks = new ListTag();
        // Drive model suppliers normally resolve during client setup; inspect them explicitly here.
        cn.dancingsnow.neoecoae.api.ECOCellModels.getDeferredModels();
        for (Family family : families()) {
            for (String id : family.cells) {
                var key = new ResourceLocation("neoecoae", id);
                var item = BuiltInRegistries.ITEM.getOptional(key).orElse(null);
                if (!family.enabled()) {
                    check(item == null, "Optional cell registered without its dependencies: " + id);
                    absent.add(id);
                    continue;
                }
                check(item instanceof IECOStorageCellItem, "Missing production optional cell: " + id);
                var housing = BuiltInRegistries.ITEM.getOptional(new ResourceLocation("neoecoae", family.housing)).orElse(null);
                check(housing != null, "Missing housing: " + family.housing);
                check(world.getRecipeManager().getRecipes().stream().anyMatch(recipe -> resultItem(recipe, world.registryAccess()).is(housing)),
                    "Missing housing recipe for " + family.housing);
                check(world.getRecipeManager().getRecipes().stream().anyMatch(recipe -> resultItem(recipe, world.registryAccess()).is(item)),
                    "Missing assembly recipe for " + id);
                var disassembly = StorageCellDisassemblyRecipe.getDisassemblyResult(world, item);
                check(!disassembly.isEmpty(), "Missing disassembly recipe for " + id);
                check(disassembly.stream().anyMatch(stack -> stack.is(housing)), "Disassembly returns the wrong housing for " + id);
                check(OptionalStorageServerProbe.class.getClassLoader().getResource("assets/neoecoae/models/item/" + id + ".json") != null,
                    "Missing item model for " + id);
                var driveModel = cn.dancingsnow.neoecoae.api.ECOCellModels.getModelLocation(item);
                check(!driveModel.equals(cn.dancingsnow.neoecoae.api.ECOCellModels.DEFAULT_MODEL),
                    "Missing drive model registration for " + id);
                check(OptionalStorageServerProbe.class.getClassLoader().getResource(
                    "assets/" + driveModel.getNamespace() + "/models/" + driveModel.getPath() + ".json") != null,
                    "Missing drive model resource for " + id);
                if (phase.equals("write")) {
                    ItemStack stack = new ItemStack(item);
                    AEKey resource = sample(family.channel);
                    if (id.equals("eco_mega_long_bulk_cell")) {
                        ((ECOStorageCellItem) item).getConfigInventory(stack).setStack(0, new GenericStack(resource, 0));
                    }
                    ISaveProvider owner = () -> {};
                    IECOStorageCell cell = ECOStorageCells.getCellInventory(stack, owner);
                    check(cell != null, "Missing handler: " + id);
                    check(cell.insert(resource, 7, Actionable.SIMULATE, IActionSource.empty()) == 7, "Insert simulation failed: " + id);
                    check(cell.getAvailableStacks().isEmpty(), "Simulation changed contents: " + id);
                    check(cell.insert(resource, 7, Actionable.MODULATE, IActionSource.empty()) == 7, "Insert failed: " + id);
                    check(cell.extract(resource, 2, Actionable.MODULATE, IActionSource.empty()) == 2, "Extract failed: " + id);
                    BigInteger expected = BigInteger.valueOf(5);
                    if (id.startsWith("eco_digital_singularity_") && cell instanceof ECOBigIntegerStorage exact) {
                        check(exact.insertBigInteger(resource, HUGE, Actionable.MODULATE, IActionSource.empty()).equals(HUGE), "Exact insert failed: " + id);
                        expected = expected.add(HUGE);
                    }
                    check(amount(cell, resource).equals(expected), "Stored amount differs: " + id);
                    if (cell instanceof ECOBigIntegerStorage exact) {
                        check(exact.extractBigInteger(resource, expected, Actionable.SIMULATE, IActionSource.empty()).equals(expected),
                            "Exact extraction simulation differs: " + id);
                    }
                    cell.persist();
                    CompoundTag entry = new CompoundTag();
                    entry.put("stack", stack.save(new CompoundTag()));
                    entry.put("key", resource.toTagGeneric());
                    entry.putString("amount", expected.toString());
                    disks.add(entry);
                    ECOStorageCells.releaseCellInventory(stack, owner);
                }
                present.add(id);
            }
        }
        if (phase.equals("write")) {
            check(!Files.exists(fixture), "Use a new acceptance world for the write phase");
            CompoundTag data = new CompoundTag(); data.put("disks", disks);
            NbtIo.writeCompressed(data, fixture.toFile());
        } else {
            CompoundTag data = NbtIo.readCompressed(fixture.toFile());
            ListTag saved = data.getList("disks", Tag.TAG_COMPOUND);
            check(saved.size() == present.size(), "Cold-load disk count differs");
            for (Tag raw : saved) {
                CompoundTag entry = (CompoundTag) raw;
                ItemStack stack = ItemStack.of(entry.getCompound("stack"));
                check(!stack.isEmpty(), "Cold-load item ID is missing");
                AEKey resource = AEKey.fromTagGeneric(entry.getCompound("key"));
                check(resource != null, "Cold-load resource key is missing");
                ISaveProvider owner = () -> {};
                IECOStorageCell cell = ECOStorageCells.getCellInventory(stack, owner);
                check(cell != null, "Cold-load handler is missing");
                check(amount(cell, resource).equals(new BigInteger(entry.getString("amount"))), "Cold-load amount differs: " + stack);
                ECOStorageCells.releaseCellInventory(stack, owner);
            }
        }
        report.addProperty("result", "PASS");
        report.add("registered_and_verified", present);
        report.add("absent_without_dependencies", absent);
        Files.writeString(root.resolve("optional-storage-" + phase + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }

    private static BigInteger amount(IECOStorageCell cell, AEKey resource) {
        if (cell instanceof ExactAmountSource exact) {
            BigInteger[] amount = {BigInteger.ZERO};
            exact.neoecoae$visitExactAmounts((key, value) -> { if (key.equals(resource)) amount[0] = value.value(); });
            return amount[0];
        }
        return BigInteger.valueOf(cell.getAvailableStacks().get(resource));
    }

    private static ItemStack resultItem(net.minecraft.world.item.crafting.Recipe<?> recipe,
                                        net.minecraft.core.RegistryAccess registries) {
        // Ars 1.20 deliberately returns EMPTY from Recipe.getResultItem; its actual output is public.
        if (recipe.getClass().getName().equals("com.hollingsworth.arsnouveau.api.enchanting_apparatus.EnchantingApparatusRecipe")) {
            try {
                return (ItemStack) recipe.getClass().getField("result").get(recipe);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot inspect Ars apparatus recipe " + recipe.getId(), failure);
            }
        }
        return recipe.getResultItem(registries);
    }

    private static AEKey sample(String channel) throws Exception {
        return switch (channel) {
            case "item" -> AEItemKey.of(Items.STONE);
            case "fluid" -> AEFluidKey.of(Fluids.WATER);
            case "mana" -> constant("appbot.ae2.ManaKey", "KEY");
            case "source" -> constant("gripe._90.arseng.me.key.SourceKey", "KEY");
            case "air" -> constant("com.wintercogs.appliedpneumatics.common.me.keys.AirKey", "INSTANCE");
            case "lightning" -> constant("com.moakiee.ae2lt.me.key.LightningKey", "HIGH_VOLTAGE");
            case "chemical", "spent_nuclear_waste", "radioactive_chemical" -> chemical(channel);
            case "fe" -> {
                Class<?> energy = Class.forName("com.glodblock.github.appflux.common.me.key.type.EnergyType");
                Object fe = energy.getField("FE").get(null);
                yield (AEKey) Class.forName("com.glodblock.github.appflux.common.me.key.FluxKey").getMethod("of", energy).invoke(null, fe);
            }
            default -> throw new IllegalStateException("No released 1.20 acceptance sample for channel " + channel);
        };
    }

    private static AEKey constant(String owner, String field) throws Exception {
        return (AEKey) Class.forName(owner).getField(field).get(null);
    }

    private static AEKey chemical(String channel) throws Exception {
        String field = channel.equals("chemical") ? "OXYGEN" : channel.equals("spent_nuclear_waste") ? "SPENT_NUCLEAR_WASTE" : "NUCLEAR_WASTE";
        Object registry = Class.forName("mekanism.common.registries.MekanismGases").getField(field).get(null);
        Class<?> gasProvider = Class.forName("mekanism.api.providers.IGasProvider");
        Class<?> stackType = Class.forName("mekanism.api.chemical.gas.GasStack");
        Object stack = stackType.getConstructor(gasProvider, long.class).newInstance(registry, 1L);
        return (AEKey) Class.forName("me.ramidzkh.mekae2.ae2.MekanismKey")
            .getMethod("of", Class.forName("mekanism.api.chemical.ChemicalStack")).invoke(null, stack);
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
}
