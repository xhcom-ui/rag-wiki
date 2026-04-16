# Java服务多阶段构建 Dockerfile
# 用法: docker build --build-arg SERVICE=rag-wiki-auth -t rag-wiki-auth .

FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY rag-wiki-common/pom.xml rag-wiki-common/pom.xml
ARG SERVICE
COPY ${SERVICE}/pom.xml ${SERVICE}/pom.xml
# 下载依赖
RUN mvn dependency:go-offline -pl ${SERVICE} -am -B
# 复制源码并构建
COPY . .
RUN mvn package -pl ${SERVICE} -am -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
ARG SERVICE
ARG PORT=8080
WORKDIR /app
COPY --from=builder /app/${SERVICE}/target/*.jar app.jar
EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
