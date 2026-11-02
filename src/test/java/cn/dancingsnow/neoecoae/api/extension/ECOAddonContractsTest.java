package cn.dancingsnow.neoecoae.api.extension;

import appeng.api.networking.IManagedGridNode;
import cn.dancingsnow.neoecoae.api.client.ECOComputationModels;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.capability.ECOFeature;
import cn.dancingsnow.neoecoae.api.computation.ECOComputationCells;
import cn.dancingsnow.neoecoae.api.computation.IECOComputationCellItem;
import cn.dancingsnow.neoecoae.api.multiblock.*;
import cn.dancingsnow.neoecoae.api.power.*;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOAddonContractsTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void profilesApplyTheSameCompleteDrawToTheRealNodeAndRefreshOnStateChanges() {
        var host = mock(NEBlockEntity.class, CALLS_REAL_METHODS);
        var node = mock(IManagedGridNode.class);
        when(host.getMainNode()).thenReturn(node);
        when(host.getBlockState()).thenReturn(Blocks.STONE.defaultBlockState());
        Identifier id = id("power");
        ECOPowerProfiles.register(id, 20, context -> context.host() == host
            ? context.defaultPower() * (context.state().is(Blocks.DIAMOND_BLOCK) ? 0.25 : 0.5) : null);
        try {
            host.applyIdlePower(ECOPowerRole.STORAGE_DRIVE, 256, 32);
            verify(node).setIdlePowerUsage(144);
            when(host.getBlockState()).thenReturn(Blocks.DIAMOND_BLOCK.defaultBlockState());
            host.refreshIdlePower();
            verify(node).setIdlePowerUsage(72);
        } finally { ECOPowerProfiles.unregister(id); }
    }

    @Test void invalidPowerDoesNotFallThroughToAFreeMachine() {
        var context = new ECOPowerProfiles.Context(mock(BlockEntity.class), Blocks.STONE.defaultBlockState(), null,
            ECOPowerRole.COMPONENT, 16, 4);
        assertEquals(20, ECOPowerProfiles.resolve(context));
        Identifier id = id("bad_power");
        ECOPowerProfiles.register(id, 0, ignored -> Double.NaN);
        try { assertThrows(IllegalArgumentException.class, () -> ECOPowerProfiles.resolve(context)); }
        finally { ECOPowerProfiles.unregister(id); }
    }

    @Test void eachHostKindCanUseItsOwnDefinitionWhileLegacyTiersRetainFallbacks() {
        var tier = mock(IECOTier.class, CALLS_REAL_METHODS);
        var host = mock(BlockEntity.class);
        var fallback = mock(MultiBlockDefinition.class);
        var custom = mock(MultiBlockDefinition.class);
        assertSame(fallback, ECOMultiblockDefinitions.resolve(host, ECOMachineKind.STORAGE, tier, () -> fallback));
        Identifier id = id("definition");
        ECOMultiblockDefinitions.register(id, 0, context -> context.host() == host && context.kind() == ECOMachineKind.COMPUTATION ? custom : null);
        try {
            assertSame(custom, ECOMultiblockDefinitions.resolve(host, ECOMachineKind.COMPUTATION, tier, () -> fallback));
            assertSame(fallback, ECOMultiblockDefinitions.resolve(host, ECOMachineKind.CRAFTING, tier, () -> fallback));
        } finally { ECOMultiblockDefinitions.unregister(id); }
        when(tier.getBuildDefinition(ECOMachineKind.STORAGE)).thenReturn(custom);
        assertSame(custom, ECOMultiblockDefinitions.resolve(host, ECOMachineKind.STORAGE, tier, () -> fallback));
    }

    @Test void aSlotAlternativeIsSharedByPreviewAndFormationWithoutConsumingItsDefaultItem() {
        var definition = MultiBlockDefinition.builder(Holder.direct(Blocks.GOLD_BLOCK))
            .setRequirement(new BlockPos(1, 0, 0), ECOMultiblockRequirement.of(Blocks.STONE.defaultBlockState(), Blocks.BRICKS.defaultBlockState()))
            .create(ignored -> {});
        Level level = mock(Level.class);
        when(level.getBlockState(any())).thenReturn(Blocks.BRICKS.defaultBlockState());
        var plan = MultiBlockPlacementService.preview(level, BlockPos.ZERO, Blocks.GOLD_BLOCK.defaultBlockState(), definition, 1, false);
        assertEquals(1, plan.getReusedBlockCount());
        assertTrue(plan.getRequiredItems().isEmpty());
        assertTrue(MultiBlockPlacementService.matches(level, BlockPos.ZERO, Blocks.GOLD_BLOCK.defaultBlockState(), definition, 1, false));
        assertEquals(Blocks.STONE.defaultBlockState(), definition.requirements(1).getFirst().requirement().defaultState());
        assertThrows(IllegalArgumentException.class, () -> definition.requirements(0));
    }

    @Test void registeredAlternativesReachTheExistingFormationHelper() {
        Identifier id = id("alternate");
        Level level = mock(Level.class);
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.BRICKS.defaultBlockState());
        ECOMultiblockRequirements.registerAlternatives(id, 0, Blocks.STONE, Set.of(Blocks.BRICKS));
        try { assertTrue(NEClusterCalculator.validateBlock(level, BlockPos.ZERO, (state, block) -> state.is(block), Blocks.STONE)); }
        finally { ECOMultiblockRequirements.unregister(id); }
        assertFalse(NEClusterCalculator.validateBlock(level, BlockPos.ZERO, (state, block) -> state.is(block), Blocks.STONE));
    }

    @Test void driveRolesRowsAndOrderAreExplicitAndCanBeWithdrawn() {
        var context = new ECOMultiblockParts.Context(null, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null);
        Identifier id = id("part");
        ECOMultiblockParts.register(id, 0, ignored -> new ECOMultiblockParts.Part(Set.of(ECOMultiblockParts.Role.COMPUTATION_DRIVE),
            ECOTier.L4, ECOMultiblockParts.DriveRow.UPPER, -20));
        try {
            assertTrue(ECOMultiblockParts.matches(context, ECOMultiblockParts.Role.COMPUTATION_DRIVE, false));
            assertFalse(ECOMultiblockParts.matches(context, ECOMultiblockParts.Role.CASING, true));
            assertEquals(ECOMultiblockParts.DriveRow.UPPER, ECOMultiblockParts.driveRow(context, ECOMultiblockParts.DriveRow.LOWER));
            assertSame(ECOTier.L4, ECOMultiblockParts.tier(context));
            assertEquals(-20, ECOMultiblockParts.order(context));
        } finally { ECOMultiblockParts.unregister(id); }
        assertEquals(ECOMultiblockParts.DriveRow.LOWER, ECOMultiblockParts.driveRow(context, ECOMultiblockParts.DriveRow.LOWER));
    }

    @Test void independentMemoryItemsAndPerStackAdaptersSupplyTheirActualCapacity() {
        Item item = mock(Item.class, withSettings().extraInterfaces(IECOComputationCellItem.class));
        ItemStack independent = mock(ItemStack.class);
        when(independent.getItem()).thenReturn(item);
        when(((IECOComputationCellItem) item).getTier(independent)).thenReturn(ECOTier.L4);
        when(((IECOComputationCellItem) item).getComputationBytes(independent)).thenReturn(123L);
        assertEquals(123, ECOComputationCells.describe(independent).bytes());
        Identifier id = id("memory_adapter");
        ECOComputationCells.register(id, 0, stack -> stack.is(Items.DIAMOND)
            ? new ECOComputationCells.Memory(ECOTier.L6, stack.getCount() * 100L) : null);
        try {
            assertEquals(300, ECOComputationCells.describe(new ItemStack(Items.DIAMOND, 3)).bytes());
            assertNull(ECOComputationCells.describe(new ItemStack(Items.STICK)));
        } finally { ECOComputationCells.unregister(id); }
        assertNull(ECOComputationCells.describe(new ItemStack(Items.DIAMOND)));
        assertThrows(IllegalArgumentException.class, () -> new ECOComputationCells.Memory(ECOTier.L4, -1));
    }

    @Test void hostSpecificCableModelsRetainTheTierLookupAndMissingModelsAreSafe() {
        IECOTier tier = mock(IECOTier.class);
        var normal = id("normal"); var formed = id("formed");
        var hostNormal = id("host_normal"); var hostFormed = id("host_formed");
        ECOComputationModels.registerCableModel(tier, normal, formed);
        ECOComputationModels.registerCableModel(tier, Blocks.GOLD_BLOCK, hostNormal, hostFormed);
        assertEquals(formed, ECOComputationModels.getCableRegistry().get(tier).formedModel());
        assertEquals(hostFormed, ECOComputationModels.getCableConnectedModel(tier, Blocks.GOLD_BLOCK));
        assertEquals(normal, ECOComputationModels.getCableDisconnectedModel(tier, Blocks.STONE));
        assertEquals(formed, ECOComputationModels.getCableConnectedModel(tier, null));
        assertTrue(ECOComputationModels.getCableModels().containsAll(java.util.Set.of(normal, formed, hostNormal, hostFormed)));
        assertNull(ECOComputationModels.getCableConnectedModel(mock(IECOTier.class), Blocks.GOLD_BLOCK));
        assertNull(ECOComputationModels.getCellRegistry().get(Items.STICK));
    }

    @Test void cableModelProvidersTakePriorityAndDeclareAdditionalModels() {
        IECOTier tier = mock(IECOTier.class);
        var providerId = id("cable_provider");
        var dynamicNormal = id("dynamic_normal"); var dynamicFormed = id("dynamic_formed");
        ECOComputationModels.registerCableModel(tier, id("static_normal"), id("static_formed"));
        ECOComputationModels.registerAdditionalModel(dynamicNormal);
        ECOComputationModels.registerAdditionalModel(dynamicFormed);
        ECOComputationModels.registerCableModelProvider(providerId, 0, (requested, host) ->
            requested == tier && host == Blocks.DIAMOND_BLOCK ? new ECOComputationModels.Entry(dynamicNormal, dynamicFormed) : null);
        try {
            assertEquals(dynamicFormed, ECOComputationModels.getCableConnectedModel(tier, Blocks.DIAMOND_BLOCK));
            assertEquals(id("static_formed"), ECOComputationModels.getCableConnectedModel(tier, Blocks.STONE));
            assertTrue(ECOComputationModels.getCableModels().containsAll(java.util.Set.of(dynamicNormal, dynamicFormed)));
        } finally { ECOComputationModels.unregisterCableModelProvider(providerId); }
        assertEquals(id("static_formed"), ECOComputationModels.getCableConnectedModel(tier, Blocks.DIAMOND_BLOCK));
    }

    @Test void builtInFeatureEligibilityIsPreservedAndAddonTiersCanOptIn() {
        assertFalse(ECOTier.L4.supportsFeature(ECOFeature.VIRTUAL_CRAFTING));
        assertTrue(ECOTier.L9.supportsFeature(ECOFeature.INFINITE_STORAGE_HOST));
        var addon = mock(IECOTier.class, CALLS_REAL_METHODS);
        when(addon.getTier()).thenReturn(0);
        assertFalse(addon.supportsFeature(ECOFeature.INFINITE_STORAGE_HOST));
        when(addon.supportsFeature(ECOFeature.INFINITE_STORAGE_HOST)).thenReturn(true);
        assertTrue(addon.supportsFeature(ECOFeature.INFINITE_STORAGE_HOST));
    }

    private static final class MemoryItem extends Item implements IECOComputationCellItem {
        MemoryItem() { super(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.parse("neoecoae_test:memory_item")))); }
        @Override public IECOTier getTier() { return ECOTier.L4; }
        @Override public long getComputationBytes(ItemStack stack) { return 123; }
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("addon_contract_test", path); }
}
