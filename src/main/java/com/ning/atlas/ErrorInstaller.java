package com.ning.atlas;

import com.google.common.util.concurrent.Futures;
import com.ning.atlas.spi.BaseComponent;
import com.ning.atlas.spi.Component;
import com.ning.atlas.spi.Deployment;
import com.ning.atlas.spi.Installer;
import com.ning.atlas.spi.Space;
import com.ning.atlas.spi.Uri;

import java.util.Map;
import java.util.concurrent.Future;

public class ErrorInstaller extends BaseComponent implements Installer
{

    public ErrorInstaller(Map<String, String> attributes)
    {

    }

    @Override
    public Future<String> describe(Host server, Uri<? extends Component> uri, Deployment deployment)
    {
        return Futures.immediateFuture("raise an error");
    }

    @Override
    public Future<?> install(Host server, Uri<Installer> uri, Deployment deployment)
    {
        throw new UnsupportedOperationException("Not Yet Implemented!");
    }
}
