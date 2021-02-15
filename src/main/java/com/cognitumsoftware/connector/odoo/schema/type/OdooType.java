package com.cognitumsoftware.connector.odoo.schema.type;

import com.cognitumsoftware.connector.odoo.schema.OdooField;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents an Odoo type like 'char' or 'binary'. Allows to convert between values from connId framework
 * and odoo XML-RPC values. See also {@link OdooTypeMapping} to retrieve the correct {@link OdooType} for a
 * given odoo type string.
 * <p>
 * An {@link OdooType} might implement {@link MultiValueOdooType} to handle delta attribute values properly.
 */
public abstract class OdooType {

    /**
     * Creates an instance of OdooType that is specific to the model field. Some odoo types have properties
     * that need to be taken into account. For example, the selection type has a list of possible selection
     * values.
     * <p>
     * Default implementation returns <code>this</code>. This is only useful when the type can be handled
     * generically and does not depend on additional properties of the model field.
     *
     * @param fieldProperties record from "ir.model.fields", i.e. definition of the field
     * @return an {@link OdooType} that handles the specific model field or null if there was a problem to
     * interpret the field properties and therefore the type cannot be used
     */
    public OdooType refine(String modelName, String fieldName, Map<String, Object> fieldProperties) {
        return this;
    }

    /**
     * @return the type that corresponds on the connId framework side to this odoo type, e.g. 'char' is mapped to String class
     */
    public abstract Class<?> getMappedConnIdType();

    /**
     * Maps a value of an odoo record field to a value that is understood by connId framework. The mapped value is of type
     * {@link #getMappedConnIdType()} or a collection of them in case of multi-value attributes.
     */
    public Object mapToConnIdValue(Object valueFromXmlRpc, OdooField context) {
        if (valueFromXmlRpc == null) {
            return null;
        }
        else if (!getMappedConnIdType().equals(Boolean.class) && valueFromXmlRpc instanceof Boolean && !(Boolean) valueFromXmlRpc) {
            // we interpret this as null although this is very strange to receive a boolean "false" when field is not set;
            // maybe this is due to XML-RPC compliance which doesn't allow "nil" by default
            return null;
        }
        else {
            return mapToConnIdValue(valueFromXmlRpc).orElseThrow(() ->
                    new ConnectorException("Unable to map odoo XML-RPC value of type " + valueFromXmlRpc.getClass().getName()
                            + " to " + getMappedConnIdType().getName() + " for model '" + context.getModel().getName() + "' field '"
                            + context.getName() + "'"));
        }
    }

    /**
     * Allows implementors to customize mapping to connId value.
     * Default implementation expects the same data type in odoo XML-RPC as in connId.
     *
     * @param valueFromXmlRpc not null
     */
    protected Optional<Object> mapToConnIdValue(Object valueFromXmlRpc) {
        if (getMappedConnIdType().isAssignableFrom(valueFromXmlRpc.getClass())) {
            return Optional.of(valueFromXmlRpc);
        }
        return Optional.empty();
    }

    /**
     * Maps a connId framework attribute value to a suitable odoo record field value for a create record operation.
     */
    public Object mapToOdooCreateRecordValue(Object attributeValueFromConnId) {
        return mapToOdooValue(attributeValueFromConnId);
    }

    /**
     * Same as {@link #mapToOdooCreateRecordValue(Object)} but for update operation.
     */
    public Object mapToOdooUpdateRecordValue(Object attributeValueFromConnId) {
        return mapToOdooCreateRecordValue(attributeValueFromConnId);
    }

    /**
     * Maps a connId framework filter value to a suitable odoo record field value for a search operation.
     * Example: Search a "res.users" record by field "login" that is {@link OdooCharType}. ConnId will supply
     * a String value which is left untouched by this mapping method because Odoo can handle it as "char".
     */
    public Object mapToOdooSearchFilterValue(Object attributeValueFromConnId) {
        return mapToOdooValue(attributeValueFromConnId);
    }

    /**
     * Default implementation for the mapToOdoo*Value methods. Maps null values to {@link Boolean#FALSE}.
     */
    protected Object mapToOdooValue(Object attributeValueFromConnId) {
        // mimic null behavior same as odoo returning boolean "false" for unset values
        return Objects.requireNonNullElse(attributeValueFromConnId, Boolean.FALSE);
    }

}
