package com.greenwave.simulation;

import java.util.Random;

final class SignalProgram {
    enum Phase { GREEN, YELLOW, RED }
    enum Type { FIXED, PEDESTRIAN }
    record State(Phase phase, Integer remainingTicks, int phaseStart) {}

    private final Type type;
    private final int offset;
    private final Random random;
    private int phaseStart;
    private Phase phase = Phase.GREEN;
    private boolean requestPending;

    SignalProgram(Type type, int offset, long seed) {
        this.type = type;
        this.offset = offset;
        this.random = new Random(seed);
    }

    void advance(int tick) {
        if (type == Type.FIXED) return;
        int elapsed = tick - phaseStart;
        if (phase == Phase.YELLOW && elapsed >= 50) {
            transition(Phase.RED, tick);
        } else if (phase == Phase.RED && elapsed >= 100) {
            transition(Phase.GREEN, tick);
        }
        if (tick > 0 && tick % 10 == 0) {
            boolean request = random.nextDouble() < 0.08;
            if (request && phase == Phase.GREEN) requestPending = true;
        }
        if (phase == Phase.GREEN && requestPending && tick - phaseStart >= 120) {
            requestPending = false;
            transition(Phase.YELLOW, tick);
        }
    }

    State at(int tick) {
        if (type == Type.PEDESTRIAN) {
            Integer remaining = switch (phase) {
                case GREEN -> null;
                case YELLOW -> 50 - (tick - phaseStart);
                case RED -> 100 - (tick - phaseStart);
            };
            return new State(phase, remaining, phaseStart);
        }
        int cycle = Math.floorMod(tick + offset, 600);
        int start = tick - cycle;
        if (cycle < 300) return new State(Phase.GREEN, 300 - cycle, start);
        if (cycle < 350) return new State(Phase.YELLOW, 350 - cycle, start + 300);
        return new State(Phase.RED, 600 - cycle, start + 350);
    }

    private void transition(Phase next, int tick) {
        phase = next;
        phaseStart = tick;
    }
}
