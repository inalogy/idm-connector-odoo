package lu.lns.connector.odoo;

import lu.lns.connector.odoo.schema.OdooField;
import lu.lns.connector.odoo.schema.OdooModel;
import lu.lns.connector.odoo.schema.type.OdooManyToOneType;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.SortKey;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AndFilter;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.CompositeFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.NotFilter;
import org.identityconnectors.framework.common.objects.filter.OrFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static lu.lns.connector.odoo.OdooConstants.*;

/**
 * Performs a search query against odoo XML-RPC API.
 */
public class OdooSearch {

    private static Map<Class<? extends AttributeFilter>, String> attributeFilterClassToOperatorMap = Map.of(
            EqualsFilter.class, OPERATOR_EQUALS,
            GreaterThanFilter.class, OPERATOR_GREATER,
            GreaterThanOrEqualFilter.class, OPERATOR_GREATER_EQUALS,
            LessThanFilter.class, OPERATOR_SMALLER,
            LessThanOrEqualFilter.class, OPERATOR_SMALLER_EQUALS
    );

    private static Map<Class<? extends CompositeFilter>, String> compositeFilterClassToOperatorMap = Map.of(
            AndFilter.class, OPERATOR_AND,
            OrFilter.class, OPERATOR_OR
    );

    private OdooClient client;
    private OdooModelCache cache;

    public OdooSearch(OdooClient client, OdooModelCache cache) {
        this.client = client;
        this.cache = cache;
    }

    /**
     * Performs a search in odoo on records of the given model.
     * Parameters as in {@link org.identityconnectors.framework.spi.operations.SearchOp}.
     */
    public void search(OdooModel model, Filter query, ResultsHandler handler, OperationOptions options) {
        // prepare
        Map<String, Object> params = prepareQueryParameters(model, options);
        List<Object> filter = query == null ? emptyList() : singletonList(translateFilter(model, query));

        boolean attributesToGetContainExpandedRelation = Arrays.stream(
                Objects.requireNonNullElse(options.getAttributesToGet(), new String[0]))
                .anyMatch(a -> a.contains(Constants.MODEL_FIELD_SEPARATOR));

        // execute search in odoo
        Object[] results = (Object[]) client.executeXmlRpc(model.getName(), OPERATION_SEARCH_READ, filter, params);

        // inspect result
        for (Object resultObj : results) {
            Map<String, Object> result = (Map<String, Object>) resultObj;

            ConnectorObjectBuilder connObj = new ConnectorObjectBuilder();
            String id = Integer.toString((int) result.get(MODEL_FIELD_FIELD_NAME_ID));
            connObj.setUid(id);
            connObj.setName(id);
            connObj.setObjectClass(new ObjectClass(model.getName()));

            for (var entry : result.entrySet()) {
                mapResultField(model, "", entry, connObj);
            }

            if (attributesToGetContainExpandedRelation) {
                queryExpandedRelations(model, options, result, connObj);
            }

            handler.handle(connObj.build());
        }
    }

    private void mapResultField(OdooModel model, String relation, Map.Entry<String, Object> field, ConnectorObjectBuilder connObj) {
        if (!field.getKey().equals(MODEL_FIELD_FIELD_NAME_ID) && model.hasField(field.getKey())) {
            OdooField modelField = model.getField(field.getKey());
            Object mapped = modelField.getType().mapToConnIdValue(field.getValue(), modelField);

            AttributeBuilder attr = new AttributeBuilder();
            attr.setName(relation + (!relation.isEmpty() ? Constants.MODEL_FIELD_SEPARATOR : "") + field.getKey());

            if (mapped instanceof Collection) { // multi-valued attribute
                attr.addValue((Collection<?>) mapped);
            }
            else if (mapped != null) {
                attr.addValue(mapped);
            }

            connObj.addAttribute(attr.build());
        }
    }

    private void queryExpandedRelations(OdooModel model, OperationOptions options, Map<String, Object> record,
            ConnectorObjectBuilder connObj) {
        // first separate the attributes according their relation
        Map<String, List<String>> relationToRetrievalAttributesMap = new HashMap<>();

        for (String attributeToGet : Objects.requireNonNullElse(options.getAttributesToGet(), new String[0])) {
            if (attributeToGet.contains(Constants.MODEL_FIELD_SEPARATOR)) {
                String[] path = attributeToGet.split(Pattern.quote(Constants.MODEL_FIELD_SEPARATOR));
                if (path.length > 2) {
                    throw new ConnectorException("Attribute '" + attributeToGet
                            + "' to be retrieved has more than one level of related record");
                }

                relationToRetrievalAttributesMap.computeIfAbsent(path[0], k -> new LinkedList<>()).add(path[1]);
            }
        }

        // check proper use of expanded relations
        for (String relation : relationToRetrievalAttributesMap.keySet()) {
            // is the relation really for a relational field?
            OdooField field = model.getField(relation);
            if (!(field.getType() instanceof OdooManyToOneType)) {
                throw new ConnectorException("Expanded relation attribute '" + relation + "' is not many2one type.");
            }
        }

        // for each related record do an extra look up
        for (var entry : relationToRetrievalAttributesMap.entrySet()) {
            OdooField field = model.getField(entry.getKey());
            OdooModel relatedModel = cache.getModel(((OdooManyToOneType) field.getType()).getRelatedModel());
            String relatedId = (String) field.getType().mapToConnIdValue(record.get(entry.getKey()), field);

            if (relatedId != null) {
                Map<String, Object> params = Map.of(OPERATION_PARAMETER_FIELDS, entry.getValue());
                List<Object> filter = Collections.singletonList(Collections.singletonList(Arrays.asList(
                        MODEL_FIELD_FIELD_NAME_ID, OPERATOR_EQUALS, relatedId)));

                Object[] results = (Object[]) client.executeXmlRpc(relatedModel.getName(), OPERATION_SEARCH_READ, filter, params);
                if (results == null || results.length != 1) {
                    throw new ConnectorException("Retrieving related record (by " + entry.getKey() + ") did not return one record");
                }

                Map<String, Object> relatedRecord = (Map<String, Object>) results[0];
                for (var relatedField : relatedRecord.entrySet()) {
                    mapResultField(relatedModel, entry.getKey(), relatedField, connObj);
                }
            }
            else {
                // retrieve all related attributes as null
                entry.getValue().forEach(connObj::addAttribute);
            }
        }
    }

    private List<Object> translateFilter(OdooModel model, Filter query) {
        if (query instanceof AttributeFilter) {
            AttributeFilter af = (AttributeFilter) query;

            if (af.getAttribute().getName().contains(Constants.MODEL_FIELD_SEPARATOR)) {
                throw new ConnectorException("Filtering by expanded relation attributes is unsupported: attribute="
                        + af.getAttribute().getName() + ", model=" + model.getName());
            }

            OdooField field = model.getField(mapSpecialAttributeNameToOdooField(af.getAttribute().getName()));
            if (field == null) {
                throw new ConnectorException("Did not find odoo field with name '" + af.getAttribute().getName() + "' in odoo model '"
                        + model.getName() + "'");
            }

            Object value;
            if (af.getAttribute().getValue() == null || af.getAttribute().getValue().isEmpty()) {
                value = null;
            }
            else if (af.getAttribute().getValue().size() > 1) {
                throw new UnsupportedOperationException("Multiple attribute values not supported for AttributeFilter");
            }
            else {
                value = field.getType().mapToOdooSearchFilterValue(af.getAttribute().getValue().iterator().next());
            }

            // as documented in odoo API: use tuple [field name, operator, value]

            if (query instanceof StartsWithFilter) {
                return singletonList(asList(
                        field.getName(),
                        OPERATOR_LIKE2,
                        escapeForLikeOperator(value) + OPERATOR_LIKE_ANY_STRING));
            }
            else if (query instanceof EndsWithFilter) {
                return singletonList(asList(
                        field.getName(),
                        OPERATOR_LIKE2,
                        OPERATOR_LIKE_ANY_STRING + escapeForLikeOperator(value)));
            }
            else if (query instanceof ContainsFilter) {
                return singletonList(asList(
                        field.getName(),
                        OPERATOR_LIKE,
                        escapeForLikeOperator(value)));
            }

            String operator = attributeFilterClassToOperatorMap.get(query.getClass());
            if (operator != null) {
                return singletonList(asList(
                        field.getName(),
                        operator,
                        value));
            }
        }
        else if (query instanceof NotFilter) {
            NotFilter not = (NotFilter) query;
            List<Object> result = new LinkedList<>();
            result.add(OPERATOR_NOT);
            result.addAll(translateFilter(model, not.getFilter()));
            return result;
        }
        else if (query instanceof CompositeFilter) {
            String operator = compositeFilterClassToOperatorMap.get(query.getClass());
            if (operator != null) {
                CompositeFilter cf = (CompositeFilter) query;
                List<Object> result = new LinkedList<>();
                result.add(operator);
                result.addAll(translateFilter(model, cf.getLeft()));
                result.addAll(translateFilter(model, cf.getRight()));
                return result;
            }
        }

        throw new UnsupportedOperationException("Filter of type " + query.getClass().getName() + " is not supported for search.");
    }

    private String escapeForLikeOperator(Object value) {
        return value.toString()
                .replace(OPERATOR_LIKE_ESCAPE_CHAR, OPERATOR_LIKE_ESCAPE_CHAR + OPERATOR_LIKE_ESCAPE_CHAR)
                .replace("%", OPERATOR_LIKE_ESCAPE_CHAR + "%")
                .replace("_", OPERATOR_LIKE_ESCAPE_CHAR + "_");
    }

    private Map<String, Object> prepareQueryParameters(OdooModel model, OperationOptions options) {
        // paging
        Integer offset = options.getPagedResultsOffset();
        Integer limit = options.getPageSize();

        Map<String, Object> params = new HashMap<>();
        if (limit != null && limit > 0) {
            params.put(OPERATION_PARAMETER_LIMIT, limit);
            params.put(OPERATION_PARAMETER_OFFSET, offset != null && offset >= 0 ? offset - 1 : 0); // offset is not zero-based in connId
        }

        // sorting
        if (options.getSortKeys() != null) {
            Iterable<SortKey> effectiveSortKeys = Utils.distinctBy(
                    Arrays.stream(options.getSortKeys())
                            .map(sk -> {
                                if (sk.getField().contains(Constants.MODEL_FIELD_SEPARATOR)) {
                                    throw new ConnectorException("Sort key for expanded relation attribute is unsupported: sortKey="
                                            + sk.getField() + ", model=" + model.getName());
                                }
                                return new SortKey(mapSpecialAttributeNameToOdooField(sk.getField()), sk.isAscendingOrder());
                            }),
                    SortKey::getField)::iterator;

            StringBuilder sortParam = new StringBuilder();
            for (SortKey sort : effectiveSortKeys) {
                if (sortParam.length() > 0) {
                    sortParam.append(", ");
                }
                sortParam.append(sort.getField());
                if (!sort.isAscendingOrder()) {
                    sortParam.append(" desc");
                }
            }
            if (sortParam.length() > 0) {
                params.put(OPERATION_PARAMETER_ORDER, sortParam.toString());
            }
        }

        // partial retrieval of attributes
        String[] retrieve = Objects.requireNonNullElse(options.getAttributesToGet(), new String[0]);
        List<String> effectiveRetrieve = Arrays.stream(retrieve)
                .filter(attr -> !attr.equals(Name.NAME))
                .filter(attr -> !attr.equals(Uid.NAME))
                .filter(attr -> !attr.equals(MODEL_FIELD_FIELD_NAME_ID))
                .map(attr -> {
                    // expanded relations: return the relation ID and do additional queries afterwards
                    if (attr.contains(Constants.MODEL_FIELD_SEPARATOR)) {
                        String[] path = attr.split(Pattern.quote(Constants.MODEL_FIELD_SEPARATOR));
                        return path[0];
                    }
                    return attr;
                })
                .distinct()
                .collect(Collectors.toList());
        params.put(OPERATION_PARAMETER_FIELDS, effectiveRetrieve); // id will always be returned

        return params;
    }

    private String mapSpecialAttributeNameToOdooField(String attributeNameFromConnId) {
        if (attributeNameFromConnId.equals(Name.NAME) || attributeNameFromConnId.equals(Uid.NAME)) {
            return MODEL_FIELD_FIELD_NAME_ID;
        }
        return attributeNameFromConnId;
    }

}
