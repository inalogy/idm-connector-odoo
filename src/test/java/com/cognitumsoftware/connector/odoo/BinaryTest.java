package com.cognitumsoftware.connector.odoo;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static com.cognitumsoftware.connector.odoo.Constants.MODEL_FIELD_SEPARATOR;

public class BinaryTest {

    public static final String ATTR_IMAGE = "image_128";

    @Test
    public void testBinaryReadAndCreate() {
        TestConnectorFactory connectorFactory = new TestConnectorFactory();
        OdooConnector connector = connectorFactory.getOdooConnector();
        ObjectClass oc = new ObjectClass("hr.employee");
        OperationOptions oo = new OperationOptionsBuilder().build();

        // Read an employee from the default data model
        TestResultsHandler results = new TestResultsHandler();
        connector.executeQuery(oc, new EqualsFilter(new Uid("12")), results, oo);
        Assertions.assertEquals(1, results.getConnectorObjects().size());
        ConnectorObject employee1 = results.getConnectorObjects().get(0);

        Attribute image = employee1.getAttributeByName(ATTR_IMAGE);
        Object jpgdata =  image.getValue().get(0);

        // Create a new employee with the same image

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(AttributeBuilder.build("name", "Yannick"));
        attrs.add(AttributeBuilder.build(ATTR_IMAGE, jpgdata));
        Uid uid = connector.create(oc, attrs, oo);

        // Read the new employee to ensure image is the same
        connector.executeQuery(oc, new EqualsFilter(uid), results, oo);
        Assertions.assertEquals(2, results.getConnectorObjects().size());
        ConnectorObject employee2 = results.getConnectorObjects().get(1);

        Assertions.assertEquals(
                employee1.getAttributeByName(ATTR_IMAGE).getValue().get(0),
                employee2.getAttributeByName(ATTR_IMAGE).getValue().get(0)
        );
    }

}
