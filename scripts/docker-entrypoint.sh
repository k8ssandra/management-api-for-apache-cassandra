#!/bin/bash
set -e

# first arg is `-f` or `--some-option`
# or there are no args
if [ "$#" -eq 0 ] || [ "${1#-}" != "$1" ]; then
    set -- cassandra -f "$@"
fi

if [ "$CASSANDRA_CONF" == "" ]; then
    export CASSANDRA_CONF=/etc/cassandra
fi

# allow the container to be started with `--user`
if [ "$1" = 'mgmtapi' -a "$(id -u)" = '0' ]; then
    find "$CASSANDRA_CONF" /var/lib/cassandra /var/log/cassandra \
        \! -user cassandra -exec chown cassandra '{}' +
    # exec gosu cassandra "$BASH_SOURCE" "$@"
fi

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

_metrics_collector_supported() {
    # currently, metrics collector does not work on arm64
    [ "$(uname -m)" != "aarch64" ]
}

if [ "$1" = 'mgmtapi' ]; then
    echo "Starting Management API"

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
    
    if _metrics_collector_supported && ! grep -qxF "JVM_OPTS=\"\$JVM_OPTS -javaagent:${MCAC_PATH}/lib/datastax-mcac-agent.jar\"" < ${CASSANDRA_CONF}/cassandra-env.sh ; then
        # ensure newline at end of file
        echo "" >> ${CASSANDRA_CONF}/cassandra-env.sh
        echo "JVM_OPTS=\"\$JVM_OPTS -javaagent:${MCAC_PATH}/lib/datastax-mcac-agent.jar\"" >> ${CASSANDRA_CONF}/cassandra-env.sh
        mkdir -p ${MCAC_PATH}
        echo "" >> ${MCAC_PATH}/config/metric-collector.yaml
        echo "data_dir_max_size_in_mb: 100" >> ${MCAC_PATH}/config/metric-collector.yaml
    fi

    MGMT_AGENT_JAR="$(find "${MAAC_PATH}" -name *datastax-mgmtapi-agent*.jar)"
    if ! grep -qxF "JVM_OPTS=\"\$JVM_OPTS -javaagent:${MGMT_AGENT_JAR}\"" < ${CASSANDRA_CONF}/cassandra-env.sh ; then
        # ensure newline at end of file
        echo "" >> ${CASSANDRA_CONF}/cassandra-env.sh
        echo "JVM_OPTS=\"\$JVM_OPTS -javaagent:${MGMT_AGENT_JAR}\"" >> ${CASSANDRA_CONF}/cassandra-env.sh
    fi

    # Set this if you want to ignore default env variables, i.e. when running inside an operator
    if [ $IGNORE_DEFAULTS ] || [ $USE_MGMT_API ]; then
        CASSANDRA_RPC_ADDRESS='0.0.0.0'
        CASSANDRA_BROADCAST_RPC_ADDRESS="$(_ip_address)"
    else
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
        
        CASSANDRA_YAML="cassandra.yaml"
        if [ $CASSANDRA_DEPLOYMENT ]; then
            CASSANDRA_DEPLOYMENT=`echo "$CASSANDRA_DEPLOYMENT" | awk '{print tolower($0)}'`
            CASSANDRA_YAML="cassandra-$CASSANDRA_DEPLOYMENT.yaml"
        fi
        
        _sed-in-place "$CASSANDRA_CONF/$CASSANDRA_YAML" \
            -r 's/(- seeds:).*/\1 "'"$CASSANDRA_SEEDS"'"/'

        for yaml in \
            broadcast_address \
            broadcast_rpc_address \
            cluster_name \
            endpoint_snitch \
            listen_address \
            num_tokens \
            rpc_address \
            start_rpc \
        ; do
            var="CASSANDRA_${yaml^^}"
            val="${!var}"
            if [ "$val" ]; then
                _sed-in-place "$CASSANDRA_CONF/$CASSANDRA_YAML" \
                    -r 's/^(# )?('"$yaml"':).*/\2 '"$val"'/'
            fi
        done

        for rackdc in dc rack; do
            var="CASSANDRA_${rackdc^^}"
            val="${!var}"
            if [ "$val" ]; then
                _sed-in-place "$CASSANDRA_CONF/cassandra-rackdc.properties" \
                    -r 's/^('"$rackdc"'=).*/\1 '"$val"'/'
            fi
        done
    fi

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

    MGMT_API_CASSANDRA_HOME="--cassandra-home ${CASSANDRA_HOME}"
    MGMT_API_ARGS="$MGMT_API_ARGS $MGMT_API_CASSANDRA_HOME"

    if [ ! -z "$MGMT_API_NO_KEEP_ALIVE" ]; then
        MGMT_API_NO_KEEP_ALIVE="--no-keep-alive $MGMT_API_NO_KEEP_ALIVE"
        MGMT_API_ARGS="$MGMT_API_ARGS $MGMT_API_NO_KEEP_ALIVE"
    fi

    MGMT_API_JAR="$(find "${MAAC_PATH}" -name *server*.jar)"

    # use default of 128m heap if env variable not set
    : "${MGMT_API_HEAP_SIZE:=128m}"
    echo "Running" java ${MGMT_API_JAVA_OPTS} -Xms${MGMT_API_HEAP_SIZE} -Xmx${MGMT_API_HEAP_SIZE} -jar "$MGMT_API_JAR" $MGMT_API_ARGS
    java ${MGMT_API_JAVA_OPTS} -Xms${MGMT_API_HEAP_SIZE} -Xmx${MGMT_API_HEAP_SIZE} -jar "$MGMT_API_JAR" $MGMT_API_ARGS
fi

exec "$@"
