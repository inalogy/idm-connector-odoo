package lu.lns.connector.odoo;

import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;

class TestResultsHandler implements ResultsHandler {

    private final List<ConnectorObject> connectorObjects = new ArrayList<>();

    @Override
    public boolean handle(ConnectorObject connectorObject) {
        connectorObjects.add(connectorObject);
        return true;
    }

    public List<ConnectorObject> getConnectorObjects() {
        return connectorObjects;
    }

    public ConnectorObject getSingleConnectorObject() {
        Assert.assertEquals(1, connectorObjects.size());
        return connectorObjects.get(0);
    }

}
