package com.aegis.example.plugin;

import com.aegis.core.plugin.FindingRule;
import com.aegis.model.context.MissionContext;
import com.aegis.model.finding.Finding;
import com.aegis.model.finding.FindingSeverity;
import com.aegis.model.observation.Observation;

import java.time.Instant;
import java.util.List;

/**
 * Worked example of a Stage 2 "Finding Plugin". Deliberately trivial —
 * flags the current page's title whenever it's exactly "Swag Labs" (the
 * real title of saucedemo.com), so running this against
 * {@code sample-saucedemo} is a guaranteed, unambiguous way to prove
 * FindingRule discovery actually fired, not just that it compiled.
 * Registered via {@code META-INF/services/com.aegis.core.plugin.FindingRule}.
 */
public class SwagLabsFindingRule implements FindingRule {

    @Override
    public List<Finding> evaluate(MissionContext context) {

        Observation observation = context.getExecutionState().getCurrentObservation();

        if (observation != null && "Swag Labs".equals(observation.pageTitle())) {
            return List.of(new Finding(
                    FindingSeverity.LOW,
                    "PLUGIN_DEMO: page title matched 'Swag Labs'",
                    observation.url(),
                    Instant.now()));
        }

        return List.of();
    }
}
