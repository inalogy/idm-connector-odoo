package com.cognitumsoftware.connector.odoo.schema.type;

import org.identityconnectors.framework.common.exceptions.ConnectorException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Maps between the {@link ZonedDateTime} used by connId framework for date/time attribute values and the
 * format used by odoo. As the odoo format is without time zone, we assume the system default time zone.
 */
public class OdooDateTimeType extends OdooType {

    private String format;
    private DateTimeFormatter formatter;
    private ZoneId defaultZoneId = ZoneId.systemDefault();

    public OdooDateTimeType() {
        this("yyyy-MM-dd HH:mm:ss");
    }

    public OdooDateTimeType(String format) {
        this.format = format;
        this.formatter = DateTimeFormatter.ofPattern(format);
    }

    @Override
    public Class<?> getMappedConnIdType() {
        return ZonedDateTime.class;
    }

    @Override
    protected Optional<Object> mapToConnIdValue(Object valueFromXmlRpc) {
        if (valueFromXmlRpc instanceof String) {
            String formattedDateTime = (String) valueFromXmlRpc;
            try {
                return Optional.of(LocalDateTime.parse(formattedDateTime, formatter).atZone(defaultZoneId));
            }
            catch (DateTimeParseException e) {
                throw new ConnectorException("Value '" + valueFromXmlRpc + "' from Odoo XML-RPC API cannot be parsed as date/time format '"
                        + format + "'", e);
            }
        }
        return Optional.empty();
    }

    @Override
    protected Object mapToOdooValue(Object attributeValueFromConnId) {
        ZonedDateTime value = (ZonedDateTime) attributeValueFromConnId;
        return value == null ? null : value.withZoneSameInstant(defaultZoneId).format(formatter);
    }

}
