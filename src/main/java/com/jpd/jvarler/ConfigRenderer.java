package com.jpd.jvarler;

import com.hubspot.jinjava.Jinjava;
import com.jpd.serialiser.JSONSerialiser;
import com.jpd.utils.Config;
import com.jpd.utils.Helpers;
import com.jpd.utils.JVarlerUtils;
import com.jpd.utils.LoggingUtils;
import com.jpd.utils.Mapper;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import static com.jpd.utils.Mapper.ArrayStrategy.APPEND;
import static com.jpd.utils.Mapper.ArrayStrategy.REPLACE;
import static com.jpd.utils.JVarlerUtils.f;
import static com.jpd.utils.JVarlerUtils.j;
import static java.nio.file.Files.readAllLines;

/** The reader is responsible for all config reading and rendering. */
public class ConfigRenderer implements IRenderer<Config> {

    /** A logger. */
    private final Logger log = LoggingUtils.getLogger(ConfigRenderer.class);

    /** My jinjava instance. */
    private final Jinjava jinjava;

    /** Used for chain jvarler invocations. In particular,
     * use exports.json from previous invocation here to build
     * on previous invocations.
     */
    private final String varsJson;

    /** Holds the rendered pages. */
    private final List<Map<String, Object>> renderedPages = new ArrayList<>();

    /** Holds the running config updated per page. */
    private final HashMap<String, Object> runningConfig = new HashMap<>();

    /** Holds the raw pages as lists of lines. */
    private final Map<Integer, List<String>> rawPages = new HashMap<>();

    /** The underlying resolver. */
    private final ValueResolver resolver = new ValueResolver();

    /** Any overrides. */
    private final JVarlerUtils.Overrides overrides;

    /** Underlying mapper. */
    private final Mapper mapper = new Mapper();

    /** Any config paths. Limited to one, but kept as a list
     * for ease of extension. Previous implementation did support
     * multiple configs.
     */
    private final List<String> configs;

    /** The final config. */
    private HashMap<String, Object> finalConfig;

    /** A builder for ease of extensibility. */
    public static class Builder {

        /** Jinjava instance factory. */
        private Helpers.IProducer<Jinjava> jinjavaInstanceFactory;

        /** Any overrides. */
        private JVarlerUtils.Overrides overrides;

        /** Any configs. */
        private List<String> configs;

        private String varsJson;

        /** Private constructor. */
        private Builder() {
        }

        /** Create a new instance. */
        public static Builder newInstance() {
            return new Builder();
        }

        /** Build renderer. */
        public ConfigRenderer build() {
            Objects.requireNonNull(configs);
            Objects.requireNonNull(overrides);
            Objects.requireNonNull(jinjavaInstanceFactory);
            return new ConfigRenderer(configs, overrides, jinjavaInstanceFactory.get(), varsJson);
        }

        /** Set jinjava instance factory. */
        public Builder withJinjavaInstanceFactory(Helpers.IProducer<Jinjava> jinjavaInstanceFactory) {
            this.jinjavaInstanceFactory = jinjavaInstanceFactory;
            return this;
        }

        /** Set overrides. */
        public Builder withOverrides(JVarlerUtils.Overrides overrides) {
            this.overrides = overrides;
            return this;
        }

        public Builder withVarsJson(String varsJson) {
            this.varsJson = varsJson;
            return this;
        }

        /** Set configs. */
        public Builder withConfigs(List<String> configs) {
            this.configs = configs;
            return this;
        }
    }

    /** Private constructor. */
    private ConfigRenderer(List<String> configs, JVarlerUtils.Overrides overrides, Jinjava jinjava, String varsJson) {
        this.overrides = overrides;
        this.varsJson = varsJson;
        this.jinjava = jinjava;
        this.configs = configs;
    }

    /** Outside callable. Call and all is done. */
    @Override
    public void render() {
        handleVarsJson();
        readlines(configs.get(0));
        while (hasNext()) {
            renderNextPage();
        }
    }

    /** Load vars json. */
    private void handleVarsJson() {
        if (varsJson == null || varsJson.isBlank()) {
            return;
        }
        try {
            log.info(f("Reading previous vars json from: %s", varsJson));
            List<String> lines = readAllLines(Path.of(varsJson));
            JSONObject jsonObject = new JSONObject(String.join("", lines));
            JSONSerialiser jsonSerialiser = new JSONSerialiser();
            Map<String, Object> update = (Map<String, Object>) jsonSerialiser
                .fromJSONStringJackson(jsonObject.toString(), HashMap.class);
            mapper.update(update, runningConfig);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Reader lines from a path. Will populate raw pages. */
    private void readlines(String inputPath) {
        try {
            List<String> lines = readAllLines(Path.of(inputPath));
            List<String> page = new ArrayList<>();
            int linesLen = lines.size();

            int pageNo = 0;
            int lineNo = 0;
            while (lineNo < linesLen) {
                if (!(rawPages.containsKey(pageNo))) {
                    rawPages.put(pageNo, page);
                }
                String line = lines.get(lineNo++);
                String stripped = line.strip();

                // Ignore comments.
                if (stripped.startsWith("#") || stripped.startsWith("\n") || stripped.equals("")) {
                    continue;
                }

                // Indicates a new page starting.
                if (line.equals("---")) {
                    pageNo++;
                    page = new ArrayList<>();
                    continue;
                }
                page.add(line);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Any pages remaining? */
    private boolean hasNext() {
        return rawPages.size() > renderedPages.size();
    }

    /** Render next (raw) page. */
    private void renderNextPage() {
        int pageNo = renderedPages.size();
        log.info(f("Rendering page %d.", pageNo));
        List<String> rawPage = rawPages.get(pageNo);
        if (rawPage == null) {
            throw new RuntimeException("Null raw page found.");
        }
        String content = j("\n", rawPage.toArray(new String[0]));
        Map<String, Object> myPage;
        Yaml yaml = new Yaml();

        // No jinja on page 0.
        if (pageNo > 0) {
            content = jinjava.render(content, runningConfig);
        }

        // Load as yaml. Resolve. Update running bindings.
        myPage = (Map<String, Object>) yaml.load(content);

        // Zeroth page overrides only.
        if (pageNo == 0) {
            if (overrides != null) {

                // Ensure overrides exist (to throw on non-meaningful overrides).
                List<String> failedOverrides = new ArrayList<>();
                overrides.getFlat().forEach((x, y) -> {
                    Object existingValue;

                    // The running config is set on page zero if we
                    // use a vars.json override. So we try both
                    // running config and first page.
                    existingValue = mapper.get(runningConfig, x);
                    if (existingValue == null) {
                        existingValue = mapper.get(myPage, x);
                    }

                    if (existingValue == null) {
                        log.severe(f("Found non-meaningful override: %s=%s", x, y));
                        failedOverrides.add(f("(missing) %s", x));
                    } else {
                        Class<?> yCls = y.getClass();
                        Class<?> vCls = existingValue.getClass();
                        if (!vCls.equals(yCls)) {
                            boolean fail = tryFixOverride(x, existingValue, y);

                            if (fail) {
                                log.severe(f(
                                    "Found type incompatible override: path=%s value=(%s) %s override=(%s) %s",
                                    x, vCls.getSimpleName(), existingValue, yCls.getSimpleName(), y));
                                failedOverrides.add(f("(type) %s", x));
                            }
                        }
                    }
                });

                // Log all first, then fail.
                if (!failedOverrides.isEmpty()) {
                    throw new RuntimeException(f("Failed during override checking: %s", failedOverrides));
                }

                // Any array overrides replace pre-existing values.
                mapper.setArrayStrategy(REPLACE);
                mapper.update(overrides.getNested(), myPage);

                // O/w default treatment is to append.
                mapper.setArrayStrategy(APPEND);
            }
        }

        // Update myPage into runningConfig for page
        // self-referential resolution of varler syntax
        // and perform.
        resolver.clearLayers();
        mapper.update(myPage, runningConfig);
        resolver.addLayer(runningConfig);
        resolver.resolveAll(runningConfig);
        renderedPages.add(myPage);

        // Set final config once done.
        if (!hasNext()) {
            finalConfig = runningConfig;
        }
    }

    private boolean tryFixOverride(String key, Object existingValue, Object override) {
        log.warning(f("Trying to fix override: %s", override));
        if (existingValue instanceof List) {
            if (override instanceof String) {
                String s = (String) override;

                // Allows unescaped entry of arrays via overrides.
                if (s.startsWith("[") && s.endsWith("]")) {
                    s = s.substring(1, s.length() - 1);
                    override = Arrays.asList(s.split(","));
                    overrides.put(key, override);
                    log.info("Treating as list of strings.");
                    return false;
                }
            }
        }
        return true;
    }

    /** Get final config. */
    @Override
    public Config getOutput() {
        return new Config(finalConfig);
    }
}
