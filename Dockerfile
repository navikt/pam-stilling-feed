FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-27@sha256:e9895023e11f0798e494f5b064ec3c79bbbdde42b061f35b6eb0206be5a00df3

EXPOSE 8080

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS="--enable-preview -XX:-OmitStackTraceInFastThrow -XX:InitialRAMPercentage=25 -XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

COPY build/install/*/lib /lib
CMD ["-cp", "/lib/*", "no.nav.pam.stilling.feed.ApplicationKt"]
