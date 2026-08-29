package com.aegis.web;

/** Process entry point: {@code java -jar aegis-web.jar [--port <n>]}. */
public final class WebMain {

    private WebMain() {
    }

    public static void main(String[] args) {

        int port = resolvePort(args);
        int concurrency = resolveConcurrency();
        String historyDirectory = resolveHistoryDirectory();
        String experienceDirectory = resolveExperienceDirectory();

        WebServer server = new WebServer(port, concurrency, historyDirectory, experienceDirectory);
        server.start();

        System.out.println("AEGIS Web UI running at http://localhost:" + server.port());
        System.out.println("Mission concurrency: " + concurrency);
        System.out.println("Mission history directory: " + historyDirectory);
        System.out.println("Mission experience directory: " + experienceDirectory);
    }

    private static int resolvePort(String[] args) {

        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equals(args[i])) {
                return Integer.parseInt(args[i + 1]);
            }
        }

        String env = System.getenv("AEGIS_WEB_PORT");
        return env != null ? Integer.parseInt(env) : 8080;
    }

    private static int resolveConcurrency() {
        String env = System.getenv("AEGIS_WEB_CONCURRENCY");
        return env != null ? Integer.parseInt(env) : 2;
    }

    private static String resolveHistoryDirectory() {
        String env = System.getenv("AEGIS_WEB_HISTORY_DIR");
        return env != null ? env : "mission-history";
    }

    private static String resolveExperienceDirectory() {
        String env = System.getenv("AEGIS_WEB_EXPERIENCE_DIR");
        return env != null ? env : "experience-history";
    }
}
