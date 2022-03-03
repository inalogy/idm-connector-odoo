package lu.lns.connector.odoo;

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
import java.util.HashSet;
import java.util.Set;

public class BinaryTest {

    // Use attribute image_1920 for Odoo 14+
    public static final String ATTR_IMAGE = "image";

    @Test
    public void testBinaryReadAndCreate() throws IOException {
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
        Object jpgdata = image.getValue().get(0);

        // Create a new employee with the same image

        Set<Attribute> attrs = new HashSet<>();
        attrs.add(AttributeBuilder.build("name", "Yannick"));
        attrs.add(AttributeBuilder.build(ATTR_IMAGE, jpgdata));
        Uid uid = connector.create(oc, attrs, oo);

        // Read the new employee to ensure image is the same
        connector.executeQuery(oc, new EqualsFilter(uid), results, oo);
        Assertions.assertEquals(2, results.getConnectorObjects().size());
        ConnectorObject employee2 = results.getConnectorObjects().get(1);

        byte[] image1 = (byte[]) employee1.getAttributeByName(ATTR_IMAGE).getValue().get(0);
        byte[] image2 = (byte[]) employee2.getAttributeByName(ATTR_IMAGE).getValue().get(0);

        // Save files for debugging purposes
        // Files.write(Paths.get("/tmp/image1.jpg"), image1);
        // Files.write(Paths.get("/tmp/image2.jpg"), image2);

        Assertions.assertArrayEquals(image1, image2);
    }

}
