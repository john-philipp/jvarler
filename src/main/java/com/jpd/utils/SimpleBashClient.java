package com.jpd.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.jpd.utils.JVarlerUtils.f;

/** Simple bash client. Specify a command, run it.
 * No imports, no variables.
 */
public class SimpleBashClient {

    /** A logger. */
    private final Logger log = LoggingUtils.getLogger(getClass());

    /** My working dir. */
    private final String workingDir;

    /** Constructor. */
    public SimpleBashClient(String workingDir) {
        this.workingDir = workingDir;
    }

    /** Run a command. */
    public String run(String command, Object... objs) {
        command = String.format(command, objs);
        log.info(f("Running shell: %s", command));
        Process process = buildAndRun(command);
        BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        int returnCode = process.exitValue();
        if (returnCode != 0) {
            throw new RuntimeException();
        }
        List<String> output = outputReader.lines().collect(Collectors.toList());
        return String.join("\n", output);
    }

    /** Inner build and run. */
    private Process buildAndRun(String command) {
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", command);
        builder.inheritIO();
        builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        builder.directory(new File(workingDir));
        try {
            return builder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
