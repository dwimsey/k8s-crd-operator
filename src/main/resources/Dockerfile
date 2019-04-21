FROM openjdk:8-jdk-alpine
VOLUME /tmp
ADD run.sh /run.sh
RUN chmod +x /run.sh
ENTRYPOINT [ "sh", "-c", "/run.sh" ]
EXPOSE 8080
ADD @project.build.finalName@.jar  /@project.build.finalName@.jar
