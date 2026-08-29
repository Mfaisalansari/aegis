package com.aegis.core.stream;

/**
 * Receives {@link MissionStreamEvent}s as a mission runs — the hook a
 * caller (e.g. aegis-web's {@code MissionJob}) plugs in to observe a
 * mission live, without {@link com.aegis.core.Aegis#run} itself needing
 * to know anything about who's listening or why. {@link #NO_OP} is what
 * every existing {@code EngineFactory.create(...)}/{@code Aegis.run(...)}
 * overload passes implicitly, so a caller that doesn't care about live
 * events pays nothing extra — no decorators get wrapped in, see {@link
 * com.aegis.core.engine.EngineFactory}.
 */
@FunctionalInterface
public interface MissionEventListener {

    void onEvent(MissionStreamEvent event);

    MissionEventListener NO_OP = event -> { };
}
