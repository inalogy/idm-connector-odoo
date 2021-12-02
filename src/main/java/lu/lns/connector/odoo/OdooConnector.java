package lu.lns.connector.odoo;

import lu.lns.connector.odoo.schema.OdooModel;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateDeltaOp;

import java.util.Collections;
import java.util.Set;

import static java.util.Collections.singletonList;

/**
 * The odoo connector uses the XML-RPC API of odoo to test connection, retrieve schema and CRUD operations.
 * See https://www.odoo.com/documentation/14.0/webservices/odoo.html.
 * <p>
 * This connector is NOT thread-safe which is assumed by connector framework according to connector implementation guide:
 * https://docs.evolveum.com/connectors/connid/1.x/connector-development-guide/
 */
@ConnectorClass(displayNameKey = "odoo.connector.display", configurationClass = OdooConfiguration.class)
public class OdooConnector implements PoolableConnector, CreateOp, DeleteOp, SearchOp<Filter>, TestOp, SchemaOp, UpdateDeltaOp {

    private static final Log LOG = Log.getLog(OdooConnector.class);

    private OdooConfiguration configuration;
    private OdooClient client;
    private OdooModelCache cache;
    private OdooSchema schemaFetcher;
    private OdooSearch searcher;
    private OdooWrite writer;

    @Override
    public OdooConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void init(Configuration cfg) {
        this.configuration = (OdooConfiguration) cfg;
        this.client = new OdooClient(configuration);
        this.cache = new OdooModelCache(client);
        this.schemaFetcher = new OdooSchema(client, configuration);
        this.searcher = new OdooSearch(client, cache);
        this.writer = new OdooWrite(client, cache);
    }

    @Override
    public void dispose() {
        // nothing to cleanup here
    }

    @Override
    public void checkAlive() {
        // for now we don't have a connection kept alive between XML-RPC calls
    }

    @Override
    public void test() {
        client.executeOperation(() -> {
            Object result = client.getXmlRpcClient().execute(client.getXmlRpcClientConfigCommon(), "version", Collections.emptyList());
            LOG.ok("Test connection result: {0}", result);
            return null;
        });
    }

    @Override
    public Schema schema() {
        cache.evict(); // when someone resolves the schema (again), it might have changed, so clear current cache

        // delegate schema resolution
        return schemaFetcher.fetch(this.getClass());
    }

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> createAttributes, OperationOptions options) {
        return client.executeOperationWithAuthentication(() -> {
            // delegate to writer
            OdooModel model = cache.getModel(objectClass);
            return writer.createRecord(model, createAttributes);
        });
    }

    @Override
    public Set<AttributeDelta> updateDelta(ObjectClass objectClass, Uid uid, Set<AttributeDelta> attributeDeltas,
            OperationOptions operationOptions) {

        return client.executeOperationWithAuthentication(() -> {
            // delegate to writer
            OdooModel model = cache.getModel(objectClass);
            return writer.updateRecord(model, uid, attributeDeltas);
        });
    }

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        client.executeOperationWithAuthentication(() -> {
            // prepare
            OdooModel model = cache.getModel(objectClass);
            Integer id = Integer.valueOf(uid.getUidValue());

            // NOTE: we do not care about related records, these must be handled by odoo

            // execute delete
            client.executeXmlRpc(model.getName(), OdooConstants.OPERATION_DELETE, singletonList(singletonList(id)));
            return null;
        });
    }

    @Override
    public FilterTranslator<Filter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        // filter translation is done within executeQuery
        return o -> o == null ? Collections.emptyList() : singletonList(o);
    }

    @Override
    public void executeQuery(ObjectClass objectClass, Filter query, ResultsHandler handler, OperationOptions options) {
        client.executeOperationWithAuthentication(() -> {
            // delegate search operation
            OdooModel model = cache.getModel(objectClass);
            searcher.search(model, query, handler, options);
            return null;
        });
    }

}
