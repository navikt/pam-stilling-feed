FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache bash
RUN adduser -u 1000 apprunner -D
USER apprunner

EXPOSE 8080

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"
ENV JAVA_OPTS="--enable-preview -XX:-OmitStackTraceInFastThrow -Xms256m -Xmx2304m"

COPY build/install/*/lib /lib
CMD ["java", "-cp", "/lib/*", "no.nav.pam.stilling.feed.ApplicationKt"]
