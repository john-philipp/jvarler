package com.jpd.jvarler;

import com.hubspot.jinjava.Jinjava;
import com.jpd.utils.Helpers;
import com.jpd.utils.LoggingUtils;
import com.jpd.utils.Mapper;
import com.jpd.utils.SimpleBashClient;
import org.apache.commons.lang3.SerializationUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.jpd.utils.JVarlerUtils.areDatasSame;
import static com.jpd.utils.JVarlerUtils.f;
import static com.jpd.utils.JVarlerUtils.getDepoRootPath;
import static com.jpd.utils.JVarlerUtils.j;
import static com.jpd.utils.JVarlerUtils.toYaml;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.writeString;
import static org.apache.commons.lang3.math.NumberUtils.max;
import static org.apache.commons.lang3.math.NumberUtils.min;

/** Destination renderer. Responsible for rendering
 * (and writing to) destinations.
 */
public class DestinationRenderer implements IRenderer<List<Map<String, Object>>> {

    /** Logger. */
    private final Logger log = LoggingUtils.getLogger(DestinationRenderer.class);

    /** My jinjava instance. */
    private final Jinjava jinjava;

    /** Config. */
    private final HashMap<String, Object> bindings;

    /** Destinations. */
    private List<Map<String, Object>> items;

    /** Allow parallel destination rendering. */
    private final Boolean allowParallel;

    /** Destination input file path. */
    private final String templateInputPath;

    /** Mapper instance. */
    private final Mapper mapper = new Mapper();

    /** Next destination wrapper for ease of value passing. */
    private static class Next {

        /** Conditional clause, write if satisfied. */
        private static class WriteIf {

            /** Will a write change the content of the file? */
            private final Boolean changed;

            public WriteIf(Boolean changed) {
                this.changed = changed;
            }
        }

        /** Any local variables defined? */
        private final Map<String, Object> variables;

        /** Write rendered result here. */
        private final String destination;

        private final WriteIf writeIf;

        /** Write rendered result here (many). */
        private final List<String> destinations;

        /** Get source template from here. */
        private final String source;

        /** Any shell cmd? Shell commands are extracted across
         * all defined destinations and run *in order* prior to
         * any destination rendering to avoid issues during
         * parallelised destination rendering.
         */
        private static class Shell {
            private final String shell;
            private final String store;
            private final Integer stripRight;
            private final Integer stripLeft;

            public Shell(String shell, String store, Integer stripLeft, Integer stripRight) {
                this.shell = shell;
                this.store = store;
                this.stripRight = stripRight;
                this.stripLeft = stripLeft;
            }
        }

        private Shell shell;

        /** Constructor. */
        private Next(
            WriteIf writeIf, Shell shell, String source, String destination,
            List<String> destinations, Map<String, Object> variables) {
            this.destination = destination;
            this.variables = variables;
            this.destinations = destinations;
            this.source = source;
            this.shell = shell;
            this.writeIf = writeIf;
        }

        /** Build from raw destination map. */
        private static Next nextFromDestination(Map<String, Object> destination, Mapper mapper) {
            Shell shell = null;
            if (destination.containsKey("shell")) {
                shell = new Shell(
                    (String) destination.get("shell"),
                    (String) destination.get("store"),
                    (Integer) destination.get("stripLeft"),
                    (Integer) destination.get("stripRight")
                );
            }

            WriteIf writeIf = null;
            if (destination.containsKey("writeIf")) {
                writeIf = new WriteIf(
                    (Boolean) mapper.get(destination, "writeIf.changed")
                );
            }

            return new Next(
                writeIf,
                shell,
                (String) destination.get("source"),
                (String) destination.get("destination"),
                (List<String>) destination.get("destinations"),
                (Map<String, Object>) destination.get("variables")
            );
        }
    }

    /** A builder for ease of extensibility. */
    public static class Builder {

        /** Jinjava instance factory. */
        private Helpers.IProducer<Jinjava> jinjavaInstanceFactory;

        /** Bindings to use during jinja rendering. */
        private HashMap<String, Object> bindings;

        /** Underlying (destination) template input path. */
        private String templateInputPath;

        /** Allow rendering in parallel. */
        private boolean allowParallel;

        /** Make private. */
        private Builder() {
        }

        /** Create a new instance. */
        public static Builder newInstance() {
            return new Builder();
        }

        /** Builder renderer. */
        public DestinationRenderer build() {
            Objects.requireNonNull(bindings);
            Objects.requireNonNull(templateInputPath);
            Objects.requireNonNull(jinjavaInstanceFactory);
            return new DestinationRenderer(
                templateInputPath, bindings, jinjavaInstanceFactory.get(), allowParallel);
        }

        /** Set bindings to use. */
        public Builder withBindings(HashMap<String, Object> bindings) {
            this.bindings = bindings;
            return this;
        }

        /** Set template input path. */
        public Builder withTemplateInputPath(String templateInputPath) {
            this.templateInputPath = templateInputPath;
            return this;
        }

        /** Allow rendering in parallel. */
        public Builder allowParallel(boolean allowParallel) {
            this.allowParallel = allowParallel;
            return this;
        }

        /** Set jinjava instance factory. */
        public DestinationRenderer.Builder withJinjavaInstanceFactory(
            Helpers.IProducer<Jinjava> jinjavaInstanceFactory) {
            this.jinjavaInstanceFactory = jinjavaInstanceFactory;
            return this;
        }
    }

    /** Private constructor. */
    private DestinationRenderer(
        String templateInputPath, HashMap<String, Object> bindings,
        Jinjava jinjava, boolean allowParallel) {
        this.bindings = bindings;
        this.jinjava = jinjava;
        this.allowParallel = allowParallel;
        this.templateInputPath = templateInputPath;
    }

    /** Render destinations. */
    @Override
    public void render() {
        log.info("Rendering destinations.");
        items = toYaml(readAndRenderTemplate(templateInputPath, bindings));
        renderAndWriteAllDestinations();
    }

    /** Read from input and render against bindings. */
    private String readAndRenderTemplate(String inputPath, Map<String, Object> localBindings) {
        try {
            // Jinja only in destinations and destination templates.
            List<String> lines = readAllLines(Path.of(inputPath));
            List<String> clean = new ArrayList<>();
            for (String line : lines) {
                String stripped = line.strip();
                if (stripped.startsWith("#") || stripped.startsWith("\n") || stripped.equals("")) {
                    // Ignore.
                    continue;
                }
                clean.add(line);
            }
            String template = j("\n", clean.toArray(new String[0]));

            // This can set from elsewhere.
            template = jinjava.render(template, localBindings);
            return template;

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Render and write all destination files. */
    private void renderAndWriteAllDestinations() {
        SimpleBashClient simpleBashClient = new SimpleBashClient(getDepoRootPath());
        List<Next.Shell> shells = new ArrayList<>();
        // Shells may rely on ordering, so we perform outside of parallel stream.
        for (Map<String, Object> destination : items) {
            Next.Shell shell = Next.nextFromDestination(destination, mapper).shell;
            if (shell != null) {
                shells.add(shell);
            }
        }

        // Handle shells. Shells are capable to store output.
        shells.forEach(x -> {
            handleShell(simpleBashClient, x);
        });

        // For debugging it may make sense to serialise.
        Stream<Map<String, Object>> destinationStream = allowParallel
            ? items.parallelStream()
            : items.stream();

        destinationStream.forEach(item -> {
            if (item != null) {
                Next next = Next.nextFromDestination(item, mapper);
                if (next.source != null) {
                    if (next.destination == null) {
                        throw new RuntimeException("Found source with destination unset.");
                    }

                    // 1. Add local variables from destination space if set.
                    // Ensure to use a deepcopy here.
                    Map<String, Object> localBindings = SerializationUtils.clone(this.bindings);
                    Map<String, Object> localVars = next.variables;
                    if (localVars != null) {
                        mapper.update(localVars, localBindings);
                    }

                    // 2. Load source file and render against global bindings + local vars.
                    String template = readAndRenderTemplate(next.source, localBindings);

                    // 3. Save to destination file.
                    try {
                        File destinationFile = new File(next.destination);
                        boolean write = okToWrite(next.writeIf, destinationFile, template);

                        if (write) {
                            File parentDir = destinationFile.getParentFile();
                            if (parentDir != null && !parentDir.exists()) {
                                if (!parentDir.mkdirs()) {
                                    // Maybe another thread raced me here?
                                    // Only throw if it still doesn't exist.
                                    if (!parentDir.exists()) {
                                        throw new RuntimeException(
                                            f("Something has failed when creating dir: %s", parentDir));
                                    }
                                }
                            }

                            // Write to single location.
                            writeString(Path.of(next.destination), template);
                            log.info(f("Rendered: %s -> %s", next.source, next.destination));
                        }

                        // Write to multiple locations.
                        if (next.destinations != null) {
                            next.destinations.forEach(destination -> {
                                try {
                                    File destFile = new File(destination);
                                    boolean writeDest = okToWrite(next.writeIf, destFile, template);

                                    if (writeDest) {
                                        writeString(destFile.toPath(), template);
                                        log.info(f("Rendered: %s -> %s", next.source, next.destination));
                                    }
                                } catch (IOException ex) {
                                    throw new RuntimeException(ex);
                                }
                            });
                        }
                    } catch (Exception ex) {
                        log.severe(f("Failed when rendering: %s -> %s", next.source, next.destination));
                        throw new RuntimeException(ex);
                    }
                }
            }
        });
    }

    private void handleShell(SimpleBashClient simpleBashClient, Next.Shell x) {
        String output = simpleBashClient.run(x.shell);
        if (output != null && x.stripLeft != null) {
            output = output.substring(max(0, x.stripLeft));
        }
        if (output != null && x.stripRight != null) {
            output = output.substring(0, max(0, output.length() - x.stripRight));
        }
        if (output != null && !output.isEmpty()) {
            log.info(f("Output: %s", output));
        }
        if (x.store != null) {
            mapper.set(output, bindings, x.store);
            log.info(f("Stored as: %s", x.store));
        }
    }

    private boolean okToWrite(Next.WriteIf writeIf, File destinationFile, String template) throws IOException {
        if (writeIf == null) {
            return true;
        }
        if (writeIf.changed != null && writeIf.changed) {
            if (destinationFile.exists()) {
                boolean changed = !areDatasSame(
                    Files.readAllBytes(destinationFile.toPath()),
                    template.getBytes(StandardCharsets.UTF_8));
                if (!changed) {
                    log.info(f("Skipping: %s", destinationFile.getPath()));
                }
                return changed;
            }
        }
        return true;
    }

    /** Null output. */
    @Override
    public List<Map<String, Object>> getOutput() {
        return null;
    }
}
