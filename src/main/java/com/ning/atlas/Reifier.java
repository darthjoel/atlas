package com.ning.atlas;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import java.io.IOException;

public class Reifier
{
    private final static ObjectMapper mapper = new ObjectMapper();

    static { mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true); }

    public static String jsonify(InstalledTemplate template ) throws IOException
    {
        return mapper.writeValueAsString(template);
    }

    public static InstalledTemplate reify(Environment e, String json) throws IOException
    {
        Base.DESERIALIZATION_HACK.set(e);
        try {
            return mapper.readValue(json, InstalledTemplate.class);
        }
        finally {
            Base.DESERIALIZATION_HACK.set(null);
        }
    }
}
