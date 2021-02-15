package com.cognitumsoftware.connector.odoo.schema.type;

/**
 * Base class for relational odoo types.
 */
public abstract class OdooRelationType extends OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return Integer.class; // multi-valued integer for one or more IDs
    }

}
