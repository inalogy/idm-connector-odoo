package com.cognitumsoftware.connector.odoo.schema.type;

public class OdooCharType implements OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return String.class;
    }

}
