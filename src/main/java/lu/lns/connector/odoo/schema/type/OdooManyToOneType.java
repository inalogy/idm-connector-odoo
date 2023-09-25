package lu.lns.connector.odoo.schema.type;

import lu.lns.connector.odoo.OdooConstants;
import lu.lns.connector.odoo.schema.OdooField;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            // same as with the for the many to many we need to convert it to String as they're extending same class
            List<String> tuple = Stream.of((Object[]) valueFromXmlRpc).map(
                    (Object obj) -> Objects.toString(obj, null)
            ).collect(Collectors.toList());

            if (!tuple.isEmpty() && tuple.get(0) instanceof String) {
                return tuple.get(0);
            }
        }
        return super.mapToConnIdValue(valueFromXmlRpc, context);
    }

    public String getRelatedModel() {
        return relatedModel;
    }

}
