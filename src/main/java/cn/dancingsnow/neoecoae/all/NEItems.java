package cn.dancingsnow.neoecoae.all;

import appeng.api.ids.AETags;
import appeng.api.stacks.AEKeyType;
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
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.DataIngredient;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SmithingTransformRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.Tags;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEItems {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
    }

    public static final ItemEntry<AxeItem> ALUMINUM_AXE = REGISTRATE
        .item("aluminum_axe", p -> new AxeItem(NEToolTier.ALUMINUM, p))
        .properties(p -> p.attributes(AxeItem.createAttributes(NEToolTier.ALUMINUM, 6.0F, -3.2F)))
        .tag(ItemTags.AXES)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ctx.get())
                .pattern("AA")
                .pattern("AB")
                .pattern(" B")
                .define('A', NETags.Items.ALUMINUM_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_aluminum_ingot", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<HoeItem> ALUMINUM_HOE = REGISTRATE
        .item("aluminum_hoe", p -> new HoeItem(NEToolTier.ALUMINUM, p))
        .properties(p -> p.attributes(HoeItem.createAttributes(NEToolTier.ALUMINUM, 0.0F, -3.0F)))
        .tag(ItemTags.HOES)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ctx.get())
                .pattern("AA")
                .pattern(" B")
                .pattern(" B")
                .define('A', NETags.Items.ALUMINUM_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_aluminum_ingot", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<ShovelItem> ALUMINUM_SHOVEL = REGISTRATE
        .item("aluminum_shovel", p -> new ShovelItem(NEToolTier.ALUMINUM, p))
        .properties(p -> p.attributes(ShovelItem.createAttributes(NEToolTier.ALUMINUM, 1.5F, -3.0F)))
        .tag(ItemTags.SHOVELS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ctx.get())
                .pattern("A")
                .pattern("B")
                .pattern("B")
                .define('A', NETags.Items.ALUMINUM_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_aluminum_ingot", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<PickaxeItem> ALUMINUM_PICKAXE = REGISTRATE
        .item("aluminum_pickaxe", p -> new PickaxeItem(NEToolTier.ALUMINUM, p))
        .properties(p -> p.attributes(ShovelItem.createAttributes(NEToolTier.ALUMINUM, 1.0F, -2.8F)))
        .tag(ItemTags.PICKAXES, Tags.Items.MINING_TOOL_TOOLS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ctx.get())
                .pattern("AAA")
                .pattern(" B ")
                .pattern(" B ")
                .define('A', NETags.Items.ALUMINUM_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_aluminum_ingot", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<SwordItem> ALUMINUM_SWORD = REGISTRATE
        .item("aluminum_sword", p -> new SwordItem(NEToolTier.ALUMINUM, p))
        .properties(p -> p.attributes(ShovelItem.createAttributes(NEToolTier.ALUMINUM, 3F, -2.4F)))
        .tag(ItemTags.SWORDS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ctx.get())
                .pattern("A")
                .pattern("A")
                .pattern("B")
                .define('A', NETags.Items.ALUMINUM_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_aluminum_ingot", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<AxeItem> TUNGSTEN_AXE = REGISTRATE
        .item("tungsten_axe", p -> new AxeItem(NEToolTier.TUNGSTEN, p))
        .properties(p -> p.attributes(AxeItem.createAttributes(NEToolTier.TUNGSTEN, 6.0F, -3.2F)))
        .tag(ItemTags.AXES)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ctx.get())
                .pattern("AA")
                .pattern("AB")
                .pattern(" B")
                .define('A', NETags.Items.TUNGSTEN_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_tungsten_ingot", RegistrateRecipeProvider.has(NETags.Items.TUNGSTEN_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<HoeItem> TUNGSTEN_HOE = REGISTRATE
        .item("tungsten_hoe", p -> new HoeItem(NEToolTier.TUNGSTEN, p))
        .properties(p -> p.attributes(HoeItem.createAttributes(NEToolTier.TUNGSTEN, 0.0F, -3.0F)))
        .tag(ItemTags.HOES)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ctx.get())
                .pattern("AA")
                .pattern(" B")
                .pattern(" B")
                .define('A', NETags.Items.TUNGSTEN_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_tungsten_ingot", RegistrateRecipeProvider.has(NETags.Items.TUNGSTEN_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<ShovelItem> TUNGSTEN_SHOVEL = REGISTRATE
        .item("tungsten_shovel", p -> new ShovelItem(NEToolTier.TUNGSTEN, p))
        .properties(p -> p.attributes(ShovelItem.createAttributes(NEToolTier.TUNGSTEN, 1.5F, -3.0F)))
        .tag(ItemTags.SHOVELS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ctx.get())
                .pattern("A")
                .pattern("B")
                .pattern("B")
                .define('A', NETags.Items.TUNGSTEN_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_tungsten_ingot", RegistrateRecipeProvider.has(NETags.Items.TUNGSTEN_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<PickaxeItem> TUNGSTEN_PICKAXE = REGISTRATE
        .item("tungsten_pickaxe", p -> new PickaxeItem(NEToolTier.TUNGSTEN, p))
        .properties(p -> p.attributes(ShovelItem.createAttributes(NEToolTier.TUNGSTEN, 1.0F, -2.8F)))
        .tag(ItemTags.PICKAXES, Tags.Items.MINING_TOOL_TOOLS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ctx.get())
                .pattern("AAA")
                .pattern(" B ")
                .pattern(" B ")
                .define('A', NETags.Items.TUNGSTEN_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_tungsten_ingot", RegistrateRecipeProvider.has(NETags.Items.TUNGSTEN_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<SwordItem> TUNGSTEN_SWORD = REGISTRATE
        .item("tungsten_sword", p -> new SwordItem(NEToolTier.TUNGSTEN, p))
        .properties(p -> p.attributes(ShovelItem.createAttributes(NEToolTier.TUNGSTEN, 3F, -2.4F)))
        .tag(ItemTags.SWORDS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ctx.get())
                .pattern("A")
                .pattern("A")
                .pattern("B")
                .define('A', NETags.Items.TUNGSTEN_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_tungsten_ingot", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<SmithingTemplateItem> ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE = REGISTRATE
        .item("aluminum_alloy_upgrade_smithing_template", p -> new SmithingTemplateItem(
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.aluminum_alloy_upgrade.applies_to"), "Aluminum Equipment").withStyle(ChatFormatting.BLUE),
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.aluminum_alloy_upgrade.ingredients"), "Aluminum Alloy Ingot").withStyle(ChatFormatting.BLUE),
            REGISTRATE.addLang("upgrade", NeoECOAE.id("aluminum_alloy_upgrade"), "Aluminum Alloy Upgrade").withStyle(ChatFormatting.GRAY),
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.aluminum_alloy_upgrade.base_slot_description"), "Add Aluminum weapon, or tool"),
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.aluminum_alloy_upgrade.additions_slot_description"), "Add Aluminum Alloy Ingot"),
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
                .requires(NETags.Items.ALUMINUM_ALLOY_INGOT)
                .requires(NEItems.ENERGIZED_FLUIX_CRYSTAL)
                .unlockedBy("has_aliminim_alloy_ingot", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_ALLOY_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<AxeItem> ALUMINUM_ALLOY_AXE = REGISTRATE
        .item("aluminum_alloy_axe", p -> new AxeItem(NEToolTier.ALUMINUM_ALLOY, p))
        .properties(p -> p.attributes(AxeItem.createAttributes(NEToolTier.ALUMINUM_ALLOY, 6.0F, -3.2F)))
        .tag(ItemTags.AXES)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.ALUMINUM_AXE),
                    Ingredient.of(NETags.Items.ALUMINUM_ALLOY_INGOT),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", RegistrateRecipeProvider.has(NEItems.ALUMINUM_ALLOY_INGOT))
                .save(prov, ctx.getId().withSuffix("_smithing"));
        })
        .register();

    public static final ItemEntry<HoeItem> ALUMINUM_ALLOY_HOE = REGISTRATE
        .item("aluminum_alloy_hoe", p -> new HoeItem(NEToolTier.ALUMINUM_ALLOY, p))
        .properties(p -> p.attributes(AxeItem.createAttributes(NEToolTier.ALUMINUM_ALLOY, 0F, -3F)))
        .tag(ItemTags.HOES)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.ALUMINUM_HOE),
                    Ingredient.of(NETags.Items.ALUMINUM_ALLOY_INGOT),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", RegistrateRecipeProvider.has(NEItems.ALUMINUM_ALLOY_INGOT))
                .save(prov, ctx.getId().withSuffix("_smithing"));
        })
        .register();

    public static final ItemEntry<ShovelItem> ALUMINUM_ALLOY_SHOVEL = REGISTRATE
        .item("aluminum_alloy_shovel", p -> new ShovelItem(NEToolTier.ALUMINUM_ALLOY, p))
        .properties(p -> p.attributes(AxeItem.createAttributes(NEToolTier.ALUMINUM_ALLOY, 1.5F, -3F)))
        .tag(ItemTags.SHOVELS)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.ALUMINUM_SHOVEL),
                    Ingredient.of(NETags.Items.ALUMINUM_ALLOY_INGOT),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", RegistrateRecipeProvider.has(NEItems.ALUMINUM_ALLOY_INGOT))
                .save(prov, ctx.getId().withSuffix("_smithing"));
        })
        .register();

    public static final ItemEntry<PickaxeItem> ALUMINUM_ALLOY_PICKAXE = REGISTRATE
        .item("aluminum_alloy_pickaxe", p -> new PickaxeItem(NEToolTier.ALUMINUM_ALLOY, p))
        .properties(p -> p.attributes(AxeItem.createAttributes(NEToolTier.ALUMINUM_ALLOY, 1F, -2.8F)))
        .tag(ItemTags.PICKAXES, Tags.Items.MINING_TOOL_TOOLS)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.ALUMINUM_PICKAXE),
                    Ingredient.of(NETags.Items.ALUMINUM_ALLOY_INGOT),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", RegistrateRecipeProvider.has(NEItems.ALUMINUM_ALLOY_INGOT))
                .save(prov, ctx.getId().withSuffix("_smithing"));
        })
        .register();

    public static final ItemEntry<SwordItem> ALUMINUM_ALLOY_SWORD = REGISTRATE
        .item("aluminum_alloy_sword", p -> new SwordItem(NEToolTier.ALUMINUM_ALLOY, p))
        .properties(p -> p.attributes(AxeItem.createAttributes(NEToolTier.ALUMINUM_ALLOY, 3F, -2.4F)))
        .tag(ItemTags.SWORDS)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.ALUMINUM_SWORD),
                    Ingredient.of(NETags.Items.ALUMINUM_ALLOY_INGOT),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", RegistrateRecipeProvider.has(NEItems.ALUMINUM_ALLOY_INGOT))
                .save(prov, ctx.getId().withSuffix("_smithing"));
        })
        .register();

    public static final ItemEntry<SmithingTemplateItem> BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE = REGISTRATE
        .item("black_tungsten_alloy_upgrade_smithing_template", p -> new SmithingTemplateItem(
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.black_tungsten_alloy_upgrade.applies_to"), "Tungsten Equipment").withStyle(ChatFormatting.BLUE),
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.black_tungsten_alloy_upgrade.ingredients"), "Black Tungsten Alloy Ingot").withStyle(ChatFormatting.BLUE),
            REGISTRATE.addLang("upgrade", NeoECOAE.id("black_tungsten_alloy_upgrade"), "Black Tungsten Alloy Upgrade").withStyle(ChatFormatting.GRAY),
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.black_tungsten_alloy_upgrade.base_slot_description"), "Add Tungsten weapon, or tool"),
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.black_tungsten_alloy_upgrade.additions_slot_description"), "Add Black Tungsten Alloy Ingot"),
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
                .requires(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT)
                .requires(NEItems.ENERGIZED_FLUIX_CRYSTAL)
                .unlockedBy("has_black_tungsten_alloy_ingot", RegistrateRecipeProvider.has(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<AxeItem> BLACK_TUNGSTEN_ALLOY_AXE = REGISTRATE
        .item("black_tungsten_alloy_axe", p -> new AxeItem(NEToolTier.BLACK_TUNGSTEN_ALLOY, p))
        .properties(p -> p.attributes(AxeItem.createAttributes(NEToolTier.BLACK_TUNGSTEN_ALLOY, 6.0F, -3.2F)))
        .tag(ItemTags.AXES)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.TUNGSTEN_AXE),
                    Ingredient.of(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", RegistrateRecipeProvider.has(NEItems.BLACK_TUNGSTEN_ALLOY_INGOT))
                .save(prov, ctx.getId().withSuffix("_smithing"));
        })
        .register();

    public static final ItemEntry<HoeItem> BLACK_TUNGSTEN_ALLOY_HOE = REGISTRATE
        .item("black_tungsten_alloy_hoe", p -> new HoeItem(NEToolTier.BLACK_TUNGSTEN_ALLOY, p))
        .properties(p -> p.attributes(AxeItem.createAttributes(NEToolTier.BLACK_TUNGSTEN_ALLOY, 0F, -3F)))
        .tag(ItemTags.HOES)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.TUNGSTEN_HOE),
                    Ingredient.of(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", RegistrateRecipeProvider.has(NEItems.BLACK_TUNGSTEN_ALLOY_INGOT))
                .save(prov, ctx.getId().withSuffix("_smithing"));
        })
        .register();

    public static final ItemEntry<ShovelItem> BLACK_TUNGSTEN_ALLOY_SHOVEL = REGISTRATE
        .item("black_tungsten_alloy_shovel", p -> new ShovelItem(NEToolTier.BLACK_TUNGSTEN_ALLOY, p))
        .properties(p -> p.attributes(AxeItem.createAttributes(NEToolTier.BLACK_TUNGSTEN_ALLOY, 1.5F, -3F)))
        .tag(ItemTags.SHOVELS)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.TUNGSTEN_SHOVEL),
                    Ingredient.of(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", RegistrateRecipeProvider.has(NEItems.BLACK_TUNGSTEN_ALLOY_INGOT))
                .save(prov, ctx.getId().withSuffix("_smithing"));
        })
        .register();

    public static final ItemEntry<PickaxeItem> BLACK_TUNGSTEN_ALLOY_PICKAXE = REGISTRATE
        .item("black_tungsten_alloy_pickaxe", p -> new PickaxeItem(NEToolTier.BLACK_TUNGSTEN_ALLOY, p))
        .properties(p -> p.attributes(AxeItem.createAttributes(NEToolTier.BLACK_TUNGSTEN_ALLOY, 1F, -2.8F)))
        .tag(ItemTags.PICKAXES, Tags.Items.MINING_TOOL_TOOLS)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.TUNGSTEN_PICKAXE),
                    Ingredient.of(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", RegistrateRecipeProvider.has(NEItems.BLACK_TUNGSTEN_ALLOY_INGOT))
                .save(prov, ctx.getId().withSuffix("_smithing"));
        })
        .register();

    public static final ItemEntry<SwordItem> BLACK_TUNGSTEN_ALLOY_SWORD = REGISTRATE
        .item("black_tungsten_alloy_sword", p -> new SwordItem(NEToolTier.BLACK_TUNGSTEN_ALLOY, p))
        .properties(p -> p.attributes(AxeItem.createAttributes(NEToolTier.BLACK_TUNGSTEN_ALLOY, 3F, -2.4F)))
        .tag(ItemTags.SWORDS)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.TUNGSTEN_SWORD),
                    Ingredient.of(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", RegistrateRecipeProvider.has(NEItems.BLACK_TUNGSTEN_ALLOY_INGOT))
                .save(prov, ctx.getId().withSuffix("_smithing"));
        })
        .register();

    public static final ItemEntry<MaterialItem> IRON_DUST = REGISTRATE
        .item("iron_dust", MaterialItem::new)
        .tag(NETags.Items.IRON_DUST, Tags.Items.DUSTS)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(Tags.Items.INGOTS_IRON, ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscribe/iron_dust"));
        })
        .register();

    public static final ItemEntry<MaterialItem> RAW_ALUMINUM_ORE = REGISTRATE
        .item("raw_aluminum_ore", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.RAW_TUNGSTEN_STORAGE_BLOCK)
                .unlockedBy("has_raw_aluminum_block", RegistrateRecipeProvider.has(NETags.Items.RAW_ALUMINUM_STORAGE_BLOCK))
                .save(prov);
        })
        .tag(NETags.Items.ALUMINUM_RAW, Tags.Items.RAW_MATERIALS)
        .register();

    public static final ItemEntry<MaterialItem> ALUMINUM_INGOT = REGISTRATE
        .item("aluminum_ingot", MaterialItem::new)
        .tag(NETags.Items.ALUMINUM_INGOT, Tags.Items.INGOTS, AETags.METAL_INGOTS)
        .recipe((ctx, prov) -> {
            prov.smelting(DataIngredient.tag(NETags.Items.ALUMINUM_ORE), RecipeCategory.MISC, ctx, 0.8f);
            prov.smelting(DataIngredient.tag(NETags.Items.ALUMINUM_RAW), RecipeCategory.MISC, ctx, 0.8f);
            prov.smelting(DataIngredient.tag(NETags.Items.ALUMINUM_DUST), RecipeCategory.MISC, ctx, 0.8f);
            prov.blasting(DataIngredient.tag(NETags.Items.ALUMINUM_ORE), RecipeCategory.MISC, ctx, 0.8f);
            prov.blasting(DataIngredient.tag(NETags.Items.ALUMINUM_RAW), RecipeCategory.MISC, ctx, 0.8f);
            prov.blasting(DataIngredient.tag(NETags.Items.ALUMINUM_DUST), RecipeCategory.MISC, ctx, 0.8f);

            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.ALUMINUM_STORAGE_BLOCK)
                .unlockedBy("has_aluminum_block", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_STORAGE_BLOCK))
                .save(prov);
        })
        .register();

    public static final ItemEntry<MaterialItem> ALUMINUM_DUST = REGISTRATE
        .item("aluminum_dust", MaterialItem::new)
        .tag(NETags.Items.ALUMINUM_DUST, Tags.Items.DUSTS)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(NETags.Items.ALUMINUM_INGOT, ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/aluminum_dust"));
        })
        .register();

    public static final ItemEntry<MaterialItem> RAW_TUNGSTEN_ORE = REGISTRATE
        .item("raw_tungsten_ore", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.RAW_TUNGSTEN_STORAGE_BLOCK)
                .unlockedBy("has_raw_tungsten_block", RegistrateRecipeProvider.has(NETags.Items.RAW_TUNGSTEN_STORAGE_BLOCK))
                .save(prov);
        })
        .tag(NETags.Items.TUNGSTEN_RAW, Tags.Items.RAW_MATERIALS)
        .register();

    public static final ItemEntry<MaterialItem> TUNGSTEN_INGOT = REGISTRATE
        .item("tungsten_ingot", MaterialItem::new)
        .recipe((ctx, prov) -> {
            prov.smelting(DataIngredient.tag(NETags.Items.TUNGSTEN_ORE), RecipeCategory.MISC, ctx, 1.0f);
            prov.smelting(DataIngredient.tag(NETags.Items.TUNGSTEN_RAW), RecipeCategory.MISC, ctx, 1.0f);
            prov.smelting(DataIngredient.tag(NETags.Items.TUNGSTEN_DUST), RecipeCategory.MISC, ctx, 1.0f);
            prov.blasting(DataIngredient.tag(NETags.Items.TUNGSTEN_ORE), RecipeCategory.MISC, ctx, 1.0f);
            prov.blasting(DataIngredient.tag(NETags.Items.TUNGSTEN_RAW), RecipeCategory.MISC, ctx, 1.0f);
            prov.blasting(DataIngredient.tag(NETags.Items.TUNGSTEN_DUST), RecipeCategory.MISC, ctx, 1.0f);

            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.TUNGSTEN_STORAGE_BLOCK)
                .unlockedBy("has_tungsten_block", RegistrateRecipeProvider.has(NETags.Items.TUNGSTEN_STORAGE_BLOCK))
                .save(prov);
        })
        .tag(NETags.Items.TUNGSTEN_INGOT, Tags.Items.INGOTS, AETags.METAL_INGOTS)
        .register();

    public static final ItemEntry<MaterialItem> TUNGSTEN_DUST = REGISTRATE
        .item("tungsten_dust", MaterialItem::new)
        .tag(NETags.Items.TUNGSTEN_DUST, Tags.Items.DUSTS)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(NETags.Items.TUNGSTEN_INGOT, ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/tungsten_dust"));
        })
        .register();

    public static final ItemEntry<MaterialItem> ALUMINUM_ALLOY_INGOT = REGISTRATE
        .item("aluminum_alloy_ingot", MaterialItem::new)
        .tag(NETags.Items.ALUMINUM_ALLOY_INGOT, Tags.Items.INGOTS, AETags.METAL_INGOTS)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.ALUMINUM_ALLOY_STORAGE_BLOCK)
                .unlockedBy("has_aluminum_alloy_block", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_ALLOY_STORAGE_BLOCK))
                .save(prov);

            prov.smelting(DataIngredient.tag(NETags.Items.ALUMINUM_ALLOY_DUST), RecipeCategory.MISC, ctx, 1.0f);
            prov.blasting(DataIngredient.tag(NETags.Items.ALUMINUM_ALLOY_DUST), RecipeCategory.MISC, ctx, 1.0f);
        })
        .register();

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

    public static final ItemEntry<MaterialItem> BLACK_TUNGSTEN_ALLOY_INGOT = REGISTRATE
        .item("black_tungsten_alloy_ingot", MaterialItem::new)
        .tag(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT, Tags.Items.INGOTS, AETags.METAL_INGOTS)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.BLACK_TUNGSTEN_ALLOY_STORAGE_BLOCK)
                .unlockedBy("has_black_tungsten_alloy_block", RegistrateRecipeProvider.has(NETags.Items.BLACK_TUNGSTEN_ALLOY_STORAGE_BLOCK))
                .save(prov);

            prov.smelting(DataIngredient.tag(NETags.Items.BLACK_TUNGSTEN_ALLOY_DUST), RecipeCategory.MISC, ctx, 1.0f);
            prov.blasting(DataIngredient.tag(NETags.Items.BLACK_TUNGSTEN_ALLOY_DUST), RecipeCategory.MISC, ctx, 1.0f);
        })
        .register();

    public static final ItemEntry<MaterialItem> BLACK_TUNGSTEN_ALLOY_DUST = REGISTRATE
        .item("black_tungsten_alloy_dust", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(NETags.Items.TUNGSTEN_DUST)
                .requires(NETags.Items.ALUMINUM_ALLOY_DUST)
                .requires(ConventionTags.FLUIX_DUST)
                .requires(ConventionTags.FLUIX_DUST)
                .unlockedBy("has_aluminum_dust", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_ALLOY_DUST))
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

    public static final ItemEntry<MaterialItem> ENERGIZED_CRYSTAL_DUST = REGISTRATE
        .item("energized_crystal_dust", MaterialItem::new)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(NETags.Items.ENERGIZED_CRYSTAL, ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/energized_crystal_dust"));
        })
        .tag(NETags.Items.ENERGIZED_CRYSTAL_DUST, Tags.Items.DUSTS)
        .register();

    public static final ItemEntry<MaterialItem> ENERGIZED_FLUIX_CRYSTAL = REGISTRATE
        .item("energized_fluix_crystal", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 4)
                .requires(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_BLOCK)
                .unlockedBy("has_energized_fluix_crytal_block", RegistrateRecipeProvider.has(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_BLOCK))
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

    public static final ItemEntry<MaterialItem> ENERGIZED_FLUIX_CRYSTAL_DUST = REGISTRATE
        .item("energized_fluix_crystal_dust", MaterialItem::new)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(NETags.Items.ENERGIZED_FLUIX_CRYSTAL, ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/energized_fluix_crystal_dust"));
        })
        .tag(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_DUST, Tags.Items.DUSTS)
        .register();

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
                .require(ConventionTags.CERTUS_QUARTZ_DUST)
                .require(ConventionTags.FLUIX_DUST)
                .require(NETags.Items.ENERGIZED_CRYSTAL_DUST)
                .require(NETags.Items.CRYSTAL_INGOT_BASE)
                .requireFluid(FluidTags.LAVA, 500)
                .itemOutput(ctx.get())
                .energy(50000)
                .save(prov);
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
                .require(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_DUST)
                .require(NETags.Items.ALUMINUM_DUST)
                .require(ConventionTags.SILICON)
                .require(NETags.Items.SUPERCONDUCTIVE_INGOT_BASE)
                .requireFluid(FluidTags.LAVA, 500)
                .itemOutput(ctx.get())
                .energy(50000)
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
                .unlockedBy("has_energized_cryztal_dust", RegistrateRecipeProvider.has(NETags.Items.ENERGIZED_CRYSTAL_DUST))
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
                .require(AEItems.CELL_COMPONENT_256K, 48)
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

    public static final ItemEntry<MaterialItem> ECO_ITEM_CELL_HOUSING = REGISTRATE
        .item("eco_item_cell_housing", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("B B")
                .pattern("CCC")
                .define('A', NEItems.CRYSTAL_MATRIX)
                .define('B', Tags.Items.DUSTS_REDSTONE)
                .define('C', NETags.Items.ALUMINUM_INGOT)
                .unlockedBy("has_crystal_matrix", RegistrateRecipeProvider.has(NEItems.CRYSTAL_MATRIX))
                .unlockedBy("has_redstone", RegistrateRecipeProvider.has(Tags.Items.DUSTS_REDSTONE))
                .unlockedBy("has_aluminum", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_INGOT))
                .save(prov);
        })
        .lang("ECO Storage Matrix Housing (Item)")
        .register();

    public static final ItemEntry<MaterialItem> ECO_FLUID_CELL_HOUSING = REGISTRATE
        .item("eco_fluid_cell_housing", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("B B")
                .pattern("CCC")
                .define('A', NEItems.CRYSTAL_MATRIX)
                .define('B', Tags.Items.DUSTS_REDSTONE)
                .define('C', NETags.Items.ALUMINUM_ALLOY_INGOT)
                .unlockedBy("has_crystal_matrix", RegistrateRecipeProvider.has(NEItems.CRYSTAL_MATRIX))
                .unlockedBy("has_redstone", RegistrateRecipeProvider.has(Tags.Items.DUSTS_REDSTONE))
                .unlockedBy("has_aluminum_allot", RegistrateRecipeProvider.has(NETags.Items.ALUMINUM_ALLOY_INGOT))
                .save(prov);
        })
        .lang("ECO Storage Matrix Housing (Fluid)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_ITEM_CELL_16M = REGISTRATE
        .item("eco_item_storage_cell_16m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.UNCOMMON),
            ECOTier.L4,
            AEKeyType.items()
        ))
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(NEItems.ECO_ITEM_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_16M)
                .unlockedBy("has_16m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_16M))
                .save(prov);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(NEItems.ECO_ITEM_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_16M.asStack()));
            prov.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE4 Storage Matrix (Item)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_ITEM_CELL_64M = REGISTRATE
        .item("eco_item_storage_cell_64m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.RARE),
            ECOTier.L6,
            AEKeyType.items()
        ))
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(NEItems.ECO_ITEM_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_64M)
                .unlockedBy("has_64m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_64M))
                .save(prov);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(NEItems.ECO_ITEM_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_64M.asStack()));
            prov.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE6 Storage Matrix (Item)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_ITEM_CELL_256M = REGISTRATE
        .item("eco_item_storage_cell_256m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.EPIC),
            ECOTier.L9,
            AEKeyType.items()
        ))
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(NEItems.ECO_ITEM_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_256M)
                .unlockedBy("has_256m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_256M))
                .save(prov);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(NEItems.ECO_ITEM_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_256M.asStack()));
            prov.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE9 Storage Matrix (Item)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_FLUID_CELL_16M = REGISTRATE
        .item("eco_fluid_storage_cell_16m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.UNCOMMON),
            ECOTier.L4,
            AEKeyType.fluids()
        ))
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(NEItems.ECO_FLUID_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_16M)
                .unlockedBy("has_16m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_16M))
                .save(prov);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(NEItems.ECO_FLUID_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_16M.asStack()));
            prov.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE4 Storage Matrix (Fluid)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_FLUID_CELL_64M = REGISTRATE
        .item("eco_fluid_storage_cell_64m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.RARE),
            ECOTier.L6,
            AEKeyType.fluids()
        ))
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(NEItems.ECO_FLUID_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_64M)
                .unlockedBy("has_64m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_64M))
                .save(prov);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(NEItems.ECO_FLUID_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_64M.asStack()));
            prov.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE6 Storage Matrix (Fluid)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_FLUID_CELL_256M = REGISTRATE
        .item("eco_fluid_storage_cell_256m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.EPIC),
            ECOTier.L9,
            AEKeyType.fluids()
        ))
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(NEItems.ECO_FLUID_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_256M)
                .unlockedBy("has_256m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_256M))
                .save(prov);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(NEItems.ECO_FLUID_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_256M.asStack()));
            prov.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE9 Storage Matrix (Fluid)")
        .register();

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
                p.stacksTo(1).rarity(rarity),
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
