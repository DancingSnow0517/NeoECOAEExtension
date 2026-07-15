package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cn.dancingsnow.neoecoae.client.gui.ldlib.storage.NEStorageHugeStackList;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageScrollbar;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class NEStorageControllerWidgetTest {
    @Test
    void fixedStorageScrollbarMapsTrackToFullScrollRange() {
        assertEquals(0.0D, NEStorageScrollbar.scrollFromMouse(0.0D, 0, 200, 500.0D));
        assertEquals(250.0D, NEStorageScrollbar.scrollFromMouse(100.0D, 0, 200, 500.0D), 2.0D);
        assertEquals(500.0D, NEStorageScrollbar.scrollFromMouse(200.0D, 0, 200, 500.0D));
    }

    @Test
    void ldlib2HostBorderSpriteIsBundledAtItsNativeSize() throws Exception {
        try (var stream =
                getClass().getResourceAsStream("/assets/neoecoae/textures/gui/storage_host_panel_border.png")) {
            assertNotNull(stream);
            var image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
        }
    }

    @Test
    void playerInventorySlotUsesAnIndependentNativeSizeSprite() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/neoecoae/textures/gui/slot.png")) {
            assertNotNull(stream);
            var image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(18, image.getWidth());
            assertEquals(18, image.getHeight());
        }
    }

    @Test
    void hugeStackFooterOnlyRequestsAvailableAdjacentPages() {
        var list = new NEStorageHugeStackList();
        var middlePage = pageState(1, 3);

        assertEquals(0, list.pageRequestAt(x -> x, y -> y, middlePage, 240, 180).orElseThrow());
        assertEquals(2, list.pageRequestAt(x -> x, y -> y, middlePage, 320, 180).orElseThrow());
        assertEquals(
                -1,
                list.pageRequestAt(x -> x, y -> y, pageState(0, 1), 320, 180).orElse(-1));
    }

    @Test
    void pageRequestsAreValidatedAgainstCurrentSnapshot() {
        var state = pageState(1, 3);

        assertEquals(false, NEStorageControllerWidget.isValidHugeStackPageRequest(-1, state));
        assertEquals(true, NEStorageControllerWidget.isValidHugeStackPageRequest(0, state));
        assertEquals(true, NEStorageControllerWidget.isValidHugeStackPageRequest(2, state));
        assertEquals(false, NEStorageControllerWidget.isValidHugeStackPageRequest(3, state));
    }

    @Test
    void changingHugeStackPageResetsRememberedPageScroll() {
        var list = new NEStorageHugeStackList();
        list.restore(48.0D);

        list.pageRequestAt(x -> x, y -> y, pageState(1, 3), 280, 180);

        assertEquals(0.0D, list.targetScrollPixels());
    }

    private static NEStorageUiState pageState(int page, int pageCount) {
        return new NEStorageUiState(
                BlockPos.ZERO,
                List.of(),
                List.of(),
                List.of(),
                page,
                pageCount,
                pageCount * 32,
                0,
                0,
                0,
                true,
                true,
                true,
                false,
                0,
                64,
                false,
                false);
    }
}
