package com.greenwave.simulation;

final class GasolineFuelModel {
    static final double IDLE_LITERS_PER_SECOND = 0.16 * 3.785411784 / 3600;
    private static final double TEST_MASS_KG = 3250 * 0.45359237;
    private static final double MPS_PER_MPH = 0.44704;
    private static final double NEWTONS_PER_LBF = 4.448221615;
    private static final double DRIVETRAIN_EFFICIENCY = 0.88;
    private static final double LAUNCH_EFFICIENCY = 0.65;
    private static final double GASOLINE_GRAMS_PER_LITER = 745;
    private static final double TRANSIENT_GRAMS_PER_RAD_PER_SECOND = 0.007;
    private static final double FINAL_DRIVE = 3.59;
    private static final double WHEEL_RADIUS_M = 0.315;
    private static final double LOW_GEAR_END_MPS = 20 / 3.6;
    static final double CUTOFF_MPS = 20 / 3.6;
    private static final double[] POWER_KW = {0, 5, 15, 30, 60};
    private static final double[] GRAMS_PER_KWH = {650, 500, 335, 270, 245};

    private GasolineFuelModel() {}

    static double litersForStep(double before, double after, double meters, double seconds) {
        return step(before, after, meters, seconds).liters();
    }

    record Step(double liters, double accelerationCorrectionLiters) {}

    static Step step(double before, double after, double meters, double seconds) {
        double meanSpeed = (before + after) / 2;
        double roadForce = roadForce(meanSpeed);
        double kineticChange = 0.5 * TEST_MASS_KG * (after * after - before * before);
        double wheelJoules = kineticChange + roadForce * meters;
        double idle = IDLE_LITERS_PER_SECOND * seconds;
        if (wheelJoules <= 0) {
            return new Step(after < before && meanSpeed >= CUTOFF_MPS ? 0 : idle, 0);
        }
        double engineJoules = wheelJoules / DRIVETRAIN_EFFICIENCY;
        double powerKw = engineJoules / (seconds * 1000);
        double grams = TRANSIENT_GRAMS_PER_RAD_PER_SECOND * Math.max(0, engineSpeed(after) - engineSpeed(before));
        double transientLiters = grams / seconds >= 0.1 ? grams / GASOLINE_GRAMS_PER_LITER : 0;
        double fuelPerJoule = specificFuel(powerKw) / (3_600_000 * GASOLINE_GRAMS_PER_LITER);
        double launchFraction = Math.max(0, 1 - meanSpeed / LOW_GEAR_END_MPS);
        double efficiency = DRIVETRAIN_EFFICIENCY - (DRIVETRAIN_EFFICIENCY - LAUNCH_EFFICIENCY) * launchFraction;
        double extraJoules = Math.max(0, kineticChange) * (1 / efficiency - 1 / DRIVETRAIN_EFFICIENCY);
        double correction = transientLiters + extraJoules * fuelPerJoule;
        return new Step(Math.max(idle, engineJoules * fuelPerJoule) + correction, correction);
    }

    private static double engineSpeed(double roadSpeed) {
        double low = Math.min(roadSpeed, LOW_GEAR_END_MPS);
        double high = Math.max(0, roadSpeed - LOW_GEAR_END_MPS);
        return (3.552 * low + 2.022 * high) * FINAL_DRIVE / WHEEL_RADIUS_M;
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
