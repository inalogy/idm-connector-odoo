package com.cognitumsoftware.connector.odoo;

import com.cognitumsoftware.connector.odoo.schema.OdooField;
import com.cognitumsoftware.connector.odoo.schema.OdooModel;
import org.apache.commons.lang3.ObjectUtils;
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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.cognitumsoftware.connector.odoo.OdooConstants.*;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class OdooSearch {

    private static Map<Class<? extends AttributeFilter>, String> attributeFilterClassToOperatorMap = Map.of(
            EqualsFilter.class, OPERATOR_EQUALS,
            ContainsFilter.class, OPERATOR_LIKE,
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

    public OdooSearch(OdooClient client) {
        this.client = client;
    }

    public void search(OdooModel model, Filter query, ResultsHandler handler, OperationOptions options) {
        // prepare
        Map<String, Object> params = prepareQueryParameters(options);
        List<Object> filter = query == null ? emptyList() : singletonList(translateFilter(query));

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
                if (!entry.getKey().equals(MODEL_FIELD_FIELD_NAME_ID) && model.hasField(entry.getKey())) {
                    OdooField modelField = model.getField(entry.getKey());
                    connObj.addAttribute(entry.getKey(), modelField.getType().mapToConnIdValue(entry.getValue(), modelField));
                }
            }

            handler.handle(connObj.build());
        }
    }

    private List<Object> translateFilter(Filter query) {
        if (query instanceof AttributeFilter) {
            AttributeFilter af = (AttributeFilter) query;
            Object value;

            if (af.getAttribute().getValue() == null || af.getAttribute().getValue().isEmpty()) {
                value = null;
            }
            else if (af.getAttribute().getValue().size() > 1) {
                throw new UnsupportedOperationException("Multiple attribute values not supported for AttributeFilter");
            }
            else {
                value = af.getAttribute().getValue().iterator().next();
            }

            // as documented in odoo API: use tuple [field name, operator, value]

            if (query instanceof StartsWithFilter) {
                return singletonList(asList(
                        mapSpecialAttributeNameToOdooField(af.getAttribute().getName()),
                        OPERATOR_LIKE2,
                        // TODO: escaping undocumented in odoo API but we should escape attribute value (more occurrences see below)
                        value + OPERATOR_LIKE_ANY_STRING));
            }
            else if (query instanceof EndsWithFilter) {
                return singletonList(asList(
                        mapSpecialAttributeNameToOdooField(af.getAttribute().getName()),
                        OPERATOR_LIKE2,
                        OPERATOR_LIKE_ANY_STRING + value));
            }

            String operator = attributeFilterClassToOperatorMap.get(query.getClass());
            if (operator != null) {
                return singletonList(asList(
                        mapSpecialAttributeNameToOdooField(af.getAttribute().getName()),
                        operator,
                        value));
            }
        }
        else if (query instanceof NotFilter) {
            NotFilter not = (NotFilter) query;
            List<Object> result = new LinkedList<>();
            result.add(OPERATOR_NOT);
            result.addAll(translateFilter(not.getFilter()));
            return result;
        }
        else if (query instanceof CompositeFilter) {
            String operator = compositeFilterClassToOperatorMap.get(query.getClass());
            if (operator != null) {
                CompositeFilter cf = (CompositeFilter) query;
                List<Object> result = new LinkedList<>();
                result.add(operator);
                result.addAll(translateFilter(cf.getLeft()));
                result.addAll(translateFilter(cf.getRight()));
                return result;
            }
        }

        throw new UnsupportedOperationException("Filter of type " + query.getClass().getName() + " is not supported.");
    }

    private Map<String, Object> prepareQueryParameters(OperationOptions options) {
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
                            .map(sk -> new SortKey(mapSpecialAttributeNameToOdooField(sk.getField()), sk.isAscendingOrder())),
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
        String[] retrieve = ObjectUtils.defaultIfNull(options.getAttributesToGet(), new String[0]);
        List<String> effectiveRetrieve = Arrays.stream(retrieve)
                .filter(attr -> !attr.equals(Name.NAME))
                .filter(attr -> !attr.equals(Uid.NAME))
                .filter(attr -> !attr.equals(MODEL_FIELD_FIELD_NAME_ID))
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
