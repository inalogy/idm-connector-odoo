package com.cognitumsoftware.connector.odoo;

import com.cognitumsoftware.connector.odoo.schema.type.OdooType;
import com.cognitumsoftware.connector.odoo.schema.type.OdooTypeMapping;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.OperationOptionInfoBuilder;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.SearchOp;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.cognitumsoftware.connector.odoo.OdooConstants.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * Fetches the whole odoo model and their field information and translates them into a connId schema.
 * Depending on configuration, some models are filtered out or expanded, see {@link OdooConfiguration}.
 */
public class OdooSchema {

    private static final Log LOG = Log.getLog(OdooSchema.class);

    private OdooClient client;
    private OdooModelNameMatcher retrieveModelsMatcher;
    private OdooModelNameMatcher expandModelsMatcher;

    public OdooSchema(OdooClient client, OdooConfiguration configuration) {
        this.client = client;
        this.retrieveModelsMatcher = new OdooModelNameMatcher(configuration.getRetrieveModels(), true);
        this.expandModelsMatcher = new OdooModelNameMatcher(configuration.getExpandModels(), false);
    }

    /**
     * Calls the XML-RPC API of odoo that provides information about models and their fields, and translates
     * them into a connId schema.
     *
     * @param connectorClass odoo connector class
     * @return odoo model and fields translated into connId schema
     */
    public Schema fetch(Class<? extends Connector> connectorClass) {
        return client.executeOperationWithAuthentication(() -> doFetch(connectorClass));
    }

    private Schema doFetch(Class<? extends Connector> connectorClass) {
        SchemaBuilder sb = new SchemaBuilder(connectorClass);

        LOG.ok("---- Fetching schema from odoo ----");

        // fetch model infos
        Object[] models = (Object[]) client.executeXmlRpc(MODEL_NAME_MODELS, OPERATION_SEARCH_READ, Collections.emptyList(),
                Map.of(OPERATION_PARAMETER_FIELDS, asList(MODEL_FIELD_MODEL, MODEL_FIELD_FIELD_IDS, MODEL_FIELD_NAME)));
        Set<String> unmappedTypes = new HashSet<>();

        for (Object modelObj : models) {
            Map<String, Object> model = (Map<String, Object>) modelObj;
            String modelName = (String) model.get(MODEL_FIELD_MODEL);

            if (!retrieveModelsMatcher.matches(modelName)) {
                // as per configuration this model is not relevant to the connector user
                continue;
            }

            ObjectClassInfoBuilder ocib = new ObjectClassInfoBuilder();
            ocib.setType(modelName);

            // fetch field infos
            Object[] fieldIds = (Object[]) model.get(MODEL_FIELD_FIELD_IDS);
            Object[] fields = (Object[]) client.executeXmlRpc(MODEL_NAME_MODEL_FIELDS, OPERATION_READ, singletonList(asList(fieldIds)));

            for (Object fieldObj : fields) {
                Map<String, Object> field = (Map<String, Object>) fieldObj;

                AttributeInfoBuilder aib = new AttributeInfoBuilder();

                String fieldName = (String) field.get(MODEL_FIELD_FIELD_NAME);
                if (fieldName.equals(MODEL_FIELD_FIELD_NAME_ID)) {
                    // special handling of ID attribute, see below
                    continue;
                }

                aib.setName(fieldName);
                aib.setRequired((Boolean) field.get(MODEL_FIELD_FIELD_REQUIRED));
                aib.setReadable(true);
                aib.setCreateable(true);
                aib.setUpdateable(true);
                aib.setReturnedByDefault(false); // only id is returned by default

                String fieldType = (String) field.get(MODEL_FIELD_FIELD_TYPE);
                OdooType mappedType = OdooTypeMapping.map(fieldType);
                if (mappedType == null) {
                    if (!unmappedTypes.contains(fieldType)) {
                        LOG.warn("Unable to map odoo type ''{0}'' to connId type, ignoring field ''{1}''", fieldType, fieldName);
                        unmappedTypes.add(fieldType);
                    }
                    LOG.ok("Skipping field ''{0}'' with unmapped type={1} and details={2}", fieldName, fieldType, field);
                    continue;
                }
                mappedType = mappedType.refine(modelName, fieldName, field);
                if (mappedType == null) {
                    LOG.ok("Skipping field ''{0}'' with unrefined type={1} and details={2}", fieldName, fieldType, field);
                    continue;
                }
                aib.setType(mappedType.getMappedConnIdType());

                ocib.addAttributeInfo(aib.build());
            }

            ocib.addAttributeInfo(buildIdAttribute(Uid.NAME));
            ocib.addAttributeInfo(buildIdAttribute(Name.NAME));

            sb.defineObjectClass(ocib.build());

            LOG.info("Model: name={0}, model={1}, fields={2}",
                    model.get(MODEL_FIELD_NAME),
                    model.get(MODEL_FIELD_MODEL),
                    fieldIds.length
            );
        }

        sb.defineOperationOption(OperationOptionInfoBuilder.buildPageSize(), SearchOp.class);
        sb.defineOperationOption(OperationOptionInfoBuilder.buildPagedResultsOffset(), SearchOp.class);
        sb.defineOperationOption(OperationOptionInfoBuilder.buildSortKeys(), SearchOp.class);

        LOG.ok("Models: {0}", models.length);
        LOG.ok("---- Fetching schema end ----");

        return sb.build();
    }

    private AttributeInfo buildIdAttribute(String name) {
        AttributeInfoBuilder uidAib = new AttributeInfoBuilder(name);
        uidAib.setNativeName("id");
        uidAib.setType(String.class); // this is actually an integer but hard-coded by connid framework (we map it)
        uidAib.setRequired(false); // Must be optional. It is not present for create operations
        uidAib.setCreateable(false);
        uidAib.setUpdateable(false);
        uidAib.setReadable(true);
        uidAib.setReturnedByDefault(true);
        return uidAib.build();
    }

}
