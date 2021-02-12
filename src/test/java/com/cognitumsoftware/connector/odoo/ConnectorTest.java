package com.cognitumsoftware.connector.odoo;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static com.cognitumsoftware.connector.odoo.OdooConstants.MODEL_NAME_MODELS;
import static com.cognitumsoftware.connector.odoo.OdooConstants.MODEL_NAME_MODEL_FIELDS;
import static org.junit.Assert.*;

/**
 * Unit tests covering parts of the connector implementation.
 */
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
        connector.test(); // will throw ConnectorException on failure
    }

    @Test
    public void testSchemaRetrievalWithBasicRestrictions() {
        OdooConfiguration conf1 = new OdooConfiguration(connector.getConfiguration());
        conf1.setRetrieveModels("res.users");

        OdooConnector conn1 = new OdooConnector();
        conn1.init(conf1);

        Schema schema1 = conn1.schema();
        assertEquals("expected one model to match", 1, schema1.getObjectClassInfo().size());
        assertEquals("expected the only specified model to match", "res.users", schema1.getObjectClassInfo().iterator().next().getType());
    }

    @Test
    public void testSchemaRetrievalWithMultipleAndWildcardRestrictions() {
        OdooConfiguration conf1 = new OdooConfiguration(connector.getConfiguration());
        conf1.setRetrieveModels(" res.* ,  hr.* ");

        OdooConnector conn1 = new OdooConnector();
        conn1.init(conf1);

        Schema schema1 = conn1.schema();
        Set<String> modelNames = schema1.getObjectClassInfo().stream().map(ObjectClassInfo::getType).collect(Collectors.toSet());

        for (String shouldMatchModelName : Arrays.asList("res.users", "hr.employee")) {
            assertTrue("expected to match model name " + shouldMatchModelName, modelNames.contains(shouldMatchModelName));
        }

        for (String shouldNotMatchModelName : Arrays.asList(MODEL_NAME_MODELS, MODEL_NAME_MODEL_FIELDS)) {
            assertFalse("expected to not match model name " + shouldNotMatchModelName, modelNames.contains(shouldNotMatchModelName));
        }
    }

}
