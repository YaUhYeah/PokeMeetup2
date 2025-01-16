#!/bin/bash

# Set memory options
JAVA_OPTS="-Xms1G -Xmx4G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200"

# Start server
java $JAVA_OPTS -jar server.jar
