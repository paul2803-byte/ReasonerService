package org.soya.consent;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;

import eu.ownyourdata.reasonerservice.ReasonerServiceController;
import jakarta.json.*;
import org.apache.jena.ontology.ObjectProperty;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.*;

// TODO: clean class
public class FlexibleConsentHandler {

    public final static String CONSENT = "http://example.org/id/consent";
    public final static String NS = "https://soya.ownyourdata.eu/SimpleConsent/";
    public static final String DPV_NS = "http://www.w3.org/ns/dpv#";
    public static final String HANDLING = "http://example.org/id/handling";
    private final OntModel baseModel;
    private final Map<ObjectProperty, OntClass> consentProperties;
    private final JsonObject d2aJson;
    private final JsonObject d3aJson;
    private final Model d2aModel;
    private final Model d3aModel;

    public FlexibleConsentHandler(JsonObject d2aJson, JsonObject d3aJson) throws UnregisteredTermException {

        Model model = ModelFactory.createDefaultModel();
        InputStream baseFileIS = FlexibleConsentHandler.class.getClassLoader().getResourceAsStream(Matching.BASE_FILE);
        RDFDataMgr.read(model, baseFileIS, Lang.TURTLE);

        consentProperties = new HashMap<>();
        baseModel = ModelFactory.createOntologyModel();
        baseModel.add(model);

        baseModel.listObjectProperties().forEach(property -> {
            OntClass range = property.getRange().asClass();
            consentProperties.put(property, range);
        });

        this.d2aJson = d2aJson;
        this.d3aJson = d3aJson;

        Resource d2aConsentResource = model.createResource(CONSENT);
        Resource d3aConsentResource = model.createResource(HANDLING);

        d2aModel = getConsent(d2aConsentResource, d2aJson);
        d3aModel = getConsent(d3aConsentResource, d3aJson);
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
            Resource resDPV;
            if (input.startsWith("dpv:")) {
                String inputValue = input.split(":")[1];
                resDPV = baseModel.createResource(DPV_NS + inputValue);
            } else {
                resDPV = baseModel.createResource(NS + input);
            }
            if (baseModel.containsResource(resDPV)) {
                restrictionValue = resDPV;
            } else {
                throw new UnregisteredTermException("'" + input + "' is not registered as terms of consent");
            }
        }

        return restrictionValue;
    }

    private static String modelToString(Model model) {
        StringWriter writer = new StringWriter();
        model.write(writer, Lang.TURTLE.getName());
        return writer.toString();
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
}
