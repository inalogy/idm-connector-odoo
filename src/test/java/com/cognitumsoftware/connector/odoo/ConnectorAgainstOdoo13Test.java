package com.cognitumsoftware.connector.odoo;

import org.identityconnectors.common.security.GuardedString;

/**
 * Tests against the v13 of Odoo.
 */
public class ConnectorAgainstOdoo13Test extends ConnectorTest {

    public ConnectorAgainstOdoo13Test() {
        super(new OdooConfiguration() {{
            setUrl("http://localhost:13082");
            setDatabase("db1");
            setUsername("admin");
            setPassword(new GuardedString("secret".toCharArray()));
        }});
    }

}
