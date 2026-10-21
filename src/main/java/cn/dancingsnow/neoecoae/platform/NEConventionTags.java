package cn.dancingsnow.neoecoae.platform;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
/** Common tag identifiers retained from the source version when Forge has no named constant. */
public final class NEConventionTags {
    private NEConventionTags(){}
    public static final TagKey<Item> MINING_TOOLS=item("tools/mining_tool");
    public static final TagKey<Item> BUDDING_ITEMS=item("budding_blocks"),CLUSTER_ITEMS=item("clusters");
    public static final TagKey<Block> BUDDING_BLOCKS=block("budding_blocks"),CLUSTER_BLOCKS=block("clusters");
    private static TagKey<Item> item(String name){return TagKey.create(Registries.ITEM,new ResourceLocation("c",name));}
    private static TagKey<Block> block(String name){return TagKey.create(Registries.BLOCK,new ResourceLocation("c",name));}
}
