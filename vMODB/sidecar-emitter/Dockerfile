FROM eclipse-temurin:21
WORKDIR /app
COPY target/sidecar-emitter-1.0-SNAPSHOT.jar emitter.jar
ENTRYPOINT ["java", "-jar", "emitter.jar"]