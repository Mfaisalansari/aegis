package com.aegis.web.view;

/** Shared HTML shell — inline CSS only, no external assets, same self-contained convention as the HTML mission report generator. */
public final class Layout {

    private Layout() {
    }

    /** Real, currently-executing-mission count vs. the configured pool size — backs the sidebar's "Worker pool" widget. Omit (see {@link #page(String, String, String)}) on pages that don't have a {@code MissionExecutor} to read this from. */
    public record WorkerPoolStatus(int active, int total) {
    }

    public static String page(String title, String bodyHtml) {
        return page(title, null, bodyHtml);
    }

    /** @param activeNav "dashboard", "runs", "coverage-map", or null when the current page isn't a nav destination (e.g. a mission detail page). */
    public static String page(String title, String activeNav, String bodyHtml) {
        return page(title, activeNav, null, bodyHtml);
    }

    /** Same as {@link #page(String, String, String)}, plus real worker-pool numbers for the sidebar widget — only {@code DashboardHandler} currently has a {@code MissionExecutor} to read these from. */
    public static String page(String title, String activeNav, WorkerPoolStatus workerPool, String bodyHtml) {
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<title>" + escapeHtml(title) + "</title>"
                + "<style>" + CSS + "</style></head><body>"
                + "<aside class=\"sidebar\">"
                + "<div class=\"brand\"><div class=\"brand-mark\"></div>"
                + "<div><div class=\"brand-name\">AEGIS</div><div class=\"brand-tag\">v1.0 &middot; agentic QA</div></div></div>"
                + "<nav class=\"side-nav\">"
                + navLink("/", "Overview", "dashboard".equals(activeNav))
                + navLink("/runs", "Runs", "runs".equals(activeNav))
                + navLink("/coverage-map", "Coverage map", "coverage-map".equals(activeNav))
                + navLinkInactive("Findings")
                + navLinkInactive("Knowledge")
                + "</nav>"
                + workerPoolWidget(workerPool)
                + "</aside>"
                + "<div class=\"main-col\">"
                + "<div class=\"topbar\">"
                + "<a class=\"search-bar\" href=\"/run\"><span class=\"search-dot\"></span>Describe a mission or search runs</a>"
                + "<a class=\"btn-newrun\" href=\"/run\">New run</a>"
                + "<div class=\"avatar\"></div>"
                + "</div>"
                + "<main class=\"main\">" + bodyHtml + "</main>"
                + "</div>"
                + "</body></html>";
    }

    private static String navLink(String href, String label, boolean active) {
        return "<a class=\"nav-link" + (active ? " active" : "") + "\" href=\"" + href + "\">"
                + "<span class=\"nav-dot\"></span>" + escapeHtml(label) + "</a>";
    }

    /** Coverage map / Knowledge — no real page exists yet (nothing today builds a page/site graph or a cross-run knowledge browser); shown dimmed rather than linking to a fabricated screen. */
    private static String navLinkInactive(String label) {
        return "<span class=\"nav-link inactive\"><span class=\"nav-dot\"></span>" + escapeHtml(label) + "</span>";
    }

    private static String workerPoolWidget(WorkerPoolStatus pool) {

        if (pool == null) {
            return "";
        }

        int percent = pool.total() == 0 ? 0 : (int) Math.round(100.0 * pool.active() / pool.total());

        return "<div class=\"worker-pool\"><div class=\"wp-label\">Worker pool</div>"
                + "<div class=\"wp-value\"><span class=\"wp-active\">" + pool.active() + "</span>"
                + "<span class=\"wp-total\">of " + pool.total() + " busy</span></div>"
                + "<div class=\"wp-track\"><div class=\"wp-fill\" style=\"width:" + percent + "%\"></div></div>"
                + "</div>";
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
                --bg:#0e0d10; --sidebar-bg:#0e0d10; --panel:rgba(255,255,255,0.04); --panel-alt:rgba(255,255,255,0.06);
                --border:rgba(255,255,255,0.09); --border-soft:rgba(255,255,255,0.14);
                --text:#f4f4f6; --text-dim:rgba(255,255,255,0.65); --text-mute:rgba(255,255,255,0.45);
                --accent:#ec3013; --accent-grad:linear-gradient(140deg,#ff7a5c,#ec3013); --accent-soft:rgba(255,122,92,0.14); --accent-text:#ffffff;
                --ok:#6ee7c0; --ok-bg:rgba(110,231,192,0.12);
                --warn:#ffb020; --warn-bg:rgba(255,176,32,0.14);
                --err:#ff6b57; --err-bg:rgba(255,107,87,0.14);
                --radius:16px; --radius-sm:11px;
            }
            * { box-sizing: border-box; }
            body {
                margin: 0; display: flex; min-height: 100vh; background: var(--bg); color: var(--text);
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; -webkit-font-smoothing: antialiased;
            }
            code, .mono { font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace; }

            .sidebar { width: 212px; flex: none; background: var(--sidebar-bg); border-right: 1px solid var(--border); padding: 18px 14px; display: flex; flex-direction: column; gap: 22px; }
            .brand { display: flex; align-items: center; gap: 10px; padding: 0 6px; }
            .brand-mark { width: 26px; height: 26px; border-radius: 9px; background: var(--accent-grad); box-shadow: 0 6px 18px rgba(236,48,19,0.45); flex: none; }
            .brand-name { font-weight: 700; font-size: 15px; letter-spacing: 0.02em; }
            .brand-tag { font-size: 10px; color: var(--text-mute); font-family: ui-monospace, monospace; margin-top: 1px; }

            .side-nav { display: flex; flex-direction: column; gap: 4px; }
            .nav-link { display: flex; align-items: center; gap: 9px; padding: 9px 11px; border-radius: 10px; font: 500 13px -apple-system, sans-serif; color: var(--text-dim); text-decoration: none; }
            .nav-link:hover { color: var(--text); background: var(--panel-alt); }
            .nav-link.active { background: var(--panel-alt); color: #fff; font-weight: 600; }
            .nav-link.inactive { color: var(--text-mute); cursor: default; }
            .nav-dot { width: 6px; height: 6px; border-radius: 2px; background: rgba(255,255,255,0.25); flex: none; }
            .nav-link.active .nav-dot { background: #ff563c; }

            .worker-pool { margin-top: auto; padding: 14px; border-radius: 14px; background: var(--panel); border: 1px solid var(--border); }
            .wp-label { font: 600 11px -apple-system, sans-serif; color: var(--text-dim); }
            .wp-value { display: flex; align-items: flex-end; gap: 8px; margin-top: 8px; }
            .wp-active { font: 700 26px/1 -apple-system, sans-serif; }
            .wp-total { font: 400 11px ui-monospace, monospace; color: var(--text-mute); padding-bottom: 2px; }
            .wp-track { height: 6px; border-radius: 999px; background: rgba(255,255,255,0.1); margin-top: 10px; overflow: hidden; }
            .wp-fill { height: 100%; border-radius: 999px; background: var(--accent-grad); }

            .main-col { flex: 1; min-width: 0; display: flex; flex-direction: column; }
            .topbar { padding: 16px 20px; display: flex; align-items: center; gap: 12px; border-bottom: 1px solid var(--border); }
            .search-bar { flex: 1; display: flex; align-items: center; gap: 9px; padding: 9px 13px; border-radius: var(--radius-sm); background: var(--panel-alt); border: 1px solid var(--border); font: 400 12.5px ui-monospace, monospace; color: var(--text-mute); text-decoration: none; }
            .search-bar:hover { color: var(--text-dim); border-color: var(--border-soft); }
            .search-dot { width: 5px; height: 5px; border-radius: 999px; background: var(--text-mute); flex: none; }
            .btn-newrun { padding: 10px 16px; border-radius: var(--radius-sm); background: var(--accent-grad); color: #fff; font: 700 13px -apple-system, sans-serif; text-decoration: none; box-shadow: 0 8px 20px rgba(236,48,19,0.35); white-space: nowrap; }
            .btn-newrun:hover { filter: brightness(1.08); }
            .avatar { width: 32px; height: 32px; border-radius: 999px; background: rgba(255,255,255,0.1); border: 1px solid var(--border-soft); flex: none; }

            .main { flex: 1; min-width: 0; padding: 20px 24px 56px; max-width: 1180px; width: 100%; }

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
                border-radius: 9px; padding: 9px 12px; font: 400 13px ui-monospace, "SF Mono", Menlo, monospace;
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
                border: solid #fff; border-width: 0 2px 2px 0; transform: rotate(45deg);
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
                display: inline-block; margin-top: 22px; background: var(--accent-grad); color: #fff; border: none;
                padding: 12px 26px; border-radius: 8px; font: 600 14px -apple-system, sans-serif; cursor: pointer; text-decoration: none;
            }
            button[type=submit]:hover, .btn-primary:hover { filter: brightness(1.08); }
            .btn-secondary { display: inline-block; padding: 8px 16px; border-radius: 7px; background: var(--panel-alt); border: 1px solid var(--border-soft); color: var(--text); font: 500 12.5px -apple-system, sans-serif; text-decoration: none; margin: 0 8px 8px 0; }
            .btn-secondary:hover { border-color: var(--accent); color: #ff8f78; }

            .errors { background: var(--err-bg); border: 1px solid var(--err); color: var(--err); border-radius: var(--radius); padding: 12px 18px; margin-bottom: 20px; font-size: 13px; }
            .errors ul { margin: 6px 0 0; padding-left: 20px; }
            .notice { background: var(--accent-soft); border: 1px solid var(--accent); color: #ff8f78; border-radius: var(--radius); padding: 12px 18px; margin-bottom: 20px; font-size: 13px; }

            .dash-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 26px; }
            .dashboard-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 24px; }
            .stat-tile { background: var(--panel); border: 1px solid var(--border); border-radius: var(--radius); padding: 16px 18px; }
            .stat-label { font-size: 11.5px; color: var(--text-mute); }
            .stat-value { font-size: 24px; font-weight: 700; margin-top: 6px; }

            .legend { display: flex; align-items: center; gap: 14px; margin-bottom: 24px; font: 400 11.5px ui-monospace, monospace; color: var(--text-mute); }
            .legend-swatch { display: inline-flex; align-items: center; gap: 6px; }
            .legend-dot { width: 8px; height: 8px; border-radius: 2px; }
            .legend-dot.low { background: var(--err); }
            .legend-dot.mid { background: var(--warn); }
            .legend-dot.high { background: var(--ok); }

            .site-group { margin-bottom: 30px; }
            .site-group:last-child { margin-bottom: 0; }
            .site-head { display: flex; align-items: center; gap: 14px; padding-bottom: 12px; margin-bottom: 14px; border-bottom: 1px solid var(--border); }
            .site-name { font: 700 16px -apple-system, sans-serif; }
            .site-avg { margin-left: auto; display: flex; align-items: baseline; gap: 6px; }
            .site-avg-value { font: 700 20px -apple-system, sans-serif; }
            .site-avg-value.low { color: var(--err); }
            .site-avg-value.mid { color: var(--warn); }
            .site-avg-value.high { color: var(--ok); }
            .site-avg-label { font: 400 11px ui-monospace, monospace; color: var(--text-mute); }
            .site-meta { font: 400 11.5px ui-monospace, monospace; color: var(--text-mute); }

            .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
            @media (max-width: 980px) { .grid { grid-template-columns: repeat(2, 1fr); } }

            .tile { padding: 16px; border-radius: var(--radius); background: var(--panel); border: 1px solid var(--border); border-left: 3px solid var(--border); }
            .tile.low { border-left-color: var(--err); }
            .tile.mid { border-left-color: var(--warn); }
            .tile.high { border-left-color: var(--ok); }
            .tile-path { font: 600 13.5px ui-monospace, monospace; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .tile-stat-row { display: flex; align-items: baseline; gap: 8px; margin-top: 12px; }
            .tile-pct { font: 700 30px/1 -apple-system, sans-serif; }
            .tile.low .tile-pct { color: var(--err); }
            .tile.mid .tile-pct { color: var(--warn); }
            .tile.high .tile-pct { color: var(--ok); }
            .tile-elements { font: 400 11px ui-monospace, monospace; color: var(--text-mute); padding-bottom: 4px; }
            .tile-track { height: 5px; border-radius: 999px; background: rgba(255,255,255,0.08); margin-top: 12px; overflow: hidden; }
            .tile-fill { height: 100%; border-radius: 999px; }
            .tile.low .tile-fill { background: var(--err); }
            .tile.mid .tile-fill { background: var(--warn); }
            .tile.high .tile-fill { background: var(--ok); }
            .tile-meta { display: flex; justify-content: space-between; margin-top: 10px; font: 400 11px ui-monospace, monospace; color: var(--text-mute); }

            .bento { display: grid; grid-template-columns: 1.6fr 1fr 1fr; grid-auto-rows: min-content; gap: 14px; }
            @media (max-width: 900px) { .bento { grid-template-columns: 1fr; } }
            .bento-card { padding: 16px; border-radius: var(--radius); background: var(--panel); border: 1px solid var(--border); }
            .bento-card.span2 { grid-column: span 2; }
            @media (max-width: 900px) { .bento-card.span2 { grid-column: span 1; } }
            .bento-title { font: 600 11.5px -apple-system, sans-serif; color: var(--text-dim); }
            .bento-stat-row { display: flex; align-items: flex-end; gap: 8px; margin-top: 8px; }
            .bento-stat { font: 700 34px/1 -apple-system, sans-serif; }
            .bento-stat.alert { color: #ff8f78; }
            .bento-delta { font: 600 11.5px -apple-system, sans-serif; color: var(--ok); padding-bottom: 4px; }
            .bento-sub { font: 400 11px ui-monospace, monospace; color: var(--text-mute); padding-bottom: 5px; }

            .sparkline { display: flex; align-items: flex-end; gap: 3px; height: 56px; margin-top: 14px; }
            .spark-bar { flex: 1; border-radius: 3px 3px 0 0; background: rgba(255,255,255,0.14); min-height: 3px; }
            .spark-bar.recent { background: var(--accent-grad); }

            .findings-rows { margin-top: 14px; display: flex; flex-direction: column; gap: 6px; }
            .findings-row { display: flex; justify-content: space-between; font: 400 11.5px ui-monospace, monospace; color: var(--text-dim); }

            .bento-card-head { display: flex; align-items: baseline; justify-content: space-between; }
            .bento-card-title { font: 600 13px -apple-system, sans-serif; }
            .view-all { font: 600 11.5px -apple-system, sans-serif; color: #ff8f78; text-decoration: none; }

            .runs-rows { margin-top: 10px; display: flex; flex-direction: column; gap: 8px; }
            .run-row2 { display: grid; grid-template-columns: 38px 1fr 118px 92px 76px; align-items: center; gap: 12px; padding: 10px 12px; border-radius: 12px; background: rgba(255,255,255,0.03); border: 1px solid var(--border); text-decoration: none; color: inherit; }
            .run-row2:hover { background: var(--panel-alt); }
            .coverage-ring { width: 34px; height: 34px; border-radius: 999px; }
            .coverage-ring.pulse { animation: pulse 1.2s ease-in-out infinite; }
            .run-row2-name { font: 600 13px -apple-system, sans-serif; }
            .run-row2-target { font: 400 11px ui-monospace, monospace; color: var(--text-mute); margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .run-row2-meta { font: 400 11.5px ui-monospace, monospace; color: var(--text-dim); }
            .run-row2-ago { font: 400 11.5px ui-monospace, monospace; color: var(--text-mute); text-align: right; }

            .empty-tile { display: flex; flex-direction: column; align-items: flex-start; justify-content: center; gap: 10px; height: 100%; min-height: 200px; }
            .empty-tile p { margin: 0; color: var(--text-mute); font-size: 13px; }

            .run-list { display: flex; flex-direction: column; gap: 8px; }
            .run-row { display: grid; grid-template-columns: 1fr auto auto auto; align-items: center; gap: 16px;
                background: var(--panel); border: 1px solid var(--border); border-radius: var(--radius); }
            .run-row { padding: 14px 18px; text-decoration: none; color: inherit; }
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
            .badge.running { background: var(--ok-bg); color: var(--ok); }
            .badge.running .dot { animation: pulse 1.2s ease-in-out infinite; }
            .badge.success { background: var(--ok-bg); color: var(--ok); }
            .badge.failed, .badge.error { background: var(--err-bg); color: var(--err); }
            .badge.partial { background: var(--warn-bg); color: var(--warn); }

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

            .stream-card { grid-row: span 2; height: 420px; display: flex; flex-direction: column; min-height: 0; background: linear-gradient(160deg, rgba(255,122,92,0.14), rgba(255,255,255,0.03)); }
            .stream-head { display: flex; align-items: center; gap: 10px; }
            .live-pill { display: inline-flex; align-items: center; gap: 7px; padding: 5px 11px; border-radius: 999px; background: var(--ok-bg); border: 1px solid rgba(110,231,192,0.3); color: var(--ok); font: 600 11px -apple-system, sans-serif; }
            .live-pill .dot { width: 6px; height: 6px; border-radius: 999px; background: var(--ok); animation: pulse 1.2s ease-in-out infinite; }
            .stream-meta { font: 400 11.5px ui-monospace, monospace; color: var(--text-mute); }
            .stream-pause { margin-left: auto; padding: 7px 12px; border-radius: 9px; background: var(--panel-alt); border: 1px solid var(--border-soft); color: #fff; font: 600 11.5px -apple-system, sans-serif; cursor: pointer; white-space: nowrap; flex: none; }
            .stream-pause:hover { background: rgba(255,255,255,0.12); }
            .stream-title { font: 700 21px/1.15 -apple-system, sans-serif; margin-top: 12px; }
            .stream-target { font: 400 12px ui-monospace, monospace; color: var(--text-mute); margin-top: 4px; }
            .stream-events { margin-top: 14px; display: flex; flex-direction: column-reverse; gap: 8px; overflow: auto; min-height: 0; padding-right: 4px; }
            .stream-event { padding: 11px 12px; border-radius: 12px; background: var(--panel-alt); border: 1px solid var(--border); }
            .stream-event-head { display: flex; align-items: center; gap: 8px; }
            .stream-event-kind { padding: 3px 8px; border-radius: 999px; background: rgba(255,255,255,0.07); font: 600 9.5px -apple-system, sans-serif; letter-spacing: 0.08em; color: var(--text-dim); }
            .stream-event-kind.EXECUTE { color: var(--ok); }
            .stream-event-kind.REASON { color: #ffcf9e; }
            .stream-event-time { font: 400 10.5px ui-monospace, monospace; color: var(--text-mute); margin-left: auto; }
            .stream-event-headline { font: 600 13px -apple-system, sans-serif; margin-top: 7px; }
            .stream-event-detail { font: 400 12px/1.5 -apple-system, sans-serif; color: var(--text-dim); margin-top: 2px; word-break: break-word; }
            .stream-empty { color: var(--text-mute); font-size: 13px; }

            body.is-parsing .sidebar, body.is-parsing .main { pointer-events: none; opacity: 0.4; filter: blur(1px); user-select: none; }
            .parsing-overlay { position: fixed; inset: 0; background: rgba(10,11,13,0.6); backdrop-filter: blur(2px);
                display: none; align-items: center; justify-content: center; z-index: 100; }
            .parsing-overlay.active { display: flex; }
            .parsing-box { background: var(--panel); border: 1px solid var(--border); border-radius: var(--radius);
                padding: 26px 34px; min-width: 260px; text-align: center; }
            .parsing-box p { margin: 14px 0 0; font: 500 13px -apple-system, sans-serif; color: var(--text-dim); }
            .progress-track { height: 6px; width: 100%; border-radius: 999px; background: var(--panel-alt); overflow: hidden; }
            .progress-fill { height: 100%; width: 40%; border-radius: 999px; background: var(--accent-grad);
                animation: progress-indeterminate 1.1s ease-in-out infinite; }
            @keyframes progress-indeterminate { 0% { margin-left: -40%; } 100% { margin-left: 100%; } }
            """;
}
