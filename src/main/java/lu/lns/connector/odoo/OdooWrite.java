package lu.lns.connector.odoo;

import lu.lns.connector.odoo.schema.OdooField;
import lu.lns.connector.odoo.schema.OdooModel;
import lu.lns.connector.odoo.schema.type.ForeignKey;
import lu.lns.connector.odoo.schema.type.MultiValueOdooType;
import lu.lns.connector.odoo.schema.type.OdooManyToOneType;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.*;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static lu.lns.connector.odoo.OdooConstants.*;

/**
 * Performs create or update operations against odoo XML-RPC API.
 */
public class OdooWrite {

    private static final Log LOG = Log.getLog(OdooConnector.class);

    private OdooClient client;
    private OdooModelCache cache;

    public OdooWrite(OdooClient client, OdooModelCache cache) {
        this.client = client;
        this.cache = cache;
    }

    /**
     * Creates a new record of the given model and sets the attributes as specified. Also handles related records to be
     * created if any, see {@link OdooConfiguration#getExpandRelations()}.
     *
     * @return Uid of new record (odoo id encapsulated as string)
     */
    public Uid createRecord(OdooModel model, Set<Attribute> createAttributes) {
        // separate related records first if any
        Set<Attribute> effectiveCreateAttributes = new HashSet<>(createAttributes);
        Map<String, Set<Attribute>> relationToRecordMap = new HashMap<>();

        for (var it = effectiveCreateAttributes.iterator(); it.hasNext(); ) {
            Attribute attr = it.next();
            if (attr.getName().contains(Constants.MODEL_FIELD_SEPARATOR)) {
                String[] path = attr.getName().split(Pattern.quote(Constants.MODEL_FIELD_SEPARATOR));
                if (path.length > 2) {
                    throw new InvalidAttributeValueException("Attribute name '" + attr.getName()
                        + "' has more than one level of related record");
                }

                Set<Attribute> relatedAttrs = relationToRecordMap.computeIfAbsent(path[0], k -> new HashSet<>());
                relatedAttrs.add(AttributeBuilder.build(path[1], attr.getValue()));

                it.remove(); // remove the related attribute from attributes for the original record to be created
            }
        }

        // check that related records are used properly
        for (String key : relationToRecordMap.keySet()) {
            // if we have a related record, the original record must not contain another relation reference for that relational field
            if (effectiveCreateAttributes.stream().anyMatch(attr -> attr.getName().equals(key))) {
                throw new InvalidAttributeValueException("Attribute '" + key + "' cannot be specified because related record fields " +
                    "are specified, too");
            }

            // is the related record really for a relational field?
            OdooField field = model.getField(key);
            if (!(field.getType() instanceof OdooManyToOneType)) {
                throw new InvalidAttributeValueException("Attribute '" + key
                    + "' has related attributes specified but is not many2one type.");
            }
        }

        // create related records first if any
        Map<String, Integer> relationToCreatedIdMap = new HashMap<>();

        try {
            for (var entry : relationToRecordMap.entrySet()) {
                OdooField field = model.getField(entry.getKey());
                OdooManyToOneType type = (OdooManyToOneType) field.getType();

                // create the related record
                Uid createdRecordUid = internalCreateRecord(cache.getModel(type.getRelatedModel()), entry.getValue());
                Integer createdRecordId = Integer.valueOf(createdRecordUid.getUidValue());
                relationToCreatedIdMap.put(entry.getKey(), createdRecordId);

                // include related record in original record as reference
                effectiveCreateAttributes.add(AttributeBuilder.build(entry.getKey(), createdRecordId));
            }

            // create the original record with relations to created (related) records
            return internalCreateRecord(model, effectiveCreateAttributes);
        } catch (Exception e) {
            // we need to rollback created (related) records
            rollbackCreatedRelatedRecords(model, relationToCreatedIdMap);

            throw e;
        }
    }

    private String extractPasswordFromGuardedString(GuardedString guardedString) {
        final List<String> passwordList = new ArrayList<>();
        guardedString.access(new GuardedString.Accessor() {
            @Override
            public void access(char[] passwordChars) {
                passwordList.add(new String(passwordChars));
            }
        });
        return passwordList.get(0);
    }

    private Uid internalCreateRecord(OdooModel model, Set<Attribute> createAttributes) {
        Map<String, Object> fields = new HashMap<>();

        for (Attribute attr : createAttributes) {
            if (attr.getName().equals(Uid.NAME) || attr.getName().equals(Name.NAME)) {
                // we ignore these attributes as they are the ID of the record to be created in odoo
                continue;
            }

            Object val;
            //Checking if password is GuardedString and if so than decrypting it
            if (attr.getName().equals("__PASSWORD__")) {
                String password;
                if (attr.getValue() != null && !attr.getValue().isEmpty()) {
                    List<Object> valueToChange = attr.getValue();
                    GuardedString guardedPassword = (GuardedString) valueToChange.get(0);
                    password = extractPasswordFromGuardedString(guardedPassword);
                    val = password;
                } else {
                    val = null;
                }

                fields.put("password", val);
                continue;
            }

            OdooField field = model.getField(attr.getName());
            if (field == null) {
                throw new ConnectorException("Did not find odoo field with name '" + attr.getName() + "' in odoo model.");
            }

            if (field.getType() instanceof MultiValueOdooType) {
                val = attr.getValue();
            } else if (attr.getValue() == null || attr.getValue().isEmpty()) {
                val = null;
            } else if (attr.getValue().size() > 1) {
                throw new InvalidAttributeValueException("Multiple attribute values not supported in create operation for " +
                    "field '" + field.getName() + "' in model '" + field.getModel().getName() + "'");
            } else {
                val = attr.getValue().iterator().next();
            }

            fields.put(field.getName(), field.getType().mapToOdooCreateRecordValue(val));
        }

        // execute create
        Integer id = (Integer) client.executeXmlRpc(model.getName(), OPERATION_CREATE, singletonList(fields));
        return new Uid(id.toString());
    }

    /**
     * Updates an existing record of the given model and sets the attributes as specified. Also handles related records to be
     * created/update if any, see {@link OdooConfiguration#getExpandRelations()}.
     */
    public Set<AttributeDelta> updateRecord(OdooModel model, Uid uid, Set<AttributeDelta> attributeDeltas) {
        Set<AttributeDelta> modifiedAttributes = new HashSet<>();
        Integer id = Integer.valueOf(uid.getUidValue());

        // separate related records first if any
        Set<AttributeDelta> effectiveAttributeDeltas = new HashSet<>(attributeDeltas);
        Map<String, Set<AttributeDelta>> relationToRecordMap = new HashMap<>();

        for (var it = effectiveAttributeDeltas.iterator(); it.hasNext(); ) {
            AttributeDelta attr = it.next();
            if (attr.getName().contains(Constants.MODEL_FIELD_SEPARATOR)) {
                String[] path = attr.getName().split(Pattern.quote(Constants.MODEL_FIELD_SEPARATOR));
                if (path.length > 2) {
                    throw new InvalidAttributeValueException("Attribute name '" + attr.getName()
                        + "' has more than one level of related record");
                }

                Set<AttributeDelta> relatedAttrs = relationToRecordMap.computeIfAbsent(path[0], k -> new HashSet<>());
                AttributeDeltaBuilder copy = new AttributeDeltaBuilder();
                copy.setName(path[1]);
                if (attr.getValuesToReplace() != null) {
                    copy.addValueToReplace(attr.getValuesToReplace());
                } else {
                    copy.addValueToAdd(attr.getValuesToAdd());
                    copy.addValueToRemove(attr.getValuesToRemove());
                }
                relatedAttrs.add(copy.build());

                it.remove(); // remove the related attribute from attributes for the original record to be created
            }
        }

        // check that related records are used properly
        for (String key : relationToRecordMap.keySet()) {
            // if we have a related record, the original record must not contain another relation reference for that relational field
            if (effectiveAttributeDeltas.stream().anyMatch(attr -> attr.getName().equals(key))) {
                throw new InvalidAttributeValueException("Attribute '" + key + "' cannot be specified because related record fields " +
                    "are specified, too");
            }

            // is the related record really for a relational field?
            OdooField field = model.getField(key);
            if (!(field.getType() instanceof OdooManyToOneType)) {
                throw new InvalidAttributeValueException("Attribute '" + key
                    + "' has related attributes specified but is not many2one type.");
            }
        }

        // create or update related records first if any
        Map<String, Integer> relationToCreatedIdMap = new HashMap<>();
        Map<String, Map<String, Object>> relationToValuesBeforeUpdateMap = new HashMap<>();

        try {
            if (!relationToRecordMap.isEmpty()) {
                // fetch the original record to see which relations are already present and therefore need to be updated instead of created
                Map<String, Object> relations = readRecord(model, id, relationToRecordMap.keySet());

                // create related records that do not already exist
                for (var entry : relationToRecordMap.entrySet()) {
                    if (((ForeignKey) relations.get(entry.getKey())).getIdAsInteger() != null) {
                        // relation already present, do not create a new related record
                        continue;
                    }

                    OdooField field = model.getField(entry.getKey());
                    OdooManyToOneType type = (OdooManyToOneType) field.getType();

                    // transform attribute deltas into attribute values to create
                    Set<Attribute> relatedAttributes = new HashSet<>();
                    for (AttributeDelta attrDelta : entry.getValue()) {
                        if (attrDelta.getValuesToReplace() == null) {
                            throw new InvalidAttributeValueException("Attribute '" + entry.getKey()
                                + "' needs to be created as related record but the related attribute delta '" + attrDelta.getName()
                                + "' doesn't have valuesToReplace set; need the full attribute values set here");
                        }
                        relatedAttributes.add(AttributeBuilder.build(attrDelta.getName(), attrDelta.getValuesToReplace()));
                    }

                    // create the related record
                    Uid createdRecordUid = internalCreateRecord(cache.getModel(type.getRelatedModel()), relatedAttributes);
                    Integer createdRecordId = Integer.valueOf(createdRecordUid.getUidValue());
                    relationToCreatedIdMap.put(entry.getKey(), createdRecordId);

                    // include related record in original record as reference
                    AttributeDelta mod = AttributeDeltaBuilder.build(entry.getKey(), createdRecordId);
                    effectiveAttributeDeltas.add(mod);
                    modifiedAttributes.add(mod);
                }

                // update related records that already exist
                for (var entry : relationToRecordMap.entrySet()) {
                    if (((ForeignKey) relations.get(entry.getKey())).getIdAsInteger() == null) {
                        // relation not present, it was created above
                        continue;
                    }

                    OdooField field = model.getField(entry.getKey());
                    OdooManyToOneType type = (OdooManyToOneType) field.getType();
                    OdooModel relatedModel = cache.getModel(type.getRelatedModel());
                    ForeignKey foreignKey = (ForeignKey) relations.get(entry.getKey());
                    Integer relatedId = foreignKey.getIdAsInteger();

                    // remember state of related record before the update for potential rollback on exception
                    Map<String, Object> relatedRecordBeforeUpdate = readRecord(relatedModel, relatedId,
                        entry.getValue().stream().map(AttributeDelta::getName).collect(Collectors.toSet()));

                    // update the related record
                    internalUpdateRecord(relatedModel, relatedId, entry.getValue());

                    // remember it only after the update is finished successfully (we assume that nothing was changed in Odoo
                    // when the update fails with an exception); only later exceptions should rollback this related record
                    relationToValuesBeforeUpdateMap.put(entry.getKey(), relatedRecordBeforeUpdate);
                }
            }

            // create the original record with relations to created nested records
            internalUpdateRecord(model, id, effectiveAttributeDeltas);

            return modifiedAttributes;
        } catch (Exception e) {
            // we need to rollback created (related) records
            rollbackCreatedRelatedRecords(model, relationToCreatedIdMap);

            // we need to rollback updated (related) records
            rollbackUpdatedRelatedRecords(model, relationToValuesBeforeUpdateMap);

            throw e;
        }
    }

    private void rollbackUpdatedRelatedRecords(OdooModel model, Map<String, Map<String, Object>> relationToValuesBeforeUpdateMap) {
        for (var entry : relationToValuesBeforeUpdateMap.entrySet()) {
            OdooField field = model.getField(entry.getKey());
            OdooModel relatedModel = cache.getModel(((OdooManyToOneType) field.getType()).getRelatedModel());
            Object idValue = entry.getValue().get(MODEL_FIELD_FIELD_NAME_ID);
            Integer relatedId = (Integer) idValue;
            try {
                // NOTE: We cannot directly call the Odoo write operation with the data retrieved before because relations are handled
                // differently (e.g. reading yields list of IDs but writing requires commands)
                internalUpdateRecord(relatedModel, relatedId, entry.getValue().entrySet().stream()
                    .map(originalData -> {
                        if (originalData.getValue() instanceof Collection) {
                            return AttributeDeltaBuilder.build(originalData.getKey(), (Collection<?>) originalData.getValue());
                        }
                        return AttributeDeltaBuilder.build(originalData.getKey(), originalData.getValue());
                    })
                    .collect(Collectors.toSet()));
            } catch (Exception inner) {
                // not much we can do here: the updated related record will remain as-is in odoo, needs to be reverted manually
                LOG.warn(inner, "Unable to rollback updated related record: model={0}, id={1}; needs to be reverted manually",
                    relatedModel.getName(), relatedId);
            }
        }
    }

    private void rollbackCreatedRelatedRecords(OdooModel model, Map<String, Integer> relationToCreatedIdMap) {
        for (var entry : relationToCreatedIdMap.entrySet()) {
            OdooField field = model.getField(entry.getKey());
            OdooModel relatedModel = cache.getModel(((OdooManyToOneType) field.getType()).getRelatedModel());
            try {
                client.executeXmlRpc(relatedModel.getName(), OPERATION_DELETE, singletonList(singletonList(entry.getValue())));
            } catch (Exception inner) {
                // not much we can do here: the created related record will remain in odoo probably until cleaned up
                LOG.warn(inner, "Unable to rollback created related record: model={0}, id={1}; needs to be cleaned-up manually",
                    relatedModel.getName(), entry.getValue());
            }
        }
    }

    private Map<String, Object> readRecord(OdooModel model, Integer id, Collection<String> fieldsToRetrieve) {
        Map<String, Object> params = Map.of(OPERATION_PARAMETER_FIELDS, new ArrayList<>(fieldsToRetrieve));
        Object filter = Collections.singletonList(Collections.singletonList(Arrays.asList(
            MODEL_FIELD_FIELD_NAME_ID, OPERATOR_EQUALS, id)));

        // read from API
        Object[] results = (Object[]) client.executeXmlRpc(model.getName(), OPERATION_SEARCH_READ, filter, params);
        if (results.length != 1) {
            throw new InvalidAttributeValueException("Record with ID " + id + " not found for update operation");
        }

        // map to connId values
        @SuppressWarnings("unchecked")
        Map<String, Object> record = (Map<String, Object>) results[0];

        for (var entry : record.entrySet()) {
            if (!entry.getKey().equals(MODEL_FIELD_FIELD_NAME_ID) && model.hasField(entry.getKey())) {
                OdooField modelField = model.getField(entry.getKey());
                entry.setValue(modelField.getType().mapToConnIdValue(entry.getValue(), modelField));
            }
        }
        return record;
    }

    private void internalUpdateRecord(OdooModel model, Integer id, Set<AttributeDelta> attributeDeltas) {
        Map<String, Object> fields = new HashMap<>();

        for (AttributeDelta delta : attributeDeltas) {
            final String deltaName = delta.getName();
            final List<Object> deltaReplacementValues = delta.getValuesToReplace();

            if (deltaName.equals(Uid.NAME) || deltaName.equals(Name.NAME)) {
                // we ignore these attributes as they are the ID of the record to be updated in odoo
                continue;
            }

            Object val;
            //Checking if password is GuardedString and if so than decrypting it
            if (deltaName.equals("__PASSWORD__")) {
                String password;
                if (deltaReplacementValues != null && !deltaReplacementValues.isEmpty()) {
                    GuardedString guardedPassword = (GuardedString) deltaReplacementValues.get(0);
                    password = extractPasswordFromGuardedString(guardedPassword);
                    val = password;
                } else {
                    val = null;
                }

                fields.put("password", val);
                continue;
            }

            OdooField field = model.getField(deltaName);

            if (field == null) {
                String fkIdProperty = ForeignKey.createPrimaryAttributeName(deltaName);
                if (fkIdProperty != null) {
                    field = model.getField(fkIdProperty);
                    if (field != null && field.getType() instanceof OdooManyToOneType) {
                        OdooManyToOneType odooType = (OdooManyToOneType) field.getType();
                        Integer fkIdValue = null;

                        if (deltaReplacementValues.get(0) != null) {
                            String relatedOdooModel = odooType.getRelatedModel();
                            String nameValue = String.valueOf(deltaReplacementValues.get(0));

                            fkIdValue = findObjectIdByName(relatedOdooModel, nameValue);

                            if (fkIdValue == null) {
                                throw new ConnectorException("No matching record found for " + deltaName
                                    + " with name " + nameValue + " in model " + relatedOdooModel);
                            }
                        }

                        fields.put(fkIdProperty, fkIdValue);
                        continue;
                    }
                }
            }

            if (field == null) {
                throw new ConnectorException("Did not find odoo field with name '" + deltaName + "' in odoo model.");
            }

            if (deltaReplacementValues != null) {
                if (field.getType() instanceof MultiValueOdooType) {
                    // multi-value mapped as a whole
                    val = deltaReplacementValues;
                } else if (deltaReplacementValues.isEmpty()) {
                    val = null;
                } else if (deltaReplacementValues.size() > 1) {
                    throw new InvalidAttributeValueException("Multiple attribute values not supported in update operation for " +
                        "field '" + field.getName() + "' in model '" + field.getModel().getName() + "'");
                } else {
                    val = deltaReplacementValues.iterator().next();
                }

                fields.put(field.getName(), field.getType().mapToOdooUpdateRecordValue(val));
            } else if (field.getType() instanceof MultiValueOdooType) {
                MultiValueOdooType mv = (MultiValueOdooType) field.getType();
                val = mv.mapToOdooUpdateRecordDeltaValue(delta.getValuesToAdd(), delta.getValuesToRemove());

                fields.put(field.getName(), val);
            } else {
                throw new InvalidAttributeValueException("Delta add/remove not supported for field '" + field.getName()
                    + "' in model '" + field.getModel().getName() + "'");
            }
        }

        // null values are supported only if we set xmlRpcCfg.setEnabledForExtensions(true). Sadly this breaks
        // compatibility with older Python 2 based Odoo releases. We can simply work around this by wrapping
        // this with False which provides exactly the same results on server side.
        for (var entry: fields.entrySet()) {
            if ( entry.getValue() == null ) {
                entry.setValue(Boolean.FALSE);
            }
        }

        // execute update
        client.executeXmlRpc(model.getName(), OPERATION_UPDATE, asList(singletonList(id), fields));
    }

    @SuppressWarnings("unchecked")
    private Integer findObjectIdByName(String odooModel, String nameValue) {
        List<Object> filter = singletonList(asList(MODEL_FIELD_FIELD_NAME, OPERATOR_EQUALS, nameValue));
        Map<String, Object> params = new HashMap<>();
        params.put("fields", asList(MODEL_FIELD_FIELD_NAME_ID, MODEL_FIELD_FIELD_NAME));
        Object[] results = (Object[]) client.executeXmlRpc(odooModel, OPERATION_SEARCH_READ, singletonList(filter), params);

        if (results.length != 1) {
            return null;
        }

        Map<String, Object> record = (Map<String, Object>) results[0];

        return (Integer) record.get(MODEL_FIELD_FIELD_NAME_ID);
    }

}
