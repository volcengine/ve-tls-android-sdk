package com.volcengine.tls.android.demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class FormalBenchmarkPlan {
    private static final int[] DEFAULT_RATES = new int[] {1, 10, 100, 200, 500};
    private static final int[] QUICK_RATES = new int[] {200, 500};
    private static final String[] DEFAULT_MODES = new String[] {"memory", "persistent"};
    private static final String[] DEFAULT_PROFILES = new String[] {"tls200", "tls700"};
    static final String PRESET_DEFAULT = "default";
    static final String PRESET_QUICK = "quick";

    private FormalBenchmarkPlan() {
    }

    static List<Scenario> defaultScenarios() {
        return scenariosForRates(DEFAULT_RATES);
    }

    static List<Scenario> quickScenarios() {
        return scenariosForRates(QUICK_RATES);
    }

    static List<Scenario> scenariosForPreset(String preset) {
        if (PRESET_QUICK.equalsIgnoreCase(preset)) {
            return quickScenarios();
        }
        return defaultScenarios();
    }

    private static List<Scenario> scenariosForRates(int[] rates) {
        List<Scenario> scenarios = new ArrayList<>();
        for (String mode : DEFAULT_MODES) {
            for (String profile : DEFAULT_PROFILES) {
                for (int rate : rates) {
                    scenarios.add(new Scenario(mode, profile, rate));
                }
            }
        }
        return Collections.unmodifiableList(scenarios);
    }

    static final class Scenario {
        final String mode;
        final String profile;
        final int targetLps;

        Scenario(String mode, String profile, int targetLps) {
            this.mode = mode;
            this.profile = profile;
            this.targetLps = targetLps;
        }

        String scenarioId() {
            return mode + "_" + profile + "_" + targetLps + "lps";
        }
    }
}
