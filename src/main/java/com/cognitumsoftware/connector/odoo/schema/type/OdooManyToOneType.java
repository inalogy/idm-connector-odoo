package com.cognitumsoftware.connector.odoo.schema.type;

import com.cognitumsoftware.connector.odoo.schema.OdooField;

public class OdooManyToOneType extends OdooRelationType {

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

}
