package com.cognitumsoftware.connector.odoo;

/**
 * Odoo related constants for XML-RPC API calls, schema introspection etc.
 */
public final class OdooConstants {

    public final static String MODEL_NAME_MODELS = "ir.model";
    public final static String MODEL_NAME_MODEL_FIELDS = "ir.model.fields";

    public final static String MODEL_FIELD_NAME = "name";
    public final static String MODEL_FIELD_MODEL = "model";
    public final static String MODEL_FIELD_FIELD_IDS = "field_id";

    public final static String MODEL_FIELD_FIELD_NAME = "name";
    public final static String MODEL_FIELD_FIELD_TYPE = "ttype";
    public final static String MODEL_FIELD_FIELD_REQUIRED = "required";
    public final static String MODEL_FIELD_FIELD_STORE = "store"; // false means computed field

    public final static String OPERATION_SEARCH_READ = "search_read";
    public final static String OPERATION_READ = "read";

    public final static String OPERATION_PARAMETER_FIELDS = "fields";
    public final static String OPERATION_PARAMETER_OFFSET = "offset";
    public final static String OPERATION_PARAMETER_LIMIT = "limit";

    private OdooConstants() {
        // no instancing
    }

}
