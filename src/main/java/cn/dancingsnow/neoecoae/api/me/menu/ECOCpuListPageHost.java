package cn.dancingsnow.neoecoae.api.me.menu;

/** Menu-local page requests use AE2's own client-action and GuiSync transport. */
public interface ECOCpuListPageHost {
    ECOCpuListPage neoecoae$getCpuPage();
    void neoecoae$requestCpuPage(ScrollRequest request);

    record ScrollRequest(int offset, long sequence) {}
}
