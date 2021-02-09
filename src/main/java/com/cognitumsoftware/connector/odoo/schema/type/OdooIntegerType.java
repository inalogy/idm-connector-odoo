package com.cognitumsoftware.connector.odoo.schema.type;

public class OdooIntegerType implements OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return Integer.class;
    }

}
