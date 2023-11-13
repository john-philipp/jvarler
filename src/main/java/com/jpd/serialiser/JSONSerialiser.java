package com.jpd.serialiser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/** JSON serialiser. Handles to and from JSON serialisation. */
public class JSONSerialiser implements ISerialiser {

    /** Jackson instance. */
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** From JSON string (jackson). */
    public <T> T fromJSONStringJackson(String json, Class<T> cls) {
        try {
            return mapper.readValue(json, cls);
        } catch (IOException ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }
}
