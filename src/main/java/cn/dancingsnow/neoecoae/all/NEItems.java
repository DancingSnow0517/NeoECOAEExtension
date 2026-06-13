package cn.dancingsnow.neoecoae.all;

import appeng.api.ids.AETags;
import appeng.api.stacks.AEKeyType;
import appeng.core.definitions.AEItems;
import appeng.core.ConventionTags;
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
import cn.dancingsnow.neoecoae.util.ECOModelUtil;
import com.tterrag.registrate.providers.generators.RegistrateRecipeProvider;
import com.tterrag.registrate.util.DataIngredient;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SmithingTransformRecipeBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.Tags;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEItems {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
    }

    public static final ItemEntry<AxeItem> ALUMINUM_AXE = REGISTRATE
        .item("aluminum_axe", p -> new AxeItem(NEToolTier.ALUMINUM, 6.0F, -3.2F, p))
        .tag(ItemTags.AXES)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(prov.itemLookup(), RecipeCategory.TOOLS, ctx.get())
                .pattern("AA")
                .pattern("AB")
                .pattern(" B")
                .define('A', NETags.Items.ALUMINUM_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_aluminum_ingot", prov.has(NETags.Items.ALUMINUM_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<HoeItem> ALUMINUM_HOE = REGISTRATE
        .item("aluminum_hoe", p -> new HoeItem(NEToolTier.ALUMINUM, 0.0F, -3.0F, p))
        .tag(ItemTags.HOES)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(prov.itemLookup(), RecipeCategory.TOOLS, ctx.get())
                .pattern("AA")
                .pattern(" B")
                .pattern(" B")
                .define('A', NETags.Items.ALUMINUM_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_aluminum_ingot", prov.has(NETags.Items.ALUMINUM_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<ShovelItem> ALUMINUM_SHOVEL = REGISTRATE
        .item("aluminum_shovel", p -> new ShovelItem(NEToolTier.ALUMINUM, 1.5F, -3.0F, p))
        .tag(ItemTags.SHOVELS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(prov.itemLookup(), RecipeCategory.TOOLS, ctx.get())
                .pattern("A")
                .pattern("B")
                .pattern("B")
                .define('A', NETags.Items.ALUMINUM_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_aluminum_ingot", prov.has(NETags.Items.ALUMINUM_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<Item> ALUMINUM_PICKAXE = REGISTRATE
        .item("aluminum_pickaxe", p -> new Item(p.pickaxe(NEToolTier.ALUMINUM, 1.0F, -2.8F)))
        .tag(ItemTags.PICKAXES, Tags.Items.MINING_TOOL_TOOLS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(prov.itemLookup(), RecipeCategory.TOOLS, ctx.get())
                .pattern("AAA")
                .pattern(" B ")
                .pattern(" B ")
                .define('A', NETags.Items.ALUMINUM_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_aluminum_ingot", prov.has(NETags.Items.ALUMINUM_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<Item> ALUMINUM_SWORD = REGISTRATE
        .item("aluminum_sword", p -> new Item(p.sword(NEToolTier.ALUMINUM, 3F, -2.4F)))
        .tag(ItemTags.SWORDS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(prov.itemLookup(), RecipeCategory.TOOLS, ctx.get())
                .pattern("A")
                .pattern("A")
                .pattern("B")
                .define('A', NETags.Items.ALUMINUM_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_aluminum_ingot", prov.has(NETags.Items.ALUMINUM_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<AxeItem> TUNGSTEN_AXE = REGISTRATE
        .item("tungsten_axe", p -> new AxeItem(NEToolTier.TUNGSTEN, 6.0F, -3.2F, p))
        .tag(ItemTags.AXES)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(prov.itemLookup(), RecipeCategory.TOOLS, ctx.get())
                .pattern("AA")
                .pattern("AB")
                .pattern(" B")
                .define('A', NETags.Items.TUNGSTEN_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_tungsten_ingot", prov.has(NETags.Items.TUNGSTEN_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<HoeItem> TUNGSTEN_HOE = REGISTRATE
        .item("tungsten_hoe", p -> new HoeItem(NEToolTier.TUNGSTEN, 0.0F, -3.0F, p))
        .tag(ItemTags.HOES)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(prov.itemLookup(), RecipeCategory.TOOLS, ctx.get())
                .pattern("AA")
                .pattern(" B")
                .pattern(" B")
                .define('A', NETags.Items.TUNGSTEN_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_tungsten_ingot", prov.has(NETags.Items.TUNGSTEN_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<ShovelItem> TUNGSTEN_SHOVEL = REGISTRATE
        .item("tungsten_shovel", p -> new ShovelItem(NEToolTier.TUNGSTEN, 1.5F, -3.0F, p))
        .tag(ItemTags.SHOVELS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(prov.itemLookup(), RecipeCategory.TOOLS, ctx.get())
                .pattern("A")
                .pattern("B")
                .pattern("B")
                .define('A', NETags.Items.TUNGSTEN_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_tungsten_ingot", prov.has(NETags.Items.TUNGSTEN_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<Item> TUNGSTEN_PICKAXE = REGISTRATE
        .item("tungsten_pickaxe", p -> new Item(p.pickaxe(NEToolTier.TUNGSTEN, 1.0F, -2.8F)))
        .tag(ItemTags.PICKAXES, Tags.Items.MINING_TOOL_TOOLS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(prov.itemLookup(), RecipeCategory.TOOLS, ctx.get())
                .pattern("AAA")
                .pattern(" B ")
                .pattern(" B ")
                .define('A', NETags.Items.TUNGSTEN_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_tungsten_ingot", prov.has(NETags.Items.TUNGSTEN_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<Item> TUNGSTEN_SWORD = REGISTRATE
        .item("tungsten_sword", p -> new Item(p.sword(NEToolTier.TUNGSTEN, 3F, -2.4F)))
        .tag(ItemTags.SWORDS)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(prov.itemLookup(), RecipeCategory.TOOLS, ctx.get())
                .pattern("A")
                .pattern("A")
                .pattern("B")
                .define('A', NETags.Items.TUNGSTEN_INGOT)
                .define('B', Items.STICK)
                .unlockedBy("has_tungsten_ingot", prov.has(NETags.Items.ALUMINUM_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<SmithingTemplateItem> ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE = REGISTRATE
        .item("aluminum_alloy_upgrade_smithing_template", p -> new SmithingTemplateItem(
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.aluminum_alloy_upgrade.applies_to"), "Aluminum Equipment").withStyle(ChatFormatting.BLUE),
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.aluminum_alloy_upgrade.ingredients"), "Aluminum Alloy Ingot").withStyle(ChatFormatting.BLUE),
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.aluminum_alloy_upgrade.base_slot_description"), "Add Aluminum weapon, or tool"),
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.aluminum_alloy_upgrade.additions_slot_description"), "Add Aluminum Alloy Ingot"),
            List.of(
                Identifier.withDefaultNamespace("container/slot/hoe"),
                Identifier.withDefaultNamespace("container/slot/axe"),
                Identifier.withDefaultNamespace("container/slot/sword"),
                Identifier.withDefaultNamespace("container/slot/shovel"),
                Identifier.withDefaultNamespace("container/slot/pickaxe")
            ),
            List.of(
                Identifier.withDefaultNamespace("container/slot/ingot")
            ),
            p
        ))
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get())
                .requires(NETags.Items.ALUMINUM_ALLOY_INGOT)
                .requires(NEItems.ENERGIZED_FLUIX_CRYSTAL)
                .unlockedBy("has_aliminim_alloy_ingot", prov.has(NETags.Items.ALUMINUM_ALLOY_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<AxeItem> ALUMINUM_ALLOY_AXE = REGISTRATE
        .item("aluminum_alloy_axe", p -> new AxeItem(NEToolTier.ALUMINUM_ALLOY, 6.0F, -3.2F, p))
        .tag(ItemTags.AXES)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.ALUMINUM_AXE),
                    Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.ALUMINUM_ALLOY_INGOT)),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", prov.has(NEItems.ALUMINUM_ALLOY_INGOT))
                .save(prov, smithingId(ctx.getId()));
        })
        .register();

    public static final ItemEntry<HoeItem> ALUMINUM_ALLOY_HOE = REGISTRATE
        .item("aluminum_alloy_hoe", p -> new HoeItem(NEToolTier.ALUMINUM_ALLOY, 0F, -3F, p))
        .tag(ItemTags.HOES)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.ALUMINUM_HOE),
                    Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.ALUMINUM_ALLOY_INGOT)),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", prov.has(NEItems.ALUMINUM_ALLOY_INGOT))
                .save(prov, smithingId(ctx.getId()));
        })
        .register();

    public static final ItemEntry<ShovelItem> ALUMINUM_ALLOY_SHOVEL = REGISTRATE
        .item("aluminum_alloy_shovel", p -> new ShovelItem(NEToolTier.ALUMINUM_ALLOY, 1.5F, -3F, p))
        .tag(ItemTags.SHOVELS)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.ALUMINUM_SHOVEL),
                    Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.ALUMINUM_ALLOY_INGOT)),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", prov.has(NEItems.ALUMINUM_ALLOY_INGOT))
                .save(prov, smithingId(ctx.getId()));
        })
        .register();

    public static final ItemEntry<Item> ALUMINUM_ALLOY_PICKAXE = REGISTRATE
        .item("aluminum_alloy_pickaxe", p -> new Item(p.pickaxe(NEToolTier.ALUMINUM_ALLOY, 1F, -2.8F)))
        .tag(ItemTags.PICKAXES, Tags.Items.MINING_TOOL_TOOLS)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.ALUMINUM_PICKAXE),
                    Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.ALUMINUM_ALLOY_INGOT)),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", prov.has(NEItems.ALUMINUM_ALLOY_INGOT))
                .save(prov, smithingId(ctx.getId()));
        })
        .register();

    public static final ItemEntry<Item> ALUMINUM_ALLOY_SWORD = REGISTRATE
        .item("aluminum_alloy_sword", p -> new Item(p.sword(NEToolTier.ALUMINUM_ALLOY, 3F, -2.4F)))
        .tag(ItemTags.SWORDS)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.ALUMINUM_SWORD),
                    Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.ALUMINUM_ALLOY_INGOT)),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", prov.has(NEItems.ALUMINUM_ALLOY_INGOT))
                .save(prov, smithingId(ctx.getId()));
        })
        .register();

    public static final ItemEntry<SmithingTemplateItem> BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE = REGISTRATE
        .item("black_tungsten_alloy_upgrade_smithing_template", p -> new SmithingTemplateItem(
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.black_tungsten_alloy_upgrade.applies_to"), "Tungsten Equipment").withStyle(ChatFormatting.BLUE),
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.black_tungsten_alloy_upgrade.ingredients"), "Black Tungsten Alloy Ingot").withStyle(ChatFormatting.BLUE),
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.black_tungsten_alloy_upgrade.base_slot_description"), "Add Tungsten weapon, or tool"),
            REGISTRATE.addLang("item", NeoECOAE.id("smithing_template.black_tungsten_alloy_upgrade.additions_slot_description"), "Add Black Tungsten Alloy Ingot"),
            List.of(
                Identifier.withDefaultNamespace("container/slot/hoe"),
                Identifier.withDefaultNamespace("container/slot/axe"),
                Identifier.withDefaultNamespace("container/slot/sword"),
                Identifier.withDefaultNamespace("container/slot/shovel"),
                Identifier.withDefaultNamespace("container/slot/pickaxe")
            ),
            List.of(
                Identifier.withDefaultNamespace("container/slot/ingot")
            ),
            p
        ))
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get())
                .requires(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT)
                .requires(NEItems.ENERGIZED_FLUIX_CRYSTAL)
                .unlockedBy("has_black_tungsten_alloy_ingot", prov.has(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT))
                .save(prov);
        })
        .register();

    public static final ItemEntry<AxeItem> BLACK_TUNGSTEN_ALLOY_AXE = REGISTRATE
        .item("black_tungsten_alloy_axe", p -> new AxeItem(NEToolTier.BLACK_TUNGSTEN_ALLOY, 6.0F, -3.2F, p))
        .tag(ItemTags.AXES)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.TUNGSTEN_AXE),
                    Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT)),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", prov.has(NEItems.BLACK_TUNGSTEN_ALLOY_INGOT))
                .save(prov, smithingId(ctx.getId()));
        })
        .register();

    public static final ItemEntry<HoeItem> BLACK_TUNGSTEN_ALLOY_HOE = REGISTRATE
        .item("black_tungsten_alloy_hoe", p -> new HoeItem(NEToolTier.BLACK_TUNGSTEN_ALLOY, 0F, -3F, p))
        .tag(ItemTags.HOES)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.TUNGSTEN_HOE),
                    Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT)),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", prov.has(NEItems.BLACK_TUNGSTEN_ALLOY_INGOT))
                .save(prov, smithingId(ctx.getId()));
        })
        .register();

    public static final ItemEntry<ShovelItem> BLACK_TUNGSTEN_ALLOY_SHOVEL = REGISTRATE
        .item("black_tungsten_alloy_shovel", p -> new ShovelItem(NEToolTier.BLACK_TUNGSTEN_ALLOY, 1.5F, -3F, p))
        .tag(ItemTags.SHOVELS)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.TUNGSTEN_SHOVEL),
                    Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT)),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", prov.has(NEItems.BLACK_TUNGSTEN_ALLOY_INGOT))
                .save(prov, smithingId(ctx.getId()));
        })
        .register();

    public static final ItemEntry<Item> BLACK_TUNGSTEN_ALLOY_PICKAXE = REGISTRATE
        .item("black_tungsten_alloy_pickaxe", p -> new Item(p.pickaxe(NEToolTier.BLACK_TUNGSTEN_ALLOY, 1F, -2.8F)))
        .tag(ItemTags.PICKAXES, Tags.Items.MINING_TOOL_TOOLS)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.TUNGSTEN_PICKAXE),
                    Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT)),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", prov.has(NEItems.BLACK_TUNGSTEN_ALLOY_INGOT))
                .save(prov, smithingId(ctx.getId()));
        })
        .register();

    public static final ItemEntry<Item> BLACK_TUNGSTEN_ALLOY_SWORD = REGISTRATE
        .item("black_tungsten_alloy_sword", p -> new Item(p.sword(NEToolTier.BLACK_TUNGSTEN_ALLOY, 3F, -2.4F)))
        .tag(ItemTags.SWORDS)
        .recipe((ctx, prov) -> {
            SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(NEItems.TUNGSTEN_SWORD),
                    Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT)),
                    RecipeCategory.TOOLS,
                    ctx.get()
                )
                .unlocks("has_item", prov.has(NEItems.BLACK_TUNGSTEN_ALLOY_INGOT))
                .save(prov, smithingId(ctx.getId()));
        })
        .register();

    public static final ItemEntry<MaterialItem> IRON_DUST = REGISTRATE
        .item("iron_dust", MaterialItem::new)
        .tag(NETags.Items.IRON_DUST, Tags.Items.DUSTS)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(Ingredient.of(prov.itemLookup().getOrThrow(Tags.Items.INGOTS_IRON)), ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/iron_dust"));
        })
        .register();

    public static final ItemEntry<MaterialItem> RAW_ALUMINUM_ORE = REGISTRATE
        .item("raw_aluminum_ore", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.RAW_ALUMINUM_STORAGE_BLOCK)
                .unlockedBy("has_raw_aluminum_block", prov.has(NETags.Items.RAW_ALUMINUM_STORAGE_BLOCK))
                .save(prov);
        })
        .tag(NETags.Items.ALUMINUM_RAW, Tags.Items.RAW_MATERIALS)
        .register();

    public static final ItemEntry<MaterialItem> ALUMINUM_INGOT = REGISTRATE
        .item("aluminum_ingot", MaterialItem::new)
        .tag(NETags.Items.ALUMINUM_INGOT, Tags.Items.INGOTS, AETags.METAL_INGOTS)
        .recipe((ctx, prov) -> {
            prov.smelting(tagIngredient(prov, NETags.Items.ALUMINUM_ORE), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 0.8f);
            prov.smelting(tagIngredient(prov, NETags.Items.ALUMINUM_RAW), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 0.8f);
            prov.smelting(tagIngredient(prov, NETags.Items.ALUMINUM_DUST), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 0.8f);
            prov.blasting(tagIngredient(prov, NETags.Items.ALUMINUM_ORE), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 0.8f);
            prov.blasting(tagIngredient(prov, NETags.Items.ALUMINUM_RAW), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 0.8f);
            prov.blasting(tagIngredient(prov, NETags.Items.ALUMINUM_DUST), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 0.8f);

            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.ALUMINUM_STORAGE_BLOCK)
                .unlockedBy("has_aluminum_block", prov.has(NETags.Items.ALUMINUM_STORAGE_BLOCK))
                .save(prov);
        })
        .register();

    public static final ItemEntry<MaterialItem> ALUMINUM_DUST = REGISTRATE
        .item("aluminum_dust", MaterialItem::new)
        .tag(NETags.Items.ALUMINUM_DUST, Tags.Items.DUSTS)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.ALUMINUM_INGOT)), ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/aluminum_dust"));
        })
        .register();

    public static final ItemEntry<MaterialItem> RAW_TUNGSTEN_ORE = REGISTRATE
        .item("raw_tungsten_ore", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.RAW_TUNGSTEN_STORAGE_BLOCK)
                .unlockedBy("has_raw_tungsten_block", prov.has(NETags.Items.RAW_TUNGSTEN_STORAGE_BLOCK))
                .save(prov);
        })
        .tag(NETags.Items.TUNGSTEN_RAW, Tags.Items.RAW_MATERIALS)
        .register();

    public static final ItemEntry<MaterialItem> TUNGSTEN_INGOT = REGISTRATE
        .item("tungsten_ingot", MaterialItem::new)
        .recipe((ctx, prov) -> {
            prov.smelting(tagIngredient(prov, NETags.Items.TUNGSTEN_ORE), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 1.0f);
            prov.smelting(tagIngredient(prov, NETags.Items.TUNGSTEN_RAW), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 1.0f);
            prov.smelting(tagIngredient(prov, NETags.Items.TUNGSTEN_DUST), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 1.0f);
            prov.blasting(tagIngredient(prov, NETags.Items.TUNGSTEN_ORE), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 1.0f);
            prov.blasting(tagIngredient(prov, NETags.Items.TUNGSTEN_RAW), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 1.0f);
            prov.blasting(tagIngredient(prov, NETags.Items.TUNGSTEN_DUST), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 1.0f);

            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.TUNGSTEN_STORAGE_BLOCK)
                .unlockedBy("has_tungsten_block", prov.has(NETags.Items.TUNGSTEN_STORAGE_BLOCK))
                .save(prov);
        })
        .tag(NETags.Items.TUNGSTEN_INGOT, Tags.Items.INGOTS, AETags.METAL_INGOTS)
        .register();

    public static final ItemEntry<MaterialItem> TUNGSTEN_DUST = REGISTRATE
        .item("tungsten_dust", MaterialItem::new)
        .tag(NETags.Items.TUNGSTEN_DUST, Tags.Items.DUSTS)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.TUNGSTEN_INGOT)), ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/tungsten_dust"));
        })
        .register();

    public static final ItemEntry<MaterialItem> ALUMINUM_ALLOY_INGOT = REGISTRATE
        .item("aluminum_alloy_ingot", MaterialItem::new)
        .tag(NETags.Items.ALUMINUM_ALLOY_INGOT, Tags.Items.INGOTS, AETags.METAL_INGOTS)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.ALUMINUM_ALLOY_STORAGE_BLOCK)
                .unlockedBy("has_aluminum_alloy_block", prov.has(NETags.Items.ALUMINUM_ALLOY_STORAGE_BLOCK))
                .save(prov);

            prov.smelting(tagIngredient(prov, NETags.Items.ALUMINUM_ALLOY_DUST), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 1.0f);
            prov.blasting(tagIngredient(prov, NETags.Items.ALUMINUM_ALLOY_DUST), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 1.0f);
        })
        .register();

    public static final ItemEntry<MaterialItem> ALUMINUM_ALLOY_DUST = REGISTRATE
        .item("aluminum_alloy_dust", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get())
                .requires(NETags.Items.IRON_DUST)
                .requires(NETags.Items.ALUMINUM_DUST)
                .requires(ConventionTags.CERTUS_QUARTZ_DUST)
                .requires(ConventionTags.CERTUS_QUARTZ_DUST)
                .unlockedBy("has_iron_dust", prov.has(NETags.Items.IRON_DUST))
                .unlockedBy("has_aluminum_dust", prov.has(NETags.Items.ALUMINUM_DUST))
                .unlockedBy("has_certus_quartz_dust", prov.has(ConventionTags.CERTUS_QUARTZ_DUST))
                .save(prov);

            InscriberRecipeBuilder.inscribe(Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.ALUMINUM_ALLOY_INGOT)), ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/aluminum_alloy_dust"));
        })
        .tag(NETags.Items.ALUMINUM_ALLOY_DUST, Tags.Items.DUSTS)
        .register();

    public static final ItemEntry<MaterialItem> BLACK_TUNGSTEN_ALLOY_INGOT = REGISTRATE
        .item("black_tungsten_alloy_ingot", MaterialItem::new)
        .tag(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT, Tags.Items.INGOTS, AETags.METAL_INGOTS)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get(), 9)
                .requires(NETags.Items.BLACK_TUNGSTEN_ALLOY_STORAGE_BLOCK)
                .unlockedBy("has_black_tungsten_alloy_block", prov.has(NETags.Items.BLACK_TUNGSTEN_ALLOY_STORAGE_BLOCK))
                .save(prov);

            prov.smelting(tagIngredient(prov, NETags.Items.BLACK_TUNGSTEN_ALLOY_DUST), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 1.0f);
            prov.blasting(tagIngredient(prov, NETags.Items.BLACK_TUNGSTEN_ALLOY_DUST), RecipeCategory.MISC, CookingBookCategory.MISC, ctx, 1.0f);
        })
        .register();

    public static final ItemEntry<MaterialItem> BLACK_TUNGSTEN_ALLOY_DUST = REGISTRATE
        .item("black_tungsten_alloy_dust", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get())
                .requires(NETags.Items.TUNGSTEN_DUST)
                .requires(NETags.Items.ALUMINUM_ALLOY_DUST)
                .requires(ConventionTags.FLUIX_DUST)
                .requires(ConventionTags.FLUIX_DUST)
                .unlockedBy("has_aluminum_dust", prov.has(NETags.Items.ALUMINUM_ALLOY_DUST))
                .unlockedBy("has_tungsten_dust", prov.has(NETags.Items.TUNGSTEN_DUST))
                .unlockedBy("has_certus_quartz_dust", prov.has(ConventionTags.CERTUS_QUARTZ_DUST))
                .save(prov);

            InscriberRecipeBuilder.inscribe(Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT)), ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/black_tungsten_alloy_dust"));
        })
        .tag(NETags.Items.BLACK_TUNGSTEN_ALLOY_DUST, Tags.Items.DUSTS)
        .register();

    public static final ItemEntry<MaterialItem> ENERGIZED_CRYSTAL = REGISTRATE
        .item("energized_crystal", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get(), 4)
                .requires(NETags.Items.ENERGIZED_CRYSTAL_BLOCK)
                .unlockedBy("has_energized_crystal_block", prov.has(NETags.Items.ENERGIZED_CRYSTAL_BLOCK))
                .save(prov);
        })
        .tag(NETags.Items.ENERGIZED_CRYSTAL, Tags.Items.GEMS)
        .register();

    public static final ItemEntry<MaterialItem> ENERGIZED_CRYSTAL_DUST = REGISTRATE
        .item("energized_crystal_dust", MaterialItem::new)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.ENERGIZED_CRYSTAL)), ctx.get(), 1)
                .save(prov, NeoECOAE.id("inscriber/energized_crystal_dust"));
        })
        .tag(NETags.Items.ENERGIZED_CRYSTAL_DUST, Tags.Items.DUSTS)
        .register();

    public static final ItemEntry<MaterialItem> ENERGIZED_FLUIX_CRYSTAL = REGISTRATE
        .item("energized_fluix_crystal", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get(), 4)
                .requires(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_BLOCK)
                .unlockedBy("has_energized_fluix_crytal_block", prov.has(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_BLOCK))
                .save(prov, recipeId(NeoECOAE.id("energized_fluix_crystal_from_block")));

            TransformRecipeBuilder.transform(
                prov,
                NeoECOAE.id("transform/energized_fluix_crystal"),
                ctx.get(),
                1,
                TransformCircumstance.fluid(FluidTags.WATER),
                Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.ENERGIZED_CRYSTAL_DUST)),
                Ingredient.of(prov.itemLookup().getOrThrow(ConventionTags.FLUIX_CRYSTAL))
            );
        })
        .tag(NETags.Items.ENERGIZED_FLUIX_CRYSTAL, Tags.Items.GEMS)
        .register();

    public static final ItemEntry<MaterialItem> ENERGIZED_FLUIX_CRYSTAL_DUST = REGISTRATE
        .item("energized_fluix_crystal_dust", MaterialItem::new)
        .recipe((ctx, prov) -> {
            InscriberRecipeBuilder.inscribe(Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.ENERGIZED_FLUIX_CRYSTAL)), ctx.get(), 1)
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
                Ingredient.of(prov.itemLookup().getOrThrow(ConventionTags.CERTUS_QUARTZ_DUST)),
                Ingredient.of(prov.itemLookup().getOrThrow(ConventionTags.FLUIX_DUST)),
                Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.ENERGIZED_CRYSTAL_DUST)),
                Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.CRYSTAL_INGOT_BASE))
            );
            IntegratedWorkingStationRecipe.builder(prov.itemLookup(), prov.registries().lookupOrThrow(Registries.FLUID))
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
            ShapedRecipeBuilder.shaped(prov.itemLookup(), RecipeCategory.MISC, ctx.get(), 1)
                .pattern("A A")
                .pattern(" A ")
                .pattern("A A")
                .define('A', NEItems.CRYSTAL_INGOT)
                .unlockedBy("has_crystal_ingot", prov.has(NEItems.CRYSTAL_INGOT))
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
                Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_DUST)),
                Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.ALUMINUM_DUST)),
                Ingredient.of(prov.itemLookup().getOrThrow(ConventionTags.SILICON)),
                Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.SUPERCONDUCTIVE_INGOT_BASE))
            );
            IntegratedWorkingStationRecipe.builder(prov.itemLookup(), prov.registries().lookupOrThrow(Registries.FLUID))
                .require(NETags.Items.ENERGIZED_FLUIX_CRYSTAL_DUST, 4)
                .require(NETags.Items.ALUMINUM_DUST, 4)
                .require(ConventionTags.SILICON, 4)
                .require(NETags.Items.SUPERCONDUCTIVE_INGOT_BASE, 4)
                .requireFluid(FluidTags.LAVA, 2000)
                .itemOutput(ctx.get(), 4)
                .energy(200000)
                .save(prov, ctx.getId().withPrefix("integrated_working_station/"));
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get(), 9)
                .requires(NEBlocks.ENERGIZED_SUPERCONDUCTIVE_BLOCK)
                .unlockedBy("has_block", prov.has(NEBlocks.ENERGIZED_SUPERCONDUCTIVE_BLOCK))
                .save(prov);
        })
        .register();

    public static final ItemEntry<MaterialItem> CRYOTHEUM = REGISTRATE
        .item("cryotheum", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get())
                .requires(Items.ICE)
                .requires(ConventionTags.CERTUS_QUARTZ_DUST)
                .requires(ConventionTags.SKY_STONE_DUST)
                .requires(Items.SNOWBALL)
                .requires(Ingredient.of(prov.itemLookup().getOrThrow(NETags.Items.ENERGIZED_CRYSTAL_DUST)), 4)
                .unlockedBy("has_energized_cryztal_dust", prov.has(NETags.Items.ENERGIZED_CRYSTAL_DUST))
                .save(prov);
        })
        .register();

    public static final ItemEntry<MaterialItem> CRYOTHEUM_CRYSTAL = REGISTRATE
        .item("cryotheum_crystal", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(prov.itemLookup(), RecipeCategory.MISC, ctx.get())
                .pattern("AAA")
                .pattern("ABA")
                .pattern("AAA")
                .define('A', ConventionTags.SKY_STONE_DUST)
                .define('B', NEItems.CRYOTHEUM)
                .unlockedBy("has_cryotheum", prov.has(NEItems.CRYOTHEUM))
                .save(prov);
        })
        .register();

    public static final ItemEntry<MaterialItem> SUPERCONDUCTING_PROCESSOR_PRESS = REGISTRATE
        .item("superconducting_processor_press", MaterialItem::new)
        .tag(ConventionTags.INSCRIBER_PRESSES)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(prov.itemLookup(), RecipeCategory.MISC, ctx.get())
                .pattern("AAA")
                .pattern("BCD")
                .pattern("AAA")
                .define('A', NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT)
                .define('B', AEItems.ENGINEERING_PROCESSOR_PRESS)
                .define('C', AEItems.CALCULATION_PROCESSOR_PRESS)
                .define('D', AEItems.LOGIC_PROCESSOR_PRESS)
                .unlockedBy("has_energized_superconductive_ingot", prov.has(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT))
                .save(prov);
            InscriberRecipeBuilder.inscribe(Ingredient.of(prov.itemLookup().getOrThrow(Tags.Items.STORAGE_BLOCKS_IRON)), ctx.get(), 1)
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
            IntegratedWorkingStationRecipe.builder(prov.itemLookup(), prov.registries().lookupOrThrow(Registries.FLUID))
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
            IntegratedWorkingStationRecipe.builder(prov.itemLookup(), prov.registries().lookupOrThrow(Registries.FLUID))
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
            IntegratedWorkingStationRecipe.builder(prov.itemLookup(), prov.registries().lookupOrThrow(Registries.FLUID))
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
            ShapedRecipeBuilder.shaped(prov.itemLookup(), RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("B B")
                .pattern("CCC")
                .define('A', NEItems.CRYSTAL_MATRIX)
                .define('B', Tags.Items.DUSTS_REDSTONE)
                .define('C', NETags.Items.ALUMINUM_INGOT)
                .unlockedBy("has_crystal_matrix", prov.has(NEItems.CRYSTAL_MATRIX))
                .unlockedBy("has_redstone", prov.has(Tags.Items.DUSTS_REDSTONE))
                .unlockedBy("has_aluminum", prov.has(NETags.Items.ALUMINUM_INGOT))
                .save(prov);
        })
        .lang("ECO Storage Matrix Housing (Item)")
        .register();

    public static final ItemEntry<MaterialItem> ECO_FLUID_CELL_HOUSING = REGISTRATE
        .item("eco_fluid_cell_housing", MaterialItem::new)
        .recipe((ctx, prov) -> {
            ShapedRecipeBuilder.shaped(prov.itemLookup(), RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("B B")
                .pattern("CCC")
                .define('A', NEItems.CRYSTAL_MATRIX)
                .define('B', Tags.Items.DUSTS_REDSTONE)
                .define('C', NETags.Items.ALUMINUM_ALLOY_INGOT)
                .unlockedBy("has_crystal_matrix", prov.has(NEItems.CRYSTAL_MATRIX))
                .unlockedBy("has_redstone", prov.has(Tags.Items.DUSTS_REDSTONE))
                .unlockedBy("has_aluminum_allot", prov.has(NETags.Items.ALUMINUM_ALLOY_INGOT))
                .save(prov);
        })
        .lang("ECO Storage Matrix Housing (Fluid)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_ITEM_CELL_16M = REGISTRATE
        .item("eco_item_storage_cell_16m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.UNCOMMON),
            ECOTier.L4,
            AEKeyType.items(),
            NECellTypes.ITEM
        ))
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get())
                .requires(NEItems.ECO_ITEM_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_16M)
                .unlockedBy("has_16m_component", prov.has(NEItems.ECO_CELL_COMPONENT_16M))
                .save(prov);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(template(NEItems.ECO_ITEM_CELL_HOUSING), template(NEItems.ECO_CELL_COMPONENT_16M)));
            prov.accept(recipeId(ctx.getId().withPrefix("disassembly/")), recipe, null);
        })
        .lang("ECO - LE4 Storage Matrix (Item)")
        .model(() -> ECOModelUtil.cellModel("item", "16m"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_ITEM_CELL_64M = REGISTRATE
        .item("eco_item_storage_cell_64m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.RARE),
            ECOTier.L6,
            AEKeyType.items(),
            NECellTypes.ITEM
        ))
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get())
                .requires(NEItems.ECO_ITEM_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_64M)
                .unlockedBy("has_64m_component", prov.has(NEItems.ECO_CELL_COMPONENT_64M))
                .save(prov);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(template(NEItems.ECO_ITEM_CELL_HOUSING), template(NEItems.ECO_CELL_COMPONENT_64M)));
            prov.accept(recipeId(ctx.getId().withPrefix("disassembly/")), recipe, null);
        })
        .lang("ECO - LE6 Storage Matrix (Item)")
        .model(() -> ECOModelUtil.cellModel("item", "64m"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_ITEM_CELL_256M = REGISTRATE
        .item("eco_item_storage_cell_256m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.EPIC),
            ECOTier.L9,
            AEKeyType.items(),
            NECellTypes.ITEM
        ))
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get())
                .requires(NEItems.ECO_ITEM_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_256M)
                .unlockedBy("has_256m_component", prov.has(NEItems.ECO_CELL_COMPONENT_256M))
                .save(prov);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(template(NEItems.ECO_ITEM_CELL_HOUSING), template(NEItems.ECO_CELL_COMPONENT_256M)));
            prov.accept(recipeId(ctx.getId().withPrefix("disassembly/")), recipe, null);
        })
        .lang("ECO - LE9 Storage Matrix (Item)")
        .model(() -> ECOModelUtil.cellModel("item", "256m"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_FLUID_CELL_16M = REGISTRATE
        .item("eco_fluid_storage_cell_16m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.UNCOMMON),
            ECOTier.L4,
            AEKeyType.fluids(),
            NECellTypes.FLUID
        ))
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get())
                .requires(NEItems.ECO_FLUID_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_16M)
                .unlockedBy("has_16m_component", prov.has(NEItems.ECO_CELL_COMPONENT_16M))
                .save(prov);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(template(NEItems.ECO_FLUID_CELL_HOUSING), template(NEItems.ECO_CELL_COMPONENT_16M)));
            prov.accept(recipeId(ctx.getId().withPrefix("disassembly/")), recipe, null);
        })
        .lang("ECO - LE4 Storage Matrix (Fluid)")
        .model(() -> ECOModelUtil.cellModel("fluid", "16m"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_FLUID_CELL_64M = REGISTRATE
        .item("eco_fluid_storage_cell_64m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.RARE),
            ECOTier.L6,
            AEKeyType.fluids(),
            NECellTypes.FLUID
        ))
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get())
                .requires(NEItems.ECO_FLUID_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_64M)
                .unlockedBy("has_64m_component", prov.has(NEItems.ECO_CELL_COMPONENT_64M))
                .save(prov);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(template(NEItems.ECO_FLUID_CELL_HOUSING), template(NEItems.ECO_CELL_COMPONENT_64M)));
            prov.accept(recipeId(ctx.getId().withPrefix("disassembly/")), recipe, null);
        })
        .lang("ECO - LE6 Storage Matrix (Fluid)")
        .model(() -> ECOModelUtil.cellModel("fluid", "64m"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_FLUID_CELL_256M = REGISTRATE
        .item("eco_fluid_storage_cell_256m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.EPIC),
            ECOTier.L9,
            AEKeyType.fluids(),
            NECellTypes.FLUID
        ))
        .recipe((ctx, prov) -> {
            ShapelessRecipeBuilder.shapeless(prov.itemLookup(), RecipeCategory.MISC, ctx.get())
                .requires(NEItems.ECO_FLUID_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_256M)
                .unlockedBy("has_256m_component", prov.has(NEItems.ECO_CELL_COMPONENT_256M))
                .save(prov);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(template(NEItems.ECO_FLUID_CELL_HOUSING), template(NEItems.ECO_CELL_COMPONENT_256M)));
            prov.accept(recipeId(ctx.getId().withPrefix("disassembly/")), recipe, null);
        })
        .lang("ECO - LE9 Storage Matrix (Fluid)")
        .model(() -> ECOModelUtil.cellModel("fluid", "256m"))
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
            .model(() -> (ctx, prov) -> prov.createWithExistingModel(ctx.get(), ctx.getId().withPrefix("item/")))
            .register();
    }

    private static ResourceKey<Recipe<?>> smithingId(Identifier id) {
        return recipeId(id.withSuffix("_smithing"));
    }

    private static ResourceKey<Recipe<?>> recipeId(Identifier id) {
        return ResourceKey.create(Registries.RECIPE, id);
    }

    private static DataIngredient tagIngredient(RegistrateRecipeProvider prov, TagKey<Item> tag) {
        return DataIngredient.tag(prov.itemLookup().getOrThrow(tag));
    }

    private static ItemStackTemplate template(ItemLike item) {
        return new ItemStackTemplate(item.asItem());
    }

    public static void register() {

    }
}
