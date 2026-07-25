package com.aegis.cli;

import java.util.Arrays;

/**
 * Entry point — dispatches to one of 5 subcommands. Stage 3 shipped only
 * {@code run}; Stage 4 ("Ecosystem") rounds it out with {@code init},
 * {@code validate}, {@code report}, {@code doctor}. Each subcommand is a
 * package-private class with its own {@code run(String[])} returning an
 * exit code (never calling {@code System.exit} itself), so subcommand
 * logic stays unit-testable independent of process termination.
 */
public final class CliMain {

    private CliMain() {
    }

    public static void main(String[] args) {

        if (args.length == 0) {
            printUsage();
            System.exit(1);
            return;
        }

        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        int exitCode = switch (args[0]) {
            case "run" -> RunCommand.run(rest);
            case "init" -> InitCommand.run(rest);
            case "validate" -> ValidateCommand.run(rest);
            case "report" -> ReportCommand.run(rest);
            case "doctor" -> DoctorCommand.run(rest);
            default -> {
                System.err.println("Unknown command: " + args[0]);
                printUsage();
                yield 1;
            }
        };

        System.exit(exitCode);
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  aegis run      --config <path> [--env <name>] [--mission <name>]
                  aegis init     <directory> [--name <appName>] [--base-url <url>]
                  aegis validate --config <path> [--env <name>] [--mission <name>]
                  aegis report   <path-to-json-report>
                  aegis doctor

                Run 'aegis <command>' with no further arguments to see that command's own usage.
                """);
    }
}
