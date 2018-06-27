FROM openjdk:10.0.1-jdk

WORKDIR /home/project-donut-server-backend-spark

COPY gradlew .
COPY gradle/ gradle/

RUN ls gradle/wrapper/
RUN ./gradlew

COPY build.gradle .
COPY src/ ./src

RUN ./gradlew build

EXPOSE 4567

CMD java -XX:+UnlockExperimentalVMOptions \
         -XX:+UseCGroupMemoryLimitForHeap \
         -XX:+UseContainerSupport \
         -jar build/libs/project-donut-server-backend-spark-all.jar \
         --spark

