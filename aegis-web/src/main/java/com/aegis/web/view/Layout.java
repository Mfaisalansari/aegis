package com.aegis.web.view;

/** Shared HTML shell — inline CSS only, no external assets, same self-contained convention as the HTML mission report generator. */
public final class Layout {

    private Layout() {
    }

    public static String page(String title, String bodyHtml) {
        return page(title, null, bodyHtml);
    }

    /** @param activeNav "dashboard", "run", or null when the current page isn't a nav destination (e.g. a mission detail page). */
    public static String page(String title, String activeNav, String bodyHtml) {
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<title>" + escapeHtml(title) + "</title>"
                + "<style>" + CSS + "</style></head><body>"
                + "<aside class=\"sidebar\"><div class=\"brand\">"
                + "<div class=\"brand-mark\"><span></span></div>"
                + "<div class=\"brand-name\">AEGIS</div></div>"
                + "<p class=\"brand-tag\">Run a mission from your browser</p>"
                + "<nav class=\"side-nav\">"
                + navLink("/", "Dashboard", "dashboard".equals(activeNav))
                + navLink("/run", "New Run", "run".equals(activeNav))
                + "</nav>"
                + "</aside>"
                + "<main class=\"main\">" + bodyHtml + "</main>"
                + "</body></html>";
    }

    private static String navLink(String href, String label, boolean active) {
        return "<a class=\"nav-link" + (active ? " active" : "") + "\" href=\"" + href + "\">" + escapeHtml(label) + "</a>";
    }

    public static String escapeHtml(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static final String CSS = """
            :root {
                --bg:#14161a; --sidebar-bg:#101114; --panel:#1b1e23; --panel-alt:#191c21;
                --border:#2a2d34; --border-soft:#33363e; --text:#edeff2; --text-dim:#9aa0ab; --text-mute:#676c76;
                --accent:#2dd4bf; --accent-text:#0b1210;
                --ok:#4ade80; --ok-bg:rgba(74,222,128,0.14);
                --warn:#fbbf24; --warn-bg:rgba(251,191,36,0.14);
                --err:#f87171; --err-bg:rgba(248,113,113,0.14);
                --radius:10px;
            }
            * { box-sizing: border-box; }
            body {
                margin: 0; display: flex; min-height: 100vh; background: var(--bg); color: var(--text);
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; -webkit-font-smoothing: antialiased;
            }
            code, .mono { font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace; }

            .sidebar { width: 220px; flex: none; background: var(--sidebar-bg); border-right: 1px solid var(--border); padding: 28px 20px; }
            .brand { display: flex; align-items: center; gap: 10px; }
            .brand-mark { width: 26px; height: 26px; border-radius: 7px; background: var(--accent); flex: none; display: flex; align-items: center; justify-content: center; }
            .brand-mark span { width: 9px; height: 9px; border-radius: 2px; background: var(--accent-text); }
            .brand-name { font-weight: 700; font-size: 16px; letter-spacing: 0.02em; }
            .brand-tag { font-size: 12.5px; color: var(--text-mute); margin: 6px 0 0 36px; line-height: 1.4; }

            .side-nav { display: flex; flex-direction: column; gap: 2px; margin-top: 26px; }
            .nav-link { display: block; padding: 9px 12px; border-radius: 7px; font: 500 13.5px -apple-system, sans-serif; color: var(--text-dim); text-decoration: none; }
            .nav-link:hover { color: var(--text); background: var(--panel-alt); }
            .nav-link.active { background: rgba(45,212,191,0.14); color: var(--accent); }

            .main { flex: 1; min-width: 0; padding: 40px 48px 80px; max-width: 900px; }

            h1 { font-size: 22px; margin: 0 0 6px; letter-spacing: -0.01em; }
            h2 { font-size: 15px; margin: 0 0 14px; }
            h3 { font-size: 11px; margin: 0 0 12px; color: var(--text-mute); text-transform: uppercase; letter-spacing: 0.05em; font-weight: 600; }
            .lede { font-size: 13.5px; color: var(--text-dim); line-height: 1.55; margin: 0 0 28px; max-width: 560px; }

            .card { background: var(--panel); border: 1px solid var(--border); border-radius: var(--radius); padding: 22px 24px; margin-bottom: 16px; }
            .subsection { padding: 18px 0; border-top: 1px solid var(--border-soft); }
            .subsection:first-child { border-top: none; padding-top: 4px; }

            label { display: block; font: 600 12px -apple-system, sans-serif; color: var(--text-dim); margin: 14px 0 7px; }
            label:first-child { margin-top: 0; }
            .help { font-size: 12px; line-height: 1.55; color: var(--text-mute); margin: 6px 0 0; }
            .help code { color: var(--accent); }

            input[type=text], input[type=password], input[type=number], select, textarea {
                width: 100%; box-sizing: border-box; background: var(--panel-alt); border: 1px solid var(--border-soft);
                border-radius: 7px; padding: 9px 12px; font: 400 13px ui-monospace, "SF Mono", Menlo, monospace;
                color: var(--text);
            }
            input:focus, select:focus, textarea:focus { outline: 1px solid var(--accent); border-color: var(--accent); }
            textarea { min-height: 74px; resize: vertical; font-size: 12.5px; }
            select { appearance: none; -webkit-appearance: none; background-image: linear-gradient(45deg, transparent 50%, var(--text-mute) 50%), linear-gradient(135deg, var(--text-mute) 50%, transparent 50%); background-position: calc(100% - 18px) center, calc(100% - 13px) center; background-size: 5px 5px, 5px 5px; background-repeat: no-repeat; padding-right: 32px; }

            .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
            .grid-3 { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 14px; align-items: end; }

            .toggle-row { display: flex; align-items: center; gap: 10px; cursor: pointer; padding: 8px 0; }
            .toggle-row input[type=checkbox] {
                appearance: none; -webkit-appearance: none; width: 16px; height: 16px; border-radius: 4px;
                border: 1px solid var(--border-soft); background: transparent; flex: none; cursor: pointer; position: relative; margin: 0;
            }
            .toggle-row input[type=checkbox]:checked { background: var(--accent); border-color: var(--accent); }
            .toggle-row input[type=checkbox]:checked::after {
                content: ""; position: absolute; left: 5px; top: 2px; width: 4px; height: 8px;
                border: solid var(--accent-text); border-width: 0 2px 2px 0; transform: rotate(45deg);
            }
            .toggle-row .toggle-label { font: 500 13.5px -apple-system, sans-serif; color: var(--text); }
            .toggle-row .toggle-hint { font-size: 12px; color: var(--text-mute); }

            details.more-settings summary { list-style: none; cursor: pointer; display: flex; align-items: center; justify-content: space-between; padding: 2px 0; font: 600 13.5px -apple-system, sans-serif; }
            details.more-settings summary::-webkit-details-marker { display: none; }
            details.more-settings summary::after { content: "+"; font: 600 16px ui-monospace, monospace; color: var(--text-mute); }
            details.more-settings[open] summary::after { content: "\\2212"; }
            details.more-settings .more-body { margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--border); }

            .llm-list { display: flex; flex-direction: column; gap: 6px; margin-top: 10px; }
            .llm-row { display: flex; justify-content: space-between; max-width: 420px; font: 400 12.5px ui-monospace, monospace; }
            .llm-row .set { color: var(--ok); }
            .llm-row .unset { color: var(--text-mute); }

            button[type=submit], .btn-primary {
                display: inline-block; margin-top: 22px; background: var(--accent); color: var(--accent-text); border: none;
                padding: 12px 26px; border-radius: 8px; font: 600 14px -apple-system, sans-serif; cursor: pointer; text-decoration: none;
            }
            button[type=submit]:hover, .btn-primary:hover { opacity: 0.92; }
            .btn-secondary { display: inline-block; padding: 8px 16px; border-radius: 7px; background: var(--panel-alt); border: 1px solid var(--border-soft); color: var(--text); font: 500 12.5px -apple-system, sans-serif; text-decoration: none; margin: 0 8px 8px 0; }
            .btn-secondary:hover { border-color: var(--accent); color: var(--accent); }

            .errors { background: var(--err-bg); border: 1px solid var(--err); color: var(--err); border-radius: var(--radius); padding: 12px 18px; margin-bottom: 20px; font-size: 13px; }
            .errors ul { margin: 6px 0 0; padding-left: 20px; }
            .notice { background: rgba(45,212,191,0.1); border: 1px solid var(--accent); color: var(--accent); border-radius: var(--radius); padding: 12px 18px; margin-bottom: 20px; font-size: 13px; }

            .dash-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 26px; }
            .dashboard-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 24px; }
            .stat-tile { background: var(--panel); border: 1px solid var(--border); border-radius: var(--radius); padding: 16px 18px; }
            .stat-label { font-size: 11.5px; color: var(--text-mute); }
            .stat-value { font-size: 24px; font-weight: 700; margin-top: 6px; }

            .run-list { display: flex; flex-direction: column; gap: 8px; }
            .run-row { display: grid; grid-template-columns: 1fr auto auto auto; align-items: center; gap: 16px;
                background: var(--panel); border: 1px solid var(--border); border-radius: var(--radius);
                padding: 14px 18px; text-decoration: none; color: inherit; }
            .run-row:hover { border-color: var(--border-soft); background: var(--panel-alt); }
            .run-name { font-size: 13.5px; font-weight: 600; color: var(--text); }
            .run-target { font-size: 12px; color: var(--text-mute); margin-top: 2px; }
            .run-duration { font-size: 12.5px; color: var(--text-dim); }
            .run-ago { font-size: 12px; color: var(--text-mute); white-space: nowrap; }

            .crumb-back { display: inline-block; font: 500 12.5px -apple-system, sans-serif; color: var(--text-mute); text-decoration: none; margin-bottom: 18px; }
            .crumb-back:hover { color: var(--text); }

            .detail-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 26px; }
            .detail-sub { display: flex; align-items: center; gap: 9px; font-size: 12.5px; color: var(--text-mute); margin-top: 6px; }
            .detail-sub .dot { width: 3px; height: 3px; border-radius: 50%; background: var(--text-mute); }

            @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.35; } }
            .badge { display: inline-flex; align-items: center; gap: 7px; padding: 5px 14px; border-radius: 20px; font: 600 12.5px -apple-system, sans-serif; flex: none; white-space: nowrap; }
            .badge .dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
            .badge.running { background: var(--warn-bg); color: var(--warn); }
            .badge.running .dot { animation: pulse 1.2s ease-in-out infinite; }
            .badge.success { background: var(--ok-bg); color: var(--ok); }
            .badge.failed, .badge.error { background: var(--err-bg); color: var(--err); }
            .badge.partial { background: rgba(251,146,60,0.16); color: #fb923c; }

            .detail-grid { display: grid; grid-template-columns: 1fr 260px; gap: 24px; align-items: start; }
            .timeline { display: flex; flex-direction: column; }
            .tl-item { display: flex; gap: 16px; padding-bottom: 20px; }
            .tl-marker { flex: none; display: flex; flex-direction: column; align-items: center; width: 26px; }
            .tl-num { width: 26px; height: 26px; border-radius: 50%; background: var(--panel-alt); border: 1px solid var(--border-soft); display: flex; align-items: center; justify-content: center; font: 600 11px ui-monospace, monospace; color: var(--text-dim); flex: none; }
            .tl-line { width: 1px; flex: 1; background: var(--border); margin-top: 4px; }
            .tl-card { flex: 1; min-width: 0; background: var(--panel); border: 1px solid var(--border); border-radius: var(--radius); padding: 13px 16px; font-size: 13px; line-height: 1.5; color: var(--text-dim); }

            .side-col { display: flex; flex-direction: column; gap: 14px; }
            .stat-row { display: flex; justify-content: space-between; font-size: 12.5px; padding: 5px 0; }
            .stat-row .k { color: var(--text-mute); }
            .stat-row .v { font-family: ui-monospace, monospace; color: var(--text); }

            body.is-parsing .sidebar, body.is-parsing .main { pointer-events: none; opacity: 0.4; filter: blur(1px); user-select: none; }
            .parsing-overlay { position: fixed; inset: 0; background: rgba(10,11,13,0.6); backdrop-filter: blur(2px);
                display: none; align-items: center; justify-content: center; z-index: 100; }
            .parsing-overlay.active { display: flex; }
            .parsing-box { background: var(--panel); border: 1px solid var(--border); border-radius: var(--radius);
                padding: 26px 34px; min-width: 260px; text-align: center; }
            .parsing-box p { margin: 14px 0 0; font: 500 13px -apple-system, sans-serif; color: var(--text-dim); }
            .progress-track { height: 6px; width: 100%; border-radius: 999px; background: var(--panel-alt); overflow: hidden; }
            .progress-fill { height: 100%; width: 40%; border-radius: 999px; background: var(--accent);
                animation: progress-indeterminate 1.1s ease-in-out infinite; }
            @keyframes progress-indeterminate { 0% { margin-left: -40%; } 100% { margin-left: 100%; } }
            """;
}
