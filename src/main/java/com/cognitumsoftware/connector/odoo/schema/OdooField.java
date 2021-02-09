package com.cognitumsoftware.connector.odoo.schema;

import com.cognitumsoftware.connector.odoo.schema.type.OdooType;

public class OdooField {

    private String name;
    private OdooType type;
    private OdooModel model;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OdooType getType() {
        return type;
    }

    public void setType(OdooType type) {
        this.type = type;
    }

    public OdooModel getModel() {
        return model;
    }

    public void setModel(OdooModel model) {
        this.model = model;
    }

}
