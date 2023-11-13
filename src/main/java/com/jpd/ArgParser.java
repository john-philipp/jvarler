package com.jpd;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.util.ArrayList;
import java.util.List;

/** Arg parsing wrapper. */
public class ArgParser {

    /** Private constructor. */
    private ArgParser() {
    }

    /** Helper class, get the args from our arg parser. */
    protected static class Args {

        /** Config files. */
        private final List<String> configs;

        /** Any overrides. */
        private final List<String> overrides;

        /** Allow destination rendering in parallel.
         * This assumes there are no dependencies b/n
         * *any* templates.
         * <p>
         * Should this assumption change, the solution
         * is *not* to simply disable this, rather raise
         * and let's think about a solution.
         * <p>
         * Setting this to `false` should be thought of
         * as a development and troubleshooting sanity
         * inducer.
         */
        private final Boolean allowParallel;

        /** The destinations file. */
        private final String destinations;

        /** The exports file. */
        private final String exports;

        /** Previous vars.json. Used to chain JVarler invocations. */
        private final String varsJson;

        /** Constructor. */
        private Args(
            List<String> configs, List<String> overrides, String destinations,
            String exports, String varsJson, Boolean allowParallel) {
            this.configs = configs == null ? new ArrayList<>() : configs;
            this.overrides = overrides == null ? new ArrayList<>() : overrides;
            this.destinations = destinations == null ? "" : destinations;
            this.exports = exports == null ? "" : exports;
            this.varsJson = varsJson == null ? "" : varsJson;
            this.allowParallel = allowParallel;
        }

        /** Get from arg parser namespace. */
        private static Args fromNamespace(Namespace namespace) {
            return new Args(
                namespace.getList("configs"),
                namespace.getList("overrides"),
                namespace.get("destinations"),
                namespace.get("exports"),
                namespace.get("varsJson"),
                namespace.get("allowParallel"));
        }

        /** Configs getter. */
        public List<String> getConfigs() {
            return configs;
        }

        /** Overrides getter. */
        public List<String> getOverrides() {
            return overrides;
        }

        /** Get allow parallel. */
        public Boolean getAllowParallel() {
            return allowParallel;
        }

        /** Get destinations. */
        public String getDestinations() {
            return destinations;
        }

        /** Get exports. */
        public String getExports() {
            return exports;
        }

        /** Get vars json. */
        public String getVarsJson() {
            return varsJson;
        }
    }

    /** Build argument parser. */
    private static ArgumentParser buildArgumentParser() {
        ArgumentParser argumentParser = ArgumentParsers.newFor("JVarlerMain")
            .build()
            .defaultHelp(true)
            .description("JVarler implementation.");
        argumentParser.addArgument("-c", "--configs")
            .help("The configs(s) used to source the variables from. "
                + "Multiple args to flag result in overrides from "
                + "left to right.")
            .required(true)
            .nargs("+");
        argumentParser.addArgument("-o", "--overrides")
            .help("Override individual variables, or indeed add some.")
            .required(false)
            .nargs("+");
        argumentParser.addArgument("-d", "--destinations")
            .help("Specifies which files to read and where to write them to.")
            .required(true);
        argumentParser.addArgument("-e", "--exports")
            .help("Specifies which path to write exports to.")
            .required(true);
        argumentParser.addArgument("-j", "--varsJson")
            .help("Vars JSON to build on (use to chain JVarler).");
        argumentParser.addArgument("-p", "--allowParallel")
            .help("Allow parallelisation of destination rendering (default=true).")
            .setDefault(true)
            .required(false);
        return argumentParser;
    }

    /** Outside callable, parse args from input. */
    public static Args parseArgs(String[] inputArgs) {
        ArgumentParser argumentParser = buildArgumentParser();
        try {
            Namespace namespace = argumentParser.parseArgs(inputArgs);
            return Args.fromNamespace(namespace);
        } catch (ArgumentParserException ex) {
            throw new RuntimeException(ex);
        }
    }
}
