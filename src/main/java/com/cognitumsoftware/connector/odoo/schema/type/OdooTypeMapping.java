package com.cognitumsoftware.connector.odoo.schema.type;

import java.util.Map;

public final class OdooTypeMapping {

    private static Map<String, OdooType> mapping = Map.ofEntries(
            Map.entry("char", new OdooCharType()),
            Map.entry("text", new OdooTextType()),
            Map.entry("integer", new OdooIntegerType()),
            Map.entry("float", new OdooFloatType()),
            Map.entry("binary", new OdooBinaryType())
    );

    private OdooTypeMapping() {
        // no instancing
    }

    public static OdooType map(String odooType) {
        return mapping.get(odooType);
    }

}
