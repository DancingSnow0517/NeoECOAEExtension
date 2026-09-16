package cn.dancingsnow.neoecoae.all;

import appeng.decorative.solid.CertusQuartzClusterBlock;
import cn.dancingsnow.neoecoae.blocks.BuddingEnergizedCrystalBlock;
import cn.dancingsnow.neoecoae.blocks.CasingBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock;
import cn.dancingsnow.neoecoae.util.LootTableUtil;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.providers.loot.RegistrateBlockLootTables;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.Tags;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

/** Shared registration primitives for the data-heavy block families. */
final class NEBlockRegistration {
    private NEBlockRegistration() {}

    static BlockEntry<Block> ore(
        String name,
        NonNullSupplier<Block> initialProperties,
        TagKey<Block> miningToolTag,
        TagKey<Block> blockOreTag,
        TagKey<Item> itemOreTag,
        Supplier<? extends Item> rawOre
    ) {
        return REGISTRATE
            .block(name, Block::new)
            .initialProperties(initialProperties)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, miningToolTag, blockOreTag, Tags.Blocks.ORES)
            .loot((prov, block) -> prov.add(block, prov.createOreDrop(block, rawOre.get())))
            .item()
            .tag(itemOreTag, Tags.Items.ORES)
            .build()
            .register();
    }

    static BlockEntry<Block> storageBlock(
        String name,
        NonNullSupplier<Block> initialProperties,
        TagKey<Block> customBlockTag,
        TagKey<Item> customItemTag,
        TagKey<Block> miningToolTag,
        boolean addCommonItemTag,
        Consumer<ShapedRecipeBuilder> recipeSetup,
        String... patterns
    ) {
        var blockBuilder = REGISTRATE
            .block(name, Block::new)
            .initialProperties(initialProperties)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, miningToolTag);
        if (customBlockTag != null) {
            blockBuilder.tag(customBlockTag);
        }
        blockBuilder.tag(Tags.Blocks.STORAGE_BLOCKS);
        blockBuilder.recipe((ctx, prov) -> saveShapedRecipe(prov, ctx.get(), recipeSetup, patterns));

        var itemBuilder = blockBuilder.item();
        if (customItemTag != null) {
            itemBuilder.tag(customItemTag);
        }
        if (addCommonItemTag) {
            itemBuilder.tag(Tags.Items.STORAGE_BLOCKS);
        }
        return itemBuilder.build().register();
    }

    static BlockEntry<CasingBlock> alloyCasing(
        String name,
        TagKey<Item> materialTag,
        String materialUnlockName
    ) {
        var blockBuilder = REGISTRATE
            .block(name, CasingBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_IRON_TOOL)
            .blockstate((ctx, prov) -> {
                BlockModelBuilder model = prov.models().withExistingParent(ctx.getName(), prov.modLoc("block/casing_base"))
                    .texture("base", prov.modLoc("block/" + ctx.getName()))
                    .texture("particle", prov.modLoc("block/" + ctx.getName()));
                prov.simpleBlock(ctx.get(), model);
            });
        blockBuilder.recipe((ctx, prov) -> saveShapedRecipe(prov, ctx.get(), recipe -> {
            recipe.define('A', materialTag);
            recipe.define('B', appeng.core.definitions.AEBlocks.QUARTZ_VIBRANT_GLASS);
            recipe.define('C', NEItems.CRYSTAL_INGOT);
            recipe.unlockedBy(materialUnlockName, RegistrateRecipeProvider.has(materialTag));
            recipe.unlockedBy("has_quartz_vibrant_glass",
                RegistrateRecipeProvider.has(appeng.core.definitions.AEBlocks.QUARTZ_VIBRANT_GLASS));
            recipe.unlockedBy("has_crystal_ingot", RegistrateRecipeProvider.has(NEItems.CRYSTAL_INGOT));
        }, "ABA", "BCB", "ABA"));
        return blockBuilder.simpleItem().register();
    }

    static BlockEntry<CertusQuartzClusterBlock> crystalBud(
        String name,
        int height,
        int offset,
        SoundType sound,
        int lightLevel,
        boolean cluster
    ) {
        return REGISTRATE
            .block(name, p -> new CertusQuartzClusterBlock(height, offset, p))
            .initialProperties(() -> Blocks.AMETHYST_CLUSTER)
            .properties(p -> p.sound(sound).lightLevel(s -> lightLevel))
            .blockstate((ctx, prov) -> {
                BlockModelBuilder model = prov.models().cross(ctx.getName(), prov.modLoc("block/" + ctx.getName()))
                    .renderType("cutout");
                prov.directionalBlock(ctx.get(), model);
            })
            .loot((prov, block) -> {
                if (cluster) {
                    LootTableUtil.energizedCluster(prov, block);
                } else {
                    LootTableUtil.energizedBud(prov, block);
                }
            })
            .tag(Tags.Blocks.CLUSTERS, BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tag(Tags.Items.CLUSTERS)
            .model((ctx, prov) -> prov.generated(ctx, prov.modLoc("block/" + ctx.getName())))
            .build()
            .register();
    }

    static BlockEntry<BuddingEnergizedCrystalBlock> buddingStage(
        String name,
        NonNullBiConsumer<RegistrateBlockLootTables, BuddingEnergizedCrystalBlock> loot,
        @Nullable NonNullBiConsumer<DataGenContext<Block, BuddingEnergizedCrystalBlock>, RegistrateRecipeProvider> recipe
    ) {
        var blockBuilder = REGISTRATE
            .block(name, BuddingEnergizedCrystalBlock::new)
            .initialProperties(() -> Blocks.QUARTZ_BLOCK)
            .properties(p -> p.randomTicks().mapColor(DyeColor.CYAN))
            .loot(loot)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL, Tags.Blocks.BUDDING_BLOCKS);
        if (recipe != null) {
            blockBuilder.recipe(recipe);
        }
        return blockBuilder
            .item()
            .tag(Tags.Items.BUDDING_BLOCKS)
            .build()
            .register();
    }

    static BlockEntry<ECOEnergyCellBlock> energyCell(
        String level,
        Rarity rarity,
        Consumer<ShapedRecipeBuilder> recipeSetup
    ) {
        return REGISTRATE
            .block("energy_cell_" + level, ECOEnergyCellBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .blockstate((ctx, provider) -> {
                provider.getVariantBuilder(ctx.get())
                    .forAllStatesExcept(state -> {
                        int cellLevel = state.getValue(ECOEnergyCellBlock.LEVEL);
                        return ConfiguredModel.builder()
                            .modelFile(provider.models().getExistingFile(provider.modLoc(
                                "block/storage_energy_cell/cell_" + level + "_" + cellLevel)))
                            .rotationY(((int) state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360)
                            .build();
                    }, ECOEnergyCellBlock.FORMED);
            })
            .recipe((ctx, prov) -> saveShapedRecipe(prov, ctx.get(), recipeSetup,
                "AAA", "ABA", "AAA"))
            .item()
            .properties(p -> p.rarity(rarity))
            .model((ctx, provider) -> provider.withExistingParent(ctx.getName(), provider.modLoc(
                "block/storage_energy_cell/cell_" + level + "_4")))
            .build()
            .lang("ECO - %s High Density Energy Cell".formatted(
                level.toUpperCase(Locale.ROOT).replace("L", "LT")))
            .register();
    }

    private static void saveShapedRecipe(
        RegistrateRecipeProvider provider,
        ItemLike result,
        Consumer<ShapedRecipeBuilder> recipeSetup,
        String... patterns
    ) {
        var recipe = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result, 1);
        for (String pattern : patterns) {
            recipe.pattern(pattern);
        }
        recipeSetup.accept(recipe);
        recipe.save(provider);
    }
}
