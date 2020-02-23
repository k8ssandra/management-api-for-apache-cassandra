FROM maven:3.6.3-jdk-8-slim as builder

WORKDIR /build

COPY pom.xml ./
COPY management-api-agent/pom.xml ./management-api-agent/pom.xml
COPY management-api-common/pom.xml ./management-api-common/pom.xml
COPY management-api-server/pom.xml ./management-api-server/pom.xml
RUN mvn dependency:go-offline

COPY . .
RUN ls -l
RUN mvn package -DskipTests

FROM cassandra:3.11

COPY --from=builder /build/management-api-common/target/datastax-mgmtapi-common-0.1.0-SNAPSHOT.jar /etc/cassandra/
COPY --from=builder /build/management-api-agent/target/datastax-mgmtapi-agent-0.1.0-SNAPSHOT.jar /etc/cassandra/
COPY --from=builder /build/management-api-server/target/datastax-mgmtapi-server-0.1.0-SNAPSHOT.jar /opt/mgmtapi/
COPY scripts/entrypoint.sh /opt/mgmtapi/
RUN grep -qxF "JVM_OPTS=\"\$JVM_OPTS -javaagent:/etc/cassandra/datastax-mgmtapi-agent-0.1.0-SNAPSHOT.jar\"" || echo "JVM_OPTS=\"\$JVM_OPTS -javaagent:/etc/cassandra/datastax-mgmtapi-agent-0.1.0-SNAPSHOT.jar\"" >> /etc/cassandra/cassandra-env.sh

EXPOSE 9103
EXPOSE 8080

ENTRYPOINT ["/opt/mgmtapi/entrypoint.sh"]
