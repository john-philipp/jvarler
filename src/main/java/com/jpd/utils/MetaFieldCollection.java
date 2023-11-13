package com.jpd.utils;

import java.util.HashMap;
import java.util.Map;

/** Holds meta fields. Will overwrite namespace `meta` in config (beware). */
public class MetaFieldCollection {

    /** Key to hold any meta fields. Will be overwritten if exists. */
    private final static String KEY_META = "meta";

    /** Inner field collection. */
    private final Map<String, Object> fields;

    public MetaFieldCollection() {
        fields = new HashMap<>();
    }

    public void put(String key, Object value) {
        fields.put(key, value);
    }

    public Map<String, Object> map() {
        return Map.of(KEY_META, fields);
    }
}
