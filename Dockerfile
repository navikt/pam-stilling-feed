FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:bd2b5c78e153b13fab48d0c1159f2925545004545bf49f05b931a66e71ecdf17

EXPOSE 8080

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS="--enable-preview -XX:-OmitStackTraceInFastThrow -XX:InitialRAMPercentage=25 -XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

COPY build/install/*/lib /lib
CMD ["-cp", "/lib/*", "no.nav.pam.stilling.feed.ApplicationKt"]
