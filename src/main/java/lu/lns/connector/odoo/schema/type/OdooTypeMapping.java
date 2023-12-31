package lu.lns.connector.odoo.schema.type;

import lu.lns.connector.odoo.OdooConstants;

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
            Map.entry("selection", new OdooSelectionType()),
            Map.entry("many2many", new OdooOneOrManyToManyType()),
            Map.entry("one2many", new OdooOneOrManyToManyType()),
            Map.entry("many2one", new OdooManyToOneType())
    );

    /**
     * Singleton for {@link OdooIdType}.
     */
    public static final OdooType ID_TYPE = new OdooIdType();

    private OdooTypeMapping() {
        // no instancing
    }

    /**
     * Maps an odoo field type ({@link OdooConstants#MODEL_FIELD_FIELD_TYPE} value
     * to an {@link OdooType} that handles mapping of odoo data to conn ID framework.
     */
    public static OdooType map(String odooType) {
        return mapping.get(odooType);
    }

}
