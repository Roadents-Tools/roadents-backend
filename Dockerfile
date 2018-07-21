FROM openjdk:10.0.1-jdk

ENV STATDB_NAME localdb
ENV STATDB_URL jdbc:postgres://localhost:5432/Stations_GTFS
ENV LOCDB_NAME localdb
ENV LOCDB_URL jdbc:postgres://localhost:5432/locations
WORKDIR /home/project-donut-server-backend-spark

COPY gradlew .
COPY gradle/ gradle/

COPY build.gradle .
RUN ./gradlew build

COPY src/ ./src
RUN ./gradlew build

EXPOSE 4567

CMD java -XX:+UnlockExperimentalVMOptions \
         -XX:+UseCGroupMemoryLimitForHeap \
         -XX:+UseContainerSupport \
         -jar build/libs/project-donut-server-backend-spark-all.jar \
         --spark

