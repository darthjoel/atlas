package com.ning.atlas;

public interface Installer
{
    public void install(Server server, String fragment, InitializedTemplate root) throws Exception;
}