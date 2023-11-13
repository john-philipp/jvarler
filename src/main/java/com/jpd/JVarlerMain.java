package com.jpd;

import com.jpd.jinjava.MyJinjavaInstanceFactory;
import com.jpd.jvarler.DestinationRenderer;
import com.jpd.jvarler.ConfigRenderer;
import com.jpd.utils.Config;
import com.jpd.utils.JVarlerUtils;
import com.jpd.utils.MetaFieldCollection;

import java.util.List;

import static com.jpd.utils.JVarlerUtils.ensureFileExists;
import static com.jpd.utils.JVarlerUtils.handleOverrides;
import static com.jpd.utils.JVarlerUtils.writeExports;

/** Java implementation of varler leveraging jinjava as underlying.
 * It is a subset of jinja2 as available in jinjava, but much faster,
 * not limited to a single thread during destination rendering,
 * and more easily maintainable.
 */
public class JVarlerMain {

    /** Meta key for overrides. */
    private final static String META_KEY_OVERRIDES = "overrides";

    /** Private constructor. */
    private JVarlerMain() {
    }

    /** Actual implementation. */
    public static void main(String[] inputArgs) {
        MetaFieldCollection metaFields;

        // Parse input args.
        ArgParser.Args args = ArgParser.parseArgs(inputArgs);

        // Check files actually exist.
        args.getConfigs().forEach(JVarlerUtils::ensureFileExists);
        ensureFileExists(args.getDestinations());

        // NB: in principle we can extend this, however,
        // in the previous implementation this never did
        // get used. Right now, this will fail for multiple
        // configs.
        if (args.getConfigs().size() > 1) {
            throw new RuntimeException("Only single configs supported for now.");
        }

        // Vars JSON is used to chain JVarler invocations.
        // I.e. use an exports file from a previous invocation.
        String varsJson = args.getVarsJson();
        if (!varsJson.isEmpty()) {
            ensureFileExists(varsJson);
        }

        // Keep track of special fields. Special fields
        // will be added to final config, e.g. overrides.
        metaFields = new MetaFieldCollection();

        // Compile and render final config.
        // NB: overrides only allowed at zeroth page atm.
        // Same as above, more functionality was available
        // in original implementation (page-specific overrides),
        // however, this never did see usage.
        List<String> overrides = args.getOverrides();
        ConfigRenderer configRenderer = ConfigRenderer.Builder.newInstance()
            .withJinjavaInstanceFactory(MyJinjavaInstanceFactory::newInstance)
            .withOverrides(handleOverrides(overrides))
            .withVarsJson(args.getVarsJson())
            .withConfigs(args.getConfigs())
            .build();
        configRenderer.render();

        // Include meta fields in config.
        Config config = configRenderer.getOutput();
        metaFields.put(META_KEY_OVERRIDES, String.join(" ", overrides));
        config.includeMetaFields(metaFields);

        // Load destinations file, render, write file(s).
        DestinationRenderer destinationRenderer = DestinationRenderer.Builder.newInstance()
            .withJinjavaInstanceFactory(MyJinjavaInstanceFactory::newInstance)
            .withTemplateInputPath(args.getDestinations())
            .withBindings(config.getMap())
            .allowParallel(true)
            .build();
        destinationRenderer.render();

        // Write final config to "exports" file.
        writeExports(args.getExports(), config.getMap());
    }
}
