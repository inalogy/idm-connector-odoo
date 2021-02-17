package com.cognitumsoftware.connector.odoo.schema.type;

import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.cognitumsoftware.connector.odoo.OdooConstants.MODEL_FIELD_FIELD_SELECTION;

/**
 * Maps the "selection" odoo type that is based on an underlying set. Only those values of the set are valid values
 * for the field. The set is parsed from the field information.
 */
public class OdooSelectionType extends OdooType {

    /**
     * Regex pattern to match selection values. Usual format is [('value1', 'label1'), ('value2', 'label2')]
     * but we do not take ' escaping into account because undocumented.
     */
    private static final Pattern VALUES_PATTERN = Pattern.compile("\\('([^']*)', '[^']*'\\)(, )?");

    private List<String> values;

    @Override
    public OdooType refine(String modelName, String fieldName, Map<String, Object> fieldProperties) {
        OdooSelectionType refined = new OdooSelectionType();

        String selection = (String) fieldProperties.get(MODEL_FIELD_FIELD_SELECTION);
        refined.values = parseValues(selection);

        return refined.values != null ? refined : null;
    }

    private List<String> parseValues(String selection) {
        if (!selection.startsWith("[") || !selection.endsWith("]")) {
            return null;
        }
        List<String> result = new ArrayList<>();
        Matcher matcher = VALUES_PATTERN.matcher(selection);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    @Override
    public Class<?> getMappedConnIdType() {
        return String.class;
    }

    @Override
    protected Object mapToOdooValue(Object attributeValueFromConnId) {
        if (attributeValueFromConnId instanceof String) {
            if (!values.contains(attributeValueFromConnId)) {
                throw new InvalidAttributeValueException("Attribute value '" + attributeValueFromConnId + "' is not contained in allowed " +
                        "selection values " + values);
            }
            return attributeValueFromConnId;
        }

        return super.mapToOdooValue(attributeValueFromConnId);
    }

}
