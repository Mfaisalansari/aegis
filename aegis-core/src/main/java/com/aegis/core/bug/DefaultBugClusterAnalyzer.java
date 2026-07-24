package com.aegis.core.bug;

import com.aegis.model.finding.Finding;
import com.aegis.model.finding.FindingSeverity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultBugClusterAnalyzer implements BugClusterAnalyzer {

    private static final int RECURRING_THRESHOLD = 3;

    @Override
    public List<BugCluster> analyze(List<Finding> findings) {

        if (findings == null || findings.isEmpty()) {
            return List.of();
        }

        Map<String, List<Finding>> grouped = findings.stream()
                .collect(Collectors.groupingBy(BugFingerprint::of, LinkedHashMap::new, Collectors.toList()));

        List<BugCluster> clusters = new ArrayList<>();

        for (Map.Entry<String, List<Finding>> entry : grouped.entrySet()) {

            List<Finding> members = entry.getValue();

            Set<String> urls = members.stream()
                    .map(Finding::url)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            Instant first = members.stream().map(Finding::detectedAt).min(Instant::compareTo).orElseThrow();
            Instant last = members.stream().map(Finding::detectedAt).max(Instant::compareTo).orElseThrow();

            clusters.add(new BugCluster(
                    entry.getKey(),
                    members.get(0).summary(),
                    predictSeverity(members),
                    members.size(),
                    urls,
                    first,
                    last
            ));
        }

        return clusters.stream()
                .sorted(Comparator.comparingInt((BugCluster c) -> c.severity().ordinal()).reversed()
                        .thenComparing(BugCluster::occurrenceCount, Comparator.reverseOrder()))
                .toList();
    }

    /**
     * Severity prediction: the worst severity among the cluster's own
     * members, escalated one level once it's recurred at least
     * RECURRING_THRESHOLD times — a problem that keeps happening is a
     * stronger signal than any single occurrence's own severity, even
     * when every individual occurrence was independently classified as
     * merely LOW or MEDIUM. Never escalates past CRITICAL.
     */
    private FindingSeverity predictSeverity(List<Finding> members) {

        FindingSeverity worst = members.stream()
                .map(Finding::severity)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(FindingSeverity.LOW);

        if (members.size() >= RECURRING_THRESHOLD && worst != FindingSeverity.CRITICAL) {
            return FindingSeverity.values()[worst.ordinal() + 1];
        }

        return worst;
    }
}
