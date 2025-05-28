package cn.dancingsnow.neoecoae.data.model;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.data.NEProviderTypes;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.providers.RegistrateProvider;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.ModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class CellModelProvider extends ModelProvider<BlockModelBuilder> implements RegistrateProvider {

    private final AbstractRegistrate<?> parent;

    public CellModelProvider(AbstractRegistrate<?> parent, PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, parent.getModid(), "cell", BlockModelBuilder::new, existingFileHelper);
        this.parent = parent;
    }

    @Override
    protected void registerModels() {
        parent.genData(NEProviderTypes.CELL_MODEL, this);
    }

    public void cellModel(String name) {
        cellModel(name, modLoc("cell/" + name), modLoc("cell/led/" + name));
    }

    public void cellModel(String name, ResourceLocation texture, ResourceLocation led) {
        withExistingParent(name, NeoECOAE.id("cell/storage_cell"))
            .texture("1", texture)
            .texture("2", led)
            .texture("particle", texture);
    }

    @Override
    public String getName() {
        return "Cell Models";
    }

    @Override
    public LogicalSide getSide() {
        return LogicalSide.CLIENT;
    }
}
