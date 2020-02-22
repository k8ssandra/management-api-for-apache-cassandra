FROM cassandra:3.11

COPY management-api-common/target/datastax-mgmtapi-common-0.1.0-SNAPSHOT.jar /etc/cassandra/
COPY management-api-agent/target/datastax-mgmtapi-agent-0.1.0-SNAPSHOT.jar /etc/cassandra/
COPY management-api-server/target/datastax-mgmtapi-server-0.1.0-SNAPSHOT.jar /opt/mgmtapi/
COPY scripts/entrypoint.sh /opt/mgmtapi/
RUN grep -qxF "JVM_OPTS=\"\$JVM_OPTS -javaagent:/etc/cassandra/datastax-mgmtapi-agent-0.1.0-SNAPSHOT.jar\"" || echo "JVM_OPTS=\"\$JVM_OPTS -javaagent:/etc/cassandra/datastax-mgmtapi-agent-0.1.0-SNAPSHOT.jar\"" >> /etc/cassandra/cassandra-env.sh

EXPOSE 9103
EXPOSE 8080

ENTRYPOINT ["/opt/mgmtapi/entrypoint.sh"]
