package com.cognitumsoftware.connector.odoo.schema.type;

public class OdooCharType extends OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return String.class;
    }

}
