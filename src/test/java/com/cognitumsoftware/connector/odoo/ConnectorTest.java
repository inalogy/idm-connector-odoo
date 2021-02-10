package com.cognitumsoftware.connector.odoo;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SortKey;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.junit.Test;

import java.util.Collections;
import java.util.Comparator;
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
        Schema s = connector.schema();
        s.getObjectClassInfo().stream().filter(oci -> oci.getType().equals("res.users")).forEach(oci -> {
            System.out.println("----------------------------------");
            System.out.println(oci.getType());

            oci.getAttributeInfo().stream().sorted(Comparator.comparing(AttributeInfo::getName)).forEach(ai -> {
                System.out.println("-> Attribute " + ai.getName() + ": type=" + ai.getType().getName() + " flags=" + ai.getFlags());
            });

            System.out.println("----------------------------------");
            System.out.println("Filtered by updatable:");
            oci.getAttributeInfo().stream().sorted(Comparator.comparing(AttributeInfo::getName)).filter(ai -> !ai.getFlags().contains(
                    AttributeInfo.Flags.NOT_UPDATEABLE)).forEach(ai -> {
                System.out.println("-> Attribute " + ai.getName() + ": type=" + ai.getType().getName() + " flags=" + ai.getFlags());
            });
        });
    }

    @Test
    public void testSearch() {
        ObjectClass oc = new ObjectClass("res.users");
        OperationOptions oo = new OperationOptionsBuilder()
                .setPagedResultsOffset(1)
                .setPageSize(10)
                .setSortKeys(new SortKey("email", true))
                //.setAttributesToGet("email", "name", "__last_update", "children", "login", "signature")
                .build();
        var filter = connector.createFilterTranslator(oc, oo).translate(
                new EqualsFilter(AttributeBuilder.build("__UID__", "28")));
        //new EqualsFilter(AttributeBuilder.build("login", "Tester1")));

        TestResultsHandler rc = new TestResultsHandler();
        connector.executeQuery(oc, filter.isEmpty() ? null : filter.iterator().next(), rc, oo);

        System.out.println(rc.getConnectorObjects());
    }

    @Test
    public void testCreateAndUpdate() {
        ObjectClass oc = new ObjectClass("res.users");
        OperationOptions oo = new OperationOptionsBuilder().build();

        Set<Attribute> attrs = new HashSet<>();
        //attrs.add(AttributeBuilder.build("login", "testcreate"));
        attrs.add(AttributeBuilder.build("name", "Create Test"));
        attrs.add(AttributeBuilder.build("email", "my@test.com"));
        attrs.add(AttributeBuilder.build("signature", "sig1"));

        Uid uid = connector.create(oc, attrs, oo);
        System.out.println("Uid: " + uid);

        Set<AttributeDelta> ch = new HashSet<>();
        ch.add(AttributeDeltaBuilder.build("login", "demo"));
        ch.add(AttributeDeltaBuilder.build("signature", Collections.emptyList()));

        connector.updateDelta(oc, uid, ch, oo);
    }

    @Test
    public void testDelete() {
        ObjectClass oc = new ObjectClass("res.users");
        OperationOptions oo = new OperationOptionsBuilder().build();

        connector.delete(oc, new Uid("8"), oo);
    }

}
