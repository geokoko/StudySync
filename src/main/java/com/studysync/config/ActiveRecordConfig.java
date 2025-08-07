package com.studysync.config;

import com.studysync.domain.entity.StudySession;
import com.studysync.domain.entity.ProjectSession;
import com.studysync.domain.entity.Task;
import com.studysync.domain.entity.Project;
import com.studysync.domain.entity.StudyGoal;
import com.studysync.domain.entity.DailyReflection;
import com.studysync.domain.entity.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class to initialize JdbcTemplate in Active Record model classes.
 * This ensures that the static database operations in models can access the database.
 */
@Configuration
public class ActiveRecordConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(ActiveRecordConfig.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    @Autowired
    public ActiveRecordConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Initialize JdbcTemplate in model classes after Spring context is loaded.
     */
    @PostConstruct
    public void initializeModelDatabaseAccess() {
        logger.info("Initializing JdbcTemplate in Active Record entities...");
        StudySession.setJdbcTemplate(jdbcTemplate);
        ProjectSession.setJdbcTemplate(jdbcTemplate);
        Task.setJdbcTemplate(jdbcTemplate);
        Project.setJdbcTemplate(jdbcTemplate);
        StudyGoal.setJdbcTemplate(jdbcTemplate);
        DailyReflection.setJdbcTemplate(jdbcTemplate);
        Category.setJdbcTemplate(jdbcTemplate);
        logger.info("Active Record entities initialized successfully with JdbcTemplate");
    }
}