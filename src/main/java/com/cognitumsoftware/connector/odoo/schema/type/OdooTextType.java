package com.cognitumsoftware.connector.odoo.schema.type;

public class OdooTextType implements OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return String.class;
    }

}
