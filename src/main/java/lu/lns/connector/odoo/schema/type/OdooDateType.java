package lu.lns.connector.odoo.schema.type;

import org.identityconnectors.framework.common.exceptions.ConnectorException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Similar to {@link OdooDateTimeType} but for date component only. The time component is cut off from the date/time
 * provided by connId.
 */
public class OdooDateType extends OdooType {

    private String format;
    private DateTimeFormatter formatter;
    private ZoneId defaultZoneId = ZoneId.systemDefault();

    public OdooDateType() {
        this("yyyy-MM-dd");
    }

    public OdooDateType(String format) {
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
                return Optional.of(LocalDate.parse(formattedDateTime, formatter).atStartOfDay(defaultZoneId));
            }
            catch (DateTimeParseException e) {
                throw new ConnectorException("Value '" + valueFromXmlRpc + "' from Odoo XML-RPC API cannot be parsed as date format '"
                        + format + "'", e);
            }
        }
        return Optional.empty();
    }

    @Override
    protected Object mapToOdooValue(Object attributeValueFromConnId) {
        ZonedDateTime value = (ZonedDateTime) attributeValueFromConnId;
        return value == null ? null : value.withZoneSameInstant(defaultZoneId).format(formatter); // cuts time component
    }

}
