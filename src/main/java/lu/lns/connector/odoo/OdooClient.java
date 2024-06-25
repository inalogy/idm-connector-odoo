package lu.lns.connector.odoo;

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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the XML-RPC client to communicate with odoo. Also handles authentication to the API automatically.
 */
public class OdooClient {

    private OdooConfiguration configuration;
    private XmlRpcClient client;
    private Integer authenticationToken;

    private XmlRpcClientConfig xmlRpcClientConfigCommon;

    public OdooClient(OdooConfiguration configuration) {
        this(configuration, false);
    }

    public OdooClient(OdooConfiguration configuration, boolean logTransport) {
        this.configuration = configuration;
        this.client = new XmlRpcClient();
        this.authenticationToken = null;
        this.xmlRpcClientConfigCommon = createXmlRpcClientConfig(OdooConstants.XMLRPC_COMMON);

        // set a default of XML-RPC client configuration that fits most cases
        client.setConfig(createXmlRpcClientConfig(OdooConstants.XMLRPC_OBJECT));

        if (logTransport) {
            client.setTransportFactory(() -> new MessageLoggingTransport(client));
        }
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

    public XmlRpcClient getXmlRpcClient() {
        return client;
    }

    /**
     * Same as executeOperation but authenticates to odoo first if not already done.
     */
    public <T> T executeOperationWithAuthentication(XmlRpcOp<T> operation) {
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
    public <T> T executeOperation(XmlRpcOp<T> operation) {
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

    public Map<String, Map<String, Object>> fetchFieldsMetadata(String modelName) {
        try {
            GuardedStringAccessor accessorSecret = new GuardedStringAccessor();
            configuration.getPassword().access(accessorSecret);

            List<Object> params = new LinkedList<>();
            params.add(configuration.getDatabase());
            params.add(authenticationToken);
            params.add(new String(accessorSecret.getClearChars()));
            params.add(modelName); // Model name for which fields metadata is requested
            params.add("fields_get"); // Operation name for fields_get
            params.add(Collections.emptyList()); // No filters for fields_get
            params.add(new HashMap<>()); // Empty map for additional params

            // Execute operation
            Map<String, Map<String, Object>> fieldsMetadata = (Map<String, Map<String, Object>>) client.execute("execute_kw", params);
            return fieldsMetadata;

        } catch (Exception e) {
            // Handle exceptions
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Functional interface for an operation performed in Odoo using its XML-RPC API. Mainly defined to handle the
     * {@link XmlRpcException}s.
     *
     * @param <T> return type of the operation
     */
    @FunctionalInterface
    protected interface XmlRpcOp<T> {

        T execute() throws XmlRpcException;

    }

}
