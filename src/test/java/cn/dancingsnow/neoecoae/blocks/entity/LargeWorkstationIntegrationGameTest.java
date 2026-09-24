package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.menu.locator.MenuLocators;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.menu.LargeIntegratedWorkingStationPatternProviderMenu;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("neoecoae")
@PrefixGameTestTemplate(false)
public final class LargeWorkstationIntegrationGameTest {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void formsStructureAndOpensThirtySixSlotMenu(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(2, 2, 1);
        BlockPos interfacePos = new BlockPos(2, 1, 2);
        var casing = NEBlocks.LARGE_INTEGRATED_WORKING_STATION_CASING.get();
        for (int y = 1; y <= 2; y++) {
            for (int x = 1; x <= 3; x++) {
                for (int z = 1; z <= 2; z++) {
                    helper.setBlock(new BlockPos(x, y, z), casing);
                }
            }
        }
        helper.setBlock(new BlockPos(1, 1, 2), NEBlocks.LARGE_INTEGRATED_WORKING_STATION_OUTPUT_HATCH.get());
        helper.setBlock(interfacePos, NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INTERFACE.get());
        helper.setBlock(new BlockPos(3, 1, 2), NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INPUT_HATCH.get());
        helper.setBlock(
                controllerPos,
                NEBlocks.INTEGRATED_WORKING_STATION
                        .get()
                        .defaultBlockState()
                        .setValue(ECOIntegratedWorkingStation.FACING, Direction.NORTH));

        helper.runAfterDelay(10, () -> {
            var controller = (ECOLargeIntegratedWorkingStationBlockEntity) helper.getBlockEntity(controllerPos);
            controller.rebuildMultiblock();
            helper.assertTrue(controller.isFormed(), "large workstation did not form");
            helper.assertTrue(
                    helper.getBlockState(controllerPos).getValue(ECOIntegratedWorkingStation.FORMED),
                    "formed block state was not published");

            var communication =
                    (ECOLargeIntegratedWorkingStationInterfaceBlockEntity) helper.getBlockEntity(interfacePos);
            helper.assertTrue(
                    communication.getWorkstationProvider().getPatternInv().size() == 36,
                    "workstation provider has the wrong number of pattern slots");

            var level = helper.getLevel();
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "WorkstationTest"));
            player.connection =
                    new ServerGamePacketListenerImpl(
                            level.getServer(), new Connection(PacketFlow.SERVERBOUND), player) {
                        @Override
                        public void send(Packet<?> packet) {}
                    };
            communication.openMenu(player, MenuLocators.forBlockEntity(communication));
            helper.assertTrue(
                    player.containerMenu instanceof LargeIntegratedWorkingStationPatternProviderMenu,
                    "workstation menu did not open");
            helper.succeed();
        });
    }
}
