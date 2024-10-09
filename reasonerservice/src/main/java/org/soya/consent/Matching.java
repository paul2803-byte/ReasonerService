package org.soya.consent;

import java.io.InputStream;

import com.clarkparsia.owlapi.explanation.BlackBoxExplanation;
import org.apache.commons.io.IOUtils;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Matching {

    public final static String BASE_FILE = "simple_consent.ttl";

    private static final Logger log = LoggerFactory.getLogger(Matching.class);

    public static boolean matchingString(String d2a, String d3a, String d2aURI, String d3aURI) {

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        try {
            InputStream libraryIS = Matching.class.getClassLoader().getResourceAsStream(BASE_FILE);
            OWLOntology ontology = manager.loadOntologyFromOntologyDocument(libraryIS);

            InputStream consentIS = IOUtils.toInputStream(d2a, "UTF-8");
            ontology.addAxioms(manager.loadOntologyFromOntologyDocument(consentIS).axioms());
            consentIS.close();

            InputStream handlingIS = IOUtils.toInputStream(d3a, "UTF-8");
            ontology.addAxioms(manager.loadOntologyFromOntologyDocument(handlingIS).axioms());
            handlingIS.close();

            OWLClass dataControllerCls = df.getOWLClass(IRI.create(d3aURI));
            OWLClass dataSubjectCls = df.getOWLClass(IRI.create(d2aURI));

            OWLReasonerFactory rf = new ReasonerFactory();
            OWLReasoner r = rf.createReasoner(ontology);
            OWLAxiom axiom = df.getOWLSubClassOfAxiom(dataControllerCls, dataSubjectCls);

            NodeSet subclasses = r.getSubClasses(dataControllerCls, true);
            // r.getSubClasses()

            // try explanation
            BlackBoxExplanation explanation = new BlackBoxExplanation(ontology, rf, r);
            // the reasoner checks if dataController is a superclass of dataSubjects
            return r.isEntailed(axiom);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return false;
    }
}

// TODO: try to get the reasoning with explanation running
/*
public static void main(String[] args) {
        try {
            // Load the ontology
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File("path/to/ontology.owl"));

            // Initialize a reasoner
            OWLReasonerFactory reasonerFactory = new ReasonerFactory();
            OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);

            // Create an Explanation Generator
            ExplanationGeneratorFactory<OWLAxiom> explanationGeneratorFactory =
                new DefaultExplanationGenerator.Factory(reasonerFactory, manager);
            ExplanationGenerator<OWLAxiom> explanationGenerator =
                explanationGeneratorFactory.createExplanationGenerator(ontology);

            // Define the class and axiom for which you want an explanation
            OWLClass owlClass = manager.getOWLDataFactory().getOWLClass(IRI.create("http://example.org#SomeClass"));
            OWLAxiom entailment = manager.getOWLDataFactory().getOWLSubClassOfAxiom(owlClass, manager.getOWLDataFactory().getOWLThing());

            // Generate explanations
            Set<Explanation<OWLAxiom>> explanations = explanationGenerator.getExplanations(entailment);
            for (Explanation<OWLAxiom> explanation : explanations) {
                System.out.println("Explanation for entailment: " + entailment);
                System.out.println("Axioms:");
                for (OWLAxiom axiom : explanation.getAxioms()) {
                    System.out.println(axiom);
                }
            }

            reasoner.dispose();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
 */