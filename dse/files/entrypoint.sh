#!/bin/bash
# -*- mode: sh -*-
#
# Copyright DataStax, Inc, 2017
#   Please review the included LICENSE file for more information.
#

set -e

. /base-checks.sh

link_external_config "${DSE_HOME}"

#create directories for Spark if the parent directories are found
if [ -d /var/lib/spark ] ; then
  mkdir -p /var/lib/spark/worker
  mkdir -p /var/lib/spark/rdd
fi
if [ -d /var/log/spark ] ; then
  mkdir -p /var/log/spark/worker
  mkdir -p /var/log/spark/master
fi

############################################
# Set up variables/configure the image
############################################

IP_ADDRESS="$(hostname --ip-address)"
CASSANDRA_CONFIG="${DSE_HOME}/resources/cassandra/conf/cassandra.yaml"
CASSANDRA_RACK_CONFIG="${DSE_HOME}/resources/cassandra/conf/cassandra-rackdc.properties"

# SNITCH sets the snitch this node will use. Use GossipingPropertyFileSnitch if not set
: ${SNITCH=GossipingPropertyFileSnitch}

# NATIVE_TRANSPORT_ADDRESS is where we listen for drivers/clients to connect to us. Setting to 0.0.0.0 by default is fine
# since we'll be specifying the NATIVE_TRANSPORT_BROADCAST_ADDRESS below
: ${NATIVE_TRANSPORT_ADDRESS='0.0.0.0'}

# LISTEN_ADDRESS is where we listen for other nodes who want to communicate. 'auto' is not a valid value here,
# so use the hostname's IP by default
: ${LISTEN_ADDRESS='auto'}
if [ "$LISTEN_ADDRESS" = 'auto' ]; then
    LISTEN_ADDRESS="$IP_ADDRESS"
fi

# BROADCAST_ADDRESS is where we tell other nodes to communicate with us. Again, 'auto' is not a valid value here,
# so default to the LISTEN_ADDRESS or the hostname's IP address if set to 'auto'
: ${BROADCAST_ADDRESS="$LISTEN_ADDRESS"}
if [ "$BROADCAST_ADDRESS" = 'auto' ]; then
    BROADCAST_ADDRESS="$IP_ADDRESS"
fi

# By default, tell drivers/clients to use the same address that other nodes are using to communicate with us
: ${NATIVE_TRANSPORT_BROADCAST_ADDRESS=$BROADCAST_ADDRESS}

# SEEDS is for other nodes in the cluster we know about. If not set (because we're the only node maybe), just
# default to ourself
: ${SEEDS:="$BROADCAST_ADDRESS"}

# modify cassandra.yaml only if not linked externally
if should_auto_configure "$CASSANDRA_CONFIG" ; then
    echo "Applying changes to $CASSANDRA_CONFIG ..."

    sed -ri 's/(endpoint_snitch:).*/\1 '"$SNITCH"'/' "$CASSANDRA_CONFIG"

    # Replace the default seeds setting in cassandra.yaml
    sed -ri 's/(- seeds:).*/\1 "'"$SEEDS"'"/' "$CASSANDRA_CONFIG"

    # Update the following settings in the cassandra.yaml file based on the ENV variable values
    for name in \
        broadcast_address \
        native_transport_broadcast_address \
        cluster_name \
        listen_address \
        num_tokens \
        native_transport_address \
        start_native_transport \
        ; do
        var="${name^^}"
        val="${!var}"
        if [ "$val" ]; then
          sed -ri 's/^(# )?('"$name"':).*/\2 '"$val"'/' "$CASSANDRA_CONFIG"
        fi
    done
    echo "done."
fi

if should_auto_configure "$CASSANDRA_RACK_CONFIG" ; then
    echo "Applying changes to $CASSANDRA_RACK_CONFIG ..."
    for rackdc in dc rack; do
        var="${rackdc^^}"
        val="${!var}"
        if [ "$val" ]; then
            sed -ri 's/^('"$rackdc"'=).*/\1 '"$val"'/' "$CASSANDRA_RACK_CONFIG"
        fi
    done
    echo "done."
fi

if [ ! -z "$OPSCENTER_IP" ]; then
    echo "Configuring agent to connect to OpsCenter (${OPSCENTER_IP}) "
    cat > "$DSE_AGENT_HOME/conf/address.yaml" <<EOF
stomp_interface: ${OPSCENTER_IP}
use_ssl: 0
local_interface: ${IP_ADDRESS}
hosts: ["${IP_ADDRESS}"]
cassandra_install_location: $DSE_HOME
cassandra_log_location: /var/log/cassandra
EOF

$DSE_AGENT_HOME/bin/datastax-agent

fi

# Run the command
if [ "$USE_MGMT_API" = "true" ] && [ -d "$MAAC_PATH" ] ; then
    echo "Starting Management API"

    # Ensure jmxremote.password file is linked and specified in cassandra-env.sh, if it exists in /config
    if [ -e "/config/jmxremote.password" ] && [ ! -e "$CASSANDRA_CONF/jmxremote.password" ]; then
        # link jmxremote.password to CASSANDRA_CONF
        ln -s /config/jmxremote.password $CASSANDRA_CONF/jmxremote.password
        # add jmxremote.password file to cassandra-env.sh
        if ! grep -qx "^JVM_OPTS=\"\$JVM_OPTS -Dcom.sun.management.jmxremote.password.file=.*" < ${CASSANDRA_CONF}/cassandra-env.sh ; then
            echo "JVM_OPTS=\"\$JVM_OPTS -Dcom.sun.management.jmxremote.password.file=${CASSANDRA_CONF}/jmxremote.password\"" >> ${CASSANDRA_CONF}/cassandra-env.sh
        fi
    fi

    if [ ! -z "$MGMT_API_DISABLE_MCAC" ]; then
      echo "JVM_OPTS=\"\$JVM_OPTS -Dinsights.default_mode=disabled\"" >> ${CASSANDRA_CONF}/cassandra-env.sh
    fi

    MGMT_API_ARGS=""
    # set the listen port to 8080 if not already set
    : ${MGMT_API_LISTEN_TCP_PORT='8080'}
    # Hardcoding these for now
    MGMT_API_CASSANDRA_SOCKET="--cassandra-socket /tmp/cassandra.sock"
    MGMT_API_LISTEN_TCP="--host tcp://0.0.0.0:${MGMT_API_LISTEN_TCP_PORT}"
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

    if [ ! -z "$MGMT_API_NO_KEEP_ALIVE" ]; then
        MGMT_API_NO_KEEP_ALIVE="--no-keep-alive $MGMT_API_NO_KEEP_ALIVE"
        MGMT_API_ARGS="$MGMT_API_ARGS $MGMT_API_NO_KEEP_ALIVE"
    fi

    # Add Management API Agent to JVM_OPTS
    MGMT_AGENT_JAR="${MAAC_PATH}/datastax-mgmtapi-agent.jar"
    if ! grep -qxF "JVM_OPTS=\"\$JVM_OPTS -javaagent:${MGMT_AGENT_JAR}\"" < ${CASSANDRA_CONF}/cassandra-env.sh ; then
        # ensure newline at end of file
        echo "" >> ${CASSANDRA_CONF}/cassandra-env.sh
        echo "JVM_OPTS=\"\$JVM_OPTS -javaagent:${MGMT_AGENT_JAR}\"" >> ${CASSANDRA_CONF}/cassandra-env.sh
    fi

    MGMT_API_JAR="${MAAC_PATH}/datastax-mgmtapi-server.jar"

    # use default of 128m heap if env variable not set
    : "${MGMT_API_HEAP_SIZE:=128m}"

    # locate Java 11 for running the server
    if [ "$JAVA11_JAVA" = "" ]; then
        # use default Java if it reports version 11
        DEFAULT_JAVA_VERSION=$(java -version 2>&1|awk -F '"' '/version/ {print $2}')
        echo "Default Java version: ${DEFAULT_JAVA_VERSION}"
        if [[ $DEFAULT_JAVA_VERSION == 11* ]]; then
            # Java version seems to be 11
            JAVA11_JAVA=java
        else
            # find java 11
            JAVA11_HOME=$(find /usr/lib/jvm -type d -name "*java-11*")
            echo "Found JAVA11 HOME: ${JAVA11_HOME}"
            JAVA11_JAVA=${JAVA11_HOME}/bin/java
        fi
    fi

    # some uses of these images require /tmp/dse.sock to exist, symlink it here
    ln -s /tmp/cassandra.sock /tmp/dse.sock

    echo "Running" ${JAVA11_JAVA} ${MGMT_API_JAVA_OPTS} -Xms${MGMT_API_HEAP_SIZE} -Xmx${MGMT_API_HEAP_SIZE} -jar "$MGMT_API_JAR" $MGMT_API_ARGS
    ${JAVA11_JAVA} ${MGMT_API_JAVA_OPTS} -Xms${MGMT_API_HEAP_SIZE} -Xmx${MGMT_API_HEAP_SIZE} -jar "$MGMT_API_JAR" $MGMT_API_ARGS
else
    echo "Running $@"
    exec "$@"
fi
