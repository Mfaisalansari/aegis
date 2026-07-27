package com.aegis.core.knowledge;

/** Deliberately limited to what {@link InspectionCheckProvider} actually detects — same discipline as {@link UxFindingType}. */
public enum InspectionCheckType {

    CONSOLE_ERROR,

    UNCAUGHT_EXCEPTION,

    NETWORK_FAILURE,

    BROKEN_LINK,

    LOW_CONTRAST,

    MISSING_ACCESSIBLE_NAME,

    ZERO_SIZE_ELEMENT,

    TEXT_OVERFLOW

}
