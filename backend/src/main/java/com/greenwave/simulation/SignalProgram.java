package com.greenwave.simulation;

import java.util.Random;

final class SignalProgram {
    static final int FIXED_CYCLE_TICKS = 600;
    static final int FIXED_GREEN_TICKS = 300;
    static final int PEDESTRIAN_RED_TICKS = 100;
    private static final int YELLOW_TICKS = 30;
    private static final int FIXED_RED_START = FIXED_GREEN_TICKS + YELLOW_TICKS;

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
        if (phase == Phase.YELLOW && elapsed >= YELLOW_TICKS) {
            transition(Phase.RED, tick);
        } else if (phase == Phase.RED && elapsed >= PEDESTRIAN_RED_TICKS) {
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
                case YELLOW -> YELLOW_TICKS - (tick - phaseStart);
                case RED -> PEDESTRIAN_RED_TICKS - (tick - phaseStart);
            };
            return new State(phase, remaining, phaseStart);
        }
        return fixedAt(tick, offset);
    }

    static State fixedAt(int tick, int offset) {
        int cycle = Math.floorMod(tick + offset, FIXED_CYCLE_TICKS);
        int start = tick - cycle;
        if (cycle < FIXED_GREEN_TICKS) return new State(Phase.GREEN, FIXED_GREEN_TICKS - cycle, start);
        if (cycle < FIXED_RED_START) return new State(Phase.YELLOW, FIXED_RED_START - cycle, start + FIXED_GREEN_TICKS);
        return new State(Phase.RED, FIXED_CYCLE_TICKS - cycle, start + FIXED_RED_START);
    }

    private void transition(Phase next, int tick) {
        phase = next;
        phaseStart = tick;
    }
}
