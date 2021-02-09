package com.cognitumsoftware.connector.odoo.schema.type;

public class OdooFloatType implements OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return Float.class;
    }

}
