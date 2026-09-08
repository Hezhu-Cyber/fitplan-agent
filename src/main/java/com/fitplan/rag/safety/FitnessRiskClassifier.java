package com.fitplan.rag.safety;

public interface FitnessRiskClassifier {

    RiskDecision classify(String message);

    enum RiskLevel {
        SAFE,
        CLARIFY,
        MEDICAL_BOUNDARY,
        URGENT
    }

    record RiskDecision(
            RiskLevel level,
            String reason,
            boolean currentRisk,
            boolean allowFitnessAdvice) {

        public static RiskDecision safe() {
            return new RiskDecision(RiskLevel.SAFE, "ordinary-fitness-request", false, true);
        }

        public static RiskDecision classifierUnavailable() {
            return new RiskDecision(
                    RiskLevel.CLARIFY,
                    "risk-classifier-unavailable",
                    true,
                    false);
        }
    }
}
