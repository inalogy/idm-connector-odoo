package com.cognitumsoftware.connector.odoo.schema.type;

public class OdooTextType extends OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return String.class;
    }

}
