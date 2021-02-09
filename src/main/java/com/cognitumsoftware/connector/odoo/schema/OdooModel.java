package com.cognitumsoftware.connector.odoo.schema;

import java.util.Map;

public class OdooModel {

    private String name;
    private Map<String, OdooField> fields;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

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
