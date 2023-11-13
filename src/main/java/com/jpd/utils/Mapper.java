package com.jpd.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The mapper is used to process nested
 * JSON data in a {@code JsonObject}.
 */
public class Mapper {

    /** This is the separator used in nested
     * queries.
     */
    private String separator = "[.]";

    /** Counter part to the separator used in
     * nested queries.
     */
    private String joiner = ".";

    public enum ArrayStrategy {
        REPLACE,
        APPEND
    }

    /** Array handling strategy. */
    private ArrayStrategy arrayStrategy = ArrayStrategy.APPEND;

    /** Constructor. */
    public Mapper() {
    }

    /** Preprocess keys. Ensures partial keys are
     * fully split.
     */
    public String[] preprocessKeys(String... keys) {
        if (keys.length == 0) {
            return keys;
        }
        List<String> validKeys = new ArrayList<>();
        for (String key : keys) {
            // Filter any null or blank values.
            if (key == null || key.isBlank()) {
                continue;
            }
            validKeys.add(key);
        }
        return String.join(joiner, validKeys).split(separator);
    }

    /** JSON based nested getter. */
    public Object get(Map<String, Object> map, String... keys) {
        return get(map, false, keys);
    }

    /** JSON based nested getter. */
    public Object get(Map<String, Object> json, boolean rm, String... keys) {
        if (json == null) {
            return null;
        }
        keys = preprocessKeys(keys);
        if (rm && keys.length == 1) {
            return json.remove(keys[0]);
        }
        for (String key : keys) {
            Object value = json.get(key);
            if (value instanceof Map && keys.length == 2 && rm) {
                return ((Map<String, Object>) value).remove(keys[1]);
            }
            if (value instanceof Map && keys.length > 1) {
                return get((Map<String, Object>) value, rm, Arrays.copyOfRange(keys, 1, keys.length));
            } else {
                return value;
            }
        }
        return null;
    }

    /** JSON based nested setter. */
    public void set(Object value, Map<String, Object> map, String... keys) {
        keys = preprocessKeys(keys);
        String lastKey = keys[keys.length - 1];
        for (String key : keys) {
            if (key.equals(lastKey)) {
                map.put(key, value);
                return;
            }
            Object obj = map.get(key);
            if (!(obj instanceof Map)) {
                map.put(key, new HashMap<>());
                obj = map.get(key);
            }
            map = (Map<String, Object>) obj;
        }
    }

    private boolean isPrimitive(Object obj) {
        return obj instanceof String || obj instanceof Number || obj instanceof Boolean;
    }

    /** Update the JSON {@code dest} with {@code src}. */
    public void update(Map<String, Object> src, Map<String, Object> dest) {
        if (src == null || dest == null) {
            return;
        }
        for (Map.Entry<String, Object> entry: src.entrySet()) {
            String key = entry.getKey();
            Object srcValue = entry.getValue();
            if (dest.containsKey(key)) {
                Object destValue = dest.get(key);
                if (isPrimitive(srcValue)) {
                    dest.put(key, srcValue);
                } else if (srcValue instanceof Map) {
                    update((Map<String, Object>) srcValue, (Map<String, Object>) destValue);
                } else if (srcValue instanceof List) {
                    if (arrayStrategy.equals(ArrayStrategy.REPLACE)) {
                        dest.put(key, srcValue);
                    } else if (!(destValue instanceof List)) {
                        return;
                    } else if (arrayStrategy.equals(ArrayStrategy.APPEND)) {
                        for (Object element : (List<Object>) srcValue) {
                            ((List<Object>) destValue).add(element);
                        }
                    }
                } else {
                    throw new RuntimeException("I didn't think this could happen while writing this. :)");
                }
            } else {
                dest.put(key, srcValue);
            }
        }
    }

    /** Replacement handler. Replaces string values throughout
     * provided JSON. That is, for replacements `x: y`, any string
     * `x` found in `json` will be replaced with string `y` on
     * any nested level within `json`.
     * @param element arbitrarily deep nested JSON.
     * @param replacements a map of string-string replacements.
     */
    public Object replace(Object element, Map<String, String> replacements) {
        if (element == null) {
            return null;
        }
        if (element instanceof Map) {
            return replace((Map<String, Object>) element, replacements);
        } else if (element instanceof List) {
            return replace((List<Object>) element, replacements);
        } else if (element instanceof String) {
            return replace((String) element, replacements);
        }
        return element;
    }

    /** Replacement handler for {@code JsonObject}s. **/
    private Map<String, Object> replace(Map<String, Object> obj, Map<String, String> replacements) {
        Map<String, Object> newObj = new HashMap<>();
        for (Map.Entry<String, Object> entry : obj.entrySet()) {
            newObj.put(entry.getKey(), replace(entry.getValue(), replacements));
        }
        return newObj;
    }

    /** Replacement handler for {@code JsonArray}s. **/
    private List<Object> replace(List<Object> array, Map<String, String> replacements) {
        List<Object> newArray = new ArrayList<>();
        for (Object o : array) {
            newArray.add(replace(o, replacements));
        }
        return newArray;
    }

    /** Replacement handler for {@code JsonPrimitive}s. */
    private String replace(String string, Map<String, String> replacements) {
        // Check if we have a replacement registered.
        // If so, apply it.
        String replacement = replacements.get(string);
        if (replacement == null) {
            return string;
        }
        return replacement;
    }

    /** Separator setter. */
    public void setSeparator(String separator) {
        this.separator = separator;
    }

    /** Joiner setter. */
    public void setJoiner(String joiner) {
        this.joiner = joiner;
    }

    /** Set array handling strategy. */
    public void setArrayStrategy(ArrayStrategy arrayStrategy) {
        this.arrayStrategy = arrayStrategy;
    }
}
