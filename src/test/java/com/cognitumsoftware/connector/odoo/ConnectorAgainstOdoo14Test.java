package com.cognitumsoftware.connector.odoo;

import org.identityconnectors.common.security.GuardedString;

/**
 * Tests against the v14 of Odoo.
 */
public class ConnectorAgainstOdoo14Test extends ConnectorTest {

    public ConnectorAgainstOdoo14Test() {
        super(new OdooConfiguration() {{
            setUrl("http://localhost:14082");
            setDatabase("db1");
            setUsername("admin");
            setPassword(new GuardedString("secret".toCharArray()));
        }});
    }

}
