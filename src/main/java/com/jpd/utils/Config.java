package com.jpd.utils;

import java.util.HashMap;

/** Wrapper for jvarler configs. Allows easy setting of meta
 * fields and in general avoids low-level interaction with
 * whatever collection implementation we happen to use. */
public class Config {

    private final Mapper mapper = new Mapper();
    private final HashMap<String, Object> map;

    public Config(HashMap<String, Object> map) {
        this.map = map;
    }

    public HashMap<String, Object> getMap() {
        return map;
    }

    public void includeMetaFields(MetaFieldCollection metaFields) {
        mapper.update(metaFields.map(), map);
    }
}
