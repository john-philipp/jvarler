package com.jpd.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jpd.utils.JVarlerUtils.f;

/** The resolver is used to resolve nested JSON using
 * JSON. By default nested JSON values are resolved
 * as `${a.b.c} -> srcJSON['a']['b']['c']`.
 */
public class Resolver {

    /** Resolver escape prefix. */
    private String prefix = "${";

    /** Resolver escape suffix. */
    private String suffix = "}";

    /** Regex escape prefix. */
    private String regexPrefix = Pattern.quote(prefix);

    /** Regex escape suffix. */
    private String regexSuffix = Pattern.quote(suffix);

    /** Fail on any unresolvable values. */
    private Boolean failOnUnresolvable = false;

    /** Nest into resolved objects. */
    private Boolean nestedResolution = false;

    /** How deeply nested are we (this refers to nested
     * variable resolution *not* collection (e.g. map/list)
     * nesting). Nested variable resolution occurs when a
     * variable resolves to another variable.
     */
    private Integer nestLevel = 0;

    /** Max nest level as per {@code nestLevel}. */
    private static final Integer MAX_NEST_LEVEL = 10;

    /** Tracks nested key extraction (i.e. `${var1.${var2}}`).
     * Keeps us from nesting indefinitely.
     */
    private boolean alreadyExtractingNestedVar = false;

    /** Limits nestability of variable resolution. E.g. ${x.${y.${z}}}. */
    private static final Integer MAX_NESTED_VAR_RESOLUTION = 10;

    /** Allow key resolution. Note that resolvable keys
     * will lead to data movements within the passed
     * object. This may incur performance losses.
     * Is useful in testing, though.
     */
    private Boolean keyResolution = false;

    /** Mapper instance. */
    private Mapper mapper = new Mapper();

    /** Layers are used to resolve nested JSON
     * values in order provided.
     */
    private List<Map<String, Object>> mapLayers = new ArrayList<>();

    /** Constructor. */
    public Resolver() {
    }

    /** Add a new layer to the resolver. */
    public Resolver addLayer(Map<String, Object> input) {
        if (input != null) {
            mapLayers.add(input);
        }
        return this;
    }

    /** Set {@code separator}. */
    public void setSeparator(String separator) {
        mapper.setSeparator(separator);
    }

    /** Set {@code joiner}. */
    public void setJoiner(String joiner) {
        mapper.setJoiner(joiner);
    }

    /** Set escape wrapper. */
    public void setWrapper(String prefix, String suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
        regexPrefix = Pattern.quote(prefix);
        regexSuffix = Pattern.quote(suffix);
    }

    /** Wrap key path using escape prefix/suffix. */
    private String wrapKeyPath(String keyPath) {
        return prefix + keyPath + suffix;
    }

    /** Overridable preprocessor invoked prior to any resolution being attempted. */
    protected void preProcessUnresolved(Object unresolved) {
    }

    public static class MapLayerInfo extends LayerInfo<Map<String, Object>> {
        public MapLayerInfo(int index, boolean isLast, Map<String, Object> layer) {
            super(index, isLast, layer);
        }
    }

    private static class LayerInfo<LayerType> {
        private int index;
        private boolean isLast;
        private LayerType layer;

        LayerInfo(int index, boolean isLast, LayerType layer) {
            this.index = index;
            this.isLast = isLast;
            this.layer = layer;
        }

        boolean isLast() {
            return isLast;
        }

        int getIndex() {
            return index;
        }

        LayerType getLayer() {
            return layer;
        }
    }

    /** Resolve all nested value references in {@code unresolved}. */
    public <T> T resolveAll(T unresolved) {
        preProcessUnresolved(unresolved);
        int last = mapLayers.size() - 1;
        for (int i = 0; i <= last; i++) {
            MapLayerInfo layerInfo = new MapLayerInfo(i, i == last, mapLayers.get(i));
            resolveValues(unresolved, layerInfo);
        }
        // Now resolved.
        return unresolved;
    }

    /** Resolve JsonPrimitives. Used for condition resolution. */
    public Object resolvePrimitive(Object unresolved) {
        Object element = unresolved;
        if (element instanceof String) {
            int last = mapLayers.size() - 1;
            for (int i = 0; i <= last; i++) {
                MapLayerInfo layerInfo = new MapLayerInfo(i, i == last, mapLayers.get(i));
                element = resolveValues(element, layerInfo);
                if (!(element instanceof String)) {
                    break;
                }
            }
        }
        return element;
    }

    /** Resolve {@code unresolved} using {@code layerInfo}. */
    private Object resolveValues(Object unresolved, MapLayerInfo layerInfo) {
        if (unresolved instanceof Map) {
            Map<String, Object> obj = (Map<String, Object>) unresolved;

            // Move any keys as per this map.
            Map<String, String> move = null;
            if (keyResolution) {
                move = new HashMap<>();
            }
            for (Map.Entry<String, Object> entry : obj.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                obj.put(key, resolveValues(value, layerInfo));

                // Try to resolve keys.
                if (move != null) {
                    // Keys must be strings.
                    String resolvedKey = (String) resolvePrimitive(key);
                    if (!key.equals(resolvedKey)) {
                        move.put(key, resolvedKey);
                    }
                }
            }
            if (move != null) {
                // Handle any movements due to resolved keys.
                move.forEach((x, y) -> {
                    Object z = obj.remove(x);
                    obj.put(y, z);
                });
            }
        } else if (unresolved instanceof List) {
            List<Object> array = (List<Object>) unresolved;
            for (int i = 0; i < array.size(); i++) {
                array.set(i, resolveValues(array.get(i), layerInfo));
            }
        } else if (unresolved instanceof String) {
            return resolveValues((String) unresolved, layerInfo);
        }
        return unresolved;
    }

    /** Resolve {@code unresolved} using {@code layerInfo}. */
    public Object resolveValues(String value, MapLayerInfo layerInfo) {
        value = resolveKeyPath(value);
        Object element = value;
        String stringValue = value;
        List<String> keyPaths = extractKeyPaths(stringValue);
        for (String keyPath : keyPaths) {
            try {
                if (keyPath == null) {
                    continue;
                }
                Object newValue = getNewValue(layerInfo, keyPath);
                if (newValue == null) {
                    // Only fail on unresolvable if configured and last layer.
                    if (failOnUnresolvable && layerInfo.isLast()) {
                        throw new IllegalArgumentException(f("unresolvable `%s`.", keyPaths.toString()));
                    }
                    continue;
                }
                String wrappedKeyPath = wrapKeyPath(keyPath);
                if (!(isPrimitive(newValue))) {
                    // Resolved to non-string, need to nest.
                    newValue = resolveValues(newValue, layerInfo);
                }
                if (newValue instanceof String && nestedResolution && nestLevel++ < MAX_NEST_LEVEL) {
                    // If configured, nest into resolved values.
                    newValue = resolveValues(newValue, layerInfo);
                    nestLevel--;
                }
                if (stringValue.equals(wrappedKeyPath)) {
                    element = newValue;
                    continue;
                }
                element = stringValue.replace(wrappedKeyPath, newValue.toString());
            } finally {
                if (isPrimitive(element)) {
                    stringValue = toString(element);
                }
            }
        }
        return element;
    }

    /** Helper: convert a primitive to a string, respecting its inner type. */
    private String toString(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value).toString();
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value instanceof String) {
            return (String) value;
        }
        throw new RuntimeException(f("Don't know how to convert value %s to string.", value));
    }

    /** Nested nuller for any unresolved elements in {@code in}.
     * Sees use in servicing {@code getResult()} call.
     */
    public <T> T nullUnresolved(T in) {
        if (in instanceof Map) {
            Map<String, Object> obj = (Map<String, Object>) in;
            obj.replaceAll((k, v) -> nullUnresolved(v));
        } else if (in instanceof List) {
            List<Object> array = (List<Object>) in;
            for (int i = 0; i < array.size(); i++) {
                array.set(i, nullUnresolved(array.get(i)));
            }
        } else if (in instanceof String) {
            List<String> keyPaths = extractKeyPaths((String) in);
            if (keyPaths.size() > 0) {
                return null;
            }
        }
        return in;
    }

    /** Get new value. Allows overrides. */
    protected Object getNewValue(MapLayerInfo layerInfo, String keyPath) {
        KeyPath kp = asKeyPath(keyPath);
        Object newValue = mapper.get(layerInfo.getLayer(), kp.parts);
        if (newValue == null && kp.defaultValue != null) {
            newValue = kp.defaultValue;
        }
        return newValue;
    }

    private String resolveKeyPath(String value) {
        if (!alreadyExtractingNestedVar) {
            alreadyExtractingNestedVar = true;
            String v0 = null;
            String v1 = value;
            Object o1;

            // Allows resolution of nested variables (e.g. `${a.${b}}`).
            for (int i = 0; i < MAX_NESTED_VAR_RESOLUTION; i++) {
                v0 = v0 == null ? value : v1;
                o1 = resolvePrimitive(v1);
                if (!(o1 instanceof String)) {
                    return v1;
                }
                v1 = (String) o1;
                if (v0.equals(v1)) {
                    alreadyExtractingNestedVar = false;
                    value = v1;
                    break;
                }
            }
        }
        return value;
    }

    /** Extract the key path wrapped using the
     * escape prefix and suffix.
     */
    private List<String> extractKeyPaths(String value) {
        String regex = String.format("%s([^${)%s]+)%s", regexPrefix, suffix, regexSuffix);
        List<String> groups = new ArrayList<>();
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            for (int i = 0; i < matcher.groupCount(); i++) {
                groups.add(matcher.group(i + 1));
            }
        }
        return groups;
    }

    /** Set to fail on any unresolvable values. */
    public Resolver setFailOnUnresolvable(Boolean failOnUnresolvable) {
        this.failOnUnresolvable = failOnUnresolvable;
        return this;
    }

    /** Set to resolve nested objects. */
    public Resolver setNestedResolution(Boolean nestedResolution) {
        this.nestedResolution = nestedResolution;
        return this;
    }

    /** Set to resolve keys. */
    public Resolver setKeyResolution(Boolean keyResolution) {
        this.keyResolution = keyResolution;
        return this;
    }

    private boolean isPrimitive(Object obj) {
        return obj instanceof String || obj instanceof Number || obj instanceof Boolean;
    }

    protected static class KeyPath {
        String[] parts;
        String defaultValue;

        KeyPath(String[] parts, String defaultValue) {
            this.defaultValue = defaultValue;
            this.parts = parts;
        }
    }

    protected KeyPath asKeyPath(String keyPath) {
        String[] parts = keyPath.split("\\.");
        String finalKey = parts[parts.length - 1];

        // Allow for string based defaults.
        String[] finalKeyParts = finalKey.split(":-");
        String defaultValue = null;

        if (finalKeyParts.length > 1) {
            defaultValue = String.join("", Arrays.copyOfRange(finalKeyParts, 1, finalKeyParts.length));
            parts[parts.length - 1] = finalKeyParts[0];
        }
        return new KeyPath(parts, defaultValue);
    }

    /** Clear all layers. */
    public void clearLayers() {
        mapLayers.clear();
    }
}
