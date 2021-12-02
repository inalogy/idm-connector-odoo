package lu.lns.connector.odoo;

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
import org.identityconnectors.framework.common.objects.SortKey;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AndFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.NotFilter;
import org.identityconnectors.framework.common.objects.filter.OrFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static lu.lns.connector.odoo.Constants.MODEL_FIELD_SEPARATOR;
import static lu.lns.connector.odoo.OdooConstants.MODEL_NAME_MODELS;
import static lu.lns.connector.odoo.OdooConstants.MODEL_NAME_MODEL_FIELDS;
import static org.junit.Assert.*;

/**
 * Unit tests covering parts of the connector implementation. It is assumed that an Odoo instance is running
 * with the configuration specified in constructor.
 */
public class ConnectorTest {

    private OdooConnector connector;

    public ConnectorTest() {
        TestConnectorFactory connectorFactory = new TestConnectorFactory();
        connector = connectorFactory.getOdooConnector();
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
        ObjectClass oc = new ObjectClass("res.users");
        OperationOptions oo = new OperationOptionsBuilder().build();

        // create groups
        int[] groups = createGroups();

        // create a user with groups relation specified
        String login = "test" + System.currentTimeMillis();
        String name = "Test U" + System.currentTimeMillis();
        Uid uid = connector.create(oc, Set.of(
                AttributeBuilder.build("login", login),
                AttributeBuilder.build("name", name),
                AttributeBuilder.build("groups_id", groups[0], groups[1])
        ), oo);

        // update user groups using delta:

        // add a reference and verify
        connector.updateDelta(oc, uid, Set.of(
                new AttributeDeltaBuilder().setName("groups_id").addValueToAdd(groups[2], groups[3]).build()), oo);

        TestResultsHandler results = new TestResultsHandler();
        connector.executeQuery(oc, new EqualsFilter(AttributeBuilder.build("name", name)), results, oo);
        assertEquals("expect 3rd/4th groups to be added", Set.of(groups[0], groups[1], groups[2], groups[3]), new HashSet<>(
                results.getConnectorObjects().iterator().next().getAttributeByName("groups_id").getValue()));

        // remove a reference
        connector.updateDelta(oc, uid, Set.of(new AttributeDeltaBuilder().setName("groups_id").addValueToRemove(groups[2]).build()), oo);

        results = new TestResultsHandler();
        connector.executeQuery(oc, new EqualsFilter(AttributeBuilder.build("name", name)), results, oo);
        assertEquals("expect 3rd group to be removed", Set.of(groups[0], groups[1], groups[3]), new HashSet<>(
                results.getConnectorObjects().iterator().next().getAttributeByName("groups_id").getValue()));
    }

    private int[] createGroups() {
        int count = 4;
        int[] result = new int[count];
        String unique = "testgroup_" + System.currentTimeMillis();

        for (int i = 0; i < result.length; i++) {
            result[i] = Integer.parseInt(connector.create(new ObjectClass("res.groups"), Set.of(
                    AttributeBuilder.build("name", unique + i)
            ), new OperationOptionsBuilder().build()).getUidValue());
        }

        return result;
    }

    @Test
    public void testSearchWithRelatedRecordRetrieval() {
        ObjectClass oc = new ObjectClass("hr.employee");

        // create an employee with user details
        String login = "test" + System.currentTimeMillis();
        String name = "Test Emp" + System.currentTimeMillis();
        String uname = "Test U" + System.currentTimeMillis();
        Set<Attribute> attrs = Set.of(
                AttributeBuilder.build("name", name),
                AttributeBuilder.build("user_id" + MODEL_FIELD_SEPARATOR + "login", login),
                AttributeBuilder.build("user_id" + MODEL_FIELD_SEPARATOR + "name", uname)
        );
        Uid uid = connector.create(oc, attrs, new OperationOptionsBuilder().build());

        // search by that employee and retrieve user details in same call via expanded relation
        TestResultsHandler results = new TestResultsHandler();
        connector.executeQuery(oc, new EqualsFilter(uid), results,
                new OperationOptionsBuilder().setAttributesToGet("name", "user_id" + MODEL_FIELD_SEPARATOR + "login").build());

        assertEquals("expect one record to be found (" + name + ", " + uid.getUidValue() + ")", 1, results.getConnectorObjects().size());

        ConnectorObject resultObj = results.getConnectorObjects().iterator().next();
        assertEquals("expect created record to be found", uid, resultObj.getUid());

        try {
            // this assertion is not working for Odoo v11 and v12, seems like the model takes name from user
            assertAttributeEquals("expect name attribute to match as created", name, resultObj, "name");
        }
        catch (AssertionError e) {
            assertAttributeEquals("expect name attribute to match as created user", uname, resultObj, "name");
        }

        assertAttributeEquals("expect related login attribute to match as created", login, resultObj,
                "user_id" + MODEL_FIELD_SEPARATOR + "login");
    }

    @Test
    public void testSearchFilters() {
        ObjectClass oc = new ObjectClass("hr.employee");

        // create an employee with some details
        String name = "Test Emp" + System.currentTimeMillis();
        String textField1 = "notes";
        String textField1Value = "123456";
        String dateField1 = "birthday";
        ZonedDateTime bd = ZonedDateTime.of(LocalDate.ofYearDay(1900, 42), LocalTime.MIN, ZoneId.systemDefault());
        Set<Attribute> attrs = Set.of(
                AttributeBuilder.build("name", name),
                AttributeBuilder.build(textField1, textField1Value),
                AttributeBuilder.build("color", 1),
                //AttributeBuilder.build("certificate", "master"), --> in v14
                AttributeBuilder.build(dateField1, bd)
        );
        Uid uid = connector.create(oc, attrs, new OperationOptionsBuilder().build());

        BiConsumer<Filter, Boolean> assertFound = createAssertFound(oc, uid);

        // very simple filter by name
        assertFound.accept(new EqualsFilter(AttributeBuilder.build("name", name)), true);
        assertFound.accept(new EqualsFilter(AttributeBuilder.build("name", name + "_notexists")), false);

        // filter with "not"
        assertFound.accept(new NotFilter(new EqualsFilter(AttributeBuilder.build("name", name))), false);
        assertFound.accept(new NotFilter(new EqualsFilter(AttributeBuilder.build("name", name + "_notexists"))), true);

        // filter with "and"
        assertFound.accept(new AndFilter(
                new EqualsFilter(AttributeBuilder.build("name", name)),
                new EqualsFilter(AttributeBuilder.build("color", 1))), true);
        assertFound.accept(new AndFilter(
                new EqualsFilter(AttributeBuilder.build("name", name)),
                new EqualsFilter(AttributeBuilder.build("color", 2))), false);

        // filter with "or"
        assertFound.accept(new OrFilter(
                new EqualsFilter(AttributeBuilder.build("name", name)),
                new EqualsFilter(AttributeBuilder.build("color", 2))), true);
        assertFound.accept(new OrFilter(
                new EqualsFilter(AttributeBuilder.build("name", name + "_notexists")),
                new EqualsFilter(AttributeBuilder.build("color", 1))), true);
        assertFound.accept(new OrFilter(
                new EqualsFilter(AttributeBuilder.build("name", name + "_notexists")),
                new EqualsFilter(AttributeBuilder.build("color", 2))), false);

        // filter with nested not/and/or
        assertFound.accept(new OrFilter(
                new NotFilter(new EqualsFilter(AttributeBuilder.build("name", name + "_notexists"))),
                new EqualsFilter(AttributeBuilder.build("color", 2))), true);
        assertFound.accept(new OrFilter(
                new AndFilter(
                        new EqualsFilter(AttributeBuilder.build("name", name)),
                        new EqualsFilter(AttributeBuilder.build(textField1, textField1Value))),
                new EqualsFilter(AttributeBuilder.build("color", 2))), true);

        // filter with "starts with", "ends with" and "contains"
        assertFound.accept(new StartsWithFilter(AttributeBuilder.build("name", name.substring(0, 5))), true);
        assertFound.accept(new StartsWithFilter(
                AttributeBuilder.build(textField1, textField1Value.substring(0, textField1Value.length() - 1))), true);
        assertFound.accept(new StartsWithFilter(AttributeBuilder.build("name", name.substring(0, 4) + "#")), false);

        assertFound.accept(new EndsWithFilter(AttributeBuilder.build("name", name.substring(5))), true);
        assertFound.accept(new EndsWithFilter(AttributeBuilder.build(textField1, textField1Value.substring(3))), true);
        assertFound.accept(new EndsWithFilter(AttributeBuilder.build("name", "#" + name.substring(5))), false);

        assertFound.accept(new ContainsFilter(AttributeBuilder.build("name", name.substring(3, name.length() - 2))), true);
        assertFound.accept(new ContainsFilter(AttributeBuilder.build("name", "#" + name.substring(4, name.length() - 2))), false);

        // filter with comparison
        var bd_before = ZonedDateTime.of(LocalDate.ofYearDay(1900, 2), LocalTime.MIN, ZoneId.systemDefault());
        var bd_after = ZonedDateTime.of(LocalDate.ofYearDay(1900, 84), LocalTime.MIN, ZoneId.systemDefault());

        assertFound.accept(new LessThanFilter(AttributeBuilder.build(dateField1, bd_after)), true);
        assertFound.accept(new LessThanFilter(AttributeBuilder.build(dateField1, bd_before)), false);
        assertFound.accept(new LessThanFilter(AttributeBuilder.build(dateField1, bd)), false);

        assertFound.accept(new LessThanOrEqualFilter(AttributeBuilder.build(dateField1, bd_after)), true);
        assertFound.accept(new LessThanOrEqualFilter(AttributeBuilder.build(dateField1, bd_before)), false);
        assertFound.accept(new LessThanOrEqualFilter(AttributeBuilder.build(dateField1, bd)), true);

        assertFound.accept(new GreaterThanFilter(AttributeBuilder.build(dateField1, bd_after)), false);
        assertFound.accept(new GreaterThanFilter(AttributeBuilder.build(dateField1, bd_before)), true);
        assertFound.accept(new GreaterThanFilter(AttributeBuilder.build(dateField1, bd)), false);

        assertFound.accept(new GreaterThanOrEqualFilter(AttributeBuilder.build(dateField1, bd_after)), false);
        assertFound.accept(new GreaterThanOrEqualFilter(AttributeBuilder.build(dateField1, bd_before)), true);
        assertFound.accept(new GreaterThanOrEqualFilter(AttributeBuilder.build(dateField1, bd)), true);
    }

    @Test
    public void testSearchLikeEscaping() {
        ObjectClass oc = new ObjectClass("hr.employee");

        // create an employee that includes the % wildcard in its name
        String name = "Emp%loyee E" + System.currentTimeMillis();
        Set<Attribute> attrs = Set.of(
                AttributeBuilder.build("name", name)
        );
        Uid uid = connector.create(oc, attrs, new OperationOptionsBuilder().build());

        // search using operators that have wildcards
        BiConsumer<Filter, Boolean> assertFound = createAssertFound(oc, uid);

        assertFound.accept(new ContainsFilter(AttributeBuilder.build("name", "Emp%")), true);
        assertFound.accept(new ContainsFilter(AttributeBuilder.build("name", "Emp_")), false);
        assertFound.accept(new ContainsFilter(AttributeBuilder.build("name", "Emp%loy_")), false);
        assertFound.accept(new StartsWithFilter(AttributeBuilder.build("name", "Emp%")), true);
        assertFound.accept(new StartsWithFilter(AttributeBuilder.build("name", "Emp%l")), true);
        assertFound.accept(new StartsWithFilter(AttributeBuilder.build("name", "Emp_")), false);
        assertFound.accept(new EndsWithFilter(AttributeBuilder.build("name", name.substring(name.indexOf('%')))), true);
    }

    private BiConsumer<Filter, Boolean> createAssertFound(ObjectClass oc, Uid uid) {
        return (filter, shouldMatch) -> {
            TestResultsHandler results = new TestResultsHandler();
            connector.executeQuery(oc, filter, results, new OperationOptionsBuilder().build());

            assertEquals("expect to " + (shouldMatch ? "" : "not") + " match record", shouldMatch,
                    results.getConnectorObjects().stream().anyMatch(obj -> obj.getUid().equals(uid)));
        };
    }

    @Test
    public void testSearchOptions() {
        ObjectClass oc = new ObjectClass("hr.employee");

        // create 2 employees with some details
        String name = "Test Emp" + System.currentTimeMillis();
        String name1 = name + "_1";
        String name2 = name + "_2";
        Uid uid1 = connector.create(oc, Set.of(AttributeBuilder.build("name", name1)), new OperationOptionsBuilder().build());
        Uid uid2 = connector.create(oc, Set.of(AttributeBuilder.build("name", name2)), new OperationOptionsBuilder().build());

        Filter filter = new StartsWithFilter(AttributeBuilder.build("name", name));

        // search with paging
        TestResultsHandler results = new TestResultsHandler();
        connector.executeQuery(oc, filter, results, new OperationOptionsBuilder().setPagedResultsOffset(1).setPageSize(10).build());
        assertTrue("expect to match record 1", results.getConnectorObjects().stream().anyMatch(obj -> obj.getUid().equals(uid1)));
        assertTrue("expect to match record 2", results.getConnectorObjects().stream().anyMatch(obj -> obj.getUid().equals(uid2)));

        results = new TestResultsHandler();
        connector.executeQuery(oc, filter, results, new OperationOptionsBuilder().setPagedResultsOffset(1).setPageSize(1).build());
        boolean uid1Contained = results.getConnectorObjects().stream().anyMatch(obj -> obj.getUid().equals(uid1));
        boolean uid2Contained = results.getConnectorObjects().stream().anyMatch(obj -> obj.getUid().equals(uid2));
        assertTrue("expect to match record 1 or 2 but not both", (uid1Contained && !uid2Contained) || (uid2Contained && !uid1Contained));

        // search with sort by name
        results = new TestResultsHandler();
        connector.executeQuery(oc, filter, results, new OperationOptionsBuilder().setSortKeys(new SortKey("name", true)).build());
        assertEquals("expect record 1 to come first when sort by name ascending", uid1, results.getConnectorObjects().get(0).getUid());
        assertEquals("expect record 2 to come second when sort by name ascending", uid2, results.getConnectorObjects().get(1).getUid());

        results = new TestResultsHandler();
        connector.executeQuery(oc, filter, results, new OperationOptionsBuilder().setSortKeys(new SortKey("name", false)).build());
        assertEquals("expect record 2 to come first when sort by name descending", uid2, results.getConnectorObjects().get(0).getUid());
        assertEquals("expect record 1 to come second when sort by name descending", uid1, results.getConnectorObjects().get(1).getUid());
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
