package com.jpd;

import com.jpd.utils.Mapper;
import com.jpd.jinjava.MyJinjavaInstanceFactory;
import com.jpd.jvarler.ConfigRenderer;
import com.jpd.jvarler.ValueResolver;
import com.jpd.utils.JVarlerUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jpd.utils.JVarlerUtils.getInnerKeyMatches;
import static com.jpd.utils.JVarlerUtils.handleOverrides;
import static com.jpd.utils.JVarlerUtils.tryDetermineType;

/** Some JVarler related tests. By no means complete.
 * Mostly written to sanity check development.
 */
public class JVarlerTests {

    /** Some sanity checking of the VResolver. */
    @Test
    public void testVResolver() {
        ValueResolver resolver = new ValueResolver();
        Mapper mapper = new Mapper();

        // This is our map to be resolved.
        Map<String, Object> unresolved = new HashMap<>();
        mapper.set("${../b/c}", unresolved, "a1");
        mapper.set("${../../c}", unresolved, "a2.b");

        // This is our set of bindings.
        Map<String, Object> bindings = new HashMap<>();
        mapper.set("Hello", bindings, "b.c");
        mapper.set("World", bindings, "c");
        resolver.addLayer(bindings);

        // Actually attempt resolution.
        resolver.resolveAll(unresolved);

        // And assert.
        Assertions.assertEquals(mapper.get(unresolved, "a1"), "Hello");
        Assertions.assertEquals(mapper.get(unresolved, "a2.b"), "World");
    }

    /** Varler syntax allows multiple values per line.
     * Sanity check underlying matching util.
     */
    @Test
    public void testInnerKeyMatcher() {
        List<String> matches = getInnerKeyMatches("${port}:${extPort}");
        Assertions.assertEquals(matches.size(), 2);
        Assertions.assertEquals(matches.get(0), "port");
        Assertions.assertEquals(matches.get(1), "extPort");
    }

    /** Test various types in override handling. */
    @Test
    public void overrideHandling() {
        Mapper mapper = new Mapper();
        String overrideString = "network.orgs=2 chaincode.name=staticdata msps=[\"A\",\"B\"]";
        List<String> overridesList = Arrays.asList(overrideString.split(" "));
        JVarlerUtils.Overrides overrides = handleOverrides(overridesList);

        List<String> msps = new ArrayList<>();
        msps.add("A");
        msps.add("B");

        Map<String, Object> map = overrides.getNested();
        Assertions.assertEquals(mapper.get(map, "network.orgs"), 2);
        Assertions.assertEquals(mapper.get(map, "chaincode.name"), "staticdata");
        Assertions.assertEquals(mapper.get(map, "msps"), msps);
    }

    /** Specific bug observed. Override comprehension failed
     * when path contained ints. Ensure fixed.
     */
    @Test
    public void overrideFailsWhenKeySuffixIsInt() {
        Mapper mapper = new Mapper();
        String overrideString = "network.orgs2=3";
        List<String> overridesList = Arrays.asList(overrideString.split(" "));
        JVarlerUtils.Overrides overrides = handleOverrides(overridesList);

        Map<String, Object> map = overrides.getNested();
        Assertions.assertEquals(mapper.get(map, "network.orgs2"), 3);
    }

    /** Override path should be found on first page.
     * I.e. override should apply to *existing* data.
     * Also override value type should correspond to
     * existing value type.
     */
    @Test
    public void overrideNotMeaningful() {
        ConfigRenderer configRenderer = ConfigRenderer.Builder.newInstance()
            .withJinjavaInstanceFactory(MyJinjavaInstanceFactory::newInstance)
            .withOverrides(handleOverrides(
                // First two should be missing, second expects an int, we provide a string.
                List.of("missing.key1=a existing.int=abc".split(" "))))
            .withConfigs(List.of("resources/testdata/testOverrideNotMeaningful.yml"))
            .build();
        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, configRenderer::render);
        Assertions.assertEquals(
            "Failed during override checking: [(missing) missing.key1, (type) existing.int]", ex.getMessage());
    }

    /** Test try determine type from a string based value. */
    @Test
    public void testTryDetermineType() {
        Map<String, Object> objects = new HashMap<>();
        String[] values = new String[]{"true", "1", "1.0", "This is a string.", "'[\"Org1\", \"Org2\"]'"};
        for (String value : values) {
            objects.put(value, tryDetermineType(value));
        }
        Assertions.assertEquals(objects.get("true"), true);
        Assertions.assertEquals(objects.get("1"), 1);
        Assertions.assertEquals(objects.get("1.0"), 1.0);
        Assertions.assertEquals(objects.get("This is a string."), "This is a string.");
    }
}
