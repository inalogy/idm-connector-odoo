package com.cognitumsoftware.connector.odoo;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.SortKey;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

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

    @Test
    public void testSearch() {
        ObjectClass oc = new ObjectClass("res.users");
        OperationOptions oo = new OperationOptionsBuilder()
                .setPagedResultsOffset(0)
                .setPageSize(10)
                .setSortKeys(new SortKey("email", true))
                .setAttributesToGet("email", "name").build();
        var filter = connector.createFilterTranslator(oc, oo).translate(null);

        TestResultsHandler rc = new TestResultsHandler();
        connector.executeQuery(oc, filter.isEmpty() ? null : filter.iterator().next(), rc, oo);

        System.out.println(rc.getConnectorObjects());
    }

    @Test
    public void testCreate() {
        ObjectClass oc = new ObjectClass("res.users");
        OperationOptions oo = new OperationOptionsBuilder().build();

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(AttributeBuilder.build("login", "testcreate"));
        attrs.add(AttributeBuilder.build("name", "Create Test"));
        attrs.add(AttributeBuilder.build("phone", "+49 123456789"));
        attrs.add(AttributeBuilder.build("email", "my@test.com"));

        System.out.println("Uid: " + connector.create(oc, attrs, oo));
    }

    @Test
    public void testDelete() {
        ObjectClass oc = new ObjectClass("res.users");
        OperationOptions oo = new OperationOptionsBuilder().build();

        connector.delete(oc, new Uid("8"), oo);
    }

}
