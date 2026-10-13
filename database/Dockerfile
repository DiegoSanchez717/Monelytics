# Keep the official PostgreSQL initialization and storage contract. The index
# digest pins every supported architecture, rather than only the local amd64 image.
FROM postgres:17.11-alpine3.24@sha256:b0f9560a2de083e2cc7382e75f808c7381a32852a7ec49117deedb300e552b24

# Upstream gosu 1.19 embeds Go 1.24.6. PostgreSQL only needs its privilege-drop
# operation, which Alpine's native su-exec implements without a Go runtime.
# Patch both entry points: docker-enforce-initdb.sh links to ensure-initdb.sh.
RUN set -eux; \
    apk add --no-cache su-exec=0.3-r0; \
    for script in /usr/local/bin/docker-entrypoint.sh /usr/local/bin/docker-ensure-initdb.sh; do \
        grep -q 'exec gosu postgres ' "$script"; \
        sed -i 's/exec gosu postgres /exec su-exec postgres /g' "$script"; \
        grep -q 'exec su-exec postgres ' "$script"; \
        ! grep -q 'exec gosu ' "$script"; \
    done; \
    rm /usr/local/bin/gosu; \
    su-exec postgres sh -c 'test "$(id -u)" = 70 && test "$(id -g)" = 70'; \
    test ! -e /usr/local/bin/gosu

# ENTRYPOINT, CMD, PGDATA, VOLUME, and SIGINT are inherited unchanged.
