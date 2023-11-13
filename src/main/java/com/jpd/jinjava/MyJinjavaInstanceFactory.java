package com.jpd.jinjava;

import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import com.hubspot.jinjava.LegacyOverrides;

/** Custom jinjava instance factory. */
public class MyJinjavaInstanceFactory {

    /** Private constructor. */
    private MyJinjavaInstanceFactory() {
    }

    /** Create new instance. */
    public static Jinjava newInstance() {
        JinjavaConfig.Builder jinjavaConfigBuilder = JinjavaConfig.newBuilder();
        jinjavaConfigBuilder.withLegacyOverrides(LegacyOverrides.newBuilder()
            // Basically don't use `Objects.toString()` when converting
            // objects to a string. O/w maps and list come out all non-json.
            .withUsePyishObjectMapper(true)
            .build());
        JinjavaConfig jinjavaConfig = jinjavaConfigBuilder.build();
        return new Jinjava(jinjavaConfig);
    }
}
