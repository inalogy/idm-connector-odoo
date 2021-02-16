package com.cognitumsoftware.connector.odoo;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.cognitumsoftware.connector.odoo.Constants.MODEL_FIELD_SEPARATOR;
import static com.cognitumsoftware.connector.odoo.OdooConstants.MODEL_NAME_MODELS;
import static com.cognitumsoftware.connector.odoo.OdooConstants.MODEL_NAME_MODEL_FIELDS;
import static org.junit.Assert.*;

/**
 * Unit tests covering parts of the connector implementation. It is assumed that an Odoo instance is running
 * with the configuration specified in {@link #ConnectorTest()}.
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

    @Test
    public void testSchemaRetrievalWithRelationExpansion() {
        OdooConfiguration conf1 = new OdooConfiguration(connector.getConfiguration());
        conf1.setRetrieveModels("res.users");
        conf1.setExpandRelations("res.users" + MODEL_FIELD_SEPARATOR + "partner_id");

        OdooConnector conn1 = new OdooConnector();
        conn1.init(conf1);

        Schema schema1 = conn1.schema();
        Set<String> modelNames = schema1.getObjectClassInfo().stream().map(ObjectClassInfo::getType).collect(Collectors.toSet());
        assertEquals("expected one model to be fetched", 1, modelNames.size());

        Set<String> fieldNames = schema1.getObjectClassInfo().iterator().next().getAttributeInfo().stream().map(AttributeInfo::getName)
                .collect(Collectors.toSet());

        for (String shouldHaveField : Arrays.asList("partner_id",
                "partner_id" + MODEL_FIELD_SEPARATOR + "phone",
                "partner_id" + MODEL_FIELD_SEPARATOR + "email")) {
            assertTrue("expected field " + shouldHaveField + " to be present in schema", fieldNames.contains(shouldHaveField));
        }
    }

    @Test
    public void testCreateAndUpdateWithRelatedRecord() {
        ObjectClass oc = new ObjectClass("res.users");
        OperationOptions oo = new OperationOptionsBuilder().build();

        // ------ create ---------
        // create a user with contact details which are stored in a related "res.partner" record
        Set<Attribute> attrs = new HashSet<>();
        String login = "test" + System.currentTimeMillis();
        String email = login + "@example.com";
        String signature = "<p>sig1</p>";
        attrs.add(AttributeBuilder.build("login", login));
        attrs.add(AttributeBuilder.build("signature", signature));
        attrs.add(AttributeBuilder.build("partner_id" + MODEL_FIELD_SEPARATOR + "name", "Test U" + System.currentTimeMillis()));
        attrs.add(AttributeBuilder.build("partner_id" + MODEL_FIELD_SEPARATOR + "email", email));
        attrs.add(AttributeBuilder.build("partner_id" + MODEL_FIELD_SEPARATOR + "phone", "+49 1234567890"));

        Uid uid = connector.create(oc, attrs, oo);

        // search created record and verify
        TestResultsHandler results = new TestResultsHandler();
        connector.executeQuery(oc, new EqualsFilter(uid), results, oo);

        assertEquals("expected one record as result", 1, results.getConnectorObjects().size());

        ConnectorObject result = results.getConnectorObjects().iterator().next();
        assertEquals("expected the created record as result", uid, result.getUid());
        assertAttributeEquals("expected login name to be set in created record", login, result, "login");
        assertAttributeEquals("expected signature to be set in created record", signature, result, "signature");

        // search related "res.partner" record and verify
        assertNotNull("relation attribute not filled", result.getAttributeByName("partner_id"));
        Integer relatedId = (Integer) result.getAttributeByName("partner_id").getValue().iterator().next();

        results = new TestResultsHandler();
        connector.executeQuery(new ObjectClass("res.partner"), new EqualsFilter(new Uid(relatedId.toString())), results, oo);

        assertEquals("expected one record as related result", 1, results.getConnectorObjects().size());
        result = results.getConnectorObjects().iterator().next();
        assertAttributeEquals("expected email to be set in created related record", email, result, "email");

        // ------ update ---------
        // now test an update with related record data
        Set<AttributeDelta> ch = new HashSet<>();
        ch.add(AttributeDeltaBuilder.build("login", login + "_c"));
        ch.add(AttributeDeltaBuilder.build("signature", Collections.emptyList()));
        ch.add(AttributeDeltaBuilder.build("partner_id" + MODEL_FIELD_SEPARATOR + "email", email + ".c"));

        connector.updateDelta(oc, uid, ch, oo);

        // search updated record and verify
        results = new TestResultsHandler();
        connector.executeQuery(oc, new EqualsFilter(uid), results, oo);

        assertEquals("expected one record as result", 1, results.getConnectorObjects().size());

        result = results.getConnectorObjects().iterator().next();
        assertEquals("expected the updated record as result", uid, result.getUid());
        assertAttributeEquals("expected login name to be changed in updated record", login + "_c", result, "login");
        assertAttributeEquals("expected signature to be cleared in updated record", null, result, "signature");

        // search related "res.partner" record and verify
        assertNotNull("relation attribute not filled", result.getAttributeByName("partner_id"));
        assertEquals("relation attribute shouldn't be changed", relatedId,
                result.getAttributeByName("partner_id").getValue().iterator().next());

        results = new TestResultsHandler();
        connector.executeQuery(new ObjectClass("res.partner"), new EqualsFilter(new Uid(relatedId.toString())), results, oo);

        assertEquals("expected one record as related result", 1, results.getConnectorObjects().size());
        result = results.getConnectorObjects().iterator().next();
        assertAttributeEquals("expected email to be changed in updated related record", email + ".c", result, "email");
    }

    @Test
    public void testUpdateWithRelatedCreatedRecord() {
        ObjectClass oc = new ObjectClass("hr.employee");
        OperationOptions oo = new OperationOptionsBuilder().build();

        // create an employee without user details
        Set<Attribute> attrs = Set.of(
                AttributeBuilder.build("work_phone", "+49 TESTDATA"),
                AttributeBuilder.build("name", "Test Emp" + System.currentTimeMillis()));
        Uid uid = connector.create(oc, attrs, oo);

        TestResultsHandler results = new TestResultsHandler();
        connector.executeQuery(oc, new EqualsFilter(uid), results, oo);
        ConnectorObject createdObj = results.getConnectorObjects().iterator().next();
        assertAttributeEquals("expected user_id to be null", null, createdObj, "user_id");

        // update the employee such that the user should be created
        String login = "test" + System.currentTimeMillis();
        Set<AttributeDelta> ch = new HashSet<>();
        ch.add(AttributeDeltaBuilder.build("user_id" + MODEL_FIELD_SEPARATOR + "login", login));
        ch.add(AttributeDeltaBuilder.build("user_id" + MODEL_FIELD_SEPARATOR + "name", "Test U" + System.currentTimeMillis()));
        Set<AttributeDelta> modified = connector.updateDelta(oc, uid, ch, oo);

        // verify
        results = new TestResultsHandler();
        connector.executeQuery(oc, new EqualsFilter(uid), results, oo);
        ConnectorObject updatedObj = results.getConnectorObjects().iterator().next();
        Integer userId = (Integer) assertAttributeNotNull("expected user_id to be created", updatedObj, "user_id");
        assertTrue("expect user_id in modified attributes in return value of updateDelta",
                modified != null && modified.size() == 1 && modified.iterator().next().getName().equals("user_id"));

        results = new TestResultsHandler();
        connector.executeQuery(new ObjectClass("res.users"), new EqualsFilter(new Uid(userId.toString())), results, oo);
        assertEquals("expected one record as related result", 1, results.getConnectorObjects().size());
        ConnectorObject user = results.getConnectorObjects().iterator().next();
        assertAttributeEquals("expected login to be set in created related record", login, user, "login");
    }

    @Test
    public void testCreateWithRelatedRecordRollback() {
        ObjectClass oc = new ObjectClass("res.users");
        OperationOptions oo = new OperationOptionsBuilder().build();

        // try to create a user with contact details which are stored in a related "res.partner" record but
        // creating the user should fail because required attribute "login" is not specified
        Set<Attribute> attrs = new HashSet<>();
        String email = "test" + System.currentTimeMillis() + "@example.com";
        attrs.add(AttributeBuilder.build("partner_id" + MODEL_FIELD_SEPARATOR + "name", "Test U" + System.currentTimeMillis()));
        attrs.add(AttributeBuilder.build("partner_id" + MODEL_FIELD_SEPARATOR + "email", email));

        try {
            connector.create(oc, attrs, oo);
            fail("expecting create to fail because of required attribute missing");
        }
        catch (ConnectorException e) {
            assertTrue("expected to fail because of required attribute missing; message=" + e.getMessage(),
                    e.getMessage().contains("cannot create a new user from here"));
        }

        // search related record and verify that it was not rolled back
        TestResultsHandler results = new TestResultsHandler();
        connector.executeQuery(oc, new EqualsFilter(AttributeBuilder.build("email", email)), results, oo);
        assertEquals("expected no related record as result", 0, results.getConnectorObjects().size());
    }

    @Test
    public void testUpdateWithRelatedCreatedRecordRollback() {
        ObjectClass oc = new ObjectClass("hr.employee");
        OperationOptions oo = new OperationOptionsBuilder().build();

        // create an employee without user details
        Set<Attribute> attrs = Set.of(AttributeBuilder.build("name", "Test Emp" + System.currentTimeMillis()));
        Uid uid = connector.create(oc, attrs, oo);

        // update the employee such that the user should be created but the actual update of employee fails; as a
        // consequence the created record should be rolled back
        String login = "test" + System.currentTimeMillis();
        Set<AttributeDelta> ch = Set.of(
                AttributeDeltaBuilder.build("name"), // provide an invalid field value
                AttributeDeltaBuilder.build("user_id" + MODEL_FIELD_SEPARATOR + "login", login),
                AttributeDeltaBuilder.build("user_id" + MODEL_FIELD_SEPARATOR + "name", "Test U" + System.currentTimeMillis()));
        try {
            connector.updateDelta(oc, uid, ch, oo);
            fail("expecting update to fail because of invalid attribute value");
        }
        catch (ConnectorException e) {
            assertTrue("expected to fail because of invalid attribute value; message=" + e.getMessage(),
                    e.getMessage().contains("Delta add/remove not supported for field 'name' in model 'hr.employee'"));
        }

        // verify that user by login is rolled back
        TestResultsHandler results = new TestResultsHandler();
        connector.executeQuery(new ObjectClass("res.users"), new EqualsFilter(AttributeBuilder.build("login", login)), results, oo);
        assertEquals("expected no related user record as result", 0, results.getConnectorObjects().size());
    }

    @Test
    public void testUpdateWithRelatedUpdatedRecordRollback() {
        ObjectClass oc = new ObjectClass("hr.employee");
        OperationOptions oo = new OperationOptionsBuilder().build();

        // create an employee with user details
        String login = "test" + System.currentTimeMillis();
        Set<Attribute> attrs = Set.of(
                AttributeBuilder.build("name", "Test Emp" + System.currentTimeMillis()),
                AttributeBuilder.build("user_id" + MODEL_FIELD_SEPARATOR + "login", login),
                AttributeBuilder.build("user_id" + MODEL_FIELD_SEPARATOR + "name", "Test U" + System.currentTimeMillis())
        );
        Uid uid = connector.create(oc, attrs, oo);

        // update the employee such that the user should be updated but the actual update of employee fails; as a
        // consequence the created record should be rolled back
        Set<AttributeDelta> ch = Set.of(
                AttributeDeltaBuilder.build("name"), // provide an invalid field value
                AttributeDeltaBuilder.build("user_id" + MODEL_FIELD_SEPARATOR + "login", login + "_c"));

        try {
            connector.updateDelta(oc, uid, ch, oo);
            fail("expecting update to fail because of invalid attribute value");
        }
        catch (ConnectorException e) {
            assertTrue("expected to fail because of invalid attribute value; message=" + e.getMessage(),
                    e.getMessage().contains("Delta add/remove not supported for field 'name' in model 'hr.employee'"));
        }

        // verify that user record update is rolled back (must not find the user by the changed login name)
        TestResultsHandler results = new TestResultsHandler();
        connector.executeQuery(new ObjectClass("res.users"), new EqualsFilter(AttributeBuilder.build("login", login + "_c")), results, oo);
        assertEquals("expected related user login to be rolled back", 0, results.getConnectorObjects().size());

        results = new TestResultsHandler();
        connector.executeQuery(new ObjectClass("res.users"), new EqualsFilter(AttributeBuilder.build("login", login)), results, oo);
        assertEquals("expected related user to be present by original login name", 1, results.getConnectorObjects().size());
    }

    @Test
    public void testUpdateRelationWithDelta() {
        // TODO:
        Uid uid = new Uid("27");
        ObjectClass oc = new ObjectClass("res.users");
        OperationOptions oo = new OperationOptionsBuilder().build();

        Set<AttributeDelta> ch = new HashSet<>();
        ch.add(new AttributeDeltaBuilder().setName("groups_id").addValueToRemove(6).build());
        //ch.add(new AttributeDeltaBuilder().setName("groups_id").addValueToAdd(3).build());

        connector.updateDelta(oc, uid, ch, oo);
    }

    @Test
    public void testSearchWithRelatedRecordRetrieval() {
        ObjectClass oc = new ObjectClass("hr.employee");

        // create an employee with user details
        String login = "test" + System.currentTimeMillis();
        String name = "Test Emp" + System.currentTimeMillis();
        Set<Attribute> attrs = Set.of(
                AttributeBuilder.build("name", name),
                AttributeBuilder.build("user_id" + MODEL_FIELD_SEPARATOR + "login", login),
                AttributeBuilder.build("user_id" + MODEL_FIELD_SEPARATOR + "name", "Test U" + System.currentTimeMillis())
        );
        Uid uid = connector.create(oc, attrs, new OperationOptionsBuilder().build());

        // search by that employee and retrieve user details in same call via expanded relation
        TestResultsHandler results = new TestResultsHandler();
        connector.executeQuery(oc, new EqualsFilter(AttributeBuilder.build("name", name)), results,
                new OperationOptionsBuilder().setAttributesToGet("name", "user_id" + MODEL_FIELD_SEPARATOR + "login").build());

        assertEquals("expect one record to be found", 1, results.getConnectorObjects().size());

        ConnectorObject resultObj = results.getConnectorObjects().iterator().next();
        assertEquals("expect created record to be found", uid, resultObj.getUid());

        assertAttributeEquals("expect name attribute to match as created", name, resultObj, "name");
        assertAttributeEquals("expect related login attribute to match as created", login, resultObj,
                "user_id" + MODEL_FIELD_SEPARATOR + "login");
    }

    @Test
    public void testSearchFilters() {
        // TODO
    }

    @Test
    public void testOdooTypeMapping() {
        // TODO
    }

    @Test
    public void testDeleteRecord() {
        // create any record
        ObjectClass oc = new ObjectClass("hr.employee");
        OperationOptions oo = new OperationOptionsBuilder().build();
        Set<Attribute> attrs = Set.of(AttributeBuilder.build("name", "Test Emp" + System.currentTimeMillis()));
        Uid uid = connector.create(oc, attrs, oo);

        // delete it
        connector.delete(oc, uid, oo);

        // verify
        TestResultsHandler results = new TestResultsHandler();
        connector.executeQuery(oc, new EqualsFilter(uid), results, oo);
        assertEquals("expected record to be deleted", 0, results.getConnectorObjects().size());
    }

    private Object assertAttributeNotNull(String message, ConnectorObject record, String attributeName) {
        assertNotNull("Attribute '" + attributeName + "' not present in record", record.getAttributeByName(attributeName));
        assertNotNull("expected non-null attribute value for '" + attributeName + "'",
                record.getAttributeByName(attributeName).getValue());
        assertEquals("expected one attribute value for '" + attributeName + "'", 1,
                record.getAttributeByName(attributeName).getValue().size());
        Object val = record.getAttributeByName(attributeName).getValue().iterator().next();
        assertNotNull(message, val);
        return val;
    }

    private void assertAttributeEquals(String message, Object expectedValue, ConnectorObject record, String attributeName) {
        assertNotNull("Attribute '" + attributeName + "' not present in record", record.getAttributeByName(attributeName));

        if (expectedValue == null) {
            // no attribute value
            assertNull(message, record.getAttributeByName(attributeName).getValue());
        }
        else {
            // single attribute value
            assertNotNull("expected non-null attribute value for '" + attributeName + "'",
                    record.getAttributeByName(attributeName).getValue());
            assertEquals("expected one attribute value for '" + attributeName + "'", 1,
                    record.getAttributeByName(attributeName).getValue().size());
            assertEquals(message, expectedValue, record.getAttributeByName(attributeName).getValue().iterator().next());
        }
    }

}
