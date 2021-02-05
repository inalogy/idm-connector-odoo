package com.cognitumsoftware.connector.odoo;

import org.apache.xmlrpc.XmlRpcException;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.ConnectorClass;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.cognitumsoftware.connector.odoo.OdooConstants.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@ConnectorClass(displayNameKey = "odoo.connector.display", configurationClass = OdooConfiguration.class)
public class OdooConnector extends AbstractOdooConnector {

    private static final Log LOG = Log.getLog(OdooConnector.class);

    @Override
    public void doTest() throws XmlRpcException {
        Object result = client.execute(xmlRpcClientConfigCommon, "version", Collections.emptyList());
        LOG.ok("Test connection result: {0}", result);
    }

    public void listDatabases() {
        executeOperation(() -> {
            Object result = client.execute(xmlRpcClientConfigDb, "list", Collections.emptyList());

            LOG.ok("Listing database result: {0}", Arrays.toString((Object[]) result));
            return null;
        });
    }

    @Override
    public Schema doSchema() throws XmlRpcException {
        SchemaBuilder sb = new SchemaBuilder(this.getClass());

        LOG.ok("---- Fetching schema from odoo ----");

        // fetch model infos
        Object[] models = (Object[]) executeXmlRpc(MODEL_NAME_MODELS, OPERATION_SEARCH_READ, Collections.emptyList(),
                Map.of(OPERATION_PARAMETER_FIELDS, asList(MODEL_FIELD_MODEL, MODEL_FIELD_FIELD_IDS, MODEL_FIELD_NAME)));
        Set<String> unmappedTypes = new HashSet<>();

        for (Object modelObj : models) {
            Map<String, Object> model = (Map<String, Object>) modelObj;

            ObjectClassInfoBuilder ocib = new ObjectClassInfoBuilder();
            ocib.setType((String) model.get(MODEL_FIELD_MODEL));

            // fetch field infos
            Object[] fieldIds = (Object[]) model.get(MODEL_FIELD_FIELD_IDS);
            Object[] fields = (Object[]) executeXmlRpc(MODEL_NAME_MODEL_FIELDS, OPERATION_READ, singletonList(asList(fieldIds)));

            for (Object fieldObj : fields) {
                Map<String, Object> field = (Map<String, Object>) fieldObj;

                AttributeInfoBuilder aib = new AttributeInfoBuilder();

                String fieldName = (String) field.get(MODEL_FIELD_FIELD_NAME);
                aib.setName(fieldName);
                aib.setRequired((Boolean) field.get(MODEL_FIELD_FIELD_REQUIRED));
                aib.setReadable(true);
                aib.setCreateable(true);
                aib.setUpdateable((Boolean) field.get(MODEL_FIELD_FIELD_STORE));

                String fieldType = (String) field.get(MODEL_FIELD_FIELD_TYPE);
                Class<?> mappedType = mapOdooTypeToConnIdType(fieldType);
                if (mappedType == null) {
                    if (!unmappedTypes.contains(fieldType)) {
                        LOG.warn("Unable to map odoo type ''{0}'' to connId type, ignoring field ''{1}''", fieldType, fieldName);
                        unmappedTypes.add(fieldType);
                    }
                    continue;
                }
                aib.setType(mappedType);

                ocib.addAttributeInfo(aib.build());
            }

            sb.defineObjectClass(ocib.build());

            LOG.ok("Model: name={0}, model={1}, fields={2}",
                    model.get(MODEL_FIELD_NAME),
                    model.get(MODEL_FIELD_MODEL),
                    fieldIds.length
            );
        }

        LOG.ok("Models: {0}", models.length);
        LOG.ok("---- Fetching schema end ----");

        return sb.build();
    }

    private Class<?> mapOdooTypeToConnIdType(String odooType) {
        switch (odooType) {
            case "char":
            case "text":
            case "selection":
            case "html":
                return String.class;
            case "boolean":
                return Boolean.class;
            case "integer":
                return Integer.class;
            case "float":
                return Float.class;
            case "date":
            case "datetime":
                return ZonedDateTime.class;
            case "binary":
                return byte[].class;
            default:
                return null;
        }
    }

    @Override
    public Uid doCreate(ObjectClass objectClass, Set<Attribute> createAttributes, OperationOptions options) {
        throw new UnsupportedOperationException("tbd");
    }

    @Override
    public void doDelete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        throw new UnsupportedOperationException("tbd");
    }

    @Override
    public FilterTranslator<Filter> doCreateFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        throw new UnsupportedOperationException("tbd");
    }

    @Override
    public void doExecuteQuery(ObjectClass objectClass, Filter query, ResultsHandler handler, OperationOptions options) {
        throw new UnsupportedOperationException("tbd");
    }

    @Override
    public Uid doUpdate(ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes, OperationOptions options) {
        throw new UnsupportedOperationException("tbd");
    }

}
