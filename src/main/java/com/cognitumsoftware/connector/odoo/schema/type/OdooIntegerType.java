package com.cognitumsoftware.connector.odoo.schema.type;

public class OdooIntegerType extends OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return Integer.class;
    }

}
