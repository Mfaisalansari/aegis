package com.aegis.core.impl.observer;

import com.aegis.core.browser.Browser;
import com.aegis.core.observer.Observer;
import com.aegis.core.reasoning.memory.VisitedStateMemory;
import com.aegis.model.context.MissionContext;
import com.aegis.model.observation.ElementInfo;
import com.aegis.model.observation.Observation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DefaultObserver implements Observer {

    private static final Logger log = LoggerFactory.getLogger(DefaultObserver.class);

    private final Browser browser;
    private final VisitedStateMemory memory;

    private Observation previous;

    public DefaultObserver(Browser browser, VisitedStateMemory memory) {
        this.browser = browser;
        this.memory = memory;
    }

    @Override
    public void observe(MissionContext context) {

        List<ElementInfo> inputs = browser.getInputs();
        List<ElementInfo> buttons = browser.getButtons();
        List<ElementInfo> links = browser.getLinks();
        List<ElementInfo> selects = browser.getSelects();

        List<ElementInfo> elements = new ArrayList<>();

        elements.addAll(inputs);
        elements.addAll(buttons);
        elements.addAll(links);
        elements.addAll(selects);

        Observation observation = new Observation(

                browser.getCurrentUrl(),

                browser.getPageTitle(),

                elements,

                buttons,

                inputs,

                links,

                selects,

                Instant.now()

        );

        context.getExecutionState().setCurrentObservation(observation);

        logObservation(observation);

        memory.remember(observation);

        previous = observation;
    }

    private void logObservation(Observation observation) {

        boolean changedFromPrevious = previous == null
                || !previous.elements().equals(observation.elements());

        if (!changedFromPrevious) {
            log.info("Observed {} — no change", observation.url());
            return;
        }

        String stateNote = memory.hasVisitedState(observation)
                ? "revisiting a known state"
                : "new state";

        log.info("Observed {} — {}, {}, {}, {} ({})",
                observation.url(),
                count(observation.inputs().size(), "input"),
                count(observation.buttons().size(), "button"),
                count(observation.links().size(), "link"),
                count(observation.selects().size(), "select"),
                stateNote);

        if (log.isDebugEnabled()) {
            logDetail(observation);
        }
    }

    private String count(int amount, String singular) {
        return amount + " " + singular + (amount == 1 ? "" : "s");
    }

    private void logDetail(Observation observation) {

        for (ElementInfo input : observation.inputs()) {
            log.debug("  input  id={} name={} type={} value='{}' locator={}",
                    input.id(), input.name(), input.type(), input.value(), input.locator());
        }

        for (ElementInfo button : observation.buttons()) {
            log.debug("  button text='{}' locator={}", button.text(), button.locator());
        }

        for (ElementInfo link : observation.links()) {
            log.debug("  link   text='{}' locator={}", link.text(), link.locator());
        }

        for (ElementInfo select : observation.selects()) {
            log.debug("  select id={} name={} locator={}", select.id(), select.name(), select.locator());
        }
    }
}
