#!/bin/bash

_ip_address() {
	# scrape the first non-localhost IP address of the container
	# in Swarm Mode, we often get two IPs -- the container IP, and the (shared) VIP, and the container IP should always be first
	ip address | awk '
		$1 == "inet" && $NF != "lo" {
			gsub(/\/.+$/, "", $2)
			print $2
			exit
		}
	'
}

# "sed -i", but without "mv" (which doesn't work on a bind-mounted file, for example)
_sed-in-place() {
	local filename="$1"; shift
	local tempFile
	tempFile="$(mktemp)"
	sed "$@" "$filename" > "$tempFile"
	cat "$tempFile" > "$filename"
	rm "$tempFile"
}

# Copy over any config files mounted at /config
# cp /config/cassandra.yaml /etc/cassandra/cassandra.yaml
if [ -d "/config" ] && ! [ "/config" -ef "$CASSANDRA_CONF" ]; then
	cp -R /config/* "${CASSANDRA_CONF:-/etc/cassandra}"
fi

# Make sure the management api agent jar is set
# We do this here for the following reasons:
# 1. configbuilder will overwrite the cassandra-env-sh, so we don't want to set this after
# 2. We don't wan't operator or configbuilder to care so much about the version number or
#    the fact this jar even exists.
grep -qxF "JVM_OPTS=\"\$JVM_OPTS -javaagent:/etc/cassandra/datastax-mgmtapi-agent-0.1.0-SNAPSHOT.jar\"" || echo "JVM_OPTS=\"\$JVM_OPTS -javaagent:/etc/cassandra/datastax-mgmtapi-agent-0.1.0-SNAPSHOT.jar\"" >> /etc/cassandra/cassandra-env.sh

if [ -d "/opt/mgmtapi" ] ; then
    echo "Starting Management API"

    MGMT_API_ARGS=""

    # Hardcoding these for now
    DSE_MGMT_DSE_SOCKET="--cassandra-socket /tmp/cassandra.sock"
    DSE_MGMT_LISTEN_TCP="--host tcp://0.0.0.0:8080"
    DSE_MGMT_LISTEN_SOCKET="--host file:///tmp/oss-mgmt.sock"

    MGMT_API_ARGS="$MGMT_API_ARGS $DSE_MGMT_DSE_SOCKET $DSE_MGMT_LISTEN_TCP $DSE_MGMT_LISTEN_SOCKET"

    # These will generally come from the dse-operator
    if [ ! -z "$DSE_MGMT_EXPLICIT_START" ]; then
        DSE_MGMT_EXPLICIT_START="--explicit-start $DSE_MGMT_EXPLICIT_START"
        MGMT_API_ARGS="$MGMT_API_ARGS $DSE_MGMT_EXPLICIT_START"
    fi

    if [ ! -z "$DSE_MGMT_TLS_CA_CERT_FILE" ]; then
        DSE_MGMT_TLS_CA_CERT_FILE="--tlscacert $DSE_MGMT_TLS_CA_CERT_FILE"
        MGMT_API_ARGS="$MGMT_API_ARGS $DSE_MGMT_TLS_CA_CERT_FILE"
    fi
    if [ ! -z "$DSE_MGMT_TLS_CERT_FILE" ]; then
        DSE_MGMT_TLS_CERT_FILE="--tlscert $DSE_MGMT_TLS_CERT_FILE"
        MGMT_API_ARGS="$MGMT_API_ARGS $DSE_MGMT_TLS_CERT_FILE"
    fi
    if [ ! -z "$DSE_MGMT_TLS_KEY_FILE" ]; then
        DSE_MGMT_TLS_KEY_FILE="--tlskey $DSE_MGMT_TLS_KEY_FILE"
        MGMT_API_ARGS="$MGMT_API_ARGS $DSE_MGMT_TLS_KEY_FILE"
    fi

    if [ ! -z "$DSE_MGMT_PID_FILE" ]; then
        DSE_MGMT_PID_FILE="--pidfile $DSE_MGMT_PID_FILE"
        MGMT_API_ARGS="$MGMT_API_ARGS $DSE_MGMT_PID_FILE"
    fi

    DSE_MGMT_DSE_PATH="--cassandra-home /usr/"
    MGMT_API_ARGS="$MGMT_API_ARGS $DSE_MGMT_DSE_PATH"

	export CASSANDRA_CONF=/etc/cassandra

    if [ ! -z "$DSE_MGMT_NO_KEEP_ALIVE" ]; then
        DSE_MGMT_NO_KEEP_ALIVE="--no-keep-alive $DSE_MGMT_NO_KEEP_ALIVE"
        MGMT_API_ARGS="$MGMT_API_ARGS $DSE_MGMT_NO_KEEP_ALIVE"
    fi

    MGMT_API_JAR="$(find "/opt/mgmtapi" -name *.jar)"

    echo "Running" java -Xms128m -Xmx128m -jar "$MGMT_API_JAR" $MGMT_API_ARGS
    exec java -Xms128m -Xmx128m -jar "$MGMT_API_JAR" $MGMT_API_ARGS
else
    echo "Running $@"
    exec "$@"
fi
