package com.fitplan.rag.service;

import java.util.List;
import java.util.UUID;

/** Persistent state used by the planning agent across multi-turn conversations. */
public interface FitnessAgentStateRepository {

    int MAX_LOGS_PER_CHAT = 50;

    UserProfile getProfile(UUID ownerId, String chatId);

    UserProfile updateProfile(UUID ownerId, String chatId, ProfileUpdate update);

    TrainingLog addTrainingLog(UUID ownerId, String chatId, TrainingLogInput input, String idempotencyKey);

    List<TrainingLog> recentTrainingLogs(UUID ownerId, String chatId);

    PlanSummary savePlan(UUID ownerId, String chatId, PlanSummaryInput input);

    PlanSummary getPlan(UUID ownerId, String chatId);

    static String requireChatId(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("chatId must not be blank");
        }
        String normalized = chatId.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("chatId must not exceed 128 characters");
        }
        return normalized;
    }

    static String choose(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate.trim();
    }

    record UserProfile(
            String age,
            String goal,
            String experience,
            String weeklyDays,
            String sessionMinutes,
            String equipment,
            String healthNotes,
            String updatedAt) {

        public static UserProfile empty() {
            return new UserProfile("", "", "", "", "", "", "", "");
        }
    }

    record ProfileUpdate(
            String age,
            String goal,
            String experience,
            String weeklyDays,
            String sessionMinutes,
            String equipment,
            String healthNotes) {
    }

    record TrainingLogInput(
            String date,
            String exercise,
            String setsAndReps,
            String load,
            String rpe,
            String notes) {
    }

    record TrainingLog(
            String date,
            String exercise,
            String setsAndReps,
            String load,
            String rpe,
            String notes) {
    }

    record PlanSummaryInput(String goal, String weeklySchedule, String progressionRule) {
    }

    record PlanSummary(String goal, String weeklySchedule, String progressionRule, String updatedAt) {

        public static PlanSummary empty() {
            return new PlanSummary("", "", "", "");
        }
    }
}
