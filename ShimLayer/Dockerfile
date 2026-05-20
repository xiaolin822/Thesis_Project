FROM eclipse-temurin:21
WORKDIR /app
COPY target/ShimLayer-1.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]