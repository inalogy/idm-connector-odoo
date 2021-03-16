package com.cognitumsoftware.connector.odoo;

import org.identityconnectors.common.security.GuardedString;

/**
 * Tests against the v12 of Odoo.
 */
public class ConnectorAgainstOdoo12Test extends ConnectorTest {

    public ConnectorAgainstOdoo12Test() {
        super(new OdooConfiguration() {{
            setUrl("http://localhost:12082");
            setDatabase("db1");
            setUsername("admin");
            setPassword(new GuardedString("secret".toCharArray()));
        }});
    }

}
