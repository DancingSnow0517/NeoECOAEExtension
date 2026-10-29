package cn.dancingsnow.neoecoae.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnergizedCrystalLootTest {
    private static Item crystal;
    private static Item cluster;
    private static LootTable table;

    @BeforeAll
    static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        crystal = registerItem("energized_crystal");
        cluster = registerItem("energized_crystal_cluster");
        var path = Path.of("src/generated/resources/data/neoecoae/loot_tables/blocks/energized_crystal_cluster.json");
        table = net.minecraftforge.common.ForgeHooks.loadLootTable(
            Deserializers.createLootTableSerializer().create(),
            new ResourceLocation("neoecoae", "blocks/energized_crystal_cluster"),
            com.google.gson.JsonParser.parseString(Files.readString(path)), true);
    }

    @Test
    void ordinaryIronPickaxeDropsFourCrystals() {
        var drops = drops(new ItemStack(Items.IRON_PICKAXE), 1);
        assertEquals(1, drops.size());
        assertSame(crystal, drops.get(0).getItem());
        assertEquals(4, drops.get(0).getCount());
    }

    @Test
    void silkTouchDropsTheCluster() {
        var tool = new ItemStack(Items.IRON_PICKAXE);
        tool.enchant(Enchantments.SILK_TOUCH, 1);
        var drops = drops(tool, 1);
        assertEquals(1, drops.size());
        assertSame(cluster, drops.get(0).getItem());
        assertEquals(1, drops.get(0).getCount());
    }

    @Test
    void fortuneAddsUpToItsLevelToTheFourCrystals() {
        var tool = new ItemStack(Items.IRON_PICKAXE);
        tool.enchant(Enchantments.BLOCK_FORTUNE, 3);
        var counts = new java.util.HashSet<Integer>();
        for (long seed = 1; seed <= 128; seed++) {
            var drops = drops(tool, seed * 0x9E3779B97F4A7C15L);
            assertEquals(1, drops.size());
            assertSame(crystal, drops.get(0).getItem());
            counts.add(drops.get(0).getCount());
        }
        assertEquals(java.util.Set.of(4, 5, 6, 7), counts);
    }

    private static Item registerItem(String name) {
        var id = new ResourceLocation("neoecoae", name);
        if (!ForgeRegistries.ITEMS.containsKey(id)) {
            ForgeRegistries.ITEMS.register(id, new Item(new Item.Properties()));
        }
        return ForgeRegistries.ITEMS.getValue(id);
    }

    private static List<ItemStack> drops(ItemStack tool, long seed) {
        var level = mock(ServerLevel.class);
        when(level.getServer()).thenReturn(mock(MinecraftServer.class));
        var params = new LootParams.Builder(level)
            .withParameter(LootContextParams.TOOL, tool)
            .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
            .withParameter(LootContextParams.BLOCK_STATE, Blocks.AMETHYST_CLUSTER.defaultBlockState())
            .create(LootContextParamSets.BLOCK);
        var context = new LootContext.Builder(params).withOptionalRandomSeed(seed).create(null);
        var drops = new ArrayList<ItemStack>();
        table.getRandomItemsRaw(context, drops::add);
        return drops;
    }
}
