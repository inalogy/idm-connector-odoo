package com.cognitumsoftware.connector.odoo.schema.type;

public abstract class OdooRelationType extends OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return Integer.class; // multi-valued integer for one or more IDs
    }

}
