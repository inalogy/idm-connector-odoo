package com.cognitumsoftware.connector.odoo.schema;

import java.util.Map;

/**
 * Simple representation of an odoo model as defined in odoo "ir.models".
 */
public class OdooModel {

    private String name;
    private Map<String, OdooField> fields;

    /**
     * @return unique name of the model
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return fields contained in the model
     */
    public Map<String, OdooField> getFields() {
        return fields;
    }

    public OdooField getField(String name) {
        return fields.get(name);
    }

    public boolean hasField(String name) {
        return fields.containsKey(name);
    }

    public void setFields(Map<String, OdooField> fields) {
        this.fields = fields;
    }

}
