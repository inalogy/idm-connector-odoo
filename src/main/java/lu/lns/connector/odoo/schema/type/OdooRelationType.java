package lu.lns.connector.odoo.schema.type;

/**
 * Base class for relational odoo types.
 */
public abstract class OdooRelationType extends OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        /* multivalued integer for one or more IDs, but we need to switch them to Strings
        that is because midPoint can't handle other type that String in associations
        and all this relational attributes could be possible association. For more
        information please see MID-7784.*/
        return String.class;
    }

}
