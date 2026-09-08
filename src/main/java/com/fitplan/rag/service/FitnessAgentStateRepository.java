package com.fitplan.rag.service;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class FitnessAgentStateRepository {

    private static final int MAX_LOGS_PER_CHAT = 50;

    private final ConcurrentMap<String, UserProfile> profiles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CopyOnWriteArrayList<TrainingLog>> trainingLogs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PlanSummary> plans = new ConcurrentHashMap<>();

    public UserProfile getProfile(String chatId) {
        return profiles.getOrDefault(chatId, UserProfile.empty());
    }

    public UserProfile updateProfile(String chatId, ProfileUpdate update) {
        return profiles.compute(chatId, (key, existing) -> {
            UserProfile current = existing == null ? UserProfile.empty() : existing;
            return new UserProfile(
                    choose(update.age(), current.age()),
                    choose(update.goal(), current.goal()),
                    choose(update.experience(), current.experience()),
                    choose(update.weeklyDays(), current.weeklyDays()),
                    choose(update.sessionMinutes(), current.sessionMinutes()),
                    choose(update.equipment(), current.equipment()),
                    choose(update.healthNotes(), current.healthNotes()),
                    Instant.now().toString());
        });
    }

    public TrainingLog addTrainingLog(String chatId, TrainingLogInput input) {
        TrainingLog log = new TrainingLog(
                choose(input.date(), Instant.now().toString()),
                input.exercise(), input.setsAndReps(), input.load(), input.rpe(), input.notes());
        CopyOnWriteArrayList<TrainingLog> logs = trainingLogs.computeIfAbsent(
                chatId, key -> new CopyOnWriteArrayList<>());
        logs.add(log);
        while (logs.size() > MAX_LOGS_PER_CHAT) {
            logs.remove(0);
        }
        return log;
    }

    public List<TrainingLog> recentTrainingLogs(String chatId) {
        List<TrainingLog> logs = trainingLogs.getOrDefault(chatId, new CopyOnWriteArrayList<>());
        int fromIndex = Math.max(0, logs.size() - 10);
        return List.copyOf(logs.subList(fromIndex, logs.size()));
    }

    public PlanSummary savePlan(String chatId, PlanSummaryInput input) {
        PlanSummary plan = new PlanSummary(
                input.goal(), input.weeklySchedule(), input.progressionRule(), Instant.now().toString());
        plans.put(chatId, plan);
        return plan;
    }

    public PlanSummary getPlan(String chatId) {
        return plans.getOrDefault(chatId, PlanSummary.empty());
    }

    private static String choose(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate.trim();
    }

    public record UserProfile(
            String age,
            String goal,
            String experience,
            String weeklyDays,
            String sessionMinutes,
            String equipment,
            String healthNotes,
            String updatedAt) {

        static UserProfile empty() {
            return new UserProfile("", "", "", "", "", "", "", "");
        }
    }

    public record ProfileUpdate(
            String age,
            String goal,
            String experience,
            String weeklyDays,
            String sessionMinutes,
            String equipment,
            String healthNotes) {
    }

    public record TrainingLogInput(
            String date,
            String exercise,
            String setsAndReps,
            String load,
            String rpe,
            String notes) {
    }

    public record TrainingLog(
            String date,
            String exercise,
            String setsAndReps,
            String load,
            String rpe,
            String notes) {
    }

    public record PlanSummaryInput(String goal, String weeklySchedule, String progressionRule) {
    }

    public record PlanSummary(String goal, String weeklySchedule, String progressionRule, String updatedAt) {
        static PlanSummary empty() {
            return new PlanSummary("", "", "", "");
        }
    }
}
