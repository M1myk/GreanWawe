package com.greenwave.simulation;

final class GasolineFuelModel {
    static final double IDLE_LITERS_PER_SECOND = 0.16 * 3.785411784 / 3600;
    private static final double TEST_MASS_KG = 3250 * 0.45359237;
    private static final double MPS_PER_MPH = 0.44704;
    private static final double NEWTONS_PER_LBF = 4.448221615;
    private static final double DRIVETRAIN_EFFICIENCY = 0.88;
    private static final double GASOLINE_GRAMS_PER_LITER = 745;
    static final double CUTOFF_MPS = 20 / 3.6;
    private static final double[] POWER_KW = {0, 5, 15, 30, 60};
    private static final double[] GRAMS_PER_KWH = {650, 500, 335, 270, 245};

    private GasolineFuelModel() {}

    static double litersForStep(double before, double after, double meters, double seconds) {
        double meanSpeed = (before + after) / 2;
        double roadForce = roadForce(meanSpeed);
        double kineticChange = 0.5 * TEST_MASS_KG * (after * after - before * before);
        double wheelJoules = kineticChange + roadForce * meters;
        double idle = IDLE_LITERS_PER_SECOND * seconds;
        if (wheelJoules <= 0) {
            return after < before && meanSpeed >= CUTOFF_MPS ? 0 : idle;
        }
        double engineJoules = wheelJoules / DRIVETRAIN_EFFICIENCY;
        double powerKw = engineJoules / (seconds * 1000);
        return Math.max(idle, engineJoules / 3_600_000 * specificFuel(powerKw) / GASOLINE_GRAMS_PER_LITER);
    }

    static double coastingDeceleration(double speed) {
        return roadForce(speed) / TEST_MASS_KG + 0.15;
    }

    private static double roadForce(double speed) {
        double mph = speed / MPS_PER_MPH;
        return NEWTONS_PER_LBF * (20.4 + 0.2116 * mph + 0.01822 * mph * mph);
    }

    private static double specificFuel(double powerKw) {
        for (int i = 1; i < POWER_KW.length; i++) {
            if (powerKw <= POWER_KW[i]) {
                double fraction = (powerKw - POWER_KW[i - 1]) / (POWER_KW[i] - POWER_KW[i - 1]);
                return GRAMS_PER_KWH[i - 1] + fraction * (GRAMS_PER_KWH[i] - GRAMS_PER_KWH[i - 1]);
            }
        }
        return GRAMS_PER_KWH[GRAMS_PER_KWH.length - 1];
    }
}
