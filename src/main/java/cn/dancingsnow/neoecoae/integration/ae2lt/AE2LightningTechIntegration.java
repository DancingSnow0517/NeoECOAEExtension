package cn.dancingsnow.neoecoae.integration.ae2lt;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.AbstractCellIntegration;
import java.util.List;

@Integration("ae2lt")
public final class AE2LightningTechIntegration extends AbstractCellIntegration {
    public AE2LightningTechIntegration() {
        super(
                AE2LightningTechCompat::getLightningKeyType,
                2,
                NELightningCellTypes::register,
                NELightningItems::register,
                ECOLightningCellHandler.INSTANCE,
                List.of(
                        NELightningItems.ECO_LIGHTNING_CELL_16M,
                        NELightningItems.ECO_LIGHTNING_CELL_64M,
                        NELightningItems.ECO_LIGHTNING_CELL_256M),
                List.of(
                        NeoECOAE.id("block/cell/storage_cell_l4_lightning"),
                        NeoECOAE.id("block/cell/storage_cell_l6_lightning"),
                        NeoECOAE.id("block/cell/storage_cell_l9_lightning")));
    }

    @Override
    protected void afterApply() {
        ECOCellModels.register(
                NELightningItems.ECO_LIGHTNING_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_lightning"));
        ECOCellModels.register(
                NELightningItems.ECO_LIGHTNING_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_lightning"));
        ECOCellModels.register(
                NELightningItems.ECO_LIGHTNING_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_lightning"));
    }
}
