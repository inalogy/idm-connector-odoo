FROM evolveum/midpoint:4.2

MAINTAINER info@evolveum.com

ENV MP_DIR /opt/midpoint

# use existing keystore, otherwise midpoint would re-generate keys on startup (and cannot
# decrypt password from predefined odoo connector)
COPY mp-home/ ${MP_DIR}/var/
