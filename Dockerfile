# ------------------------------------------------------------------------------
# 성능 측정용 앱 이미지. multi-stage 빌드로 최종 이미지에 gradle과 소스가 남지 않게 한다.
# ------------------------------------------------------------------------------

# 빌드 단계: Gradle + JDK 21로 bootJar 생성.
FROM gradle:8.10-jdk21 AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

# 런타임 단계: JRE만 남긴 slim 이미지. 실행할 jar만 복사.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/app.jar
EXPOSE 8080 9091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
