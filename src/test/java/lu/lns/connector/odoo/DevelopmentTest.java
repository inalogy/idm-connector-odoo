package lu.lns.connector.odoo;

import org.apache.commons.lang3.StringUtils;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static lu.lns.connector.odoo.OdooConstants.*;

/**
 * Not really unit tests, just for coding purpose.
 * They need to be transformed into real tests with assertions, finally.
 */
public class DevelopmentTest {

    private final OdooConnector connector;

    public DevelopmentTest() {
        connector = new OdooConnector();
        connector.init(new OdooConfiguration() {{
            setUrl("http://localhost:8069");
            setDatabase("db1");
            setUsername("admin");
            setPassword(new GuardedString("admin".toCharArray()));
        }});
    }

    public void dumpModel() {
        OdooClient client = new OdooClient(connector.getConfiguration());
        String modelName = "hr.employee";

        client.executeOperationWithAuthentication(() -> {
            Object[] models = (Object[]) client.executeXmlRpc(MODEL_NAME_MODELS, OPERATION_SEARCH_READ,
                    singletonList(singletonList(asList(MODEL_FIELD_MODEL, OPERATOR_EQUALS, modelName))),
                    Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> model = (Map<String, Object>) models[0];

            System.out.println("----------- Model ------------");
            model.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(this::dumpField);

            Object[] fieldIds = (Object[]) model.get(MODEL_FIELD_FIELD_IDS);
            Object[] fields = (Object[]) client.executeXmlRpc(MODEL_NAME_MODEL_FIELDS, OPERATION_READ, singletonList(asList(fieldIds)));

            System.out.println("----------- Fields Overview ------------");
            for (var fieldObj : fields) {
                @SuppressWarnings("unchecked")
                Map<String, Object> field = (Map<String, Object>) fieldObj;
                System.out.println(
                        "Field " + StringUtils.rightPad((String) field.get(MODEL_FIELD_FIELD_NAME), 35)
                                + ": ttype=" + StringUtils.rightPad((String) field.get(MODEL_FIELD_FIELD_TYPE), 10)
                                + ", req=" + StringUtils.rightPad(field.get(MODEL_FIELD_FIELD_REQUIRED).toString(), 5)
                                + ", store=" + StringUtils.rightPad(field.get("store").toString(), 5)
                                + ", rel=" + StringUtils.rightPad(field.get("related").toString(), 25));
            }

//            System.out.println("----------- Fields ------------");
//
//            for (var fieldObj : fields) {
//                Map<String, Object> field = (Map<String, Object>) fieldObj;
//
//                System.out.println("--- Field: " + field.get(MODEL_FIELD_FIELD_NAME) + " ---");
//                field.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(this::dumpField);
//            }

            return null;
        });
    }

    private void displayEmployee(int employeeId) {
        OperationOptions oo = new OperationOptionsBuilder().build();
        ObjectClass oc = new ObjectClass("hr.employee");
        Uid uid = new Uid(Integer.toString(employeeId));
        TestResultsHandler resultsHandler = new TestResultsHandler();

        connector.executeQuery(oc, new EqualsFilter(uid), resultsHandler, oo);

        ConnectorObject employee = resultsHandler.getSingleConnectorObject();
        System.out.println(employee);
    }

    private void dumpField(Map.Entry<String, Object> field) {
        Object value = field.getValue();
        if (value.getClass().isArray()) {
            value = Arrays.toString((Object[]) value);
        }
        System.out.println(field.getKey() + ": " + value);
    }

    public static void main(String[] args) {
        DevelopmentTest developmentTest = new DevelopmentTest();
//        developmentTest.dumpModel();
        developmentTest.displayEmployee(1);
    }

}
