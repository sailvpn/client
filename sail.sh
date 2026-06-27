#!/bin/bash

# Configuration
JAR_NAME="my_app.jar"
PID_FILE="app.pid"

case "$1" in
    start)
        # Check if PID file exists AND the process is genuinely running
        if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
            echo "Application is already running (PID: $(cat $PID_FILE))."
        else
            echo "Starting application..."
            nohup java -jar "$JAR_NAME" > app.log 2>&1 &
            echo $! > "$PID_FILE"
            echo "Application started in background (PID: $!)."
        fi
        ;;
    stop)
        if [ -f "$PID_FILE" ]; then
            PID=$(cat "$PID_FILE")

            # Check if the process is actually running before killing
            if kill -0 "$PID" 2>/dev/null; then
                echo "Stopping application (PID: $PID)..."
                kill "$PID"

                # Wait up to 5 seconds for it to shut down gracefully
                for i in {1..5}; do
                    if ! kill -0 "$PID" 2>/dev/null; then
                        break
                    fi
                    sleep 1
                done

                # Force kill if it's still stubborn
                if kill -0 "$PID" 2>/dev/null; then
                    echo "Application did not stop gracefully. Forcing shutdown..."
                    kill -9 "$PID"
                fi
            else
                echo "Process $PID is not running, cleaning up stale PID file."
            fi

            rm "$PID_FILE"
            echo "Application stopped."
        else
            echo "PID file not found. Is the application running?"
        fi
        ;;
    status)
        # Bonus: Easily check if your app is alive anytime
        if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
            echo "Application is RUNNING (PID: $(cat $PID_FILE))."
        else
            echo "Application is STOPPED."
        fi
        ;;
    *)
        echo "Usage: $0 {start|stop|status}"
        exit 1
        ;;
esac

