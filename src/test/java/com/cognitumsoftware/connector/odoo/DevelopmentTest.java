package com.cognitumsoftware.connector.odoo;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SortKey;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import static com.cognitumsoftware.connector.odoo.OdooConstants.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * Not really unit tests, just for coding purpose.
 * They need to be transformed into real tests with assertions, finally.
 */
@Ignore
public class DevelopmentTest {

    private OdooConnector connector;

    public DevelopmentTest() {
        connector = new OdooConnector();
        connector.init(new OdooConfiguration() {{
            setUrl("http://localhost:10082");
            setDatabase("db1");
            setUsername("admin");
            setPassword(new GuardedString("secret".toCharArray()));
        }});
    }

    @Test
    public void testSchemaRetrieval() {
        Schema s = connector.schema();
        s.getObjectClassInfo().stream().filter(oci -> oci.getType().equals("res.users")).forEach(oci -> {
            System.out.println("----------------------------------");
            System.out.println(oci.getType());

            oci.getAttributeInfo().stream().sorted(Comparator.comparing(AttributeInfo::getName)).forEach(ai ->
                    System.out.println("-> Attribute " + ai.getName() + ": type=" + ai.getType().getName() + " flags=" + ai.getFlags()));

            System.out.println("----------------------------------");
            System.out.println("Filtered by updatable:");
            oci.getAttributeInfo().stream().sorted(Comparator.comparing(AttributeInfo::getName))
                    .filter(ai -> !ai.getFlags().contains(AttributeInfo.Flags.NOT_UPDATEABLE))
                    .forEach(ai ->
                            System.out.println(
                                    "-> Attribute " + ai.getName() + ": type=" + ai.getType().getName() + " flags=" + ai.getFlags()));
        });
    }

    @Test
    public void dumpModel() {
        OdooClient client = new OdooClient(connector.getConfiguration());
        String modelName = "res.users";

        client.executeOperationWithAuthentication(() -> {
            Object[] models = (Object[]) client.executeXmlRpc(MODEL_NAME_MODELS, OPERATION_SEARCH_READ,
                    singletonList(singletonList(asList(MODEL_FIELD_MODEL, OPERATOR_EQUALS, modelName))),
                    Map.of());

            Map<String, Object> model = (Map<String, Object>) models[0];

            System.out.println("----------- Model ------------");
            model.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(this::dumpField);

            Object[] fieldIds = (Object[]) model.get(MODEL_FIELD_FIELD_IDS);
            Object[] fields = (Object[]) client.executeXmlRpc(MODEL_NAME_MODEL_FIELDS, OPERATION_READ, singletonList(asList(fieldIds)));

            System.out.println("----------- Fields Overview ------------");
            for (var fieldObj : fields) {
                Map<String, Object> field = (Map<String, Object>) fieldObj;
                System.out.println("Field " + field.get(MODEL_FIELD_FIELD_NAME) + ": ttype=" + field.get(MODEL_FIELD_FIELD_TYPE)
                        + ", required=" + field.get(MODEL_FIELD_FIELD_REQUIRED) + ", related=" + field.get("related"));
            }

            System.out.println("----------- Fields ------------");

            for (var fieldObj : fields) {
                Map<String, Object> field = (Map<String, Object>) fieldObj;

                System.out.println("--- Field: " + field.get(MODEL_FIELD_FIELD_NAME) + " ---");
                field.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(this::dumpField);
            }

            return null;
        });
    }

    private void dumpField(Map.Entry<String, Object> field) {
        Object value = field.getValue();
        if (value.getClass().isArray()) {
            value = Arrays.toString((Object[]) value);
        }
        System.out.println(field.getKey() + ": " + value);
    }

    @Test
    public void testSearch() {
        ObjectClass oc = new ObjectClass("res.groups");
        OperationOptions oo = new OperationOptionsBuilder()
                .setPagedResultsOffset(1)
                .setPageSize(10)
                .setSortKeys(new SortKey("name", true))
                //.setAttributesToGet("email", "name", "__last_update", "children", "login", "signature")
                .build();
        var filter = connector.createFilterTranslator(oc, oo).translate(
                null);
        //new EqualsFilter(AttributeBuilder.build("__UID__", "28")));
        //new EqualsFilter(AttributeBuilder.build("login", "Tester1")));

        TestResultsHandler rc = new TestResultsHandler();
        connector.executeQuery(oc, filter.isEmpty() ? null : filter.iterator().next(), rc, oo);

        rc.getConnectorObjects().forEach(System.out::println);
    }

}
