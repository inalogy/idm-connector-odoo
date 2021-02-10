package com.cognitumsoftware.connector.odoo.schema.type;

public class OdooFloatType extends OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return Double.class;
    }

}
