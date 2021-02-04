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
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Provides:
 * - authentication to odoo for operations that require it
 * - helper methods
 * - proper exception translation for connector operations
 */
public abstract class AbstractOdooConnector implements Connector,
        CreateOp, DeleteOp, SearchOp<Filter>, TestOp, SchemaOp, UpdateOp {

    protected OdooConfiguration configuration;
    protected XmlRpcClient client;
    protected Integer authenticationToken;

    protected XmlRpcClientConfig xmlRpcClientConfigCommon;
    protected XmlRpcClientConfig xmlRpcClientConfigObject;
    protected XmlRpcClientConfig xmlRpcClientConfigDb;

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void init(Configuration cfg) {
        this.configuration = (OdooConfiguration) cfg;
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

    @Override
    public void dispose() {
        // nothing to cleanup here
    }

    @Override
    public void test() {
        executeOperation(() -> {
            doTest();
            return null;
        });
    }

    protected abstract void doTest() throws XmlRpcException;

    @Override
    public Schema schema() {
        return executeOperationWithAuthentication(this::doSchema);
    }

    protected abstract Schema doSchema() throws XmlRpcException;

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> createAttributes, OperationOptions options) {
        return executeOperationWithAuthentication(() -> doCreate(objectClass, createAttributes, options));
    }

    protected abstract Uid doCreate(ObjectClass objectClass, Set<Attribute> createAttributes, OperationOptions options)
            throws XmlRpcException;

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        executeOperationWithAuthentication(() -> {
            doDelete(objectClass, uid, options);
            return null;
        });
    }

    protected abstract void doDelete(ObjectClass objectClass, Uid uid, OperationOptions options)
            throws XmlRpcException;

    @Override
    public FilterTranslator<Filter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        return executeOperationWithAuthentication(() -> doCreateFilterTranslator(objectClass, options));
    }

    protected abstract FilterTranslator<Filter> doCreateFilterTranslator(ObjectClass objectClass, OperationOptions options)
            throws XmlRpcException;

    @Override
    public void executeQuery(ObjectClass objectClass, Filter query, ResultsHandler handler, OperationOptions options) {
        executeOperationWithAuthentication(() -> {
            doExecuteQuery(objectClass, query, handler, options);
            return null;
        });
    }

    protected abstract void doExecuteQuery(ObjectClass objectClass, Filter query, ResultsHandler handler, OperationOptions options)
            throws XmlRpcException;

    @Override
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes, OperationOptions options) {
        return executeOperationWithAuthentication(() -> doUpdate(objectClass, uid, replaceAttributes, options));
    }

    protected abstract Uid doUpdate(ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes, OperationOptions options)
            throws XmlRpcException;

    /**
     * Same as executeOperation but authenticates to odoo first if not already done.
     */
    protected <T> T executeOperationWithAuthentication(ConnectorOp<T> operation) {
        return executeOperation(() -> {
            if (authenticationToken == null) {
                GuardedStringAccessor accessorSecret = new GuardedStringAccessor();
                configuration.getPassword().access(accessorSecret);

                Object authenticationResult = client.execute(xmlRpcClientConfigCommon, "authenticate", Arrays.asList(
                        configuration.getDatabase(), configuration.getUsername(), new String(accessorSecret.getClearChars()),
                        Collections.emptyMap()));

                if (Boolean.FALSE.equals(authenticationResult)) {
                    throw new InvalidCredentialException();
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
    protected <T> T executeOperation(ConnectorOp<T> operation) {
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
    protected Object executeXmlRpc(String model, String operation, Object... operationParameters) throws XmlRpcException {
        GuardedStringAccessor accessorSecret = new GuardedStringAccessor();
        configuration.getPassword().access(accessorSecret);

        List<Object> params = new LinkedList<>();
        params.add(configuration.getDatabase());
        params.add(authenticationToken);
        params.add(new String(accessorSecret.getClearChars()));
        params.add(model);
        params.add(operation);
        params.addAll(Arrays.asList(operationParameters));

        return client.execute("execute_kw", params);
    }

    @FunctionalInterface
    protected interface ConnectorOp<T> {

        T execute() throws XmlRpcException;

    }

}
