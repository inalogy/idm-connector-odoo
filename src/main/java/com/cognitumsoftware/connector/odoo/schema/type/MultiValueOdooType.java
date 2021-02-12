package com.cognitumsoftware.connector.odoo.schema.type;

import java.util.List;

public interface MultiValueOdooType {

    Object mapToOdooUpdateRecordDeltaValue(List<Object> valuesToAdd, List<Object> valuesToRemove);

}
