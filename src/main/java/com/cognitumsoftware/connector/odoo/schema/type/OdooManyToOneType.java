package com.cognitumsoftware.connector.odoo.schema.type;

import com.cognitumsoftware.connector.odoo.OdooConstants;
import com.cognitumsoftware.connector.odoo.schema.OdooField;

import java.util.Map;

/**
 * Maps an odoo relational record field with a target cardinality of 1. The value retrieved from odoo for
 * such a field is a tuple [id, name] but we only need the ID.
 */
public class OdooManyToOneType extends OdooRelationType {

    private String relatedModel;

    @Override
    public OdooType refine(String modelName, String fieldName, Map<String, Object> fieldProperties) {
        OdooManyToOneType refined = new OdooManyToOneType();
        refined.relatedModel = (String) fieldProperties.get(OdooConstants.MODEL_FIELD_FIELD_MANY2ONE_RELATED_MODEL);

        return refined.relatedModel != null ? refined : null;
    }

    @Override
    public Object mapToConnIdValue(Object valueFromXmlRpc, OdooField context) {
        if (valueFromXmlRpc instanceof Object[]) {
            // a pair of integer ID and name (?), did not find info in docs; however, we only need the ID
            Object[] tuple = (Object[]) valueFromXmlRpc;
            if (tuple.length > 0 && tuple[0] instanceof Integer) {
                return tuple[0];
            }
        }
        return super.mapToConnIdValue(valueFromXmlRpc, context);
    }

    public String getRelatedModel() {
        return relatedModel;
    }

}
