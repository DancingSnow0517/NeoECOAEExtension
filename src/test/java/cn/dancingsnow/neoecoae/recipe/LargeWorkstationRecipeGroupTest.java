package cn.dancingsnow.neoecoae.recipe;

import appeng.api.config.PowerUnit;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;
import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationRecipeAdapters;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeWorkstationRecipeGroupTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void sharedGroupKeepsBothCardsAndTheirOwnEnergyAndLightningCosts() {
        var science = recipe("ae2cs:processor", "ae2cs:circuit_etcher_recipe", 36, 57600, List.of());
        var lightning = recipe("ae2lt:processor", "ae2lt:overload_processing", 36, 200000,
            List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 1)));
        try (var catalog = catalog(science, lightning)) {
            var group = onlyGroup(catalog);
            assertSame(lightning, group.representative());
            assertEquals(Set.of(module(LargeWorkstationModule.CRYSTAL_SCIENCE), module(LargeWorkstationModule.LIGHTNING_TECH)),
                Set.copyOf(group.supportedModules()));
            assertEquals(2, group.upgradeCards().size());
            assertFalse(group.availableWithoutCard());
            assertSame(science, match(catalog, LargeWorkstationModule.CRYSTAL_SCIENCE, 36).recipe());
            assertSame(lightning, match(catalog, LargeWorkstationModule.LIGHTNING_TECH, 36).recipe());
            assertNull(match(catalog, LargeWorkstationModule.REGULAR, 36));
            assertSame(science, LargeWorkstationRecipes.getById(catalog.manager(), catalog.registries(), science.id()));
            assertSame(group, LargeWorkstationRecipes.getGroup(catalog.level(), science.id()));
            assertSame(group, LargeWorkstationRecipes.getGroup(catalog.level(), lightning.id()));
        }
    }

    @Test void proportionalBatchesShareAGroupWithoutUnlockingFractionalScienceOperations() {
        var small = recipe("extendedae:processor", "extendedae:crystal_assembler", 4, 2000, List.of());
        var large = recipe("ae2cs:processor", "ae2cs:crystal_aggregator_recipe", 32, 51200, List.of());
        try (var catalog = catalog(small, large)) {
            var group = onlyGroup(catalog);
            assertSame(small, group.representative());
            assertEquals(2, group.variants().size());
            assertNull(match(catalog, LargeWorkstationModule.CRYSTAL_SCIENCE, 4));
            assertEquals(new LargeWorkstationRecipes.PatternMatch(large, 1), match(catalog, LargeWorkstationModule.CRYSTAL_SCIENCE, 32));
            assertEquals(new LargeWorkstationRecipes.PatternMatch(small, 8), match(catalog, LargeWorkstationModule.EXTENDED_AE, 32));
            assertEquals(new LargeWorkstationRecipes.PatternMatch(large, 2), match(catalog, LargeWorkstationModule.CRYSTAL_SCIENCE, 64));
            assertNull(match(catalog, LargeWorkstationModule.CRYSTAL_SCIENCE, 33));
        }
    }

    @Test void bypassingCardsRetainsSmallBatchesAndComparesCostsAtTheRequestedScale() {
        var small = recipe("extendedae:small", "extendedae:crystal_assembler", 4, 2000, List.of());
        var large = recipe("ae2cs:large", "ae2cs:crystal_aggregator_recipe", 32, 51200, List.of());
        boolean previous = NEConfig.largeWorkstationIgnoreUpgradeCards;
        try (var catalog = catalog(small, large)) {
            NEConfig.largeWorkstationIgnoreUpgradeCards = true;
            assertEquals(2, LargeWorkstationRecipes.getAvailable(catalog.level(), LargeWorkstationModule.REGULAR).size());
            assertEquals(new LargeWorkstationRecipes.PatternMatch(small, 8), match(catalog, LargeWorkstationModule.REGULAR, 32));
            assertEquals(new LargeWorkstationRecipes.PatternMatch(small, 1), match(catalog, LargeWorkstationModule.REGULAR, 4));
            assertEquals(1, LargeWorkstationRecipes.getAll(catalog.level()).size());
        } finally { NEConfig.largeWorkstationIgnoreUpgradeCards = previous; }
    }

    @Test void normalizedCostsCanPreferALargerBatchAndNeverOverflowDuringComparison() {
        var small = recipe("extendedae:small", "extendedae:crystal_assembler", 4, Long.MAX_VALUE, List.of());
        var large = recipe("ae2cs:large", "ae2cs:crystal_aggregator_recipe", 32, Long.MAX_VALUE, List.of());
        try (var catalog = catalog(small, large)) {
            assertSame(large, onlyGroup(catalog).representative());
            assertEquals(new LargeWorkstationRecipes.PatternMatch(large, 1),
                LargeWorkstationRecipes.findScaled(List.of(small, large), items(32), outputs(32)));
        }
    }

    @Test void sameCardAndBatchFixOnePassiveLightningCostAndKeepOldIdsRestorable() {
        var fewer = recipe("ae2lt:fewer", "ae2lt:overload_processing", 4, 1000,
            List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 1)));
        var more = recipe("ae2lt:more", "ae2lt:lightning_simulation", 4, 2000,
            List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 4)));
        try (var catalog = catalog(fewer, more)) {
            assertSame(fewer, onlyGroup(catalog).representative());
            assertEquals(List.of(fewer), LargeWorkstationRecipes.getAvailable(catalog.level(), LargeWorkstationModule.LIGHTNING_TECH));
            assertEquals(List.of(fewer), onlyGroup(catalog).availableVariants(module(LargeWorkstationModule.LIGHTNING_TECH))
                .stream().map(LargeWorkstationRecipeGroup.Variant::recipe).toList());
            assertSame(fewer, match(catalog, LargeWorkstationModule.LIGHTNING_TECH, 4).recipe(),
                "Passive extraction must use the same fixed lightning amount as the card tooltip");
            var input = items(4);
            input.add(LightningKey.HIGH_VOLTAGE, 4);
            var match = LargeWorkstationRecipes.findScaled(catalog.level(), input, outputs(4), LargeWorkstationModule.LIGHTNING_TECH);
            assertNull(match, "Explicit extras cannot silently select the discarded cost variant");
            assertSame(more, LargeWorkstationRecipes.getById(catalog.manager(), catalog.registries(), more.id()));
            assertSame(onlyGroup(catalog), LargeWorkstationRecipes.getGroup(catalog.level(), more.id()));
        }
    }

    @Test void screenshotCostsUseTheExistingEnergyPriorityRegardlessOfSourceOrderAndCardBypass() {
        var slow = recipe("ae2lt:slow", "ae2lt:lightning_assembly", 4, 0,
            List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 8))).processing(200, 1_000_000, false);
        var fast = recipe("ae2lt:fast", "ae2lt:overload_processing", 4, 0,
            List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 1))).processing(5, 2_000_000, false);
        boolean previous = NEConfig.largeWorkstationIgnoreUpgradeCards;
        try {
            for (var order : List.of(List.of(slow, fast), List.of(fast, slow))) {
                try (var catalog = catalog(order.toArray(LargeWorkstationRecipe[]::new))) {
                    var group = onlyGroup(catalog);
                    assertEquals(2, group.variants().size(), "Original IDs remain available for saved tasks");
                    assertEquals(List.of(slow), group.displayVariants().stream().map(LargeWorkstationRecipeGroup.Variant::recipe).toList());
                    assertSame(slow, match(catalog, LargeWorkstationModule.LIGHTNING_TECH, 4).recipe());
                    NEConfig.largeWorkstationIgnoreUpgradeCards = true;
                    assertEquals(List.of(slow), LargeWorkstationRecipes.getAvailable(catalog.level(), LargeWorkstationModule.REGULAR));
                    assertSame(slow, match(catalog, LargeWorkstationModule.REGULAR, 4).recipe());
                    NEConfig.largeWorkstationIgnoreUpgradeCards = previous;
                }
            }
        } finally { NEConfig.largeWorkstationIgnoreUpgradeCards = previous; }
    }

    @Test void sameCardRetainsDifferentLegalBatchesAfterFixingEachBatchCost() {
        var small = recipe("ae2lt:small", "ae2lt:overload_processing", 4, 1000,
            List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 1)));
        var large = recipe("ae2lt:large", "ae2lt:lightning_assembly", 32, 4000,
            List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 2)));
        try (var catalog = catalog(small, large)) {
            assertEquals(2, onlyGroup(catalog).availableVariants(module(LargeWorkstationModule.LIGHTNING_TECH)).size());
            assertSame(small, match(catalog, LargeWorkstationModule.LIGHTNING_TECH, 4).recipe());
            assertSame(large, match(catalog, LargeWorkstationModule.LIGHTNING_TECH, 32).recipe());
        }
    }

    @Test void overflowingPreferredLightningRouteCannotHideAPayableVariant() {
        var lightning = recipe("ae2lt:overflow", "ae2lt:overload_processing", 4, Long.MAX_VALUE,
            List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 1)));
        var regular = recipe("neoecoae:payable", "neoecoae:integrated_working_station", 4, 100, List.of());
        assertEquals(new LargeWorkstationRecipes.PatternMatch(regular, 2),
            LargeWorkstationRecipes.findScaled(List.of(lightning, regular), items(8), outputs(8)));
    }

    @Test void outputComponentsByproductsFluidQuantitiesAndNonLightningResourcesPreventFalseGrouping() {
        var base = recipe("test:base", "ae2cs:crystal_aggregator_recipe", 4, 2000, List.of());
        var data = new LargeWorkstationRecipe(id("test:data"), base.display(), 2000,
            List.of(new GenericStack(AEFluidKey.of(Fluids.LAVA), 1)), base.sourceType());
        var byproduct = new LargeWorkstationRecipe(id("test:byproduct"), base.display(), 2000, List.of(),
            base.sourceType(), List.of(), List.of(new GenericStack(AEItemKey.of(Items.EMERALD), 1)));
        var fluid = new LargeWorkstationRecipe(id("test:fluid"), base.display(), 2000, List.of(),
            base.sourceType(), List.of(SizedFluidIngredient.of(Fluids.WATER, 250)), List.of());
        var changedStack = new ItemStack(Items.DIAMOND, 4);
        changedStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Named"));
        var changed = new LargeWorkstationRecipe(id("test:components"), new IntegratedWorkingStationRecipe(
            base.display().inputItems(), base.display().inputFluid(), changedStack, FluidStack.EMPTY, 2000), 2000, List.of(), base.sourceType());
        try (var catalog = catalog(base, data, byproduct, fluid, changed)) {
            assertEquals(5, LargeWorkstationRecipes.getGroups(catalog.level()).size());
        }
    }

    @Test void partialAndStrictIngredientPredicatesAreNotMergedByTheirDisplayItems() {
        var base = recipe("test:plain", "ae2cs:crystal_aggregator_recipe", 4, 1000, List.of());
        var strict = new LargeWorkstationRecipe(id("test:strict"), new IntegratedWorkingStationRecipe(
            List.of(new SizedIngredient(DataComponentIngredient.of(true, new ItemStack(Items.IRON_INGOT)), 4)),
            base.display().inputFluid(), new ItemStack(Items.DIAMOND, 4), FluidStack.EMPTY, 1000), 1000, List.of(), base.sourceType());
        try (var catalog = catalog(base, strict)) {
            assertEquals(2, LargeWorkstationRecipes.getGroups(catalog.level()).size());
        }
    }

    @Test void borrowedReactionHasAStableAliasOriginalProvenanceAndSeparateLightningPayment() {
        var source = recipe("advanced_ae:reaction", "advanced_ae:reaction", 4, 10000, List.of());
        var borrowed = LargeWorkstationRecipeSources.borrowReaction(source);
        assertEquals(id("neoecoae:workstation_borrowed/ae2lt/advanced_ae/reaction"), borrowed.recipe().id());
        assertEquals(source.id(), borrowed.sourceId());
        assertEquals(source.sourceType(), borrowed.sourceType());
        assertTrue(borrowed.borrowed());
        assertFalse(borrowed.unlocks(module(LargeWorkstationModule.ADVANCED_AE)));
        assertTrue(borrowed.unlocks(module(LargeWorkstationModule.LIGHTNING_TECH)));
        assertEquals(0, borrowed.recipe().energy());
        assertEquals(10000, borrowed.recipe().feEnergy());
        assertEquals(4, borrowed.recipe().processingTicks());
        assertFalse(borrowed.recipe().requiresAppliedFlux());
        assertEquals(List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 1)), borrowed.recipe().extraInputs());
        var groups = LargeWorkstationRecipes.group(List.of(LargeWorkstationRecipeSources.direct(source), borrowed),
            registries().createSerializationContext(JsonOps.INSTANCE));
        assertEquals(1, groups.size());
        assertEquals(2, groups.getFirst().supportedModules().size());
        assertSame(source, groups.getFirst().availableVariants(module(LargeWorkstationModule.ADVANCED_AE)).getFirst().recipe());
    }

    @Test void realReactionCapabilityIsAddedOnceAndRemovedWithItsSourceOnReload() {
        var source = recipe("advanced_ae:real", "advanced_ae:reaction", 4, 10000, List.of());
        try (var mods = mockStatic(ModList.class)) {
            var modList = mock(ModList.class);
            when(modList.isLoaded("ae2lt")).thenReturn(true);
            mods.when(ModList::get).thenReturn(modList);
            var external = mock(net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe.class);
            doReturn(RecipeType.simple(source.sourceType())).when(external).getType();
            try (var catalog = catalog(Map.of(source.id(), source), List.of(new RecipeHolder<>(source.id(), external)))) {
                var group = onlyGroup(catalog);
                assertEquals(2, group.variants().size());
                assertSame(source, group.representative(), "Viewers retain the real source ID for recipe/bookmark lookup");
                assertEquals(1, group.variants().stream().filter(LargeWorkstationRecipeGroup.Variant::borrowed).count());
                assertNotNull(match(catalog, LargeWorkstationModule.LIGHTNING_TECH, 4), "AE substitution keeps FE recipes executable without Applied Flux");
                when(modList.isLoaded("appflux")).thenReturn(true);
                var match = match(catalog, LargeWorkstationModule.LIGHTNING_TECH, 4);
                assertNotNull(match);
                assertSame(match.recipe(), LargeWorkstationRecipes.getById(catalog.manager(), catalog.registries(), match.recipe().id()));
                verify(external, never()).getSerializer();
                assertEquals(1, catalog.manager().getRecipes().size(), "Borrowing must not register another source recipe");
                when(catalog.manager().getRecipes()).thenReturn(List.of());
                assertTrue(LargeWorkstationRecipes.getGroups(catalog.level()).isEmpty());
                assertNull(LargeWorkstationRecipes.getById(catalog.manager(), catalog.registries(), match.recipe().id()));
            }
        }
    }

    @Test void borrowedRoutesRespectAnAddonClaimingTheLightningRecipeFamily() {
        var customId = id("recipe_group_test:custom_lightning");
        var custom = new ECOWorkstationModules.Module(customId, id("minecraft:diamond"),
            Set.of(id("ae2lt:overload_processing")), net.minecraft.network.chat.Component.literal("Custom lightning"),
            net.minecraft.network.chat.Component.literal("Custom"), 0, null, null, null, null);
        ECOWorkstationModules.register(0, custom);
        try {
            var borrowed = LargeWorkstationRecipeSources.borrowReaction(recipe("advanced_ae:custom", "advanced_ae:reaction", 4, 10000, List.of()));
            assertTrue(borrowed.unlocks(customId));
            assertFalse(borrowed.unlocks(module(LargeWorkstationModule.LIGHTNING_TECH)));
        } finally { ECOWorkstationModules.unregister(customId); }
    }

    @Test void dataSeedSharesOnlyItsExplicitIntegrationAndDoesNotUnlockOtherDataRecipes() {
        var seed = recipe("ae2cs:data_reassembler/data_crystal_seed", "data_energistics:data_reassembler", 4, 0, List.of());
        var unrelated = recipe("data_energistics:unrelated", "data_energistics:data_reassembler", 8, 0, List.of());
        try (var mods = mockStatic(ModList.class)) {
            var modList = mock(ModList.class);
            when(modList.isLoaded("ae2cs")).thenReturn(true);
            mods.when(ModList::get).thenReturn(modList);
            try (var catalog = catalog(seed, unrelated)) {
                assertEquals(List.of(seed), LargeWorkstationRecipes.getAvailable(catalog.level(), LargeWorkstationModule.CRYSTAL_SCIENCE));
                assertEquals(2, LargeWorkstationRecipes.getAvailable(catalog.level(), LargeWorkstationModule.DATA_ENERGISTICS).size());
            }
        }
    }

    @Test void releasedReactionCodecLoadsBothRoutesAndPreservesTheOriginalFluidPredicate() throws ReflectiveOperationException {
        // As in the released AppGen codec tests, supply AE2's key registry and deferred missing-content item.
        var itemField = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        itemField.setAccessible(true);
        var previousMissingItem = itemField.get(appeng.core.definitions.AEItems.MISSING_CONTENT);
        try (var mods = mockStatic(ModList.class);
             var types = mockStatic(appeng.api.stacks.AEKeyTypes.class);
             var internal = mockStatic(appeng.api.stacks.AEKeyTypesInternal.class)) {
            internal.when(appeng.api.stacks.AEKeyTypesInternal::getRegistry)
                .thenReturn(cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys.REGISTRY);
            types.when(() -> appeng.api.stacks.AEKeyTypes.get(any(ResourceLocation.class))).thenAnswer(call ->
                cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys.REGISTRY.get(call.<ResourceLocation>getArgument(0)));
            var missingItem = mock(net.neoforged.neoforge.registries.DeferredItem.class);
            when(missingItem.get()).thenReturn(Items.BARRIER);
            when(missingItem.asItem()).thenReturn(Items.BARRIER);
            itemField.set(appeng.core.definitions.AEItems.MISSING_CONTENT, missingItem);
            var modList = mock(ModList.class);
            when(modList.isLoaded("ae2lt")).thenReturn(true);
            mods.when(ModList::get).thenReturn(modList);
            var registries = registries();
            var external = net.pedroksl.advanced_ae.recipes.ReactionChamberRecipeSerializer.INSTANCE.codec().codec().parse(
                registries.createSerializationContext(JsonOps.INSTANCE), com.google.gson.JsonParser.parseString("""
                    {"input_items":[{"ingredient":{"item":"minecraft:iron_ingot"},"amount":4}],
                     "input_fluid":{"ingredient":{"fluid":"minecraft:water"},"amount":250},
                     "output":{"#":4,"#t":"ae2:i","id":"minecraft:diamond"},"input_energy":10000}
                    """)).getOrThrow();
            var sourceId = id("advanced_ae:codec_reaction");
            var manager = mock(RecipeManager.class);
            when(manager.getRecipes()).thenReturn(List.of(new RecipeHolder<>(sourceId, external)));
            var groups = LargeWorkstationRecipes.getGroups(manager, registries);
            assertEquals(1, groups.size());
            assertEquals(2, groups.getFirst().variants().size());
            assertEquals(sourceId, groups.getFirst().representative().id());
            var nativeRecipe = groups.getFirst().availableVariants(module(LargeWorkstationModule.ADVANCED_AE)).getFirst().recipe();
            var borrowed = groups.getFirst().availableVariants(module(LargeWorkstationModule.LIGHTNING_TECH)).getFirst().recipe();
            assertSame(nativeRecipe.display().inputFluid(), borrowed.display().inputFluid());
            assertEquals(10000, nativeRecipe.energy());
            assertEquals(0, borrowed.energy());
            assertEquals(10000, borrowed.feEnergy());
            assertEquals(100, nativeRecipe.processingTicks());
            assertTrue(nativeRecipe.feAlternative());
            assertEquals(LargeWorkstationRecipes.feEquivalent(10000), nativeRecipe.feEnergy());
            var inputs = items(4);
            inputs.add(AEFluidKey.of(Fluids.WATER), 250);
            assertEquals(1, borrowed.matchingMultiplier(inputs, outputs(4)));
            inputs.remove(AEFluidKey.of(Fluids.WATER), 1);
            assertEquals(0, borrowed.matchingMultiplier(inputs, outputs(4)));
        } finally {
            itemField.set(appeng.core.definitions.AEItems.MISSING_CONTENT, previousMissingItem);
            LargeWorkstationRecipes.invalidate();
        }
    }

    @Test void tagInvalidationRebuildsGroupsEvenWhenRecipeCollectionIdentityDoesNotChange() {
        var base = recipe("test:iron", "ae2cs:crystal_aggregator_recipe", 4, 1000, List.of());
        var tag = mock(Ingredient.class);
        when(tag.isSimple()).thenReturn(true);
        when(tag.getItems()).thenReturn(new ItemStack[]{new ItemStack(Items.GOLD_INGOT)});
        var changing = new LargeWorkstationRecipe(id("test:tag"), new IntegratedWorkingStationRecipe(
            List.of(new SizedIngredient(tag, 4)), base.display().inputFluid(), new ItemStack(Items.DIAMOND, 4),
            FluidStack.EMPTY, 1000), 1000, List.of(), base.sourceType());
        try (var catalog = catalog(base, changing)) {
            assertEquals(2, LargeWorkstationRecipes.getGroups(catalog.level()).size());
            when(tag.getItems()).thenReturn(new ItemStack[]{new ItemStack(Items.IRON_INGOT)});
            LargeWorkstationRecipes.invalidate();
            assertEquals(1, LargeWorkstationRecipes.getGroups(catalog.level()).size());
        }
    }

    private static LargeWorkstationRecipeGroup onlyGroup(Catalog catalog) {
        var groups = LargeWorkstationRecipes.getGroups(catalog.level());
        assertEquals(1, groups.size());
        return groups.getFirst();
    }

    private static LargeWorkstationRecipes.PatternMatch match(Catalog catalog, LargeWorkstationModule module, int count) {
        return LargeWorkstationRecipes.findScaled(catalog.level(), items(count), outputs(count), module);
    }

    private static KeyCounter items(int count) {
        var counter = new KeyCounter();
        counter.add(AEItemKey.of(Items.IRON_INGOT), count);
        return counter;
    }

    private static KeyCounter outputs(int count) {
        var counter = new KeyCounter();
        counter.add(AEItemKey.of(Items.DIAMOND), count);
        return counter;
    }

    private static LargeWorkstationRecipe recipe(String id, String type, int count, long energy, List<GenericStack> extras) {
        var display = new IntegratedWorkingStationRecipe(List.of(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), count)),
            new SizedFluidIngredient(FluidIngredient.empty(), 1), new ItemStack(Items.DIAMOND, count), FluidStack.EMPTY,
            (int) Math.min(Integer.MAX_VALUE, energy));
        return new LargeWorkstationRecipe(id(id), display, energy, extras, id(type));
    }

    private static Catalog catalog(LargeWorkstationRecipe... recipes) {
        var byId = List.of(recipes).stream().collect(Collectors.toMap(LargeWorkstationRecipe::id, Function.identity()));
        List<RecipeHolder<?>> holders = new ArrayList<>();
        for (var recipe : recipes) {
            Recipe<?> external = mock(Recipe.class);
            doReturn(RecipeType.simple(recipe.sourceType())).when(external).getType();
            holders.add(new RecipeHolder<>(recipe.id(), external));
        }
        return catalog(byId, holders);
    }

    private static Catalog catalog(Map<ResourceLocation, LargeWorkstationRecipe> recipes, List<RecipeHolder<?>> holders) {
        var registrations = new ArrayList<ResourceLocation>();
        for (var type : recipes.values().stream().map(LargeWorkstationRecipe::sourceType).distinct().toList()) {
            var adapter = ResourceLocation.fromNamespaceAndPath("recipe_group_test", type.getNamespace() + "/" + type.getPath());
            ECOWorkstationRecipeAdapters.register(adapter, Integer.MAX_VALUE, type, (holder, lookup) -> recipes.get(holder.id()));
            registrations.add(adapter);
        }
        var manager = mock(RecipeManager.class);
        when(manager.getRecipes()).thenReturn(List.copyOf(holders));
        var registries = registries();
        var level = mock(Level.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(level.registryAccess()).thenReturn(registries);
        return new Catalog(manager, registries, level, registrations);
    }

    private record Catalog(RecipeManager manager, RegistryAccess.Frozen registries, Level level,
                           List<ResourceLocation> registrations) implements AutoCloseable {
        @Override public void close() {
            registrations.forEach(ECOWorkstationRecipeAdapters::unregister);
            LargeWorkstationRecipes.invalidate();
        }
    }

    private static RegistryAccess.Frozen registries() { return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY); }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static ResourceLocation module(LargeWorkstationModule module) { return ECOWorkstationModules.builtin(module).id(); }
}
