package lu.lns.connector.odoo;

/**
 * Constants related to the connector itself and its configuration.
 */
public final class Constants {

    /**
     * Used to reference a field of a model, e.g. "res.users--partner_id", or to reference a field of
     * a related record, e.g. "partner_id--phone" when writing to a "res.users" record. If changing this
     * constant, adapt also in Messages.properties translation files.
     * <p>
     * NOTE: Special characters like "#" or "->" would but more intuitive but are producing ugly attribute
     * names in midpoint because of escaping.
     */
    public static final String MODEL_FIELD_SEPARATOR = "--";

    private Constants() {
        // no instancing
    }

}
