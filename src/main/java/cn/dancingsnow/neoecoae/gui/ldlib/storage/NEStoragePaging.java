package cn.dancingsnow.neoecoae.gui.ldlib.storage;

import java.util.List;

/** Shared, bounded paging rules for the infinite-storage contents panel. */
public final class NEStoragePaging {
    public static final int PAGE_SIZE = 32;

    public static <T> Page<T> page(List<T> entries, int requestedPage) {
        int totalCount = entries.size();
        int pageCount = totalCount == 0 ? 1 : (totalCount - 1) / PAGE_SIZE + 1;
        int pageIndex = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int fromIndex = Math.min(totalCount, pageIndex * PAGE_SIZE);
        int toIndex = Math.min(totalCount, fromIndex + PAGE_SIZE);
        return new Page<>(List.copyOf(entries.subList(fromIndex, toIndex)), pageIndex, pageCount, totalCount);
    }

    public record Page<T>(List<T> entries, int pageIndex, int pageCount, int totalCount) {}

    private NEStoragePaging() {}
}
