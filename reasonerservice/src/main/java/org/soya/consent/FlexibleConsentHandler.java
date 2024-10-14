package org.soya.consent;

import java.util.*;

import jakarta.json.*;
import org.apache.jena.ontology.ObjectProperty;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;

public class FlexibleConsentHandler {

    public final static String CONSENT = "http://example.org/id/consent";
    public static final String HANDLING = "http://example.org/id/handling";
    private final String baseURL;
    private final OntModel baseModel;
    private final Map<ObjectProperty, OntClass> consentProperties;
    private final JsonObject d2aJson;
    private final JsonObject d3aJson;
    private final Model d2aModel;
    private final Model d3aModel;

    public FlexibleConsentHandler(JsonObject d2aJson, JsonObject d3aJson, OntModel ontology, String baseURL) throws UnregisteredTermException {

        this.baseModel = ontology;

        this.consentProperties = new HashMap<>();
        this.baseModel.listObjectProperties().forEach(property -> {
            OntClass range = property.getRange().asClass();
            this.consentProperties.put(property, range);
        });

        this.baseURL = baseURL;
        this.d2aJson = d2aJson;
        this.d3aJson = d3aJson;
        this.d2aModel = getConsent(this.baseModel.createResource(CONSENT), d2aJson);
        this.d3aModel = getConsent(this.baseModel.createResource(HANDLING), d3aJson);
    }

    public Model getConsent(Resource consent, JsonObject object)
            throws UnregisteredTermException {

        Model consentModel = ModelFactory.createDefaultModel();

        Resource equivClass = consentModel.createResource();

        RDFList policyList = null;
        for (Map.Entry<ObjectProperty, OntClass> consentProperty : consentProperties.entrySet()) {
            ObjectProperty op = consentProperty.getKey();
            OntClass oc = consentProperty.getValue();
            JsonValue value = object.get(op.getLocalName());
            Resource bnode = setRestriction(consentModel, value.asJsonArray(), op, oc);

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

    private Resource setRestriction(Model consentModel, JsonArray input, Property property, Resource defaultValue)
            throws UnregisteredTermException {

        Resource restriction = consentModel.createResource();
        Resource value = getRestrictionValueOfList(consentModel, input, defaultValue);

        restriction.addProperty(RDF.type, OWL.Restriction);
        restriction.addProperty(OWL.onProperty, property);
        restriction.addProperty(OWL.someValuesFrom, value);

        return restriction;
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
}
