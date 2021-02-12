package com.cognitumsoftware.connector.odoo;

import com.cognitumsoftware.connector.odoo.schema.OdooField;
import com.cognitumsoftware.connector.odoo.schema.OdooModel;
import com.cognitumsoftware.connector.odoo.schema.type.MultiValueOdooType;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.Name;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.cognitumsoftware.connector.odoo.OdooConstants.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@ConnectorClass(displayNameKey = "odoo.connector.display", configurationClass = OdooConfiguration.class)
public class OdooConnector implements PoolableConnector, CreateOp, DeleteOp, SearchOp<Filter>, TestOp, SchemaOp, UpdateDeltaOp {

    private static final Log LOG = Log.getLog(OdooConnector.class);

    private OdooConfiguration configuration;
    private OdooClient client;
    private OdooModelCache cache;
    private OdooSchema schemaFetcher;
    private OdooSearch searcher;

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
        this.searcher = new OdooSearch(client);
    }

    @Override
    public void dispose() {
        // nothing to cleanup here
    }

    @Override
    public void checkAlive() {
        // for now we don't have a connection
    }

    @Override
    public void test() {
        client.executeOperation(() -> {
            Object result = client.getClient().execute(client.getXmlRpcClientConfigCommon(), "version", Collections.emptyList());
            LOG.ok("Test connection result: {0}", result);
            return null;
        });
    }

    public void listDatabases() {
        client.executeOperation(() -> {
            Object result = client.getClient().execute(client.getXmlRpcClientConfigDb(), "list", Collections.emptyList());

            LOG.ok("Listing database result: {0}", Arrays.toString((Object[]) result));
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
            // prepare
            OdooModel model = cache.getModel(objectClass);

            Map<String, Object> fields = new HashMap<>();

            for (Attribute attr : createAttributes) {
                if (attr.getName().equals(Uid.NAME) || attr.getName().equals(Name.NAME)) {
                    // we ignore these attributes as they are the ID of the record to be created in odoo
                    continue;
                }

                OdooField field = model.getField(attr.getName());
                if (field == null) {
                    throw new ConnectorException("Did not find odoo field with name '" + attr.getName() + "' in odoo model.");
                }

                Object val;
                if (attr.getValue() == null || attr.getValue().isEmpty()) {
                    val = null;
                }
                else if (attr.getValue().size() > 1) {
                    if (field.getType() instanceof MultiValueOdooType) {
                        val = attr.getValue();
                    }
                    else {
                        throw new InvalidAttributeValueException("Multiple attribute values not supported in create operation for " +
                                "field '" + field.getName() + "' in model '" + field.getModel().getName() + "'");
                    }
                }
                else {
                    val = attr.getValue().iterator().next();
                }

                fields.put(field.getName(), field.getType().mapToOdooCreateRecordValue(val));
            }

            // execute create
            Integer id = (Integer) client.executeXmlRpc(model.getName(), OPERATION_CREATE, singletonList(fields));
            return new Uid(id.toString());
        });
    }

    @Override
    public Set<AttributeDelta> updateDelta(ObjectClass objectClass, Uid uid, Set<AttributeDelta> attributeDeltas,
            OperationOptions operationOptions) {

        client.executeOperationWithAuthentication(() -> {
            // prepare
            OdooModel model = cache.getModel(objectClass);
            Integer id = Integer.valueOf(uid.getUidValue());

            Map<String, Object> fields = new HashMap<>();

            for (AttributeDelta delta : attributeDeltas) {
                if (delta.getName().equals(Uid.NAME) || delta.getName().equals(Name.NAME)) {
                    // we ignore these attributes as they are the ID of the record to be updated in odoo
                    continue;
                }

                OdooField field = model.getField(delta.getName());
                if (field == null) {
                    throw new ConnectorException("Did not find odoo field with name '" + delta.getName() + "' in odoo model.");
                }

                if (delta.getValuesToReplace() != null) {
                    Object val;

                    if (field.getType() instanceof MultiValueOdooType) {
                        // multi-value mapped as a whole
                        val = delta.getValuesToReplace();
                    }
                    else if (delta.getValuesToReplace().isEmpty()) {
                        val = null;
                    }
                    else if (delta.getValuesToReplace().size() > 1) {
                        throw new InvalidAttributeValueException("Multiple attribute values not supported in update operation for " +
                                "field '" + field.getName() + "' in model '" + field.getModel().getName() + "'");
                    }
                    else {
                        val = delta.getValuesToReplace().iterator().next();
                    }

                    fields.put(field.getName(), field.getType().mapToOdooUpdateRecordValue(val));
                }
                else if (field.getType() instanceof MultiValueOdooType) {
                    MultiValueOdooType mv = (MultiValueOdooType) field.getType();
                    Object val = mv.mapToOdooUpdateRecordDeltaValue(delta.getValuesToAdd(), delta.getValuesToRemove());

                    fields.put(field.getName(), val);
                }
                else {
                    throw new InvalidAttributeValueException("Delta add/remove not supported for field '" + field.getName()
                            + "' in model '" + field.getModel().getName() + "'");
                }
            }

            // execute update
            client.executeXmlRpc(model.getName(), OPERATION_UPDATE, asList(singletonList(id), fields));
            return null;
        });

        return Collections.emptySet();
    }

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        client.executeOperationWithAuthentication(() -> {
            // prepare
            OdooModel model = cache.getModel(objectClass);
            Integer id = Integer.valueOf(uid.getUidValue());

            // execute delete
            client.executeXmlRpc(model.getName(), OPERATION_DELETE, singletonList(singletonList(id)));
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
            OdooModel model = cache.getModel(objectClass);
            searcher.search(model, query, handler, options);
            return null;
        });
    }

}
