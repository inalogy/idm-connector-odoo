package com.cognitumsoftware.connector.odoo.schema.type;

import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static com.cognitumsoftware.connector.odoo.OdooConstants.*;

public class OdooOneOrManyToManyType extends OdooRelationType implements MultiValueOdooType {

    @Override
    protected Optional<Object> mapToConnIdValue(Object valueFromXmlRpc) {
        if (valueFromXmlRpc instanceof Object[]) {
            // expecting integer ID array
            Object[] ids = (Object[]) valueFromXmlRpc;
            return Optional.of(Arrays.asList(ids));
        }
        return super.mapToConnIdValue(valueFromXmlRpc);
    }

    @Override
    protected Object mapToOdooValue(Object attributeValueFromConnId) {
        if (attributeValueFromConnId == null) {
            return Collections.singletonList(getX2ManyWriteCommandReplaceAll(Collections.emptyList()));
        }
        else if (attributeValueFromConnId instanceof List) {
            List<Integer> ids = (List<Integer>) attributeValueFromConnId;
            return Collections.singletonList(getX2ManyWriteCommandReplaceAll(ids));
        }
        throw new InvalidAttributeValueException("Unexpected connId value for *2many type: Expects null or list of integer IDs");
    }

    @Override
    public Object mapToOdooUpdateRecordDeltaValue(List<Object> valuesToAdd, List<Object> valuesToRemove) {
        List<Object> commands = new LinkedList<>();

        if (valuesToRemove != null) {
            valuesToRemove.forEach(idObj -> commands.add(getX2ManyWriteCommandRemoveRef((Integer) idObj)));
        }

        if (valuesToAdd != null) {
            valuesToAdd.forEach(idObj -> commands.add(getX2ManyWriteCommandAddRef((Integer) idObj)));
        }

        if (commands.isEmpty()) {
            throw new InvalidAttributeValueException("Unexpected connId value for *2many type: Expects at least one add or remove " +
                    "for update delta mapping");
        }

        return commands;
    }

}
