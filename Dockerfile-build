FROM maven:3.6.3-jdk-8-slim as builder

WORKDIR /build

COPY pom.xml ./
COPY management-api-agent/pom.xml ./management-api-agent/pom.xml
COPY management-api-common/pom.xml ./management-api-common/pom.xml
COPY management-api-server/pom.xml ./management-api-server/pom.xml
COPY management-api-shim-3.x/pom.xml ./management-api-shim-3.x/pom.xml
COPY management-api-shim-4.x/pom.xml ./management-api-shim-4.x/pom.xml
# this duplicates work done in the next steps, but this should provide
# a solid cache layer that only gets reset on pom.xml changes
RUN mvn -T 1C install && rm -rf target

COPY management-api-agent ./management-api-agent
COPY management-api-common ./management-api-common
COPY management-api-server ./management-api-server
COPY management-api-shim-3.x ./management-api-shim-3.x
COPY management-api-shim-4.x ./management-api-shim-4.x
RUN mvn package -DskipTests

FROM cassandra:3.11

COPY --from=builder /build/management-api-common/target/datastax-mgmtapi-common-0.1.0-SNAPSHOT.jar /etc/cassandra/
COPY --from=builder /build/management-api-agent/target/datastax-mgmtapi-agent-0.1.0-SNAPSHOT.jar /etc/cassandra/
COPY --from=builder /build/management-api-server/target/datastax-mgmtapi-server-0.1.0-SNAPSHOT.jar /opt/mgmtapi/
COPY --from=builder /build/management-api-shim-3.x/target/datastax-mgmtapi-shim-3.x-0.1.0-SNAPSHOT.jar /opt/mgmtapi/
COPY --from=builder /build/management-api-shim-4.x/target/datastax-mgmtapi-shim-4.x-0.1.0-SNAPSHOT.jar /opt/mgmtapi/

EXPOSE 9103
EXPOSE 8080

ENV TINI_VERSION v0.18.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini /tini
RUN chmod +x /tini

COPY scripts/docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh
RUN ln -sf /usr/local/bin/docker-entrypoint.sh /docker-entrypoint.sh # backwards compat

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["mgmtapi"]
