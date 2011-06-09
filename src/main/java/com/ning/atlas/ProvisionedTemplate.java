package com.ning.atlas;

import com.google.common.util.concurrent.ListenableFuture;
import com.ning.atlas.tree.Tree;

import java.util.List;

public abstract class ProvisionedTemplate implements Tree<ProvisionedTemplate>
{
    private final String name;

    public ProvisionedTemplate(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public abstract List<? extends ProvisionedTemplate> getChildren();
    public abstract ListenableFuture<? extends InitializedTemplate> initialize();
}