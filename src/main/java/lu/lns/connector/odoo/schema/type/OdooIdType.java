package lu.lns.connector.odoo.schema.type;

import java.util.Optional;

/**
 * Special {@link OdooType} that maps an integer odoo ID as string for connId (special attributes
 * {@link org.identityconnectors.framework.common.objects.Uid} and {@link org.identityconnectors.framework.common.objects.Name}).
 */
public class OdooIdType extends OdooType {

    @Override
    public Class<?> getMappedConnIdType() {
        return String.class;
    }

    @Override
    protected Optional<Object> mapToConnIdValue(Object valueFromXmlRpc) {
        if (valueFromXmlRpc instanceof Integer) {
            return Optional.of(valueFromXmlRpc.toString());
        }
        return Optional.empty();
    }

    @Override
    protected Object mapToOdooValue(Object attributeValueFromConnId) {
        if (attributeValueFromConnId instanceof String) {
            return Integer.valueOf((String) attributeValueFromConnId);
        }

        return super.mapToOdooValue(attributeValueFromConnId);
    }

}
