package com.studysync.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Immutable record representing a task category for organizing tasks in the StudySync system.
 * 
 * <p>TaskCategory provides a way to group related tasks together, enabling better organization
 * and filtering capabilities. Categories help users structure their work and find tasks more
 * efficiently. Each category is identified by a unique name that must be meaningful to users.</p>
 * 
 * <p><strong>Category Examples:</strong>
 * <ul>
 *   <li><strong>Academic:</strong> School assignments, study sessions, research</li>
 *   <li><strong>Personal:</strong> Household tasks, hobbies, personal projects</li>
 *   <li><strong>Work:</strong> Professional tasks, meetings, deadlines</li>
 *   <li><strong>Health:</strong> Exercise, medical appointments, wellness activities</li>
 * </ul></p>
 * 
 * <p><strong>Usage Examples:</strong>
 * <pre>
 * // Create a category for academic tasks
 * TaskCategory academic = new TaskCategory("Academic");
 * 
 * // Create with default category
 * TaskCategory defaultCat = new TaskCategory();
 * 
 * // Use in task creation
 * Task task = new Task("Study for exam", null, academic.name(), priority, deadline);
 * </pre></p>
 * 
 * <p><strong>Validation Rules:</strong>
 * <ul>
 *   <li>Category name is required (cannot be null or blank)</li>
 *   <li>Name must be between 1 and 50 characters</li>
 *   <li>Whitespace-only names are not allowed</li>
 * </ul></p>
 * 
 * <p><strong>Design Notes:</strong> This class is implemented as a Java record for immutability
 * and value semantics. It includes Jackson annotations for JSON serialization and Bean Validation
 * constraints for data integrity.</p>
 *
 * @param name the category name (required, 1-50 characters)
 * @author geokoko 
 * @version 0.1.0-BETA
 * @since 0.1.0
 * @see Task
 */
public record TaskCategory(
    @JsonProperty("name") 
    @NotBlank(message = "Category name is required")
    @Size(min = 1, max = 50, message = "Category name must be between 1 and 50 characters")
    String name) {
    
    /**
     * Creates a TaskCategory from JSON data with null-safety.
     * 
     * <p>This constructor is used by Jackson during JSON deserialization.
     * It provides null-safety by defaulting to "Default" if a null name is provided,
     * preventing null pointer exceptions during object creation.</p>
     * 
     * @param name the category name from JSON (null-safe)
     */
    @JsonCreator
    public TaskCategory(@JsonProperty("name") String name) {
        this.name = name != null ? name : "Default";
    }
    
    /**
     * Creates a TaskCategory with the default name "Default".
     * 
     * <p>This constructor provides a convenient way to create a category when
     * no specific name is required. It's useful for initialization or when
     * a category will be set later.</p>
     */
    public TaskCategory() {
        this("Default");
    }
    
    /**
     * Returns the category name.
     * 
     * <p>This method provides backward compatibility with older code that expects
     * a getter method. For new code, the {@code name()} record accessor method
     * should be preferred.</p>
     * 
     * @return the category name
     * @deprecated Use {@code name()} record accessor instead
     */
    @Deprecated
    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
        return name;
    }
}
