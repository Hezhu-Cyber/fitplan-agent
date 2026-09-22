package com.fitplan.rag.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Local/test fallback. Production uses {@link JdbcFitnessAgentStateRepository}. */
@Repository
@ConditionalOnProperty(prefix = "fitplan.state", name = "storage", havingValue = "memory")
public class InMemoryFitnessAgentStateRepository implements FitnessAgentStateRepository {

    private final ConcurrentMap<String, UserProfile> profiles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CopyOnWriteArrayList<TrainingLog>> trainingLogs =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TrainingLog> logsByIdempotencyKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PlanSummary> plans = new ConcurrentHashMap<>();

    @Override
    public UserProfile getProfile(UUID ownerId, String chatId) {
        return profiles.getOrDefault(key(ownerId, chatId), UserProfile.empty());
    }

    @Override
    public UserProfile updateProfile(UUID ownerId, String chatId, ProfileUpdate update) {
        String key = key(ownerId, chatId);
        return profiles.compute(key, (ignored, existing) -> {
            UserProfile current = existing == null ? UserProfile.empty() : existing;
            return new UserProfile(
                    FitnessAgentStateRepository.choose(update.age(), current.age()),
                    FitnessAgentStateRepository.choose(update.goal(), current.goal()),
                    FitnessAgentStateRepository.choose(update.experience(), current.experience()),
                    FitnessAgentStateRepository.choose(update.weeklyDays(), current.weeklyDays()),
                    FitnessAgentStateRepository.choose(update.sessionMinutes(), current.sessionMinutes()),
                    FitnessAgentStateRepository.choose(update.equipment(), current.equipment()),
                    FitnessAgentStateRepository.choose(update.healthNotes(), current.healthNotes()),
                    Instant.now().toString());
        });
    }

    @Override
    public TrainingLog addTrainingLog(
            UUID ownerId,
            String chatId,
            TrainingLogInput input,
            String idempotencyKey) {
        String chatKey = key(ownerId, chatId);
        String logKey = ownerId + ":" + idempotencyKey;
        TrainingLog existing = logsByIdempotencyKey.get(logKey);
        if (existing != null) {
            return existing;
        }
        TrainingLog log = new TrainingLog(
                FitnessAgentStateRepository.choose(input.date(), Instant.now().toString()),
                input.exercise(),
                input.setsAndReps(),
                input.load(),
                input.rpe(),
                input.notes());
        TrainingLog previous = logsByIdempotencyKey.putIfAbsent(logKey, log);
        if (previous != null) {
            return previous;
        }
        CopyOnWriteArrayList<TrainingLog> logs = trainingLogs.computeIfAbsent(
                chatKey, ignored -> new CopyOnWriteArrayList<>());
        logs.add(log);
        while (logs.size() > MAX_LOGS_PER_CHAT) {
            logs.removeFirst();
        }
        return log;
    }

    @Override
    public List<TrainingLog> recentTrainingLogs(UUID ownerId, String chatId) {
        List<TrainingLog> logs = trainingLogs.getOrDefault(
                key(ownerId, chatId), new CopyOnWriteArrayList<>());
        int fromIndex = Math.max(0, logs.size() - 10);
        return List.copyOf(logs.subList(fromIndex, logs.size()));
    }

    @Override
    public PlanSummary savePlan(UUID ownerId, String chatId, PlanSummaryInput input) {
        PlanSummary plan = new PlanSummary(
                input.goal(), input.weeklySchedule(), input.progressionRule(), Instant.now().toString());
        plans.put(key(ownerId, chatId), plan);
        return plan;
    }

    @Override
    public PlanSummary getPlan(UUID ownerId, String chatId) {
        return plans.getOrDefault(key(ownerId, chatId), PlanSummary.empty());
    }

    private static String key(UUID ownerId, String chatId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        return ownerId + ":" + FitnessAgentStateRepository.requireChatId(chatId);
    }
}
