#!/bin/bash

# Configuration
JAR_NAME="my_app.jar"
PID_FILE="app.pid"

case "$1" in
    start)
        if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
            echo "Application is already running."
        else
            echo "Starting application..."
            # nohup and & run it in the background; stdout redirects to app.log
            nohup java -jar "$JAR_NAME" > app.log 2>&1 &
            # Save the background PID to a file
            echo $! > "$PID_FILE"
            echo "Application started in background."
        fi
        ;;
    stop)
        if [ -f "$PID_FILE" ]; then
            PID=$(cat "$PID_FILE")
            echo "Stopping application (PID: $PID)..."
            kill "$PID"
            rm "$PID_FILE"
            echo "Application stopped."
        else
            echo "PID file not found. Is the application running?"
        fi
        ;;
    *)
        echo "Usage: $0 {start|stop}"
        exit 1
        ;;
esac
