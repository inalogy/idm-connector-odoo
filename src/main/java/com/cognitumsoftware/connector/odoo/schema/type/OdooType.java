package com.cognitumsoftware.connector.odoo.schema.type;

import com.cognitumsoftware.connector.odoo.schema.OdooField;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

public interface OdooType {

    Class<?> getMappedConnIdType();

    default Object mapToConnIdValue(Object valueFromXmlRpc, OdooField context) {
        if (valueFromXmlRpc == null) {
            return null;
        }
        else if (!getMappedConnIdType().equals(Boolean.class) && valueFromXmlRpc instanceof Boolean && !(Boolean) valueFromXmlRpc) {
            // we interpret this as null although this is very strange to receive a boolean "false" when field is not set
            return null;
        }
        else if (getMappedConnIdType().isAssignableFrom(valueFromXmlRpc.getClass())) {
            return valueFromXmlRpc;
        }
        else {
            throw new ConnectorException("Unable to map odoo XML-RPC value of type " + valueFromXmlRpc.getClass().getName()
                    + " to " + getMappedConnIdType().getName() + " for model '" + context.getModel().getName() + "' field '"
                    + context.getName() + "'");
        }
    }

    default Object mapToOdooCreateRecordValue(Object attributeValueFromConnId) {
        return attributeValueFromConnId;
    }

    default Object mapToOdooSearchFilterValue(Object attributeValueFromConnId) {
        return attributeValueFromConnId;
    }

}
