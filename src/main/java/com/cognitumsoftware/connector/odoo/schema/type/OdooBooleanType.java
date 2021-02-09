package com.cognitumsoftware.connector.odoo.schema.type;

public class OdooBooleanType implements OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return Boolean.class;
    }

}
