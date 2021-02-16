# Odoo connId connector

## Features

- look up Odoo models & fields and provide them as schema to connId
- CRUD operations
- handle basic data types like char, selection, integer etc
- handle relational data types many2one, one2many and many2many
- allows modelling Odoo "res.groups" as entitlements in midpoint

## How to build

Call gradle task *jar*:

> ./gradlew clean jar

The built connector jar file can then be found in the folder *./build/libs/*.

However, we recommend using the gradle *build* task instead which also runs unit tests but requires Odoo to be run with some basic setup,
see instructions below.

## How to Test Connector

### Using unit test

Unit tests require an Odoo instance running. Follow step 1 and 2 of the next paragraph to start an Odoo instance. See ConnectorTest for test
configuration.

### Using midpoint

Follow these steps:

1. start docker-compose file *docker/docker-compose-odoo.yml*
2. open Odoo at http://localhost:10082 and fill in the initial form:
    - database: db1
    - password: secret
    - email: admin
    - demo data: ✓
3. start gradle task *testInMidpoint* which builds the connector JAR and invokes
   *docker/docker-compose-midpoint.yml*
4. open http://localhost: and log in with *administrator* and *5ecr3t*
5. the odoo connector resource is already defined but if you want to add a new resource using the Odoo connector then use parameters:
    - url: http://odoo:8069
    - database: db1
    - user name: admin
    - password: secret
6. test by triggering synchronization manually, e.g. use the import task of "res.groups" entitlements that imports Odoo groups as midpoint
   roles with prefix "Odoo_"
7. test by add new midpoint user and assign Odoo resource or Odoo role (when groups imported before)
8. when you are finished, type key + ENTER in gradle console to properly stop midpoint containers

You can skip step 1 and 2 if already done once. Midpoint data will not be lost on gradle task restart. Killing gradle process before step 7
requires manual stopping of midpoint docker containers.

If you do want to start from scratch with midpoint, you will need to remove the docker containers for midpoint and its database, and
afterwards execute

> docker volume prune

which removes any unused docker volumes. Then continue with step 3.

You can debug the connector using **remote debugging** in your IDE. Port 5005 is exposed from midpoint container for this purpose.

## Limitations

- When using the "expand relations" feature, create and update operations may performs multiple API calls which are not covered by a single
  transaction. This may cause trouble regarding data consistency when other write operations are executed on the same records in Odoo. Also
  rollback in case of partial execution due to an error is implemented but may fail if data is updated in parallel.

- Some necessary escaping is undocumented in Odoo API, see TODOs.

## Resources

- connector development guide:
  https://docs.evolveum.com/connectors/connid/1.x/connector-development-guide/

- odoo v14 external API documentation:
  https://www.odoo.com/documentation/14.0/webservices/odoo.html

- connid framework javadocs (source files not distributed in repo)
  https://connid.tirasa.net/apidocs/1.5/index.html