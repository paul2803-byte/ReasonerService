package org.soya.consent;

import java.util.*;

import jakarta.json.*;

import org.apache.jena.ontology.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;

public class FlexibleConsentHandler {

    public final static String CONSENT = "http://example.org/id/consent";
    public static final String HANDLING = "http://example.org/id/handling";
    private final String baseURL;
    private final OntModel baseModel;
    private final List<ObjectProperty> objectProperties;
    private final List<DatatypeProperty> dataProperties;
    private final JsonObject d2aJson;
    private final JsonObject d3aJson;
    private final Model d2aModel;
    private final Model d3aModel;

    public FlexibleConsentHandler(JsonObject d2aJson, JsonObject d3aJson, OntModel ontology, String baseURL) throws UnregisteredTermException {

        // TODO: split class in data class and factory
        this.baseModel = ontology;
        this.objectProperties = this.baseModel.listObjectProperties().toList();
        this.dataProperties = this.baseModel.listDatatypeProperties().toList();
        this.baseURL = baseURL;
        this.d2aJson = d2aJson;
        this.d3aJson = d3aJson;
        this.d2aModel = getConsent(this.baseModel.createResource(CONSENT), d2aJson);
        this.d3aModel = getConsent(this.baseModel.createResource(HANDLING), d3aJson);
    }

    private Model getConsent(Resource consent, JsonObject object)
            throws UnregisteredTermException {

        Model consentModel = ModelFactory.createDefaultModel();
        consentModel.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#");
        Resource equivClass = consentModel.createResource();

        RDFList policyList = null;
        for (ObjectProperty cp : objectProperties) {
            JsonValue value = object.get(cp.getLocalName());
            Resource bnode = setObjectRestriction(consentModel, value.asJsonArray(), cp);
            if(policyList==null) {
                policyList = consentModel.createList().with(bnode);
            } else {
                policyList.add(bnode);
            }
        }
        for (DatatypeProperty dp : dataProperties) {
            JsonValue value = object.get(dp.getLocalName());
            Resource bnode = setDataRestriction(consentModel, value, dp);
            if(policyList==null) {
                policyList = consentModel.createList().with(bnode);
            } else {
                policyList.add(bnode);
            }
        }
        equivClass.addProperty(OWL.intersectionOf, policyList);

        consentModel.add(consent, RDF.type, OWL.Class);
        consentModel.add(consent, OWL.equivalentClass, equivClass);

        return consentModel;
    }

    private Resource setObjectRestriction(Model consentModel, JsonArray input, ObjectProperty property)
            throws UnregisteredTermException {

        Resource value = getRestrictionValueOfList(consentModel, input, property.getRange().asClass());
        return consentModel.createResource()
                .addProperty(RDF.type, OWL.Restriction)
                .addProperty(OWL.onProperty, property)
                .addProperty(OWL.someValuesFrom, value);
    }

    private Resource getRestrictionValueOfList(Model consentModel, JsonArray inputs, Resource defaultValue) throws UnregisteredTermException {
        Resource restrictionValue;

        List<String> values = new ArrayList<>();
        inputs.forEach(input -> {
            String strInput = input.toString().replaceAll("\"", "");
            values.add(strInput);
        } );
        restrictionValue = consentModel.createResource();
        RDFList unionOf = null;
        for (int i = 0; i < values.size(); i++) {
            String valueI = values.get(i);
            Resource value = checkExistingResource(valueI, defaultValue);
            if (unionOf == null) {
                unionOf = consentModel.createList().with(value);
            } else {
                unionOf.add(value);
            }
        }
        restrictionValue.addProperty(OWL.unionOf, unionOf);

        return restrictionValue;
    }

    private Resource checkExistingResource(String input, Resource defaultValue) throws UnregisteredTermException {
        Resource restrictionValue;

        if (input.isEmpty()) {
            restrictionValue = defaultValue; // empty means default!
        } else {
            Resource res = baseModel.createResource(this.baseURL + input);
            if (baseModel.containsResource(res)) {
                restrictionValue = res;
            } else {
                throw new UnregisteredTermException("'" + input + "' is not registered as terms of consent");
            }
        }

        return restrictionValue;
    }

    private Resource setDataRestriction(Model consentModel, JsonValue value, DatatypeProperty dp){

        if(value.getValueType() == JsonValue.ValueType.NUMBER) {
            JsonNumber jsonNum = (JsonNumber)value;
            int intVal = jsonNum.intValue();

            return consentModel.createResource()
                    .addProperty(RDF.type, OWL.Restriction)
                    .addProperty(OWL.onProperty, dp)
                    .addProperty(OWL.someValuesFrom, getNumericRestriction(consentModel, intVal));
        } else {
            throw new IllegalArgumentException(String.format(
                    "The value for %s must be numeric.", dp.getLocalName()));
        }
    }

    private Resource getNumericRestriction(Model consentModel, int value) {
        Resource expiration = consentModel.createResource().addProperty(
                consentModel.createProperty("http://www.w3.org/2001/XMLSchema#maxInclusive"),
                consentModel.createTypedLiteral(value));
        Resource start = consentModel.createResource().addProperty(
                consentModel.createProperty("http://www.w3.org/2001/XMLSchema#minInclusive"),
                consentModel.createTypedLiteral(0));
        RDFList restrictionsList = consentModel.createList(expiration, start);
        return consentModel.createResource()
                .addProperty(consentModel.createProperty(OWL.getURI()+"onDatatype"), XSD.integer)
                .addProperty(consentModel.createProperty(OWL.getURI()+"withRestrictions"), restrictionsList);
    }

    public JsonObject getD2aJson() {
        return d2aJson;
    }

    public JsonObject getD3aJson() {
        return d3aJson;
    }

    public Model getD2aModel() {
        return d2aModel;
    }

    public Model getD3aModel() {
        return d3aModel;
    }

    public String getBaseURL() {
        return baseURL;
    }

    public OntModel getBaseModel() {
        return baseModel;
    }

    public List<ObjectProperty> getObjectProperties() {
        return objectProperties;
    }

    public List<DatatypeProperty> getDataProperties() {
        return dataProperties;
    }
}
