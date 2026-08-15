package me.Lusik21556.skxloader;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.logging.Logger;

public class AgentDetector {

    private static final List<String> FLAGS = List.of(
            "-javaagent", "burpsuite", "frida", "byteman", "aspectj",
            "jacoco", "mockito", "bytebuddy", "byte-buddy-agent", "sa-jdi"
    );

    public static boolean present(Logger log) {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            String lower = arg.toLowerCase();
            for (String flag : FLAGS) {
                if (lower.contains(flag)) {
                    log.severe("suspicious JVM arg: " + arg);
                    return true;
                }
            }
        }
        return false;
    }
}
