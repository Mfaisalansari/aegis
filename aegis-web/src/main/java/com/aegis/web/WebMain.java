package com.aegis.web;

/** Process entry point: {@code java -jar aegis-web.jar [--port <n>]}. */
public final class WebMain {

    private WebMain() {
    }

    public static void main(String[] args) {

        int port = resolvePort(args);
        int concurrency = resolveConcurrency();

        WebServer server = new WebServer(port, concurrency);
        server.start();

        System.out.println("AEGIS Web UI running at http://localhost:" + server.port());
        System.out.println("Mission concurrency: " + concurrency);
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
}
