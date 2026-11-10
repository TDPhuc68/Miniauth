# Stage 1: Build JAR với Maven và JDK 21
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy mã nguồn và build package
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime image tối ưu dung lượng với JRE 21
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Tạo user không có đặc quyền root để tăng tính bảo mật
RUN addgroup -S miniauth && adduser -S miniauth -G miniauth
USER miniauth

COPY --from=builder /app/target/miniauth-server-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
