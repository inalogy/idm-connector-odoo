package com.cognitumsoftware.connector.odoo;

import com.evolveum.polygon.common.GuardedStringAccessor;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfig;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class OdooClient {

    protected OdooConfiguration configuration;
    protected XmlRpcClient client;
    protected Integer authenticationToken;

    protected XmlRpcClientConfig xmlRpcClientConfigCommon;
    protected XmlRpcClientConfig xmlRpcClientConfigObject;
    protected XmlRpcClientConfig xmlRpcClientConfigDb;

    public OdooClient(OdooConfiguration configuration) {
        this.configuration = configuration;
        this.client = new XmlRpcClient();
        this.authenticationToken = null;

        this.xmlRpcClientConfigCommon = createXmlRpcClientConfig("/xmlrpc/2/common");
        this.xmlRpcClientConfigObject = createXmlRpcClientConfig("/xmlrpc/2/object");
        this.xmlRpcClientConfigDb = createXmlRpcClientConfig("/xmlrpc/2/db");

        // set a default of XML-RPC client configuration that fits most cases
        client.setConfig(xmlRpcClientConfigObject);
    }

    private XmlRpcClientConfig createXmlRpcClientConfig(String path) {
        try {
            XmlRpcClientConfigImpl xmlRpcCfg = new XmlRpcClientConfigImpl();
            xmlRpcCfg.setServerURL(new URL(String.format("%s" + path, configuration.getUrl())));
            return xmlRpcCfg;
        }
        catch (MalformedURLException e) {
            throw new ConfigurationException("Invalid server URL", e);
        }
    }

    public XmlRpcClientConfig getXmlRpcClientConfigCommon() {
        return xmlRpcClientConfigCommon;
    }

    public XmlRpcClientConfig getXmlRpcClientConfigDb() {
        return xmlRpcClientConfigDb;
    }

    public XmlRpcClient getClient() {
        return client;
    }

    /**
     * Same as executeOperation but authenticates to odoo first if not already done.
     */
    public <T> T executeOperationWithAuthentication(ConnectorOp<T> operation) {
        return executeOperation(() -> {
            if (authenticationToken == null) {
                GuardedStringAccessor accessorSecret = new GuardedStringAccessor();
                configuration.getPassword().access(accessorSecret);

                Object authenticationResult = client.execute(xmlRpcClientConfigCommon, "authenticate", Arrays.asList(
                        configuration.getDatabase(), configuration.getUsername(), new String(accessorSecret.getClearChars()),
                        Collections.emptyMap()));

                if (Boolean.FALSE.equals(authenticationResult)) {
                    throw new ConnectorException("Authentication failed, probably invalid username or password");
                }

                authenticationToken = (Integer) authenticationResult;
            }
            return operation.execute();
        });
    }

    /**
     * Executes the connector operation and translates exceptions thrown.
     *
     * @param <T> return type of operation
     */
    public <T> T executeOperation(ConnectorOp<T> operation) {
        try {
            return operation.execute();
        }
        catch (XmlRpcException e) {
            if (e.getCause() instanceof ConnectException) {
                throw new ConnectionFailedException(e);
            }
            else if (e.getCause() instanceof IOException) {
                throw new ConnectorIOException(e);
            }
            throw new ConnectorException(e);
        }
    }

    /**
     * Simplifies an XML-RPC call to odoo. The usual first parameters (db, uid, pwd) are taken from configuration and
     * authentication. The XML-RPC function called is "execute_kw".
     */
    public Object executeXmlRpc(String model, String operation, Object... operationParameters) {
        GuardedStringAccessor accessorSecret = new GuardedStringAccessor();
        configuration.getPassword().access(accessorSecret);

        List<Object> params = new LinkedList<>();
        params.add(configuration.getDatabase());
        params.add(authenticationToken);
        params.add(new String(accessorSecret.getClearChars()));
        params.add(model);
        params.add(operation);
        params.addAll(Arrays.asList(operationParameters));

        return executeOperation(() -> client.execute("execute_kw", params));
    }

    @FunctionalInterface
    protected interface ConnectorOp<T> {

        T execute() throws XmlRpcException;

    }

}
