package com.studysync.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Immutable record representing a star-based priority level for tasks in the StudySync system.
 * 
 * <p>TaskPriority uses a five-star rating system to categorize task importance and urgency.
 * This provides an intuitive way for users to understand and assign priorities to their tasks.
 * The star system ranges from 1 (lowest priority) to 5 (highest priority).</p>
 * 
 * <p><strong>Priority Levels:</strong>
 * <ul>
 *   <li><strong>1 Star (★☆☆☆☆):</strong> Low priority - nice to have tasks</li>
 *   <li><strong>2 Stars (★★☆☆☆):</strong> Low-medium priority - can be delayed</li>
 *   <li><strong>3 Stars (★★★☆☆):</strong> Medium priority - normal importance</li>
 *   <li><strong>4 Stars (★★★★☆):</strong> High priority - important tasks</li>
 *   <li><strong>5 Stars (★★★★★):</strong> Critical priority - urgent and important</li>
 * </ul></p>
 * 
 * <p><strong>Usage Examples:</strong>
 * <pre>
 * // Create a high priority task
 * TaskPriority highPriority = new TaskPriority(4);
 * 
 * // Create using factory method for JSON
 * TaskPriority criticalPriority = TaskPriority.of(5);
 * 
 * // Default low priority
 * TaskPriority defaultPriority = new TaskPriority();
 * </pre></p>
 * 
 * <p><strong>Design Notes:</strong> This class is implemented as a Java record for immutability
 * and value semantics. It includes JSON serialization support via Jackson annotations and
 * Bean Validation constraints for data integrity.</p>
 * 
 * @param stars the star rating (1-5) for this priority level
 * @author geokoko 
 * @version 0.1.0-BETA
 * @since 0.1.0
 * @see Task
 */
public record TaskPriority(
    @JsonProperty("stars")
    @Min(value = 1, message = "Priority must be at least 1 star")
    @Max(value = 5, message = "Priority cannot exceed 5 stars")
    int stars) {

    /**
     * Compact constructor with validation to ensure stars are within valid range.
     * 
     * <p>This constructor is automatically generated for records and includes
     * validation to ensure the star rating is between 1 and 5 inclusive.</p>
     * 
     * @throws IllegalArgumentException if stars is not between 1 and 5
     */
    public TaskPriority {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Priority stars must be between 1 and 5");
        }
    }

    /**
     * Creates a TaskPriority with the lowest priority (1 star).
     * 
     * <p>This constructor provides a convenient way to create low-priority tasks
     * without explicitly specifying the star count.</p>
     */
    public TaskPriority() {
        this(1);
    }

    /**
     * Factory method for creating TaskPriority instances from JSON.
     * 
     * <p>This method is used by Jackson during JSON deserialization to create
     * TaskPriority objects from JSON data. It provides the same validation as
     * the primary constructor.</p>
     * 
     * @param stars the star rating (1-5) from JSON
     * @return a new TaskPriority instance
     * @throws IllegalArgumentException if stars is not between 1 and 5
     */
    @JsonCreator
    public static TaskPriority of(@JsonProperty("stars") int stars) {
        return new TaskPriority(stars);
    }

    /**
     * Returns the star rating of this priority.
     * 
     * <p>This method provides backward compatibility with older code that expects
     * a getter method. For new code, the {@code stars()} record accessor method
     * should be preferred.</p>
     * 
     * @return the star rating (1-5)
     * @deprecated Use {@code stars()} record accessor instead
     */
    @Deprecated
    public int getStars() {
        return stars;
    }

    /**
     * Returns a visual string representation of this priority using star symbols.
     * 
     * <p>This method creates an intuitive visual representation where filled stars (★)
     * represent the priority level and empty stars (☆) show the remaining levels.
     * This is useful for displaying priorities in user interfaces.</p>
     * 
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>1 star: "★☆☆☆☆"</li>
     *   <li>3 stars: "★★★☆☆"</li>
     *   <li>5 stars: "★★★★★"</li>
     * </ul></p>
     * 
     * @return a visual representation using star symbols
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            if (i <= stars) {
                sb.append("★");
            } else {
                sb.append("☆");
            }
        }
        return sb.toString();
    }

    /**
     * Returns a simple text representation of the star rating.
     * 
     * <p>This method provides a plain text description that's useful for
     * accessibility, logging, or contexts where star symbols aren't appropriate.
     * The text correctly handles singular vs plural forms.</p>
     * 
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>1 star: "1 star"</li>
     *   <li>3 stars: "3 stars"</li>
     *   <li>5 stars: "5 stars"</li>
     * </ul></p>
     *
     * @return the star rating as text (e.g., "3 stars")
     */
    public String toText() {
        return stars + " star" + (stars == 1 ? "" : "s");
    }
}
