# 베이스 이미지 선택
#FROM openjdk:17-jdk-slim
# Amazon Corretto 21 with Amazon Linux 2 for 안정적인 실행 환경
FROM amazoncorretto:21.0.7-al2

# 타임존 설정 (옵션)
ENV TZ=Asia/Seoul

# 작업 디렉토리 설정
WORKDIR /app

# 빌드된 JAR 복사
COPY build/libs/*.jar app.jar

# 애플리케이션 포트 노출 (Spring Boot 기본 포트)
EXPOSE 8080

# 실행 명령
ENTRYPOINT ["java", "-jar", "app.jar"]