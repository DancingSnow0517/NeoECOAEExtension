package cn.dancingsnow.neoecoae.all;

import appeng.api.ids.AETags;
import appeng.api.stacks.AEKeyType;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.datagen.providers.tags.ConventionTags;
import appeng.items.materials.MaterialItem;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipeBuilder;
import appeng.recipes.transform.TransformCircumstance;
import appeng.recipes.transform.TransformRecipeBuilder;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import cn.dancingsnow.neoecoae.items.ECOInfiniteResourceCellItem;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.DataIngredient;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SmithingTransformRecipeBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.Tags;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEItems {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
    }

    private interface ToolAttributes {
        ItemAttributeModifiers create(Tier tier, float attackDamage, float attackSpeed);
    }

    private enum ToolKind {
        AXE("axe", AxeItem::createAttributes, 6.0F, -3.2F, new String[]{"AA", "AB", " B"}, ItemTags.AXES),
        HOE("hoe", HoeItem::createAttributes, 0.0F, -3.0F, new String[]{"AA", " B", " B"}, ItemTags.HOES),
        SHOVEL("shovel", ShovelItem::createAttributes, 1.5F, -3.0F, new String[]{"A", "B", "B"}, ItemTags.SHOVELS),
        PICKAXE("pickaxe", PickaxeItem::createAttributes, 1.0F, -2.8F, new String[]{"AAA", " B ", " B "}, ItemTags.PICKAXES, Tags.Items.MINING_TOOL_TOOLS),
        SWORD("sword", SwordItem::createAttributes, 3.0F, -2.4F, new String[]{"A", "A", "B"}, ItemTags.SWORDS);

        final String id;
        final ToolAttributes attributes;
        final float attackDamage;
        final float attackSpeed;
        final String[] pattern;
        final TagKey<Item>[] tags;

        ToolKind(String id, ToolAttributes attributes, float attackDamage, float attackSpeed, String[] pattern, TagKey<Item>... tags) {
            this.id = id;
            this.attributes = attributes;
            this.attackDamage = attackDamage;
            this.attackSpeed = attackSpeed;
            this.pattern = pattern;
            this.tags = tags;
        }
    }

    private static <T extends Item> ItemEntry<T> tool(
        ToolKind kind, BiFunction<Tier, Item.Properties, T> factory, Tier tier, String material, TagKey<Item> ingot
    ) {
        return REGISTRATE
            .item(material + "_" + kind.id, p -> factory.apply(tier, p))
            .properties(p -> p.attributes(kind.attributes.create(tier, kind.attackDamage, kind.attackSpeed)))
            .tag(kind.tags)
            .recipe((ctx, prov) -> {
                ShapedRecipeBuilder shaped = ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ctx.get());
                for (String row : kind.pattern) {
                    shaped.pattern(row);
                }
                shaped.define('A', ingot)
                    .define('B', Items.STICK)
                    .unlockedBy("has_" + material + "_ingot", RegistrateRecipeProvider.has(ingot))
                    .save(prov);
            })
            .register();
    }

    private static <T extends Item> ItemEntry<T> alloyTool(
        ToolKind kind, BiFunction<Tier, Item.Properties, T> factory, Tier tier, String material,
        ItemEntry<? extends Item> base, ItemEntry<SmithingTemplateItem> template,
        TagKey<Item> ingot, Supplier<? extends ItemLike> unlockItem
    ) {
        return REGISTRATE
            .item(material + "_" + kind.id, p -> factory.apply(tier, p))
            .properties(p -> p.attributes(AxeItem.createAttributes(tier, kind.attackDamage, kind.attackSpeed)))
            .tag(kind.tags)
            .recipe((ctx, prov) -> {
                SmithingTransformRecipeBuilder.smithing(
                        Ingredient.of(template),
                        Ingredient.of(base),
                        Ingredient.of(ingot),
                        RecipeCategory.TOOLS,
                        ctx.get()
                    )
                    .unlocks("has_item", RegistrateRecipeProvider.has(unlockItem.get()))
                    .save(prov, ctx.getId().withSuffix("_smithing"));
            })
            .register();
    }

    public static final ItemEntry<AxeItem> ALUMINUM_AXE = tool(
        ToolKind.AXE, AxeItem::new, NEToolTier.ALUMINUM, "aluminum", NETags.Items.ALUMINUM_INGOT);

    public static final ItemEntry<HoeItem> ALUMINUM_HOE = tool(
        ToolKind.HOE, HoeItem::new, NEToolTier.ALUMINUM, "aluminum", NETags.Items.ALUMINUM_INGOT);

    public static final ItemEntry<ShovelItem> ALUMINUM_SHOVEL = tool(
        ToolKind.SHOVEL, ShovelItem::new, NEToolTier.ALUMINUM, "aluminum", NETags.Items.ALUMINUM_INGOT);

    public static final ItemEntry<PickaxeItem> ALUMINUM_PICKAXE = tool(
        ToolKind.PICKAXE, PickaxeItem::new, NEToolTier.ALUMINUM, "aluminum", NETags.Items.ALUMINUM_INGOT);

    public static final ItemEntry<SwordItem> ALUMINUM_SWORD = tool(
        ToolKind.SWORD, SwordItem::new, NEToolTier.ALUMINUM, "aluminum", NETags.Items.ALUMINUM_INGOT);

    public static final ItemEntry<AxeItem> TUNGSTEN_AXE = tool(
        ToolKind.AXE, AxeItem::new, NEToolTier.TUNGSTEN, "tungsten", NETags.Items.TUNGSTEN_INGOT);

    public static final ItemEntry<HoeItem> TUNGSTEN_HOE = tool(
        ToolKind.HOE, HoeItem::new, NEToolTier.TUNGSTEN, "tungsten", NETags.Items.TUNGSTEN_INGOT);

    public static final ItemEntry<ShovelItem> TUNGSTEN_SHOVEL = tool(
        ToolKind.SHOVEL, ShovelItem::new, NEToolTier.TUNGSTEN, "tungsten", NETags.Items.TUNGSTEN_INGOT);

    public static final ItemEntry<PickaxeItem> TUNGSTEN_PICKAXE = tool(
        ToolKind.PICKAXE, PickaxeItem::new, NEToolTier.TUNGSTEN, "tungsten", NETags.Items.TUNGSTEN_INGOT);

    public static final ItemEntry<SwordItem> TUNGSTEN_SWORD = tool(
        ToolKind.SWORD, SwordItem::new, NEToolTier.TUNGSTEN, "tungsten", NETags.Items.TUNGSTEN_INGOT);

    private static ItemEntry<SmithingTemplateItem> alloyUpgradeTemplate(
        String id, String appliesTo, String ingredients, String upgrade, String baseSlot, String additionsSlot,
        TagKey<Item> ingot, String ingotCriterion
    ) {
        return REGISTRATE
            .item(id + "_smithing_template", p -> new SmithingTemplateItem(
                REGISTRATE.addLang("item", NeoECOAE.id("smithing_template." + id + ".applies_to"), appliesTo).withStyle(ChatFormatting.BLUE),
                REGISTRATE.addLang("item", NeoECOAE.id("smithing_template." + id + ".ingredients"), ingredients).withStyle(ChatFormatting.BLUE),
                REGISTRATE.addLang("upgrade", NeoECOAE.id(id), upgrade).withStyle(ChatFormatting.GRAY),
                REGISTRATE.addLang("item", NeoECOAE.id("smithing_template." + id + ".base_slot_description"), baseSlot),
                REGISTRATE.addLang("item", NeoECOAE.id("smithing_template." + id + ".additions_slot_description"), additionsSlot),
                List.of(
                    ResourceLocation.withDefaultNamespace("item/empty_slot_hoe"),
                    ResourceLocation.withDefaultNamespace("item/empty_slot_axe"),
                    ResourceLocation.withDefaultNamespace("item/empty_slot_sword"),
                    ResourceLocation.withDefaultNamespace("item/empty_slot_shovel"),
                    ResourceLocation.withDefaultNamespace("item/empty_slot_pickaxe")
                ),
                List.of(
                    ResourceLocation.withDefaultNamespace("item/empty_slot_ingot")
                )
            ))
            .recipe((ctx, prov) -> {
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                    .requires(ingot)
                    .requires(NEItems.ENERGIZED_FLUIX_CRYSTAL)
                    .unlockedBy(ingotCriterion, RegistrateRecipeProvider.has(ingot))
                    .save(prov);
            })
            .register();
    }

    public static final ItemEntry<SmithingTemplateItem> ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE = alloyUpgradeTemplate(
        "aluminum_alloy_upgrade", "Aluminum Equipment", "Aluminum Alloy Ingot", "Aluminum Alloy Upgrade",
        "Add Aluminum weapon, or tool", "Add Aluminum Alloy Ingot",
        NETags.Items.ALUMINUM_ALLOY_INGOT, "has_aluminum_alloy_ingot");

    public static final ItemEntry<AxeItem> ALUMINUM_ALLOY_AXE = alloyTool(
        ToolKind.AXE, AxeItem::new, NEToolTier.ALUMINUM_ALLOY, "aluminum_alloy", NEItems.ALUMINUM_AXE,
        NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE, NETags.Items.ALUMINUM_ALLOY_INGOT,
        () -> NEItems.ALUMINUM_ALLOY_INGOT);

    public static final ItemEntry<HoeItem> ALUMINUM_ALLOY_HOE = alloyTool(
        ToolKind.HOE, HoeItem::new, NEToolTier.ALUMINUM_ALLOY, "aluminum_alloy", NEItems.ALUMINUM_HOE,
        NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE, NETags.Items.ALUMINUM_ALLOY_INGOT,
        () -> NEItems.ALUMINUM_ALLOY_INGOT);

    public static final ItemEntry<ShovelItem> ALUMINUM_ALLOY_SHOVEL = alloyTool(
        ToolKind.SHOVEL, ShovelItem::new, NEToolTier.ALUMINUM_ALLOY, "aluminum_alloy", NEItems.ALUMINUM_SHOVEL,
        NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE, NETags.Items.ALUMINUM_ALLOY_INGOT,
        () -> NEItems.ALUMINUM_ALLOY_INGOT);

    public static final ItemEntry<PickaxeItem> ALUMINUM_ALLOY_PICKAXE = alloyTool(
        ToolKind.PICKAXE, PickaxeItem::new, NEToolTier.ALUMINUM_ALLOY, "aluminum_alloy", NEItems.ALUMINUM_PICKAXE,
        NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE, NETags.Items.ALUMINUM_ALLOY_INGOT,
        () -> NEItems.ALUMINUM_ALLOY_INGOT);

    public static final ItemEntry<SwordItem> ALUMINUM_ALLOY_SWORD = alloyTool(
        ToolKind.SWORD, SwordItem::new, NEToolTier.ALUMINUM_ALLOY, "aluminum_alloy", NEItems.ALUMINUM_SWORD,
        NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE, NETags.Items.ALUMINUM_ALLOY_INGOT,
        () -> NEItems.ALUMINUM_ALLOY_INGOT);

    public static final ItemEntry<SmithingTemplateItem> BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE = alloyUpgradeTemplate(
        "black_tungsten_alloy_upgrade", "Tungsten Equipment", "Black Tungsten Alloy Ingot", "Black Tungsten Alloy Upgrade",
        "Add Tungsten weapon, or tool", "Add Black Tungsten Alloy Ingot",
        NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT, "has_black_tungsten_alloy_ingot");

    public static final ItemEntry<AxeItem> BLACK_TUNGSTEN_ALLOY_AXE = alloyTool(
        ToolKind.AXE, AxeItem::new, NEToolTier.BLACK_TUNGSTEN_ALLOY, "black_tungsten_alloy", NEItems.TUNGSTEN_AXE,
        NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE, NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT,
        () -> NEItems.BLACK_TUNGSTEN_ALLOY_INGOT);

    public static final ItemEntry<HoeItem> BLACK_TUNGSTEN_ALLOY_HOE = alloyTool(
        ToolKind.HOE, HoeItem::new, NEToolTier.BLACK_TUNGSTEN_ALLOY, "black_tungsten_alloy", NEItems.TUNGSTEN_HOE,
        NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE, NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT,
        () -> NEItems.BLACK_TUNGSTEN_ALLOY_INGOT);

    public static final ItemEntry<ShovelItem> BLACK_TUNGSTEN_ALLOY_SHOVEL = alloyTool(
        ToolKind.SHOVEL, ShovelItem::new, NEToolTier.BLACK_TUNGSTEN_ALLOY, "black_tungsten_alloy", NEItems.TUNGSTEN_SHOVEL,
        NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE, NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT,
        () -> NEItems.BLACK_TUNGSTEN_ALLOY_INGOT);

    public static final ItemEntry<PickaxeItem> BLACK_TUNGSTEN_ALLOY_PICKAXE = alloyTool(
        ToolKind.PICKAXE, PickaxeItem::new, NEToolTier.BLACK_TUNGSTEN_ALLOY, "black_tungsten_alloy", NEItems.TUNGSTEN_PICKAXE,
        NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE, NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT,
        () -> NEItems.BLACK_TUNGSTEN_ALLOY_INGOT);

    public static final ItemEntry<SwordItem> BLACK_TUNGSTEN_ALLOY_SWORD = alloyTool(
        ToolKind.SWORD, SwordItem::new, NEToolTier.BLACK_TUNGSTEN_ALLOY, "black_tungsten_alloy", NEItems.TUNGSTEN_SWORD,
        NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE, NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT,
        () -> NEItems.BLACK_TUNGSTEN_ALLOY_INGOT);

    private static ItemEntry<MaterialItem> dust(String name, TagKey<Item> source, TagKey<Item> dustTag) {
        return REGISTRATE
            .item(name, MaterialItem::new)
            .tag(dustTag, Tags.Items.DUSTS)
            .recipe((ctx, prov) -> {
                InscriberRecipeBuilder.inscribe(source, ctx.get(), 1)
                    .save(prov, NeoECOAE.id("inscriber/" + name));
            })
            .register();
    }

    private static ItemEntry<MaterialItem> rawOre(String material, TagKey<Item> rawTag, TagKey<Item> storageBlock) {
        return REGISTRATE
            .item("raw_" + material + "_ore", MaterialItem::new)
            .recipe((ctx, prov) -> {
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                    .requires(storageBlock)
                    .unlockedBy("has_raw_" + material + "_block", RegistrateRecipeProvider.has(storageBlock))
                    .save(prov);
            })
            .tag(rawTag, Tags.Items.RAW_MATERIALS)
            .register();
    }

    private static ItemEntry<MaterialItem> oreIngot(
        String material, TagKey<Item> ingot, TagKey<Item> ore, TagKey<Item> raw, TagKey<Item> dust,
        TagKey<Item> storageBlock, float cookingTime
    ) {
        return REGISTRATE
            .item(material + "_ingot", MaterialItem::new)
            .tag(ingot, Tags.Items.INGOTS, AETags.METAL_INGOTS)
            .recipe((ctx, prov) -> {
                prov.smelting(DataIngredient.tag(ore), RecipeCategory.MISC, ctx, cookingTime);
                prov.smelting(DataIngredient.tag(raw), RecipeCategory.MISC, ctx, cookingTime);
                prov.smelting(DataIngredient.tag(dust), RecipeCategory.MISC, ctx, cookingTime);
                prov.blasting(DataIngredient.tag(ore), RecipeCategory.MISC, ctx, cookingTime);
                prov.blasting(DataIngredient.tag(raw), RecipeCategory.MISC, ctx, cookingTime);
                prov.blasting(DataIngredient.tag(dust), RecipeCategory.MISC, ctx, cookingTime);

                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                    .requires(storageBlock)
                    .unlockedBy("has_" + material + "_block", RegistrateRecipeProvider.has(storageBlock))
                    .save(prov);
            })
            .register();
    }

    private static ItemEntry<MaterialItem> alloyIngot(
        String material, TagKey<Item> ingot, TagKey<Item> dust, TagKey<Item> storageBlock
    ) {
        return REGISTRATE
            .item(material + "_ingot", MaterialItem::new)
            .tag(ingot, Tags.Items.INGOTS, AETags.METAL_INGOTS)
            .recipe((ctx, prov) -> {
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                    .requires(storageBlock)
                    .unlockedBy("has_" + material + "_block", RegistrateRecipeProvider.has(storageBlock))
                    .save(prov);

                prov.smelting(DataIngredient.tag(dust), RecipeCategory.MISC, ctx, 1.0f);
                prov.blasting(DataIngredient.tag(dust), RecipeCategory.MISC, ctx, 1.0f);
            })
            .register();
    }

    public static final ItemEntry<MaterialItem> IRON_DUST = dust(
        "iron_dust", Tags.Items.INGOTS_IRON, NETags.Items.IRON_DUST);

    public static final ItemEntry<MaterialItem> RAW_ALUMINUM_ORE = rawOre(
        "aluminum", NETags.Items.ALUMINUM_RAW, NETags.Items.RAW_ALUMINUM_STORAGE_BLOCK);

    public static final ItemEntry<MaterialItem> ALUMINUM_INGOT = oreIngot(
        "aluminum", NETags.Items.ALUMINUM_INGOT, NETags.Items.ALUMINUM_ORE, NETags.Items.ALUMINUM_RAW,
        NETags.Items.ALUMINUM_DUST, NETags.Items.ALUMINUM_STORAGE_BLOCK, 0.8f);

    public static final ItemEntry<MaterialItem> ALUMINUM_DUST = dust(
        "aluminum_dust", NETags.Items.ALUMINUM_INGOT, NETags.Items.ALUMINUM_DUST);

    public static final ItemEntry<MaterialItem> RAW_TUNGSTEN_ORE = rawOre(
        "tungsten", NETags.Items.TUNGSTEN_RAW, NETags.Items.RAW_TUNGSTEN_STORAGE_BLOCK);

    public static final ItemEntry<MaterialItem> TUNGSTEN_INGOT = oreIngot(
        "tungsten", NETags.Items.TUNGSTEN_INGOT, NETags.Items.TUNGSTEN_ORE, NETags.Items.TUNGSTEN_RAW,
        NETags.Items.TUNGSTEN_DUST, NETags.Items.TUNGSTEN_STORAGE_BLOCK, 1.0f);

    public static final ItemEntry<MaterialItem> TUNGSTEN_DUST = dust(
        "tungsten_dust", NETags.Items.TUNGSTEN_INGOT, NETags.Items.TUNGSTEN_DUST);

    public static final ItemEntry<MaterialItem> ALUMINUM_ALLOY_INGOT = alloyIngot(
        "aluminum_alloy", NETags.Items.ALUMINUM_ALLOY_INGOT, NETags.Items.ALUMINUM_ALLOY_DUST,
        NETags.Items.ALUMINUM_ALLOY_STORAGE_BLOCK);

    public static final ItemEntry<MaterialItem> ALUMINUM_ALLOY_DUST = REGISTRATE
        .item("aluminum_alloy_dust", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(NETags.Items.IRON_DUST)
                .requires(NETags.Items.ALUMINUM_DUST)
                .requires(ConventionTags.CERTUS_QUARTZ_DUST)
                .requires(ConventionTags.CERTUS_QUARTZ_DUST)
                .unlockedBy("has_iron_dust", RegistrateRecipeProvider.has(NETags.Items.IRON_DUST))
                .unlockedBy("has_aluminum_dust", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_DUST))
                .unlockedBy("has_certus_quartz_dust", RegistrateRecipeProvider.has(ConventionTags.CERTUS_QUARTZ_DUST))
                .save(prov);

            InscriberRecipeBuilder.inscribe(NETags.Items.ALUMINUM_ALLOY_INGOT, ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/aluminum_alloy_dust"));
        })
        .tag(NETags.Items.ALUMINUM_ALLOY_DUST, Tags.Items.DUSTS)
        .register();

    public static final ItemEntry<MaterialItem> BLACK_TUNGSTEN_ALLOY_INGOT = alloyIngot(
        "black_tungsten_alloy", NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT, NETags.Items.BLACK_TUNGSTEN_ALLOY_DUST,
        NETags.Items.BLACK_TUNGSTEN_ALLOY_STORAGE_BLOCK);

    public static final ItemEntry<MaterialItem> BLACK_TUNGSTEN_ALLOY_DUST = REGISTRATE
        .item("black_tungsten_alloy_dust", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(NETags.Items.TUNGSTEN_DUST)
                .requires(NETags.Items.ALUMINUM_ALLOY_DUST)
                .requires(ConventionTags.FLUIX_DUST)
                .requires(ConventionTags.FLUIX_DUST)
                .unlockedBy("has_aluminum_alloy_dust", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_ALLOY_DUST))
                .unlockedBy("has_tungsten_dust", RegistrateRecipeProvider.has(NETags.Items.TUNGSTEN_DUST))
                .unlockedBy("has_certus_quartz_dust", RegistrateRecipeProvider.has(ConventionTags.CERTUS_QUARTZ_DUST))
                .save(prov);

            InscriberRecipeBuilder.inscribe(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT, ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/black_tungsten_alloy_dust"));
        })
        .tag(NETags.Items.BLACK_TUNGSTEN_ALLOY_DUST, Tags.Items.DUSTS)
        .register();

    public static final ItemEntry<MaterialItem> ENERGIZED_CRYSTAL = REGISTRATE
        .item("energized_crystal", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 4)
                .requires(NETags.Items.ENERGIZED_CRYSTAL_BLOCK)
                .unlockedBy("has_energized_crystal_block", RegistrateRecipeProvider.has(NETags.Items.ENERGIZED_CRYSTAL_BLOCK))
                .save(prov);
        })
        .tag(NETags.Items.ENERGIZED_CRYSTAL, Tags.Items.GEMS)
        .register();

    public static final ItemEntry<MaterialItem> ENERGIZED_CRYSTAL_DUST = dust(
        "energized_crystal_dust", NETags.Items.ENERGIZED_CRYSTAL, NETags.Items.ENERGIZED_CRYSTAL_DUST);

    public static final ItemEntry<MaterialItem> ENERGIZED_FLUIX_CRYSTAL = REGISTRATE
        .item("energized_fluix_crystal", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 4)
                .requires(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_BLOCK)
                .unlockedBy("has_energized_fluix_crystal_block", RegistrateRecipeProvider.has(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_BLOCK))
                .save(prov, NeoECOAE.id("energized_fluix_crystal_from_block"));

            TransformRecipeBuilder.transform(
                prov,
                NeoECOAE.id("transform/energized_fluix_crystal"),
                ctx.get(),
                1,
                TransformCircumstance.fluid(FluidTags.WATER),
                Ingredient.of(NETags.Items.ENERGIZED_CRYSTAL_DUST),
                Ingredient.of(ConventionTags.FLUIX_CRYSTAL)
            );
        })
        .tag(NETags.Items.ENERGIZED_FLUIX_CRYSTAL, Tags.Items.GEMS)
        .register();

    public static final ItemEntry<MaterialItem> ENERGIZED_FLUIX_CRYSTAL_DUST = dust(
        "energized_fluix_crystal_dust", NETags.Items.ENERGIZED_FLUIX_CRYSTAL, NETags.Items.ENERGIZED_FLUIX_CRYSTAL_DUST);

    public static final ItemEntry<MaterialItem> CRYSTAL_INGOT = REGISTRATE
        .item("crystal_ingot", MaterialItem::new)
        .recipe((ctx, prov) -> {
            TransformRecipeBuilder.transform(
                prov,
                NeoECOAE.id("transform/crystal_ingot"),
                ctx.get(),
                1,
                TransformCircumstance.EXPLOSION,
                Ingredient.of(ConventionTags.CERTUS_QUARTZ_DUST),
                Ingredient.of(ConventionTags.FLUIX_DUST),
                Ingredient.of(NETags.Items.ENERGIZED_CRYSTAL_DUST),
                Ingredient.of(NETags.Items.CRYSTAL_INGOT_BASE)
            );
            IntegratedWorkingStationRecipe.builder()
                .require(ConventionTags.CERTUS_QUARTZ_DUST, 4)
                .require(ConventionTags.FLUIX_DUST, 4)
                .require(NETags.Items.ENERGIZED_CRYSTAL_DUST, 4)
                .require(NETags.Items.CRYSTAL_INGOT_BASE, 4)
                .requireFluid(FluidTags.LAVA, 2000)
                .itemOutput(ctx.get(), 4)
                .energy(200000)
                .save(prov, ctx.getId().withPrefix("integrated_working_station/"));
        })
        .register();


    public static final ItemEntry<MaterialItem> CRYSTAL_MATRIX = REGISTRATE
        .item("crystal_matrix", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 1)
                .pattern("A A")
                .pattern(" A ")
                .pattern("A A")
                .define('A', NEItems.CRYSTAL_INGOT)
                .unlockedBy("has_crystal_ingot", RegistrateRecipeProvider.has(NEItems.CRYSTAL_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<MaterialItem> ENERGIZED_SUPERCONDUCTIVE_INGOT = REGISTRATE
        .item("energized_superconductive_ingot", MaterialItem::new)
        .recipe((ctx, prov) -> {
            TransformRecipeBuilder.transform(
                prov,
                NeoECOAE.id("transform/energized_superconductive_ingot"),
                ctx.get(),
                1,
                TransformCircumstance.EXPLOSION,
                Ingredient.of(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_DUST),
                Ingredient.of(NETags.Items.ALUMINUM_DUST),
                Ingredient.of(ConventionTags.SILICON),
                Ingredient.of(NETags.Items.SUPERCONDUCTIVE_INGOT_BASE)
            );
            IntegratedWorkingStationRecipe.builder()
                .require(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_DUST, 4)
                .require(NETags.Items.ALUMINUM_DUST, 4)
                .require(ConventionTags.SILICON, 4)
                .require(NETags.Items.SUPERCONDUCTIVE_INGOT_BASE, 4)
                .requireFluid(FluidTags.LAVA, 2000)
                .itemOutput(ctx.get(), 4)
                .energy(200000)
                .save(prov, ctx.getId().withPrefix("integrated_working_station/"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                .requires(NEBlocks.ENERGIZED_SUPERCONDUCTIVE_BLOCK)
                .unlockedBy("has_block", RegistrateRecipeProvider.has(NEBlocks.ENERGIZED_SUPERCONDUCTIVE_BLOCK))
                .save(prov);
        })
        .register();

    public static final ItemEntry<MaterialItem> CRYOTHEUM = REGISTRATE
        .item("cryotheum", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(Items.ICE)
                .requires(ConventionTags.CERTUS_QUARTZ_DUST)
                .requires(ConventionTags.SKY_STONE_DUST)
                .requires(Items.SNOWBALL)
                .requires(Ingredient.of(NETags.Items.ENERGIZED_CRYSTAL_DUST), 4)
                .unlockedBy("has_energized_crystal_dust", RegistrateRecipeProvider.has(NETags.Items.ENERGIZED_CRYSTAL_DUST))
                .save(prov);
        })
        .register();

    public static final ItemEntry<MaterialItem> CRYOTHEUM_CRYSTAL = REGISTRATE
        .item("cryotheum_crystal", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("AAA")
                .pattern("ABA")
                .pattern("AAA")
                .define('A', ConventionTags.SKY_STONE_DUST)
                .define('B', NEItems.CRYOTHEUM)
                .unlockedBy("has_cryotheum", RegistrateRecipeProvider.has(NEItems.CRYOTHEUM))
                .save(prov);
        })
        .register();

    public static final ItemEntry<MaterialItem> SUPERCONDUCTING_PROCESSOR_PRESS = REGISTRATE
        .item("superconducting_processor_press", MaterialItem::new)
        .tag(ConventionTags.INSCRIBER_PRESSES)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("AAA")
                .pattern("BCD")
                .pattern("AAA")
                .define('A', NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT)
                .define('B', AEItems.ENGINEERING_PROCESSOR_PRESS)
                .define('C', AEItems.CALCULATION_PROCESSOR_PRESS)
                .define('D', AEItems.LOGIC_PROCESSOR_PRESS)
                .unlockedBy("has_energized_superconductive_ingot", RegistrateRecipeProvider.has(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT))
                .save(prov);
            InscriberRecipeBuilder.inscribe(Tags.Items.STORAGE_BLOCKS_IRON, ctx.get(), 1)
                .setMode(InscriberProcessType.INSCRIBE)
                .setTop(Ingredient.of(ctx.get()))
                .save(prov, NeoECOAE.id("inscriber/superconducting_processor_press"));
        })
        .register();

    public static final ItemEntry<MaterialItem> SUPERCONDUCTING_PROCESSOR_PRINT = REGISTRATE
        .item("superconducting_processor_print", MaterialItem::new)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, ctx.get(), 1)
                .setMode(InscriberProcessType.INSCRIBE)
                .setTop(Ingredient.of(NEItems.SUPERCONDUCTING_PROCESSOR_PRESS))
                .save(prov, NeoECOAE.id("inscriber/superconducting_processor_print"));
        })
        .register();

    public static final ItemEntry<MaterialItem> SUPERCONDUCTING_PROCESSOR = REGISTRATE
        .item("superconducting_processor", MaterialItem::new)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(Ingredient.of(NEItems.CRYSTAL_MATRIX), ctx.get(), 1)
                .setMode(InscriberProcessType.PRESS)
                .setTop(Ingredient.of(NEItems.SUPERCONDUCTING_PROCESSOR_PRINT))
                .setBottom(Ingredient.of(AEItems.SILICON_PRINT))
                .save(prov, NeoECOAE.id("inscriber/superconducting_processor"));
        })
        .register();

    public static final ItemEntry<MaterialItem> ECO_CELL_COMPONENT_16M = REGISTRATE
        .item("eco_cell_component_16m", MaterialItem::new)
        .recipe((ctx, prov) -> {
            IntegratedWorkingStationRecipe.builder()
                .require(AEItems.CELL_COMPONENT_256K, 12)
                .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 32)
                .require(NEItems.SUPERCONDUCTING_PROCESSOR, 4)
                .require(NEItems.CRYSTAL_INGOT)
                .energy(16000)
                .itemOutput(ctx.get())
                .save(prov);
        })
        .lang("16M ECO Storage Component")
        .register();

    public static final ItemEntry<MaterialItem> ECO_CELL_COMPONENT_64M = REGISTRATE
        .item("eco_cell_component_64m", MaterialItem::new)
        .recipe((ctx, prov) -> {
            IntegratedWorkingStationRecipe.builder()
                .require(NEItems.ECO_CELL_COMPONENT_16M, 3)
                .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 48)
                .require(NEItems.SUPERCONDUCTING_PROCESSOR, 16)
                .require(NEItems.CRYSTAL_INGOT)
                .itemOutput(ctx.get())
                .energy(48000)
                .save(prov);
        })
        .lang("64M ECO Storage Component")
        .register();

    public static final ItemEntry<MaterialItem> ECO_CELL_COMPONENT_256M = REGISTRATE
        .item("eco_cell_component_256m", MaterialItem::new)
        .recipe((ctx, prov) -> {
            IntegratedWorkingStationRecipe.builder()
                .require(NEItems.ECO_CELL_COMPONENT_64M, 3)
                .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 64)
                .require(NEItems.SUPERCONDUCTING_PROCESSOR, 64)
                .require(NEItems.CRYSTAL_INGOT)
                .itemOutput(ctx.get())
                .energy(144000)
                .save(prov);
        })
        .lang("256M ECO Storage Component")
        .register();

    public static final ItemEntry<Item> ECO_INFINITE_CELL_COMPONENT = REGISTRATE
        .<Item>item("eco_infinite_cell_component", p -> new Item(p.rarity(Rarity.EPIC)) {
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
                lines.add(Component.translatable("tooltip.neoecoae.infinite_component.header")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
                lines.add(Component.translatable("tooltip.neoecoae.infinite_component.components")
                    .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tooltip.neoecoae.infinite_component.matrices")
                    .withStyle(ChatFormatting.AQUA));
            }
        })
        .tag(NETags.Items.INFINITE_CELL_COMPONENTS)
        .recipe((ctx, prov) -> {
            IntegratedWorkingStationRecipe.builder()
                .require(Items.WATER_BUCKET)
                .require(Items.LAVA_BUCKET)
                .require(Items.COBBLESTONE, 64)
                .require(NEItems.ECO_CELL_COMPONENT_256M)
                .require(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT, 8)
                .require(NEItems.SUPERCONDUCTING_PROCESSOR, 8)
                .require(NEItems.CRYSTAL_MATRIX, 4)
                .energy(144_000)
                .itemOutput(ctx.get())
                .save(prov);
        })
        .lang("ECO Infinite Storage Component")
        .register();

    private static ItemEntry<MaterialItem> cellHousing(
        String name, String lang, TagKey<Item> ingot, String ingotCriterion
    ) {
        return REGISTRATE
            .item(name, MaterialItem::new)
            .recipe((ctx, prov) -> {
                ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                    .pattern("ABA")
                    .pattern("B B")
                    .pattern("CCC")
                    .define('A', NEItems.CRYSTAL_MATRIX)
                    .define('B', Tags.Items.DUSTS_REDSTONE)
                    .define('C', ingot)
                    .unlockedBy("has_crystal_matrix", RegistrateRecipeProvider.has(NEItems.CRYSTAL_MATRIX))
                    .unlockedBy("has_redstone", RegistrateRecipeProvider.has(Tags.Items.DUSTS_REDSTONE))
                    .unlockedBy(ingotCriterion, RegistrateRecipeProvider.has(ingot))
                    .save(prov);
            })
            .lang(lang)
            .model(ItemModelUtil.importedCellModel(name))
            .register();
    }

    public static final ItemEntry<MaterialItem> ECO_ITEM_CELL_HOUSING = cellHousing(
        "eco_item_cell_housing", "ECO Storage Matrix Housing (Item)",
        NETags.Items.ALUMINUM_INGOT, "has_aluminum");

    public static final ItemEntry<MaterialItem> ECO_BULK_ITEM_CELL_HOUSING = cellHousing(
        "eco_bulk_item_cell_housing", "ECO Storage Matrix Housing (Bulk Item)",
        NETags.Items.ALUMINUM_ALLOY_INGOT, "has_aluminum_alloy");

    public static final ItemEntry<MaterialItem> ECO_FLUID_CELL_HOUSING = cellHousing(
        "eco_fluid_cell_housing", "ECO Storage Matrix Housing (Fluid)",
        NETags.Items.ALUMINUM_ALLOY_INGOT, "has_aluminum_alloy");

    private enum CellKind {
        ITEM("item", AEKeyType.items(), NECellTypes.ITEM),
        FLUID("fluid", AEKeyType.fluids(), NECellTypes.FLUID);

        final String id;
        final AEKeyType keyType;
        final NECellTypeEntry cellType;

        CellKind(String id, AEKeyType keyType, NECellTypeEntry cellType) {
            this.id = id;
            this.keyType = keyType;
            this.cellType = cellType;
        }
    }

    private static ItemEntry<ECOStorageCellItem> storageCell(
        CellKind kind, String size, ECOTier tier, Rarity rarity,
        ItemEntry<?> housing, ItemEntry<?> component, String lang
    ) {
        return REGISTRATE
            .item("eco_" + kind.id + "_storage_cell_" + size, p -> new ECOStorageCellItem(
                p.stacksTo(1).rarity(rarity),
                tier,
                kind.keyType,
                kind.cellType
            ))
            .recipe((ctx, prov) -> {
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                    .requires(housing)
                    .requires(component)
                    .unlockedBy("has_" + size + "_component", RegistrateRecipeProvider.has(component))
                    .save(prov);
                StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(housing.asStack(), component.asStack()));
                prov.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
            })
            .lang(lang)
            .model(ItemModelUtil.cellModel(kind.id, size))
            .register();
    }

    public static final ItemEntry<ECOStorageCellItem> ECO_ITEM_CELL_16M = storageCell(
        CellKind.ITEM, "16m", ECOTier.L4, Rarity.UNCOMMON,
        NEItems.ECO_ITEM_CELL_HOUSING, NEItems.ECO_CELL_COMPONENT_16M, "ECO - LE4 Storage Matrix (Item)");

    public static final ItemEntry<ECOStorageCellItem> ECO_ITEM_CELL_64M = storageCell(
        CellKind.ITEM, "64m", ECOTier.L6, Rarity.RARE,
        NEItems.ECO_ITEM_CELL_HOUSING, NEItems.ECO_CELL_COMPONENT_64M, "ECO - LE6 Storage Matrix (Item)");

    public static final ItemEntry<ECOStorageCellItem> ECO_ITEM_CELL_256M = storageCell(
        CellKind.ITEM, "256m", ECOTier.L9, Rarity.EPIC,
        NEItems.ECO_ITEM_CELL_HOUSING, NEItems.ECO_CELL_COMPONENT_256M, "ECO - LE9 Storage Matrix (Item)");

    public static final ItemEntry<ECOStorageCellItem> ECO_BULK_ITEM_CELL_16M = bulkItemCell(
        "eco_bulk_item_cell_16m", ECOTier.L4, "16m", ECO_CELL_COMPONENT_16M, Rarity.UNCOMMON,
        "ECO - LE4 Storage Matrix (Bulk Item)");
    public static final ItemEntry<ECOStorageCellItem> ECO_BULK_ITEM_CELL_64M = bulkItemCell(
        "eco_bulk_item_cell_64m", ECOTier.L6, "64m", ECO_CELL_COMPONENT_64M, Rarity.RARE,
        "ECO - LE6 Storage Matrix (Bulk Item)");
    public static final ItemEntry<ECOStorageCellItem> ECO_BULK_ITEM_CELL_256M = bulkItemCell(
        "eco_bulk_item_cell_256m", ECOTier.L9, "256m", ECO_CELL_COMPONENT_256M, Rarity.EPIC,
        "ECO - LE9 Storage Matrix (Bulk Item)");

    private static ItemEntry<ECOStorageCellItem> bulkItemCell(
        String name, ECOTier tier, String size, ItemEntry<?> component, Rarity rarity, String lang
    ) {
        long capacity = tier.getStorageTotalBytes();
        return REGISTRATE.item(name, p -> new ECOStorageCellItem(
                p.stacksTo(1).rarity(rarity), tier, AEKeyType.items(), NECellTypes.BULK_ITEM,
                capacity, Math.toIntExact(capacity), (double) capacity / (1L << 20)))
            .recipe((ctx, prov) -> {
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                    .requires(ECO_BULK_ITEM_CELL_HOUSING)
                    .requires(component)
                    .unlockedBy("has_component", RegistrateRecipeProvider.has(component))
                    .save(prov);
                StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(
                    ctx.get(), List.of(ECO_BULK_ITEM_CELL_HOUSING.asStack(), component.asStack()));
                prov.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
            })
            .lang(lang)
            .model(ItemModelUtil.cellModel("bulk_item", size))
            .removeTab(NECreativeTabs.ECO.getKey())
            .register();
    }

    /**
     * ECO infinite base resource storage matrix: an unbounded source and sink for a hard-locked set
     * of base resources (see {@link ECOInfiniteResourceCellItem#lockedKeys()}). Capacity uses
     * {@link Long#MAX_VALUE} as the "unbounded" sentinel that {@link ECOStorageCellItem} renders as ∞.
     * The finished matrix is assembled from the infinite component and the standard item housing.
     */
    public static final ItemEntry<ECOInfiniteResourceCellItem> ECO_INFINITE_ITEM_CELL = REGISTRATE
        .item("eco_infinite_item_cell", p -> new ECOInfiniteResourceCellItem(
            p.stacksTo(1).rarity(Rarity.EPIC),
            ECOTier.L9,
            NECellTypes.ITEM
        ))
        .recipe((ctx, prov) -> IntegratedWorkingStationRecipe.builder()
            .require(Items.WATER_BUCKET)
            .require(Items.LAVA_BUCKET)
            .require(NEItems.ECO_CELL_COMPONENT_256M)
            .require(NEItems.CRYSTAL_MATRIX)
            .itemOutput(ctx.get())
            .energy(144_000)
            .save(prov))
        .lang("ECO Infinite Base Resource Storage Matrix")
        .model(ItemModelUtil.infiniteCellModel("eco_infinite_cell_housing"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_FLUID_CELL_16M = storageCell(
        CellKind.FLUID, "16m", ECOTier.L4, Rarity.UNCOMMON,
        NEItems.ECO_FLUID_CELL_HOUSING, NEItems.ECO_CELL_COMPONENT_16M, "ECO - LE4 Storage Matrix (Fluid)");

    public static final ItemEntry<ECOStorageCellItem> ECO_FLUID_CELL_64M = storageCell(
        CellKind.FLUID, "64m", ECOTier.L6, Rarity.RARE,
        NEItems.ECO_FLUID_CELL_HOUSING, NEItems.ECO_CELL_COMPONENT_64M, "ECO - LE6 Storage Matrix (Fluid)");

    public static final ItemEntry<ECOStorageCellItem> ECO_FLUID_CELL_256M = storageCell(
        CellKind.FLUID, "256m", ECOTier.L9, Rarity.EPIC,
        NEItems.ECO_FLUID_CELL_HOUSING, NEItems.ECO_CELL_COMPONENT_256M, "ECO - LE9 Storage Matrix (Fluid)");

    public static final ItemEntry<ECOComputationCellItem> ECO_COMPUTATION_CELL_L4 = createComputationCell(
        "l4",
        ECOTier.L4,
        Rarity.UNCOMMON
    );

    public static final ItemEntry<ECOComputationCellItem> ECO_COMPUTATION_CELL_L6 = createComputationCell(
        "l6",
        ECOTier.L6,
        Rarity.RARE
    );
    public static final ItemEntry<ECOComputationCellItem> ECO_COMPUTATION_CELL_L9 = createComputationCell(
        "l9",
        ECOTier.L9,
        Rarity.EPIC
    );

    private static ItemEntry<ECOComputationCellItem> createComputationCell(
        String tierString,
        IECOTier tier,
        Rarity rarity
    ) {
        return REGISTRATE
            .item("eco_computation_cell_" + tierString, p -> new ECOComputationCellItem(
                p.stacksTo(8).rarity(rarity),
                tier
            ))
            .lang("ECO - %s Flash Crystal Matrix".formatted(tierString.replace("l", "CE")))
            .model((ctx, prov) -> {
            })
            .register();
    }

    public static void register() {

    }
}
