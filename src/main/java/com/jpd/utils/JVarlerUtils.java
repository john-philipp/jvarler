package com.jpd.utils;

import com.jpd.serialiser.JSONSerialiser;
import org.json.JSONException;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.Files.writeString;

/** Some utils. */
public class JVarlerUtils {

    public static final Integer PRETTY_TAB_SIZE = 4;

    /** Private constructor. */
    private JVarlerUtils() {
    }

    /** A logger. */
    private static final Logger log = LoggingUtils.getLogger(JVarlerUtils.class);

    /** Format a string. */
    public static String f(String format, Object... args) {
        return String.format(format, args);
    }

    /** Join a string. */
    public static String j(String delimiter, String... args) {
        return String.join(delimiter, args);
    }

    /** Prettify helper for {@code Object}s. */
    public static String toPrettyString(Map<String, Object> map) {
        try {
            return toPrettyString(new JSONObject(map));
        } catch (JSONException e) {
            return null;
        }
    }

    /** Prettify helper for {@code JSONObject}s.
     * @return JSON as a string ready to pretty print.*/
    public static String toPrettyString(JSONObject jsonObject) {
        if (jsonObject != null) {
            try {
                return jsonObject.toString(PRETTY_TAB_SIZE);
            } catch (JSONException e) {
                return null;
            }
        }
        return null;
    }

    /** Convert a string to yaml. */
    public static <T> T toYaml(String template) {
        return (T) new Yaml().load(template);
    }

    /** Get depo root path. */
    public static String getDepoRootPath() {
        String userDir = System.getProperty("user.dir");
        Objects.requireNonNull(userDir);
        return userDir.replaceFirst("depo/.*", "depo");
    }

    /** Get inner key matches. */
    public static List<String> getInnerKeyMatches(String input) {
        return getMatches("\\$\\{([^}]+)}", input);
    }

    /** Get relative path matches. */
    public static List<String> getRelPathMatches(String input) {
        return getMatches("\\$\\{(\\.\\./(\\.\\./)*)", input);
    }

    /** Get override matches. */
    public static List<String> getOverrideMatches(String input) {
        return getMatches("^(\\d)?:?([a-zA-Z_\\.0-9]+)=(.*)$", input);
    }

    /** Generic match provider. */
    public static List<String> getMatches(String pattern, String input) {
        List<String> groups = new ArrayList<>();
        Pattern compiled = Pattern.compile(pattern);
        Matcher matcher = compiled.matcher(input);
        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                groups.add(matcher.group(i));
            }
        }
        return groups;
    }

    /** Ensure file exists. */
    public static void ensureFileExists(String path) {
        if (!(new File(path).exists())) {
            throw new RuntimeException(f("Missing file: %s", path));
        }
    }

    public static class Overrides {
        private Map<String, Object> nested;
        private Map<String, Object> flat;
        private Mapper mapper;

        public Overrides(Mapper mapper) {
            this.nested = new HashMap<>();
            this.flat = new HashMap<>();
            this.mapper = mapper;
        }

        public void put(String key, Object value) {
            mapper.set(value, nested, key);
            flat.put(key, value);
        }

        public Map<String, Object> getNested() {
            return nested;
        }

        public Map<String, Object> getFlat() {
            return flat;
        }
    }

    /** Handle overrides. */
    public static Overrides handleOverrides(List<String> rawOverrides) {
        Overrides overrides = new Overrides(new Mapper());

        final int requiredMatches = 3;
        final int indexPageNo = 0;
        final int indexNestedKey = 1;
        final int indexValue = 2;

        for (String override : rawOverrides) {

            log.info(f("Found override: %s", override));
            List<String> matches = getOverrideMatches(override);
            if (matches.size() != requiredMatches) {
                throw new RuntimeException("Unexpected number of matches returned from override!");
            }

            String pageNo = matches.get(indexPageNo);
            String nestedKey = matches.get(indexNestedKey);
            Object value = tryDetermineType(matches.get(indexValue));
            if (pageNo != null) {
                throw new RuntimeException("Page specific overrides aren't supported.");
            }

            overrides.put(nestedKey, value);
        }
        return overrides;
    }

    /** Try to determine type from a string based value. */
    public static Object tryDetermineType(String value) {
        try {
            // Tested for boolean, int, double, fails on string.
            return new JSONSerialiser().fromJSONStringJackson(value, Object.class);
        } catch (Exception ex) {
            // Treat as string.
            return value;
        }
    }

    /** Write exports file. */
    public static void writeExports(String path, Map<String, Object> config) {
        try {
            String prettyString = toPrettyString(config);
            Objects.requireNonNull(prettyString);
            writeString(Path.of(path), prettyString);
            log.info(f("Written config to: %s", path));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Get hash from bytes. */
    public static String getHash(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(data);
            return new BigInteger(1, hash).toString(16);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /** Compare two byte arrays. */
    public static Boolean areDatasSame(byte[] data1, byte[] data2) {
        return getHash(data1).equals(getHash(data2));
    }
}
