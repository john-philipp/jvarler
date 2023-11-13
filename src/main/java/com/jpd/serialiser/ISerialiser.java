package com.jpd.serialiser;

/** Serialiser interface. Specifically for
 * moving from and to JSON.
 */
public interface ISerialiser {

    /** From JSON string (jackson). */
    <T> T fromJSONStringJackson(String json, Class<T> cls);
}
