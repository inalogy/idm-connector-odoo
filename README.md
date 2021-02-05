# Odoo connId connector

## How to Test Connector

### Using unit test

tbd

### Using midpoint

Follow these steps:

1. start docker-compose file *docker/docker-compose-odoo.yml*
2. open Odoo at http://localhost:10082 and fill in the initial form:
    - database: db1
    - password: secret
    - email: admin
    - demo data: ✓
3. start gradle task *testInMidpoint* which builds the connector JAR and invokes *docker/docker-compose-midpoint.yml*
4. open http://localhost: and log in with *administrator* and *5ecr3t*
5. the odoo connector resource is already defined but if you want to add a new resource using the Odoo connector then use parameters:
    - url: http://odoo:8069
    - database: db1
    - user name: admin
    - password: secret
6. test by triggering synchronization manually
7. when you are finished, type key + ENTER in gradle console to properly shutdown midpoint containers.

You can skip step 1 and 2 if already done once. Midpoint data will be lost on gradle task restart. Killing gradle process before step 7
requires manual removing of midpoint docker containers.

You can debug the connector using **remote debugging** in your IDE. Port 5005 is exposed from midpoint container for this purpose.

## Resources

- connector development guide:
  https://docs.evolveum.com/connectors/connid/1.x/connector-development-guide/

- odoo v14 external API documentation:
  https://www.odoo.com/documentation/14.0/webservices/odoo.html

- connid framework javadocs (source files not distributed in repo)
  https://connid.tirasa.net/apidocs/1.5/index.html