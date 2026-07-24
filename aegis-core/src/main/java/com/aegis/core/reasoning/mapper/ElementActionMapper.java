package com.aegis.core.reasoning.mapper;

import com.aegis.model.observation.ElementInfo;
import com.aegis.model.action.ActionType;

import java.util.List;

public interface ElementActionMapper {

    List<ActionType> supportedActions(ElementInfo element);

}