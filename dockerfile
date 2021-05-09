FROM openjdk:11

COPY app/rest/target/scala-3.0.0-RC1/rest-assembly-0.1.0-SNAPSHOT.jar rest-assembly-0.1.0-SNAPSHOT.jar 

ENTRYPOINT ["java", "-jar", "rest-assembly-0.1.0-SNAPSHOT.jar"]