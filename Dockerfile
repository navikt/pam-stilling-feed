FROM gcr.io/distroless/java21

COPY build/libs/pam-stilling-feed-all.jar ./app.jar
EXPOSE 8080
ENV JAVA_OPTS="-XX:-OmitStackTraceInFastThrow -Xms256m -Xmx1536m"
ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"

ENTRYPOINT ["java", "-jar", "/app.jar"]