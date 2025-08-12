#!/bin/bash

echo "🚀 Oddiya Travel API Starter"
echo "============================"
echo ""
echo "Select environment:"
echo "1) H2 Database (Development/Testing)"
echo "2) PostgreSQL (Production)"
echo "3) Custom profile"
echo ""
read -p "Enter choice [1-3]: " choice

case $choice in
    1)
        PROFILE="h2"
        echo "Starting with H2 in-memory database..."
        ;;
    2)
        PROFILE="local"
        echo "Starting with PostgreSQL..."
        echo "Make sure PostgreSQL is running on localhost:5432"
        ;;
    3)
        read -p "Enter profile name: " PROFILE
        echo "Starting with profile: $PROFILE"
        ;;
    *)
        echo "Invalid choice. Using default profile."
        PROFILE="local"
        ;;
esac

echo ""
read -p "Enter port (default 8080): " PORT
PORT=${PORT:-8080}

echo ""
echo "Starting application..."
echo "Profile: $PROFILE"
echo "Port: $PORT"
echo ""

if [ "$PROFILE" = "h2" ]; then
    echo "📌 H2 Console: http://localhost:$PORT/h2-console"
    echo "   JDBC URL: jdbc:h2:mem:oddiya"
    echo "   Username: sa"
    echo "   Password: (leave empty)"
fi

echo "📌 API Health: http://localhost:$PORT/api/v1/health"
echo "📌 Swagger UI: http://localhost:$PORT/swagger-ui.html"
echo ""
echo "Press Ctrl+C to stop"
echo "============================"
echo ""

# Run the application
./gradlew bootRun --args="--spring.profiles.active=$PROFILE --server.port=$PORT"