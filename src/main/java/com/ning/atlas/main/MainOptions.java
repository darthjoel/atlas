package com.ning.atlas.main;

import com.google.common.collect.Iterables;
import com.sun.xml.internal.xsom.impl.scd.Iterators;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggerRepository;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.logging.LogManager;

import static java.util.Arrays.asList;

public class MainOptions
{
    private final String       environmentPath;
    private final String       systemPath;
    private final OptionParser parser;
    private final Command      command;
    private final String[]     commandArguments;

    public MainOptions(String... args) throws IOException
    {
        parser = new OptionParser();
        parser.posixlyCorrect(true);

        OptionSpec<String> e = parser.acceptsAll(asList("e", "env", "environment"), "Environment specification file")
                                     .withRequiredArg()
                                     .ofType(String.class);
        OptionSpec<String> s = parser.acceptsAll(asList("s", "sys", "system"), "System specification file")
                                     .withRequiredArg()
                                     .ofType(String.class);
        OptionSpec<File> f = parser.acceptsAll(asList("L", "log-file")).withRequiredArg().ofType(File.class);

        parser.acceptsAll(asList("v", "verbose"), "verbose output");
        parser.acceptsAll(asList("vv", "very-verbose"), "very verbose output");

        OptionSet o = parser.parse(args);

        if (o.has("v")) {
            Logger.getRootLogger().setLevel(Level.INFO);
        }
        if (o.has("vv")) {
            Logger.getRootLogger().setLevel(Level.DEBUG);
        }
        if (o.has(f)) {
            File log_file = o.valueOf(f);
            Appender console = Logger.getRootLogger().getAppender("console");
            Logger.getRootLogger().removeAppender(console);
            Logger.getRootLogger().addAppender(new FileAppender(console.getLayout(),
                                                                log_file.getAbsolutePath(),
                                                                true));
        }


        if ((o.has(e) && o.has(s))) {
            this.environmentPath = o.valueOf(e);
            this.systemPath = o.valueOf(s);
            this.command = Command.valueOf(o.nonOptionArguments().size() >= 1 ? o.nonOptionArguments().get(0) : "help");
            this.commandArguments = o.nonOptionArguments().size() >= 1
                                    ? o.nonOptionArguments()
                                       .subList(1, o.nonOptionArguments().size())
                                       .toArray(new String[o.nonOptionArguments().size() - 1])
                                    : new String[0];
        }

        else {
            System.err.println("Missing one or both of environment or system specification paths");
            this.command = Command.help;
            this.commandArguments = new String[0];
            this.environmentPath = "";
            this.systemPath = "";
        }
    }


    public String getEnvironmentPath()
    {
        return environmentPath;
    }

    public String getSystemPath()
    {
        return systemPath;
    }

    public OptionParser getParser()
    {
        return parser;
    }

    public Command getCommand()
    {
        return command;
    }

    public String[] getCommandArguments()
    {
        return commandArguments;
    }
}
