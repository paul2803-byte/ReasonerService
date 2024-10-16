package org.soya.consent;

import jakarta.json.JsonObject;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Model;

public record Consent(String baseURL, OntModel baseModel, JsonObject d2aJson, JsonObject d3aJson, Model d2aModel,
                      Model d3aModel) {
    public final static String CONSENT = "http://example.org/id/consent";
    public static final String HANDLING = "http://example.org/id/handling";
}
