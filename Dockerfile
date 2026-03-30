FROM amazoncorretto:25-al2023-headless

WORKDIR /app

ARG XMX=640m
ENV JAVA_OPTS="-Xms256m -Xmx${XMX} -XX:+UseContainerSupport"

COPY build/libs/*.jar app.jar

RUN mkdir -p /app/logs

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]