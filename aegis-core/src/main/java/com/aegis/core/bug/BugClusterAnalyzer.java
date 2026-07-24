package com.aegis.core.bug;

import com.aegis.model.finding.Finding;

import java.util.List;

public interface BugClusterAnalyzer {

    List<BugCluster> analyze(List<Finding> findings);

}
