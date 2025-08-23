#!/bin/bash

# StudySync Fast Startup Script
# Optimizes JVM settings for faster desktop application startup

# JVM optimization flags for faster startup (Java 21 compatible)
export JAVA_OPTS="
-XX:+UseG1GC
-XX:+TieredCompilation
-XX:TieredStopAtLevel=1
-XX:+DisableExplicitGC
-XX:+UseStringDeduplication
-XX:+UseCompressedOops
-XX:+UseCompressedClassPointers
-Xms256m
-Xmx1g
-XX:NewRatio=2
-XX:SurvivorRatio=8
-XX:MaxGCPauseMillis=50
-XX:InitiatingHeapOccupancyPercent=35
-XX:G1HeapRegionSize=16m
-Djava.awt.headless=false
-Dspring.main.lazy-initialization=true
-Dspring.jpa.show-sql=false
-Dlogging.level.org.springframework=WARN
-Dlogging.level.org.hibernate=WARN
-Dlogging.level.com.zaxxer.hikari=WARN
"

echo "Starting StudySync with optimized JVM settings..."
echo "JVM Options: $JAVA_OPTS"

# Run the application with optimizations
if [ $# -eq 0 ]; then
    ./gradlew bootRun
else
    ./gradlew bootRun --args="$*"
fi