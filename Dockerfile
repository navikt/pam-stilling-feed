FROM ghcr.io/navikt/baseimages/temurin:19

COPY build/libs/pam-stilling-feed-all.jar ./app.jar
EXPOSE 8080
ENV JAVA_OPTS="-XX:-OmitStackTraceInFastThrow -Xms256m -Xmx1536m"
