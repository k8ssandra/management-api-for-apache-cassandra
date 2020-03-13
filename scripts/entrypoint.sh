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
if ! grep -qxF "JVM_OPTS=\"\$JVM_OPTS -javaagent:/etc/cassandra/datastax-mgmtapi-agent-0.1.0-SNAPSHOT.jar\""; then
	# ensure newline at end of file
	echo "" >> /etc/cassandra/cassandra-env.sh
	echo "JVM_OPTS=\"\$JVM_OPTS -javaagent:/etc/cassandra/datastax-mgmtapi-agent-0.1.0-SNAPSHOT.jar\"" >> /etc/cassandra/cassandra-env.sh
fi
 

if [ -d "/opt/mgmtapi" ] ; then
    echo "Starting Management API"

	  : ${CASSANDRA_RPC_ADDRESS='0.0.0.0'}

	  : ${CASSANDRA_LISTEN_ADDRESS='auto'}
	  if [ "$CASSANDRA_LISTEN_ADDRESS" = 'auto' ]; then
		  CASSANDRA_LISTEN_ADDRESS="$(_ip_address)"
	  fi

	  : ${CASSANDRA_BROADCAST_ADDRESS="$CASSANDRA_LISTEN_ADDRESS"}

	  if [ "$CASSANDRA_BROADCAST_ADDRESS" = 'auto' ]; then
		  CASSANDRA_BROADCAST_ADDRESS="$(_ip_address)"
	  fi
	  : ${CASSANDRA_BROADCAST_RPC_ADDRESS:=$CASSANDRA_BROADCAST_ADDRESS}

	  if [ -n "${CASSANDRA_NAME:+1}" ]; then
		  : ${CASSANDRA_SEEDS:="cassandra"}
	  fi
	  : ${CASSANDRA_SEEDS:="$CASSANDRA_BROADCAST_ADDRESS"}

    	for yaml in \
		broadcast_address \
		broadcast_rpc_address \
		listen_address \
		rpc_address \
	; do
		var="CASSANDRA_${yaml^^}"
		val="${!var}"
		if [ "$val" ]; then
			_sed-in-place "/etc/cassandra/cassandra.yaml" \
				-r 's/^(# )?('"$yaml"':).*/\2 '"$val"'/'
		fi
	done

    
    MGMT_API_ARGS=""

    # Hardcoding these for now
    MGMT_API_CASSANDRA_SOCKET="--cassandra-socket /tmp/cassandra.sock"
    MGMT_API_LISTEN_TCP="--host tcp://0.0.0.0:8080"
    MGMT_API_LISTEN_SOCKET="--host file:///tmp/oss-mgmt.sock"

    MGMT_API_ARGS="$MGMT_API_ARGS $MGMT_API_CASSANDRA_SOCKET $MGMT_API_LISTEN_TCP $MGMT_API_LISTEN_SOCKET"

    # These will generally come from the k8s operator
    if [ ! -z "$MGMT_API_EXPLICIT_START" ]; then
        MGMT_API_EXPLICIT_START="--explicit-start $MGMT_API_EXPLICIT_START"
        MGMT_API_ARGS="$MGMT_API_ARGS $MGMT_API_EXPLICIT_START"
    fi

    if [ ! -z "$MGMT_API_TLS_CA_CERT_FILE" ]; then
        MGMT_API_TLS_CA_CERT_FILE="--tlscacert $MGMT_API_TLS_CA_CERT_FILE"
        MGMT_API_ARGS="$MGMT_API_ARGS $MGMT_API_TLS_CA_CERT_FILE"
    fi
    if [ ! -z "$MGMT_API_TLS_CERT_FILE" ]; then
        MGMT_API_TLS_CERT_FILE="--tlscert $MGMT_API_TLS_CERT_FILE"
        MGMT_API_ARGS="$MGMT_API_ARGS $MGMT_API_TLS_CERT_FILE"
    fi
    if [ ! -z "$MGMT_API_TLS_KEY_FILE" ]; then
        MGMT_API_TLS_KEY_FILE="--tlskey $MGMT_API_TLS_KEY_FILE"
        MGMT_API_ARGS="$MGMT_API_ARGS $MGMT_API_TLS_KEY_FILE"
    fi

    if [ ! -z "$MGMT_API_PID_FILE" ]; then
        MGMT_API_PID_FILE="--pidfile $MGMT_API_PID_FILE"
        MGMT_API_ARGS="$MGMT_API_ARGS $MGMT_API_PID_FILE"
    fi

    MGMT_API_CASSANDRA_HOME="--cassandra-home /usr/"
    MGMT_API_ARGS="$MGMT_API_ARGS $MGMT_API_CASSANDRA_HOME"

	export CASSANDRA_CONF=/etc/cassandra

    if [ ! -z "$MGMT_API_NO_KEEP_ALIVE" ]; then
        MGMT_API_NO_KEEP_ALIVE="--no-keep-alive $MGMT_API_NO_KEEP_ALIVE"
        MGMT_API_ARGS="$MGMT_API_ARGS $MGMT_API_NO_KEEP_ALIVE"
    fi

    MGMT_API_JAR="$(find "/opt/mgmtapi" -name *.jar)"

    echo "Running" java -Xms128m -Xmx128m -jar "$MGMT_API_JAR" $MGMT_API_ARGS
    exec java -Xms128m -Xmx128m -jar "$MGMT_API_JAR" $MGMT_API_ARGS
else
    echo "Running $@"
    exec "$@"
fi
