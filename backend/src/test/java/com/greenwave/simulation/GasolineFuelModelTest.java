package com.greenwave.simulation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GasolineFuelModelTest {
    @Test void idleCruisingAccelerationAndEngineBrakingUseDifferentFuelFlows() {
        double idle = GasolineFuelModel.litersForStep(0, 0, 0, 1);
        double cruising = GasolineFuelModel.litersForStep(10, 10, 10, 1);
        double accelerating = GasolineFuelModel.litersForStep(10, 12, 11, 1);
        assertEquals(0.16 * 3.785411784 / 3600, idle, 1e-10);
        assertTrue(cruising > idle);
        assertTrue(accelerating > cruising);
        assertEquals(0, GasolineFuelModel.litersForStep(10, 8, 9, 1));
        assertEquals(idle, GasolineFuelModel.litersForStep(2, 0, 1, 1));
    }

    @Test void stopAndRelaunchIncreaseFuelOnAComparableRoute() {
        double continuous = 10 * GasolineFuelModel.litersForStep(10, 10, 10, 1);
        double interrupted = 5 * GasolineFuelModel.litersForStep(10, 10, 10, 1)
                + GasolineFuelModel.litersForStep(10, 0, 25, 5)
                + GasolineFuelModel.litersForStep(0, 0, 0, 20)
                + GasolineFuelModel.litersForStep(0, 10, 25, 5);
        assertTrue(interrupted > continuous);
    }
}
