package com.aegis.core.engine;

import com.aegis.core.browser.Browser;
import com.aegis.model.observation.AnomalySignal;
import com.aegis.model.observation.ElementInfo;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test-only {@link Browser} fake that just records whether {@link #close()}
 * was called — used by {@link EngineFactoryResourceLeakTest} to prove a
 * launched browser is still closed when something later in {@code
 * EngineFactory.create(...)} throws (Stage 5 hardening).
 */
class TestLeakProbeBrowser implements Browser {

    private final AtomicBoolean closed = new AtomicBoolean(false);

    boolean wasClosed() {
        return closed.get();
    }

    @Override
    public void launch() {
    }

    @Override
    public void close() {
        closed.set(true);
    }

    @Override
    public void navigate(String url) {
    }

    @Override
    public void refresh() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void goBack() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void click(String locator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void doubleClick(String locator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void raceClick(String locator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void type(String locator, String text) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void select(String locator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void scrollTo(String locator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getPageTitle() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCurrentUrl() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ElementInfo> getButtons() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ElementInfo> getInputs() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ElementInfo> getLinks() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ElementInfo> getSelects() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AnomalySignal> drainAnomalies() {
        return List.of();
    }
}
