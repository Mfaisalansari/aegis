package com.aegis.cli;

import com.aegis.api.AegisConfig;
import com.aegis.api.AegisConfigLoader;
import com.aegis.api.EnterpriseConfig;
import com.aegis.api.EnterpriseConfigLoader;
import com.aegis.api.Launcher;
import com.aegis.api.MissionBuilder;
import com.aegis.model.mission.Mission;
import com.aegis.model.mission.MissionStatus;

import java.nio.file.Path;

/**
 * Stage 3 CLI. Scope is deliberately just {@code run} — matching the
 * spec's own example ({@code aegis run --config production.yaml}) and
 * leaving {@code init}/{@code report}/{@code validate}/{@code doctor}
 * to Stage 4 ("Ecosystem"), a separate, later initiative.
 *
 * "Mission scheduling" (also Stage 3) is deliberately NOT an internal
 * daemon/scheduler — a CI/CD system (GitHub Actions cron trigger,
 * Jenkins cron, a k8s CronJob) already does that well. This class's job
 * is being a clean, one-shot, exit-code-driven command such a scheduler
 * can invoke — SUCCESS → 0, FAILED → 1, PARTIAL → 2, so a pipeline step
 * can act on the real mission outcome without parsing any output.
 */
public final class CliMain {

    private CliMain() {
    }

    public static void main(String[] args) {

        if (args.length == 0 || !"run".equals(args[0])) {
            printUsage();
            System.exit(1);
            return;
        }

        String configPath = null;
        String environment = null;
        String missionName = null;

        int i = 1;
        while (i < args.length) {

            String flag = args[i];

            if (!flag.equals("--config") && !flag.equals("--env") && !flag.equals("--mission")) {
                fail("Unknown flag: " + flag);
            }

            if (i + 1 >= args.length) {
                fail("Missing value for " + flag);
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
            fail("Missing required --config <path>");
        }

        MissionStatus status = run(Path.of(configPath), environment, missionName);

        System.exit(exitCodeFor(status));
    }

    private static MissionStatus run(Path configPath, String environment, String missionName) {

        if (EnterpriseConfigLoader.isEnterpriseShaped(configPath)) {

            if (environment == null || missionName == null) {
                fail("This config defines environments/missions — pass both --env <name> and --mission <name>");
            }

            EnterpriseConfig enterpriseConfig = EnterpriseConfigLoader.load(configPath);
            AegisConfig resolved = enterpriseConfig.resolve(environment, missionName);
            Mission mission = MissionBuilder.from(resolved).build();

            return Launcher.run(mission, resolved.browser(), resolved.report().directory());
        }

        AegisConfig config = AegisConfigLoader.load(configPath);
        Mission mission = MissionBuilder.from(config).build();

        return Launcher.run(mission, config.browser(), config.report().directory());
    }

    private static int exitCodeFor(MissionStatus status) {
        return switch (status) {
            case SUCCESS -> 0;
            case FAILED -> 1;
            case PARTIAL -> 2;
        };
    }

    private static void fail(String message) {
        System.err.println("Error: " + message);
        printUsage();
        System.exit(1);
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  aegis run --config <path> [--env <name>] [--mission <name>]

                --config   Path to an application.yml (single-mission shape) or an
                           enterprise config (environments:/missions: shape).
                --env      Environment profile name — required if --config is enterprise-shaped.
                --mission  Mission profile name — required if --config is enterprise-shaped.

                Exit code: 0 = SUCCESS, 1 = FAILED, 2 = PARTIAL.
                """);
    }
}
