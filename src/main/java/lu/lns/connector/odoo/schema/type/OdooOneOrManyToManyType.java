package lu.lns.connector.odoo.schema.type;

import lu.lns.connector.odoo.OdooConstants;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Special type mapping of an odoo relational record field that has a target cardinality of n.
 * Writing such a field (create, update) is done by providing "commands", see documentation:
 * <p>
 * https://www.odoo.com/documentation/14.0/reference/orm.html?highlight=write#odoo.models.Model.write
 */
public class OdooOneOrManyToManyType extends OdooRelationType implements MultiValueOdooType {

    @Override
    protected Optional<Object> mapToConnIdValue(Object valueFromXmlRpc) {
        if (valueFromXmlRpc instanceof Object[]) {
            // expecting integer ID array represented as Object[], we need to
            // manually convert them to String[] so why not directly to List<String>
            return Optional.of(Stream.of((Object[]) valueFromXmlRpc).map(
                    (Object obj) -> Objects.toString(obj, null)
            ).collect(Collectors.toList()));
        }
        return super.mapToConnIdValue(valueFromXmlRpc);
    }

    @Override
    protected Object mapToOdooValue(Object attributeValueFromConnId) {
        if (attributeValueFromConnId == null) {
            return Collections.singletonList(OdooConstants.getX2ManyWriteCommandReplaceAll(Collections.emptyList()));
        }
        else if (attributeValueFromConnId instanceof List) {
            // As we converted these relation fields to Strings for midPoint, now we need to convert them back to Integers
            List<Integer> ids = ((List<?>) attributeValueFromConnId).stream().map(String::valueOf).map(Integer::valueOf).collect(Collectors.toList());
            return Collections.singletonList(OdooConstants.getX2ManyWriteCommandReplaceAll(ids));
        }
        throw new InvalidAttributeValueException("Unexpected connId value for *2many type: Expects null or list of integer IDs");
    }

    @Override
    public Object mapToOdooUpdateRecordDeltaValue(List<Object> valuesToAdd, List<Object> valuesToRemove) {
        List<Object> commands = new LinkedList<>();

        if (valuesToRemove != null) {
            valuesToRemove.forEach(idObj -> commands.add(OdooConstants.getX2ManyWriteCommandRemoveRef((Integer) idObj)));
        }

        if (valuesToAdd != null) {
            valuesToAdd.forEach(idObj -> commands.add(OdooConstants.getX2ManyWriteCommandAddRef((Integer) idObj)));
        }

        if (commands.isEmpty()) {
            throw new InvalidAttributeValueException("Unexpected connId value for *2many type: Expects at least one add or remove " +
                    "for update delta mapping");
        }

        return commands;
    }

}
