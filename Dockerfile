FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-27@sha256:9f124e43e7d3c8605f42c6078f898421c9386087d87f1e1829a5bdc4e2a56bea

EXPOSE 8080

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS="--enable-preview -XX:-OmitStackTraceInFastThrow -XX:InitialRAMPercentage=25 -XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

COPY build/install/*/lib /lib
CMD ["-cp", "/lib/*", "no.nav.pam.stilling.feed.ApplicationKt"]
