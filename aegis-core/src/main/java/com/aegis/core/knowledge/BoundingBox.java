package com.aegis.core.knowledge;

/** An element's on-page position/size in CSS pixels, as Playwright's {@code Locator.boundingBox()} reports it. */
public record BoundingBox(double x, double y, double width, double height) {

    public boolean isZeroArea() {
        return width <= 0 || height <= 0;
    }
}
