package cn.dancingsnow.neoecoae.client.gui.crafting;

import cn.dancingsnow.neoecoae.api.me.menu.ECOCpuListPage;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCpuListPageHost.ScrollRequest;
import org.jetbrains.annotations.Nullable;

/** Keep the last complete page visible until the latest requested page has arrived. */
public final class ECOCpuListClientState {
    private ECOCpuListPage visible = ECOCpuListPage.EMPTY;
    private long sequence;
    private int offset;
    private boolean pending;

    public boolean accept(ECOCpuListPage page) {
        if (page.sequence() < sequence || page == visible) return false;
        visible = page;
        sequence = page.sequence();
        offset = page.offset();
        pending = false;
        return true;
    }

    public @Nullable ScrollRequest scrollTo(int requested) {
        int next = Math.clamp(requested, 0, Math.max(0, visible.total() - ECOCpuListPage.ROWS));
        if (next == offset) return null;
        offset = next;
        pending = true;
        return new ScrollRequest(offset, ++sequence);
    }

    public ECOCpuListPage visible() { return visible; }
    public int offset() { return offset; }
    public boolean canSelect() { return !pending; }
}
