package com.cognitumsoftware.connector.odoo;

import org.identityconnectors.common.security.GuardedString;
import org.junit.Test;

public class ConnectorTest {

    private OdooConnector connector;

    public ConnectorTest() {
        connector = new OdooConnector();
        connector.init(new OdooConfiguration() {{
            setUrl("http://localhost:10082");
            setDatabase("db1");
            setUsername("admin");
            setPassword(new GuardedString("secret".toCharArray()));
        }});
    }

    @Test
    public void testConnection() {
        connector.test();
    }

    @Test
    public void testListDatabases() {
        connector.listDatabases();
    }

    @Test
    public void testSchemaRetrieval() {
        connector.schema();
    }

}
