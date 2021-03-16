package com.cognitumsoftware.connector.odoo;

import org.identityconnectors.common.security.GuardedString;

/**
 * Tests against the v10 of Odoo.
 */
public class ConnectorAgainstOdoo10Test extends ConnectorTest {

    public ConnectorAgainstOdoo10Test() {
        super(new OdooConfiguration() {{
            setUrl("http://localhost:10082");
            setDatabase("db1");
            setUsername("admin");
            setPassword(new GuardedString("secret".toCharArray()));
        }});
    }

}
