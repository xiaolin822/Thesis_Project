FROM eclipse-temurin:21
WORKDIR /app
COPY target/customer-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar
ENTRYPOINT ["java", "--enable-preview", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED", "--add-opens", "java.base/jdk.internal.util=ALL-UNNAMED", "-jar", "app.jar"]