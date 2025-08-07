package com.studysync.domain.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain entity representing a category in the StudySync system.
 * Uses Active Record pattern - handles its own database operations.
 * 
 * <p>Categories provide a way to organize and group related tasks and projects.
 * They enable filtering and reporting capabilities within the application.</p>
 * 
 * @author geokoko 
 * @version 0.1.0-BETA
 * @since 0.1.0
 */
public class Category {
    private static final Logger logger = LoggerFactory.getLogger(Category.class);
    private static JdbcTemplate jdbcTemplate;
    
    public static void setJdbcTemplate(JdbcTemplate template) {
        jdbcTemplate = template;
    }
    
    private String id;
    
    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    private String name;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    private LocalDateTime createdAt;
    
    /**
     * Default constructor for frameworks.
     */
    public Category() {
        this.createdAt = LocalDateTime.now();
    }
    
    /**
     * Creates a new category with the specified name.
     * 
     * @param name the category name (required)
     */
    public Category(String name) {
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }
    
    /**
     * Creates a new category with name and description.
     * 
     * @param name the category name (required)
     * @param description the category description (optional)
     */
    public Category(String name, String description) {
        this.name = name;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }
    
    /**
     * Full constructor for database loading.
     */
    public Category(String id, String name, String description, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }
    
    /**
     * Save this category to the database (insert or update).
     */
    public Category save() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized. Make sure Spring context is loaded.");
        }
        
        String id = (this.id == null || this.id.isBlank()) ? UUID.randomUUID().toString() : this.id;
        this.id = id;
        
        String sql = """
            MERGE INTO task_categories (id, name, description, created_at)
            VALUES (?, ?, ?, ?)
            """;
        
        jdbcTemplate.update(sql,
            id,
            this.name,
            this.description,
            this.createdAt
        );
        
        logger.debug("Category saved: {} - {}", id, this.name);
        return this;
    }
    
    /**
     * Delete this category from the database.
     */
    public boolean delete() {
        if (jdbcTemplate == null || this.id == null) {
            return false;
        }
        
        String sql = "DELETE FROM task_categories WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, this.id);
        boolean deleted = rowsAffected > 0;
        
        if (deleted) {
            logger.info("Category deleted: {} - {}", this.id, this.name);
        } else {
            logger.warn("Category not found for deletion: {}", this.id);
        }
        
        return deleted;
    }
    
    /**
     * Get all categories ordered by name.
     */
    public static List<Category> findAll() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate not initialized");
        }
        
        String sql = "SELECT * FROM task_categories ORDER BY name ASC";
        List<Category> categories = jdbcTemplate.query(sql, getRowMapper());
        logger.debug("Retrieved {} categories", categories.size());
        return categories;
    }
    
    /**
     * Find a category by its ID.
     */
    public static Optional<Category> findById(String categoryId) {
        if (jdbcTemplate == null || categoryId == null || categoryId.isBlank()) {
            return Optional.empty();
        }
        
        String sql = "SELECT * FROM task_categories WHERE id = ?";
        try {
            Category category = jdbcTemplate.queryForObject(sql, getRowMapper(), categoryId);
            return Optional.ofNullable(category);
        } catch (Exception e) {
            logger.debug("Category not found: {}", categoryId);
            return Optional.empty();
        }
    }
    
    /**
     * Find a category by its name.
     */
    public static Optional<Category> findByName(String name) {
        if (jdbcTemplate == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        
        String sql = "SELECT * FROM task_categories WHERE LOWER(name) = LOWER(?)";
        try {
            Category category = jdbcTemplate.queryForObject(sql, getRowMapper(), name.trim());
            return Optional.ofNullable(category);
        } catch (Exception e) {
            logger.debug("Category not found: {}", name);
            return Optional.empty();
        }
    }
    
    /**
     * Check if category exists by name.
     */
    public static boolean existsByName(String name) {
        if (jdbcTemplate == null || name == null || name.isBlank()) {
            return false;
        }
        
        String sql = "SELECT 1 FROM task_categories WHERE LOWER(name) = LOWER(?) LIMIT 1";
        try {
            Integer result = jdbcTemplate.queryForObject(sql, Integer.class, name.trim());
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if category exists by ID.
     */
    public static boolean existsById(String categoryId) {
        if (jdbcTemplate == null || categoryId == null || categoryId.isBlank()) {
            return false;
        }
        
        String sql = "SELECT 1 FROM task_categories WHERE id = ? LIMIT 1";
        try {
            Integer result = jdbcTemplate.queryForObject(sql, Integer.class, categoryId);
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Delete a category by ID (static method).
     */
    public static boolean deleteById(String categoryId) {
        if (jdbcTemplate == null || categoryId == null || categoryId.isBlank()) {
            return false;
        }
        
        String sql = "DELETE FROM task_categories WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, categoryId);
        boolean deleted = rowsAffected > 0;
        
        if (deleted) {
            logger.info("Category deleted: {}", categoryId);
        } else {
            logger.warn("Category not found for deletion: {}", categoryId);
        }
        
        return deleted;
    }
    
    /**
     * RowMapper for converting database rows to Category objects.
     */
    private static RowMapper<Category> getRowMapper() {
        return (rs, rowNum) -> {
            Category category = new Category();
            category.id = rs.getString("id");
            category.name = rs.getString("name");
            category.description = rs.getString("description");
            category.createdAt = rs.getTimestamp("created_at") != null 
                ? rs.getTimestamp("created_at").toLocalDateTime()
                : LocalDateTime.now();
            return category;
        };
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return name != null ? name : "Category";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Category category = (Category) obj;
        return name != null && name.equals(category.name);
    }
    
    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}