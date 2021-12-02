package lu.lns.connector.odoo.schema;

import lu.lns.connector.odoo.schema.type.OdooType;

/**
 * Represents an odoo model's field.
 */
public class OdooField {

    private String name;
    private OdooType type;
    private OdooModel model;

    /**
     * @return name of the field
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return type of field allowing to handle field values
     */
    public OdooType getType() {
        return type;
    }

    public void setType(OdooType type) {
        this.type = type;
    }

    /**
     * @return the model where the field resides in
     */
    public OdooModel getModel() {
        return model;
    }

    public void setModel(OdooModel model) {
        this.model = model;
    }

}
