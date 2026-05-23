FROM eclipse-temurin:11-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew build

FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /app/build/libs/payment-backend-1.0.0.jar ./app.jar
CMD ["java", "-jar", "app.jar"]