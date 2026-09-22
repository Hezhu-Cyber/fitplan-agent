package com.fitplan.rag.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "fitplan.state", name = "storage", havingValue = "jdbc", matchIfMissing = true)
public class JdbcFitnessAgentStateRepository implements FitnessAgentStateRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcFitnessAgentStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserProfile getProfile(UUID ownerId, String chatId) {
        List<UserProfile> profiles = jdbcTemplate.query(
                """
                SELECT age, goal, experience, weekly_days, session_minutes,
                       equipment, health_notes, updated_at
                FROM fitplan_user_profile
                WHERE owner_id = ? AND chat_id = ?
                """,
                (rs, rowNum) -> new UserProfile(
                        value(rs.getString("age")), value(rs.getString("goal")),
                        value(rs.getString("experience")), value(rs.getString("weekly_days")),
                        value(rs.getString("session_minutes")), value(rs.getString("equipment")),
                        value(rs.getString("health_notes")),
                        rs.getTimestamp("updated_at").toInstant().toString()),
                ownerId, FitnessAgentStateRepository.requireChatId(chatId));
        return profiles.isEmpty() ? UserProfile.empty() : profiles.getFirst();
    }

    @Override
    public UserProfile updateProfile(UUID ownerId, String chatId, ProfileUpdate update) {
        String key = FitnessAgentStateRepository.requireChatId(chatId);
        Timestamp updatedAt = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                INSERT INTO fitplan_user_profile(
                    owner_id, chat_id, age, goal, experience, weekly_days, session_minutes,
                    equipment, health_notes, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(owner_id, chat_id) DO UPDATE SET
                    age = COALESCE(NULLIF(EXCLUDED.age, ''), fitplan_user_profile.age),
                    goal = COALESCE(NULLIF(EXCLUDED.goal, ''), fitplan_user_profile.goal),
                    experience = COALESCE(NULLIF(EXCLUDED.experience, ''), fitplan_user_profile.experience),
                    weekly_days = COALESCE(NULLIF(EXCLUDED.weekly_days, ''), fitplan_user_profile.weekly_days),
                    session_minutes = COALESCE(NULLIF(EXCLUDED.session_minutes, ''), fitplan_user_profile.session_minutes),
                    equipment = COALESCE(NULLIF(EXCLUDED.equipment, ''), fitplan_user_profile.equipment),
                    health_notes = COALESCE(NULLIF(EXCLUDED.health_notes, ''), fitplan_user_profile.health_notes),
                    updated_at = EXCLUDED.updated_at
                """,
                ownerId, key, normalize(update.age()), normalize(update.goal()),
                normalize(update.experience()), normalize(update.weeklyDays()),
                normalize(update.sessionMinutes()), normalize(update.equipment()),
                normalize(update.healthNotes()), updatedAt);
        return getProfile(ownerId, key);
    }

    @Override
    public TrainingLog addTrainingLog(UUID ownerId, String chatId, TrainingLogInput input, String idempotencyKey) {
        String key = FitnessAgentStateRepository.requireChatId(chatId);
        String operationKey = requireIdempotencyKey(idempotencyKey);
        String date = FitnessAgentStateRepository.choose(input.date(), Instant.now().toString());
        String exercise = normalize(input.exercise());
        String setsAndReps = normalize(input.setsAndReps());
        String load = normalize(input.load());
        String rpe = normalize(input.rpe());
        String notes = normalize(input.notes());
        jdbcTemplate.update(
                """
                INSERT INTO fitplan_training_log(
                    owner_id, chat_id, log_date, exercise, sets_and_reps,
                    load_value, rpe, notes, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (owner_id, idempotency_key)
                    WHERE idempotency_key IS NOT NULL
                DO NOTHING
                """,
                ownerId, key, date, exercise, setsAndReps, load, rpe, notes, operationKey);
        jdbcTemplate.update(
                """
                DELETE FROM fitplan_training_log
                WHERE owner_id = ? AND chat_id = ?
                  AND id NOT IN (
                      SELECT id FROM fitplan_training_log
                      WHERE owner_id = ? AND chat_id = ?
                      ORDER BY id DESC LIMIT ?
                  )
                """,
                ownerId, key, ownerId, key, MAX_LOGS_PER_CHAT);
        return jdbcTemplate.query(
                        """
                        SELECT log_date, exercise, sets_and_reps, load_value, rpe, notes
                        FROM fitplan_training_log
                        WHERE owner_id = ? AND idempotency_key = ?
                        """,
                        (rs, rowNum) -> new TrainingLog(
                                value(rs.getString("log_date")), value(rs.getString("exercise")),
                                value(rs.getString("sets_and_reps")), value(rs.getString("load_value")),
                                value(rs.getString("rpe")), value(rs.getString("notes"))),
                        ownerId, operationKey)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Training log was not persisted"));
    }

    @Override
    public List<TrainingLog> recentTrainingLogs(UUID ownerId, String chatId) {
        return jdbcTemplate.query(
                """
                SELECT log_date, exercise, sets_and_reps, load_value, rpe, notes
                FROM (
                    SELECT id, log_date, exercise, sets_and_reps, load_value, rpe, notes
                    FROM fitplan_training_log
                    WHERE owner_id = ? AND chat_id = ?
                    ORDER BY id DESC LIMIT 10
                ) recent
                ORDER BY id ASC
                """,
                (rs, rowNum) -> new TrainingLog(
                        value(rs.getString("log_date")), value(rs.getString("exercise")),
                        value(rs.getString("sets_and_reps")), value(rs.getString("load_value")),
                        value(rs.getString("rpe")), value(rs.getString("notes"))),
                ownerId, FitnessAgentStateRepository.requireChatId(chatId));
    }

    @Override
    public PlanSummary savePlan(UUID ownerId, String chatId, PlanSummaryInput input) {
        String key = FitnessAgentStateRepository.requireChatId(chatId);
        String goal = normalize(input.goal());
        String weeklySchedule = normalize(input.weeklySchedule());
        String progressionRule = normalize(input.progressionRule());
        Timestamp updatedAt = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                INSERT INTO fitplan_plan_summary(
                    owner_id, chat_id, goal, weekly_schedule, progression_rule, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(owner_id, chat_id) DO UPDATE SET
                    goal = EXCLUDED.goal,
                    weekly_schedule = EXCLUDED.weekly_schedule,
                    progression_rule = EXCLUDED.progression_rule,
                    updated_at = EXCLUDED.updated_at
                """,
                ownerId, key, goal, weeklySchedule, progressionRule, updatedAt);
        return new PlanSummary(goal, weeklySchedule, progressionRule, updatedAt.toInstant().toString());
    }

    @Override
    public PlanSummary getPlan(UUID ownerId, String chatId) {
        List<PlanSummary> plans = jdbcTemplate.query(
                """
                SELECT goal, weekly_schedule, progression_rule, updated_at
                FROM fitplan_plan_summary
                WHERE owner_id = ? AND chat_id = ?
                """,
                (rs, rowNum) -> new PlanSummary(
                        value(rs.getString("goal")), value(rs.getString("weekly_schedule")),
                        value(rs.getString("progression_rule")),
                        rs.getTimestamp("updated_at").toInstant().toString()),
                ownerId, FitnessAgentStateRepository.requireChatId(chatId));
        return plans.isEmpty() ? PlanSummary.empty() : plans.getFirst();
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must not exceed 128 characters");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
