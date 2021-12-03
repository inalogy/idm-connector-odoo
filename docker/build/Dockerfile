FROM openjdk:11.0.13-jdk-slim-bullseye

RUN mkdir /output
WORKDIR /work
COPY . /work
RUN ls -l
RUN ./gradlew clean jar

CMD cp build/libs/* /output
