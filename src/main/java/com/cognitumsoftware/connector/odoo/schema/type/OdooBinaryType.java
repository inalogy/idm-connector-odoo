package com.cognitumsoftware.connector.odoo.schema.type;

public class OdooBinaryType implements OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return byte[].class;
    }

}
