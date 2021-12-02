package lu.lns.connector.odoo.schema.type;

public class OdooHtmlType extends OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return String.class;
    }

}
