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
    DSE_MGMT_DSE_SOCKET="--dse-socket /tmp/cassandra.sock"
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
