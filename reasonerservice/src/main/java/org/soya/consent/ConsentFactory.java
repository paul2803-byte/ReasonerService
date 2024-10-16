package org.soya.consent;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.apache.jena.ontology.DatatypeProperty;
import org.apache.jena.ontology.ObjectProperty;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;

import java.util.ArrayList;
import java.util.List;

public class ConsentFactory {

    public Consent createConsent(JsonObject d2aJson, JsonObject d3aJson, OntModel baseModel, String baseURL)
            throws UnregisteredTermException {
        return new Consent(
                baseURL,
                baseModel,
                d2aJson,
                d3aJson,
                getConsent(Consent.CONSENT, d2aJson, baseModel, baseURL),
                getConsent(Consent.HANDLING, d3aJson, baseModel, baseURL));
    }

    private Model getConsent(String consentString, JsonObject object, OntModel baseModel, String baseURL)
            throws UnregisteredTermException {

        Model consentModel = ModelFactory.createDefaultModel();
        consentModel.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#");
        Resource equivClass = consentModel.createResource();

        RDFList policyList = null;
        for (ObjectProperty cp : baseModel.listObjectProperties().toList()) {
            JsonValue value = object.get(cp.getLocalName());
            Resource bnode = setObjectRestriction(consentModel, value.asJsonArray(), cp, baseModel, baseURL);
            if(policyList==null) {
                policyList = consentModel.createList().with(bnode);
            } else {
                policyList.add(bnode);
            }
        }
        for (DatatypeProperty dp : baseModel.listDatatypeProperties().toList()) {
            JsonValue value = object.get(dp.getLocalName());
            Resource bnode = setDataRestriction(consentModel, value, dp);
            if(policyList==null) {
                policyList = consentModel.createList().with(bnode);
            } else {
                policyList.add(bnode);
            }
        }
        equivClass.addProperty(OWL.intersectionOf, policyList);
        Resource consent = baseModel.createResource(consentString);
        consentModel.add(consent, RDF.type, OWL.Class);
        consentModel.add(consent, OWL.equivalentClass, equivClass);

        return consentModel;
    }

    private Resource setObjectRestriction(Model consentModel, JsonArray input, ObjectProperty property, OntModel baseModel, String baseURL)
            throws UnregisteredTermException {

        Resource value = getRestrictionValueOfList(consentModel, input, property.getRange().asClass(), baseModel, baseURL);
        return consentModel.createResource()
                .addProperty(RDF.type, OWL.Restriction)
                .addProperty(OWL.onProperty, property)
                .addProperty(OWL.someValuesFrom, value);
    }

    private Resource getRestrictionValueOfList(Model consentModel, JsonArray inputs, Resource defaultValue, OntModel baseModel, String baseURL)
            throws UnregisteredTermException {
        Resource restrictionValue;

        List<String> values = new ArrayList<>();
        inputs.forEach(input -> {
            String strInput = input.toString().replaceAll("\"", "");
            values.add(strInput);
        } );
        restrictionValue = consentModel.createResource();
        RDFList unionOf = null;
        for (String valueString : values) {
            Resource value = checkExistingResource(valueString, defaultValue, baseModel, baseURL);
            if (unionOf == null) {
                unionOf = consentModel.createList().with(value);
            } else {
                unionOf.add(value);
            }
        }
        restrictionValue.addProperty(OWL.unionOf, unionOf);

        return restrictionValue;
    }

    private Resource checkExistingResource(String input, Resource defaultValue, OntModel baseModel, String baseURL)
            throws UnregisteredTermException {

        Resource restrictionValue;
        if (input.isEmpty()) {
            restrictionValue = defaultValue; // empty means default!
        } else {
            Resource res = baseModel.createResource(baseURL + input);
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
}