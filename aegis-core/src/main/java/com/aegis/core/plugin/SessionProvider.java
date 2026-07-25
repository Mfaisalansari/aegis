package com.aegis.core.plugin;

import com.aegis.model.mission.Mission;

import java.util.Optional;

/**
 * Stage 2 "Identity Integration": establishes a pre-authenticated session
 * before a mission's exploration begins — log in via an API, restore
 * cookies, reuse an existing browser profile, complete an SSO flow,
 * retrieve a stored session. Returns *data* ({@link AuthenticatedSession}),
 * never performs UI actions itself — AEGIS applies the returned session
 * to the browser (cookies/storage/headers/profile), then the autonomous
 * exploration engine takes over exactly as if the site had been visited
 * fresh and already logged in.
 *
 * Discovered via {@link java.util.ServiceLoader}. If multiple providers
 * are present, the first one to return a non-empty {@link Optional}
 * wins — ServiceLoader's discovery order isn't a hard guarantee, so a
 * setup relying on more than one provider being tried in a specific
 * order should account for that (or just run with exactly one provider
 * jar on the classpath, the common case).
 *
 * Return {@link Optional#empty()} (not an exception) when this provider
 * doesn't apply to the given mission — e.g. it only handles one specific
 * baseUrl and this mission targets a different one.
 */
public interface SessionProvider {

    Optional<AuthenticatedSession> createSession(Mission mission);
}
