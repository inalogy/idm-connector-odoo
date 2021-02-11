package com.cognitumsoftware.connector.odoo.schema.type;

import java.util.Map;

public final class OdooTypeMapping {

    private static Map<String, OdooType> mapping = Map.ofEntries(
            Map.entry("char", new OdooCharType()),
            Map.entry("text", new OdooTextType()),
            Map.entry("html", new OdooHtmlType()),
            Map.entry("integer", new OdooIntegerType()),
            Map.entry("float", new OdooFloatType()),
            Map.entry("boolean", new OdooBooleanType()),
            Map.entry("date", new OdooDateType()),
            Map.entry("datetime", new OdooDateTimeType()),
            Map.entry("binary", new OdooBinaryType()),
            Map.entry("selection", new OdooSelectionType())
    );

    public static final OdooType ID_TYPE = new OdooIdType();

    private OdooTypeMapping() {
        // no instancing
    }

    public static OdooType map(String odooType) {
        return mapping.get(odooType);
    }

}
