package com.aegis.core.reasoning.mapper;

import com.aegis.model.action.ActionType;
import com.aegis.model.observation.ElementInfo;

import java.util.ArrayList;
import java.util.List;

public class DefaultElementActionMapper implements ElementActionMapper {

    @Override
    public List<ActionType> supportedActions(ElementInfo element) {

        List<ActionType> actions = new ArrayList<>();

        if (!element.visible() || !element.enabled()) {
            return actions;
        }

        switch (element.type().toUpperCase()) {

            case "TEXT":
            case "PASSWORD":
            case "EMAIL":
            case "SEARCH":
            case "NUMBER":
            case "TEL":
            case "URL":
            case "TEXTAREA":

                actions.add(ActionType.TYPE);
                break;

            case "BUTTON":
            case "SUBMIT":

                actions.add(ActionType.CLICK);
                break;

            case "CHECKBOX":

                actions.add(ActionType.CLICK);
                break;

            case "RADIO":

                actions.add(ActionType.CLICK);
                break;

            case "SELECT":

                actions.add(ActionType.SELECT);
                break;

            case "LINK":

                actions.add(ActionType.CLICK);
                break;

            default:
                break;
        }

        return actions;
    }
}