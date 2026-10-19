package dev.curvegen.core.edit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StepperTest {
    @Test
    void aStepAddsAndStopsAtTheLimits() {
        assertEquals(6, Stepper.step(5, 1, 1, 400));
        assertEquals(4, Stepper.step(5, -1, 1, 400));
        assertEquals(400, Stepper.step(400, 1, 1, 400));
        assertEquals(1, Stepper.step(1, -1, 1, 400));
        assertEquals(0.0625, Stepper.step(0.25, -0.25, 0.0625, 50), "a thickness stops at its least, not at 0");
        assertEquals(1.55, Stepper.step(1.3, 0.25, 0.0625, 50), 1e-12, "and a value off the steps keeps its offset");
    }

    @Test
    void aValuePastALimitComesBackToIt() {
        assertEquals(8, Stepper.step(12, 1, 0, 8));
        assertEquals(8, Stepper.step(12, -1, 0, 8));
        assertEquals(0, Stepper.step(-3, 1, 0, 8));
    }

    @Test
    void theNextGridValueIsAWholeStepFromOneOnTheGrid() {
        assertEquals(2, Stepper.next(1.5, 0.5, 1, 0, 8));
        assertEquals(1, Stepper.next(1.5, 0.5, -1, 0, 8));
        assertEquals(0.5, Stepper.next(0, 0.5, 1, 0, 8));
    }

    @Test
    void theNextGridValueFromOneOffTheGridIsOnIt() {
        assertEquals(1.5, Stepper.next(1.3, 0.5, 1, 0, 8));
        assertEquals(1, Stepper.next(1.3, 0.5, -1, 0, 8));
        assertEquals(1.5, Stepper.next(1.499, 0.5, 1, 0, 8));
        assertEquals(1.5, Stepper.next(1.501, 0.5, -1, 0, 8));
    }

    @Test
    void theNextGridValueStaysWithinTheLimits() {
        assertEquals(8, Stepper.next(8, 0.5, 1, 0, 8));
        assertEquals(0, Stepper.next(0, 0.5, -1, 0, 8));
        assertEquals(7, Stepper.next(7, 0.5, 1, 0, 7));
        assertEquals(8, Stepper.next(11.2, 0.5, -1, 0, 8), "a point outside the box comes back to its side");
    }

    @Test
    void manyStepsDontDrift() {
        double v = 0;
        for (int k = 0; k < 15; k++) v = Stepper.next(v, 0.5, 1, 0, 100);
        assertEquals(7.5, v);
        for (int k = 0; k < 15; k++) v = Stepper.next(v, 0.5, -1, 0, 100);
        assertEquals(0, v);
    }
}
