package com.cognitumsoftware.connector.odoo.schema.type;

public class OdooBooleanType extends OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return Boolean.class;
    }

}
