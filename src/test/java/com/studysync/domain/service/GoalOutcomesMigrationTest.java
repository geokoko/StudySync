package com.studysync.domain.service;

import com.studysync.domain.entity.StudyGoal;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalOutcomesMigrationTest {
    @Test
    void schemaUpgradeAndRepeatedStartupPreserveDroppedOutcomesAndReopenedGoals() {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).addScript("schema.sql").build();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            // Simulate a database from the previous version, without the new optional field.
            jdbc.execute("ALTER TABLE study_goals DROP COLUMN drop_reason");
            var schema = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
            schema.execute(database);
            StudyGoal.setJdbcTemplate(jdbc);
            StudyGoal goal = new StudyGoal("No longer achievable");
            goal.setDate(LocalDate.now());
            goal.setDoneCriteria("[x] Research\n[ ] Build");
            goal.save();
            assertTrue(StudyGoal.dropGoal(goal.getId(), "Dependency removed"));
            schema.execute(database);
            schema.execute(database);
            StudyGoal dropped = StudyGoal.findById(goal.getId()).orElseThrow();
            assertTrue(dropped.isDropped());
            assertTrue(dropped.isDroppedAttempt());
            assertFalse(dropped.isFailed());
            assertEquals("Dependency removed", dropped.getDropReason());
            assertEquals(1, StudyGoal.findAll().size());

            assertTrue(StudyGoal.reopenDroppedGoal(goal.getId(), LocalDate.now().plusDays(1)));
            schema.execute(database);
            StudyGoal reopened = StudyGoal.findById(goal.getId()).orElseThrow();
            assertEquals(StudyGoal.GoalStatus.ACTIVE, reopened.getStatus());
            assertEquals(StudyGoal.AttemptOutcome.PENDING, reopened.getAttemptOutcome());
            assertEquals(goal.getDoneCriteria(), reopened.getDoneCriteria());
            assertEquals(2, StudyGoal.findAll().size());
            assertTrue(StudyGoal.findAllByDate(goal.getDate()).getFirst().isDroppedAttempt());
        } finally {
            StudyGoal.setJdbcTemplate(null);
            database.shutdown();
        }
    }
}
