package com.cognitumsoftware.connector.odoo;

import org.identityconnectors.common.security.GuardedString;

/**
 * Tests against the v11 of Odoo.
 */
public class ConnectorAgainstOdoo11Test extends ConnectorTest {

    public ConnectorAgainstOdoo11Test() {
        super(new OdooConfiguration() {{
            setUrl("http://localhost:11082");
            setDatabase("db1");
            setUsername("admin");
            setPassword(new GuardedString("secret".toCharArray()));
        }});
    }

}
