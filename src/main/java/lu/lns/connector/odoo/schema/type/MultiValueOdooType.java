package lu.lns.connector.odoo.schema.type;

import java.util.List;

/**
 * Allows to handle the connId update delta operation for multi-value attributes properly. A multi-value attribute
 * value can either be replaced at once (valuesToReplace) or by specifying add/remove values (valuesToAdd,
 * valuesToRemove).
 */
public interface MultiValueOdooType {

    /**
     * For an update operation of multi-value attribute (connId), maps to a value for odoo to add and remove
     * some values of the field.
     */
    Object mapToOdooUpdateRecordDeltaValue(List<Object> valuesToAdd, List<Object> valuesToRemove);

}
