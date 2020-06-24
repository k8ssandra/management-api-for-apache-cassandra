FROM datastax/dse-server:6.8.0

USER root
RUN set -eux; \
  apt-get update; \
  apt-get install -y --no-install-recommends wget; \
  rm -rf /var/lib/apt/lists/*

ENV DS_LICENSE=accept
ENV PATH $DSE_HOME/bin:$PATH

# smoke test
USER dse:root
RUN dse -v

VOLUME ["/var/lib/cassandra", "/var/lib/spark", "/var/lib/dsefs", "/var/log/cassandra", "/var/log/spark"]

COPY docker-entrypoint.sh /usr/local/bin/
RUN ln -s usr/local/bin/docker-entrypoint.sh /docker-entrypoint.sh # backwards compat
ENTRYPOINT ["docker-entrypoint.sh"]

CMD ["dse", "cassandra", "-f"]
