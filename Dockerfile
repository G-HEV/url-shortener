FROM  maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

COPY ./pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /build/target/url-shortener-0.0.1-SNAPSHOT.jar  ./app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
