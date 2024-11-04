package lu.lns.connector.odoo.schema.type;


public class ForeignKey {

    private static final String SUFFIX_ID = "_id";
    private static final String SUFFIX_NAME = "_name";

    private final String id;
    private final String name;

    public ForeignKey(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public Integer getIdAsInteger() {
        return id == null ? null : Integer.valueOf(id);
    }

    public String getName() {
        return name;
    }

    public static String createPrimaryAttributeName(String name) {
        if (name.endsWith(SUFFIX_NAME)) {
            return name.substring(0, name.length() - SUFFIX_NAME.length()) + SUFFIX_ID;
        } else {
            return null;
        }
    }

    public static String createSecondaryAttributeName(String name) {
        if (name.endsWith(SUFFIX_ID)) {
            return name.substring(0, name.length() - SUFFIX_ID.length()) + SUFFIX_NAME;
        } else {
            return null;
        }
    }
}
