FROM openjdk:18
LABEL maintainer="Paul Feichtenshlager <feichtenschlager10@gmail.com>"

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

COPY target/*.jar /usr/src/app/

CMD java -jar *.jar

EXPOSE 8081