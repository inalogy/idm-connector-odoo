package com.cognitumsoftware.connector.odoo;

import com.cognitumsoftware.connector.odoo.schema.OdooField;
import com.cognitumsoftware.connector.odoo.schema.OdooModel;
import com.cognitumsoftware.connector.odoo.schema.type.OdooType;
import com.cognitumsoftware.connector.odoo.schema.type.OdooTypeMapping;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.HashMap;
import java.util.Map;

import static com.cognitumsoftware.connector.odoo.OdooConstants.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class OdooModelCache {

    private OdooClient client;
    private Map<String, OdooModel> modelCache = new HashMap<>();

    public OdooModelCache(OdooClient client) {
        this.client = client;
    }

    public OdooModel getModel(ObjectClass oc) {
        return getModel(oc.getObjectClassValue());
    }

    public OdooModel getModel(String modelName) {
        OdooModel result = modelCache.get(modelName);

        if (result == null) {
            result = retrieveModel(modelName);
            modelCache.put(modelName, result);
        }

        return result;
    }

    private OdooModel retrieveModel(String modelName) {
        // retrieve model info
        Object[] models = (Object[]) client.executeXmlRpc(MODEL_NAME_MODELS, OPERATION_SEARCH_READ,
                singletonList(singletonList(asList(MODEL_FIELD_MODEL, OPERATOR_EQUALS, modelName))),
                Map.of(OPERATION_PARAMETER_FIELDS, asList(MODEL_FIELD_MODEL, MODEL_FIELD_FIELD_IDS)));

        if (models.length != 1) {
            throw new ConnectorException("Unable to retrieve odoo model '" + modelName + "'");
        }

        Map<String, Object> model = (Map<String, Object>) models[0];
        OdooModel result = new OdooModel();
        result.setName((String) model.get(MODEL_FIELD_MODEL));
        result.setFields(new HashMap<>());

        // retrieve model field infos
        Object[] fieldIds = (Object[]) model.get(MODEL_FIELD_FIELD_IDS);
        Object[] fields = (Object[]) client.executeXmlRpc(MODEL_NAME_MODEL_FIELDS, OPERATION_READ, singletonList(asList(fieldIds)));

        for (Object fieldObj : fields) {
            Map<String, Object> field = (Map<String, Object>) fieldObj;

            String fieldName = (String) field.get(MODEL_FIELD_FIELD_NAME);
            if (fieldName.equals(MODEL_FIELD_FIELD_NAME_ID)) {
                // special handling of ID attribute, see below
                continue;
            }

            OdooField f = new OdooField();
            f.setModel(result);
            f.setName(fieldName);

            String fieldType = (String) field.get(MODEL_FIELD_FIELD_TYPE);
            OdooType type = OdooTypeMapping.map(fieldType);

            if (type != null) {
                f.setType(type.refine(modelName, fieldName, field));
                if (f.getType() != null) {
                    result.getFields().put(f.getName(), f);
                }
            }
        }

        // add special id field that is always present but not retrieved with query above
        OdooField f = new OdooField();
        f.setModel(result);
        f.setName(MODEL_FIELD_FIELD_NAME_ID);
        f.setType(OdooTypeMapping.ID_TYPE);
        result.getFields().put(f.getName(), f);

        return result;
    }

    public void evict() {
        modelCache.clear();
    }

}
