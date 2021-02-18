package com.cognitumsoftware.connector.odoo;

import java.util.Arrays;
import java.util.List;

/**
 * Odoo related constants for XML-RPC API calls, schema introspection etc.
 */
public final class OdooConstants {

    public final static String XMLRPC_COMMON = "/xmlrpc/2/common";
    public final static String XMLRPC_OBJECT = "/xmlrpc/2/object";

    public final static String MODEL_NAME_MODELS = "ir.model";
    public final static String MODEL_NAME_MODEL_FIELDS = "ir.model.fields";

    public final static String MODEL_FIELD_NAME = "name";
    public final static String MODEL_FIELD_MODEL = "model";
    public final static String MODEL_FIELD_FIELD_IDS = "field_id";

    public final static String MODEL_FIELD_FIELD_NAME = "name";
    public final static String MODEL_FIELD_FIELD_NAME_ID = "id";
    public final static String MODEL_FIELD_FIELD_TYPE = "ttype";
    public final static String MODEL_FIELD_FIELD_REQUIRED = "required";
    public final static String MODEL_FIELD_FIELD_STORE = "store"; // false means not stored in database but computed
    public final static String MODEL_FIELD_FIELD_SELECTION = "selection";
    public final static String MODEL_FIELD_FIELD_SELECTION_ADD = "selection_add";
    public final static String MODEL_FIELD_FIELD_MANY2ONE_RELATED_MODEL = "relation";

    public final static String OPERATION_SEARCH_READ = "search_read";
    public final static String OPERATION_READ = "read";
    public final static String OPERATION_CREATE = "create";
    public final static String OPERATION_UPDATE = "write";
    public final static String OPERATION_DELETE = "unlink";

    public final static String OPERATION_PARAMETER_FIELDS = "fields";
    public final static String OPERATION_PARAMETER_OFFSET = "offset";
    public final static String OPERATION_PARAMETER_LIMIT = "limit";
    public final static String OPERATION_PARAMETER_ORDER = "order";

    public final static String OPERATOR_NOT = "!";
    public final static String OPERATOR_AND = "&";
    public final static String OPERATOR_OR = "|";

    public final static String OPERATOR_EQUALS = "=";
    public final static String OPERATOR_NOT_EQUALS = "!=";
    public final static String OPERATOR_IN = "in";
    public final static String OPERATOR_LIKE = "like";
    public final static String OPERATOR_LIKE2 = "=like";
    public final static String OPERATOR_LIKE_ANY_CHAR = "_";
    public final static String OPERATOR_LIKE_ANY_STRING = "%";
    public final static String OPERATOR_SMALLER = "<";
    public final static String OPERATOR_SMALLER_EQUALS = "<=";
    public final static String OPERATOR_GREATER = ">";
    public final static String OPERATOR_GREATER_EQUALS = ">=";

    public final static String OPERATOR_LIKE_ESCAPE_CHAR = "\\";

    private OdooConstants() {
        // no instancing
    }

    // for one2many/many2many write commands see documentation:
    // https://www.odoo.com/documentation/14.0/reference/orm.html#odoo.models.Model.write

    public static Object getX2ManyWriteCommandReplaceAll(List<Integer> ids) {
        return Arrays.asList(6, 0, ids);
    }

    public static Object getX2ManyWriteCommandAddRef(Integer id) {
        return Arrays.asList(4, id, 0);
    }

    public static Object getX2ManyWriteCommandRemoveRef(Integer id) {
        return Arrays.asList(3, id, 0); // doesn't delete referenced record
    }

}
