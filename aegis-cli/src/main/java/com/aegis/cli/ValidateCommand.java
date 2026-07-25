package com.aegis.cli;

import com.aegis.api.AegisConfig;
import com.aegis.api.AegisConfigException;
import com.aegis.api.AegisConfigLoader;
import com.aegis.api.EnterpriseConfig;
import com.aegis.api.EnterpriseConfigLoader;

import java.nio.file.Path;

/**
 * {@code aegis validate --config <path> [--env <name>] [--mission <name>]}
 *
 * A pure static check — parses and resolves the config exactly like
 * {@code aegis run} would, but never launches a browser or runs a
 * mission. Useful as a fast CI pre-flight step before the real run.
 */
final class ValidateCommand {

    private ValidateCommand() {
    }

    static int run(String[] args) {

        String configPath = null;
        String environment = null;
        String missionName = null;

        int i = 0;
        while (i < args.length) {

            String flag = args[i];

            if (!flag.equals("--config") && !flag.equals("--env") && !flag.equals("--mission")) {
                return fail("Unknown flag: " + flag);
            }

            if (i + 1 >= args.length) {
                return fail("Missing value for " + flag);
            }

            String value = args[i + 1];

            switch (flag) {
                case "--config" -> configPath = value;
                case "--env" -> environment = value;
                case "--mission" -> missionName = value;
            }

            i += 2;
        }

        if (configPath == null) {
            return fail("Missing required --config <path>");
        }

        Path path = Path.of(configPath);

        try {

            AegisConfig resolved;

            if (EnterpriseConfigLoader.isEnterpriseShaped(path)) {

                if (environment == null || missionName == null) {
                    return fail("This config defines environments/missions — pass both --env <name> and --mission <name>");
                }

                EnterpriseConfig enterpriseConfig = EnterpriseConfigLoader.load(path);
                resolved = enterpriseConfig.resolve(environment, missionName);

            } else {
                resolved = AegisConfigLoader.load(path);
            }

            printSummary(resolved);
            return 0;

        } catch (AegisConfigException e) {
            return fail(e.getMessage());
        }
    }

    private static void printSummary(AegisConfig config) {

        System.out.println("Config OK");
        System.out.println();
        System.out.println("application.baseUrl            : " + describe(config.application().baseUrl()));
        System.out.println("application.username            : " + describe(config.application().username()));
        System.out.println("application.password            : " + (config.application().password() == null ? "<not set>" : "<redacted>"));
        System.out.println("application.successUrlContains  : " + describe(config.application().successUrlContains()));
        System.out.println("browser.type                    : " + config.browser().type());
        System.out.println("browser.headless                : " + config.browser().headless());
        System.out.println("mission.strategy                : " + describe(config.mission().strategy()));
        System.out.println("mission.maxIterations            : " + describe(config.mission().maxIterations() == null ? null : String.valueOf(config.mission().maxIterations())));
        System.out.println("report.directory                 : " + config.report().directory());
    }

    private static String describe(String value) {
        return value == null ? "<not set>" : value;
    }

    private static int fail(String message) {
        System.err.println("Error: " + message);
        printUsage();
        return 1;
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  aegis validate --config <path> [--env <name>] [--mission <name>]

                Resolves the config and prints a summary — no browser is launched
                and no mission is run.
                """);
    }
}
