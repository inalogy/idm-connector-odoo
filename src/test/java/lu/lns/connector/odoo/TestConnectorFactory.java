package lu.lns.connector.odoo;

import org.identityconnectors.common.security.GuardedString;

public class TestConnectorFactory {

    public OdooConnector getOdooConnector() {
        OdooConfiguration cfg = new OdooConfiguration() {{
            setUrl("http://odoo:8069");
            setDatabase("db1");
            setUsername("admin");
            setPassword(new GuardedString("admin".toCharArray()));
        }};

        OdooConnector connector = new OdooConnector();
        connector.init(cfg);
        return connector;
    }

}
