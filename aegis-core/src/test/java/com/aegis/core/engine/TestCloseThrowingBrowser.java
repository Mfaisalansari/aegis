package com.aegis.core.engine;

import com.aegis.core.browser.Browser;
import com.aegis.model.observation.AnomalySignal;
import com.aegis.model.observation.ElementInfo;

import java.util.List;

/**
 * Test-only {@link Browser} fake whose {@link #close()} always throws —
 * used by {@link EngineFactoryResourceLeakTest} to prove a close()
 * failure during cleanup doesn't replace/mask whatever originally
 * triggered that cleanup (code-review follow-up to the Stage 5 leak fix).
 */
class TestCloseThrowingBrowser implements Browser {

    @Override
    public void launch() {
    }

    @Override
    public void close() {
        throw new RuntimeException("simulated close failure");
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
