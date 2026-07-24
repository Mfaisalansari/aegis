package com.aegis.core.anomaly;

import com.aegis.model.context.MissionContext;
import com.aegis.model.finding.Finding;

import java.util.List;

public interface AnomalyDetector {

    List<Finding> detect(MissionContext context);

}
