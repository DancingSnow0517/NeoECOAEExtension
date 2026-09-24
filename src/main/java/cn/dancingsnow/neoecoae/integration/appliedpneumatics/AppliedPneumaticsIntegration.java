package cn.dancingsnow.neoecoae.integration.appliedpneumatics;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.AbstractCellIntegration;
import java.util.List;

@Integration("appliedpneumatics")
public final class AppliedPneumaticsIntegration extends AbstractCellIntegration {
    public AppliedPneumaticsIntegration() {
        super(
                AppliedPneumaticsCompat::getAirKeyType,
                1,
                NEAppliedPneumaticsCellTypes::register,
                NEAppliedPneumaticsItems::register,
                ECOAirCellHandler.INSTANCE,
                List.of(
                        NEAppliedPneumaticsItems.AIR_CELL_16M,
                        NEAppliedPneumaticsItems.AIR_CELL_64M,
                        NEAppliedPneumaticsItems.AIR_CELL_256M),
                List.of(
                        NeoECOAE.id("block/cell/storage_cell_l4_air"),
                        NeoECOAE.id("block/cell/storage_cell_l6_air"),
                        NeoECOAE.id("block/cell/storage_cell_l9_air")));
    }

    @Override
    protected void afterApply() {
        ECOCellModels.register(NEAppliedPneumaticsItems.AIR_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_air"));
        ECOCellModels.register(NEAppliedPneumaticsItems.AIR_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_air"));
        ECOCellModels.register(NEAppliedPneumaticsItems.AIR_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_air"));
    }
}
