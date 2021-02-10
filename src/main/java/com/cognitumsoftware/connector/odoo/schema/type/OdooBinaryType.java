package com.cognitumsoftware.connector.odoo.schema.type;

import java.util.Base64;
import java.util.Optional;

public class OdooBinaryType extends OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return byte[].class;
    }

    @Override
    protected Optional<Object> mapToConnIdValue(Object valueFromXmlRpc) {
        if (valueFromXmlRpc instanceof String) {
            return Optional.of(Base64.getDecoder().decode(valueFromXmlRpc.toString()));
        }

        return Optional.empty();
    }

}
