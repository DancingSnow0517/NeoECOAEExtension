package com.lowdragmc.lowdraglib2.syncdata;

public interface IContentChangeAware {
    void setOnContentsChanged(Runnable onContentChanged);

    Runnable getOnContentsChanged();
}
