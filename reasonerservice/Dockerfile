FROM openjdk:18
MAINTAINER "Christoph Fabianek" christoph@ownyourdata.eu
LABEL maintainer="Paul Feichtenschlager <feichtenschlager10@gmail.com>"

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

COPY target/*.jar /usr/src/app/

CMD java -jar *.jar

EXPOSE 8081