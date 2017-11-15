package services.similarity.ontologyManagement;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.semanticweb.HermiT.Configuration;
import org.semanticweb.HermiT.Reasoner;
import org.semanticweb.elk.owlapi.ElkReasoner;
import org.semanticweb.elk.owlapi.ElkReasonerConfiguration;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owl.explanation.api.ExplanationGenerator;
import org.semanticweb.owl.explanation.api.ExplanationGeneratorFactory;
import org.semanticweb.owl.explanation.api.ExplanationManager;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.ClassExpressionType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationSubject;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLLogicalEntity;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectComplementOf;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.OWLSubPropertyChainOfAxiom;
import org.semanticweb.owlapi.model.PrefixManager;
import org.semanticweb.owlapi.reasoner.ConsoleProgressMonitor;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerConfiguration;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.SimpleConfiguration;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.DefaultPrefixManager;

import com.google.common.collect.Multimap;

import services.similarity.similarity.InformationContent;
import services.similarity.similarity.matching.AnnotationComparison;
import services.similarity.test.ComparisonResult;
import services.similarity.test.DatasetTest;

public class MyOWLOntology {
	private OWLOntologyManager manager;
	private OWLOntology o;
	private Map<String, OWLConcept> concepts;
	private Map<OWLLogicalEntity, Set<OWLClass>> ancestors;
	private Map<OWLProperty, Set<OWLProperty>> directSuperProperties;
	private Map<OWLProperty, Set<OWLProperty>> allSuperProperties;
	private Map<OWLLogicalEntity, Set<OWLIndividual>> ancestorsCat;
	private Map<String, MyOWLIndividual> individuals;
	private Map<OWLClass, Map<OWLClass, Integer>> conceptDistances;
	private Map<String, OWLRelation> relations;
	private OWLReasoner reasoner;
	private OWLDataFactory factory;
	private ExplanationGenerator<OWLAxiom> expl;
	private String prefix;
	private Map<OWLRelation, Set<List<OWLRelation>>> propertyChains;
	private int expID;
	private Map<AnnotationComparison, OWLConcept> lcas;
	private Map<AnnotationComparison, Set<OWLConcept>> disAncestors;
	private static int equivalentClassNumber = 0;
	private HashMap<OWLClass, Integer> conceptProfs = new HashMap<OWLClass,Integer>();
	private HashMap<OWLProperty, Integer> relationProfs = new HashMap<OWLProperty,Integer>();
	private HashMap<OWLIndividual, Integer> categoryProfs = new HashMap<OWLIndividual, Integer>();
	private boolean storing = true;
	private Set<String> taxonomicProperties;
	
	public MyOWLOntology(String ontFile, String pr) throws FileNotFoundException
	{
		this(ontFile, pr, "hermit");
	}

	public MyOWLOntology(InputStream ontFile, String pr, String reasonerName)
	{
		this(ontFile, pr, reasonerName, new HashSet<String>(Arrays.asList("http://purl.org/dc/terms/subject","http://www.w3.org/2004/02/skos/core#broader")));
	}

	public MyOWLOntology(InputStream ontFile, String pr, String reasonerName, Set<String> taxUris)
	{
		concepts = new HashMap<String,OWLConcept>();
		individuals = new HashMap<String, MyOWLIndividual>();
		relations = new HashMap<String,OWLRelation>();
		ancestors = new HashMap<OWLLogicalEntity, Set<OWLClass>>();
		ancestorsCat = new HashMap<OWLLogicalEntity, Set<OWLIndividual>>();
		manager = OWLManager.createOWLOntologyManager();
		factory = manager.getOWLDataFactory();
		conceptDistances = new HashMap<OWLClass, Map<OWLClass, Integer>>();
		prefix = pr;
		lcas = new HashMap<AnnotationComparison, OWLConcept>();
		disAncestors = new HashMap<AnnotationComparison, Set<OWLConcept>>();
    	expID = 0;
    	allSuperProperties = new HashMap<OWLProperty, Set<OWLProperty>>();
    	directSuperProperties = new HashMap<OWLProperty, Set<OWLProperty>>();
		
		try {
			o = manager.loadOntologyFromOntologyDocument(ontFile);
			System.out.println("GOOOOL");
			
			reasonerName = reasonerName.toLowerCase();
			if (reasonerName.toLowerCase().matches("elk"))
				startELKReasoner();
			else if (reasonerName.toLowerCase().matches("hermit"))
				startHermiTReasoner();
			else if (reasonerName.toLowerCase().equals("structural"))
				startStructuralReasoner();
            System.out.println("Reasoner ready");			
			
            Set<OWLObjectProperty> objectProperties = o.getObjectPropertiesInSignature();
			objectProperties.remove(factory.getOWLTopObjectProperty());
			for (Iterator<OWLObjectProperty> i = objectProperties.iterator(); i.hasNext();)
			{
				OWLObjectProperty current = i.next();
				relations.put(current.toStringID(), new OWLRelation(current, this));
			}
			System.out.println("Relations read");

			Set<OWLClass> classes = o.getClassesInSignature();
			//Fix: when there are not custom classes rather than both RDF + OWL standard classes the set is empty and does not allow to add other elements
			if (classes.size() == 0) {
				System.out.println("Classes In Signature are empty, loading an empty set");
				classes = new HashSet<OWLClass>();
			}

			classes.add(factory.getOWLThing());
			for (Iterator<OWLClass> i = classes.iterator(); i.hasNext();)
			{
				OWLClass current = i.next();
				concepts.put(current.toStringID(), new OWLConcept(current, this));
			}
			System.out.println("Classes read");
			classes = null; //Finished with classes
			

            Set<OWLNamedIndividual> indivs = o.getIndividualsInSignature();
            for (Iterator<OWLNamedIndividual> i = indivs.iterator(); i.hasNext();)
            {
            	OWLNamedIndividual current = i.next();
            	if (concepts.get(current.toStringID()) == null)
            		individuals.put(current.toStringID(), new MyOWLIndividual(current, this));
            }
            
            /*roots = new HashSet<OWLConcept>();
            roots.add(concepts.get(factory.getOWLThing().toStringID()));
            roots.add(concepts.get(this.getOntologyPrefix() + "GO_0008150"));
            roots.add(concepts.get(this.getOntologyPrefix() + "GO_0005575"));
            roots.add(concepts.get(this.getOntologyPrefix() + "GO_0003674"));*/
		} catch (OWLOntologyCreationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		propertyChains = this.getPropertyChains();
		
		taxonomicProperties = new HashSet<String>();
		taxonomicProperties.addAll(taxUris);
	}

	public MyOWLOntology(String ontFile, String pr, String reasonerName) throws FileNotFoundException
	{
		this(new FileInputStream(ontFile), pr, reasonerName, new HashSet<String>(Arrays.asList("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")));
	}
	
	public OWLOntology getOWLOntology()
	{
		return o;
	}
	
	public String getOntologyPrefix()
	{
		return prefix;
	}
	
	public void addIndividuals(Set<String> uris)
	{
		for (String s: uris)
		{
			if (individuals.get(s) == null)
			{
				OWLNamedIndividual individual = factory.getOWLNamedIndividual(IRI.create(s));
				OWLClass classExpression = factory.getOWLThing();
				OWLClassAssertionAxiom ax = factory.getOWLClassAssertionAxiom(classExpression, individual);
				manager.addAxiom(o, ax);
				individuals.put(s, new MyOWLIndividual(individual, this));
			}
		}
	}
	
	public Set<OWLRelation> getOWLRelations()
	{
		return new HashSet<OWLRelation>(relations.values());
	}
	
	public Set<OWLLink> getConceptOWLLink (OWLConcept c)
	{
		Set<OWLLink> ownLinks = new HashSet<OWLLink>();
		Set<OWLConcept> potentialNeighbors = getIsland(c);
		if (reasoner instanceof Reasoner)
		{
			for (Iterator<OWLConcept> j = potentialNeighbors.iterator(); j.hasNext();)
			{
				OWLConcept d = j.next();
				for (Iterator<OWLRelation> k = relations.values().iterator(); k.hasNext();)
				{
					OWLRelation r = k.next();
					Set<OWLExplanation> exps = checkOWLLink(c, r, d); 
					if (exps != null)
					{
						OWLLink link = new OWLLink(r, d, exps); //All the links, inferred and not inferred, have explanations
						ownLinks.add(link);
					}
				}
			}
		}
		else if (reasoner instanceof ElkReasoner)
			ownLinks = getConceptOWLLinks(c, potentialNeighbors);
		return ownLinks;
	}
	
	public void setOWLLinks (Set<MyOWLLogicalEntity> entities)
	{
		Set<OWLConcept> conceptsE = new HashSet<OWLConcept>(); 
		for (MyOWLLogicalEntity e: entities)
		{
			if (e instanceof OWLConcept)
				conceptsE.add((OWLConcept) e);
			else
			{
				MyOWLIndividual ind = (MyOWLIndividual) e;
				ind.getNeighbors();
			}
		}
		setOWLLinksConcepts(conceptsE);
	}
	
	public void setOWLLinksConcepts (Set<OWLConcept> concepts)
	{
		Map<OWLConcept, Map<OWLClass, OWLAxiom>> axioms = new HashMap<OWLConcept, Map<OWLClass, OWLAxiom>>();
		
		
		for (Iterator<OWLConcept> i = concepts.iterator(); i.hasNext();)
		{
			OWLConcept c = i.next();
			Set<OWLConcept> potentialNeighbors = getIsland(c);
			Map<OWLClass, OWLAxiom> register = addEquivalentAxioms(c, potentialNeighbors);
			axioms.put(c, register);
		}
		reasoner.flush();

		for (Iterator<OWLConcept> i = axioms.keySet().iterator(); i.hasNext();)
		{
			OWLConcept c = i.next();
			OWLClass a = c.getOWLClass();
			Map<OWLClass, OWLAxiom> register = axioms.get(c);
			Set<OWLLink> links = new HashSet<OWLLink>();
			for (Iterator<OWLClass> j = register.keySet().iterator(); j.hasNext();)
			{
				OWLClass test = j.next();
				OWLSubClassOfAxiom expression = (OWLSubClassOfAxiom) register.get(test);
				links.addAll(confirmedLinks(a, test, expression));
			}
			c.setNeighbors(links);
		}
		
		fullExplanations(axioms.keySet());

		for (Iterator<OWLConcept> i = axioms.keySet().iterator(); i.hasNext();)
		{
			OWLConcept c = i.next();
			Map<OWLClass, OWLAxiom> register = axioms.get(c);
			for (Iterator<OWLClass> j = register.keySet().iterator(); j.hasNext();)
			{
				OWLClass test = j.next();
				OWLAxiom expression = register.get(test);
				if (o.containsAxiom(expression))
					manager.removeAxiom(o, expression);
			}
		}
		reasoner.flush();
	}
	
	private void fullExplanations(Set<OWLConcept> axioms)
	{
		Map<OWLSubClassOfAxiom, OWLLink> mapAxioms = new HashMap<OWLSubClassOfAxiom, OWLLink>();
		for (Iterator<OWLConcept> i = axioms.iterator(); i.hasNext();)
		{
			OWLConcept c = i.next();
			for (OWLLink l: c.getNeighbors())
			{
				OWLSubClassOfAxiom linkAxiom = buildAxiom(l.getRelation().getOWLObjectProperty(), c.getOWLClass(), l.getDestiny().getOWLConcept().getOWLClass());
				mapAxioms.put(linkAxiom, l);
			}
		}
		Map<OWLSubClassOfAxiom, Set<OWLExplanation>> explanations;
		explanations = getExplanations(mapAxioms.keySet());
		for (OWLSubClassOfAxiom ax: explanations.keySet())
		{
			mapAxioms.get(ax).setExplanations(explanations.get(ax));
		}
	}
	
	
	//This function returns only the links entailed in the ontology. The explanations are not computed at this time
	private Set<OWLLink> confirmedLinks(OWLClass a, OWLClass test, OWLSubClassOfAxiom expression)
	{
		Set<OWLLink> links = new HashSet<OWLLink>();
		if (reasoner instanceof ElkReasoner)
		{
			if (!reasoner.isSatisfiable(test))
			{
				OWLObjectIntersectionOf intersection = (OWLObjectIntersectionOf) expression.getSuperClass();
				Set<OWLClassExpression> interSet = intersection.asConjunctSet();
				interSet.remove(a);
				OWLObjectComplementOf negation = (OWLObjectComplementOf) interSet.iterator().next();
				OWLObjectSomeValuesFrom exp = (OWLObjectSomeValuesFrom) negation.getOperand();
				OWLRelation r = this.getOWLRelation(exp.getProperty().asOWLObjectProperty().toStringID());
				OWLConcept d = this.getOWLConcept(exp.getFiller().asOWLClass().toStringID());
				//OWLObjectSomeValuesFrom relationAxiom = factory.getOWLObjectSomeValuesFrom(r.getOWLObjectProperty(), d.getOWLClass());
				//OWLSubClassOfAxiom linkAxiom = factory.getOWLSubClassOfAxiom(a, relationAxiom);
				OWLLink link = new OWLLink(r, d);//, getQuickExplanations(linkAxiom));
				links.add(link);
			}
		}
		else if (reasoner instanceof Reasoner)
		{
			OWLObjectIntersectionOf intersection = (OWLObjectIntersectionOf) expression.getSuperClass(); //aux.iterator().next();
			Set<OWLClassExpression> interSet = intersection.asConjunctSet();
			interSet.remove(a);
			OWLObjectComplementOf negation = (OWLObjectComplementOf) interSet.iterator().next();
			OWLObjectSomeValuesFrom exp = (OWLObjectSomeValuesFrom) negation.getOperand();
			OWLRelation r = this.getOWLRelation(exp.getProperty().asOWLObjectProperty().toStringID());
			OWLConcept d = this.getOWLConcept(exp.getFiller().asOWLClass().toStringID());
			OWLObjectSomeValuesFrom relationAxiom = factory.getOWLObjectSomeValuesFrom(r.getOWLObjectProperty(), d.getOWLClass());
			OWLSubClassOfAxiom linkAxiom = factory.getOWLSubClassOfAxiom(a, relationAxiom);
			if (reasoner.isEntailed(linkAxiom))
			{
				OWLLink link = new OWLLink(r, d);//, getExplanations(linkAxiom));
				links.add(link);
			}
		}
		return links;
	}
	
	public Map<OWLClass, OWLAxiom> addEquivalentAxioms(OWLConcept c, Set<OWLConcept> potentialNeighbors)
	{
		Map<OWLClass, OWLAxiom> register = new HashMap<OWLClass, OWLAxiom>();
		OWLClass a = c.getOWLClass();
		@SuppressWarnings("deprecation")
		PrefixManager pm = new DefaultPrefixManager(prefix);
		for (Iterator<OWLConcept> j = potentialNeighbors.iterator(); j.hasNext();)
		{
			OWLConcept d = j.next();
			OWLClass b = d.getOWLClass();
			for (Iterator<OWLRelation> k = relations.values().iterator(); k.hasNext();)
			{
				OWLRelation r = k.next();
				OWLObjectProperty p = r.getOWLObjectProperty();
				OWLObjectSomeValuesFrom relationAxiom = factory.getOWLObjectSomeValuesFrom(p, b);
				OWLClassExpression negation = factory.getOWLObjectComplementOf(relationAxiom);
		        Set<OWLClassExpression> intersection = new HashSet<OWLClassExpression>();
		        intersection.add(a);
		        intersection.add(negation);
		        OWLObjectIntersectionOf intersectionAxiom = factory.getOWLObjectIntersectionOf(intersection);
		        OWLClass test = factory.getOWLClass(":test" + equivalentClassNumber, pm);
		        equivalentClassNumber++;
		        OWLAxiom definition = factory.getOWLSubClassOfAxiom(test, intersectionAxiom);//.getOWLEquivalentClassesAxiom(test, intersectionAxiom);
		        register.put(test, definition);
			}
		}
		manager.addAxioms(o, new HashSet<OWLAxiom>(register.values()));
		return register;
	}
	
	
	public Set<OWLLink> getConceptOWLLinks (OWLConcept c, Set<OWLConcept> potentialNeighbors)
	{
		Set<OWLLink> links = new HashSet<OWLLink>();
		OWLClass a = c.getOWLClass();
		Map<OWLClass, OWLAxiom> register = addEquivalentAxioms(c, potentialNeighbors);
		
		reasoner.flush();
		for (Iterator<OWLClass> i = register.keySet().iterator(); i.hasNext();)
		{
			OWLClass test = i.next();
			OWLSubClassOfAxiom expression = (OWLSubClassOfAxiom) register.get(test);
			links.addAll(confirmedLinks(a, test, expression));
		}
		manager.removeAxioms(o, new HashSet<OWLAxiom>(register.values()));
		return links;
	}
	
	private OWLSubClassOfAxiom buildAxiom (OWLObjectProperty p, OWLClass origin, OWLClass destiny)
	{
		//====================BUILD AXIOM=================================
        OWLObjectSomeValuesFrom relationAxiom = factory.getOWLObjectSomeValuesFrom(p, destiny);
        OWLSubClassOfAxiom linkAxiom = factory.getOWLSubClassOfAxiom(origin, relationAxiom);
        return linkAxiom;
        //============== END BUILDING AXIOM ================================
	}
	
	private Map<OWLSubClassOfAxiom, Set<OWLExplanation>> getExplanations (Set<OWLSubClassOfAxiom> linkAxioms)
	{
		Map<OWLSubClassOfAxiom, Set<OWLExplanation>> mapExpl = new HashMap<OWLSubClassOfAxiom, Set<OWLExplanation>>();
		for (OWLSubClassOfAxiom linkAxiom: linkAxioms)
		{
			if (o.containsAxiom(linkAxiom))
	        {
	        	mapExpl.put(linkAxiom, new HashSet<OWLExplanation>());
	        }
			else
			{
				Set<OWLExplanation> explanations = Collections.emptySet();
				mapExpl.put(linkAxiom, explanations);//getExplanations(linkAxiom));
			}
		}
		return mapExpl;
	}
	
	private Set<OWLExplanation> getExplanations (OWLSubClassOfAxiom linkAxiom)
	{
		//============== GETTING EXPLANATION ===============================
        Set<OWLExplanation> explanations = null;
        if (o.containsAxiom(linkAxiom))
        {
        	explanations = Collections.emptySet();
        }
        else
        {
        	explanations = new HashSet<OWLExplanation>();
        	Set<Explanation<OWLAxiom>> expAxioms = expl.getExplanations(linkAxiom, 1);
        	for (Iterator<Explanation<OWLAxiom>> j = expAxioms.iterator(); j.hasNext();)
        	{
        		OWLExplanation e;
				try {
					e = new OWLExplanation(j.next().getAxioms(), this);
					explanations.add(e);
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
        	}
        }
        return explanations;
        //============= END GETTING EXPLANATION ===============================
	}
	
	
	public Set<OWLLink> getDirectNeighbors (OWLConcept c)
	{
		Set<OWLLink> ownLinks = new HashSet<OWLLink>();
		Set<OWLClass> superClasses = reasoner.getSuperClasses(c.getOWLClass(), false).getFlattened();
		Stack<OWLClass> stck = new Stack<OWLClass>();
		stck.addAll(superClasses);
		superClasses.clear();
		double size = stck.size();
		for (int i = 0; i < size; i++)
		{
			OWLClass cl = stck.pop();
			Set<OWLClassExpression> clExps = new HashSet<OWLClassExpression>(EntitySearcher.getSuperClasses(cl, o));
			for (OWLClassExpression clExp: clExps)
			{
				if (clExp.getClassExpressionType() == ClassExpressionType.OBJECT_SOME_VALUES_FROM)
				{
					OWLObjectSomeValuesFrom aux = (OWLObjectSomeValuesFrom) clExp;
					OWLClass destiny = aux.getFiller().asOWLClass();
					OWLConcept destinyConcept = getOWLConcept(destiny.toStringID());
					OWLObjectProperty p = aux.getProperty().asOWLObjectProperty();
					OWLRelation r = this.getOWLRelation(p.getIRI().toURI().toString());
					//====================BUILD AXIOM=================================
					//OWLSubClassOfAxiom linkAxiom = buildAxiom(p, c.getOWLClass(), destiny);
					//============== END BUILDING AXIOM ================================
			        //============== GETTING EXPLANATION ===============================
			        Set<OWLExplanation> explanations = Collections.emptySet();//getExplanations(linkAxiom);
			        //============= END GETTING EXPLANATION ===============================
					ownLinks.add(new OWLLink(r, destinyConcept, explanations));
					//===================================
				}
			}
		}
		return ownLinks;
	}
	
	
	public Set<OWLRelation> getTransitiveOWLRelations()
	{
		Set<OWLRelation> transitives = new HashSet<OWLRelation>();
		
		for (Iterator<OWLRelation> i = relations.values().iterator(); i.hasNext();)
		{
			OWLRelation r = i.next();
			OWLObjectProperty oP = r.getOWLObjectProperty();
			if (EntitySearcher.isTransitive(oP, o))
				transitives.add(r);
				
		}
		return transitives;
	}
	
	public Set<List<OWLRelation>> getPropertyChains(OWLRelation r)
	{
		return getPropertyChains().get(r);
	}
	
	public Map<OWLRelation, Set<List<OWLRelation>>> getPropertyChains()
	{
		Map<OWLRelation, Set<List<OWLRelation>>> propertyChains = new HashMap<OWLRelation, Set<List<OWLRelation>>>();
		
		
		Set<OWLSubPropertyChainOfAxiom> axioms = o.getAxioms(AxiomType.SUB_PROPERTY_CHAIN_OF);
	
		for (Iterator<OWLSubPropertyChainOfAxiom> j = axioms.iterator(); j.hasNext();)
		{
			OWLSubPropertyChainOfAxiom oPChain = j.next();
			List<OWLObjectPropertyExpression> properties = oPChain.getPropertyChain();
			OWLObjectProperty op = oPChain.getSuperProperty().asOWLObjectProperty();
			OWLRelation r = this.getOWLRelation(op.toStringID());
			
			List<OWLRelation> relationChain = new ArrayList<OWLRelation>();
			for (Iterator<OWLObjectPropertyExpression> k = properties.iterator(); k.hasNext();)
			{
				OWLObjectProperty oChain = k.next().asOWLObjectProperty();
				relationChain.add(this.getOWLRelation(oChain.toStringID()));
			}
			Set<List<OWLRelation>> relationChains = propertyChains.get(r);
			if (relationChains == null)
			{
				relationChains = new HashSet<List<OWLRelation>>();
				propertyChains.put(r, relationChains);
			}
			relationChains.add(relationChain);
			
		}
		return propertyChains;
	}
	
	
	public Set<OWLLink> getIndividualOWLLink (MyOWLIndividual ind)
	{
 		Set<OWLLink> ownLinks = new HashSet<OWLLink>();
 		Collection<OWLIndividual> sameInd = EntitySearcher.getSameIndividuals(ind.getOWLIndividual().getOWLNamedIndividual(), o);
 		sameInd.add(ind.getOWLNamedIndividual());
		//System.out.println("sameInd: "+sameInd);
 		for (OWLIndividual oInd : sameInd)
 		{
	 		Set<OWLObjectPropertyAssertionAxiom> axioms = o.getObjectPropertyAssertionAxioms(oInd);
	 		for (OWLObjectPropertyAssertionAxiom oax: axioms)
	 		{
	 			if (!taxonomicProperties.contains(oax.getProperty().asOWLObjectProperty().toStringID()) && oax.getObject().toStringID().contains("http://dbpedia.org"))
	 			{
	 				OWLRelation rel = new OWLRelation(oax.getProperty().asOWLObjectProperty(), this);
	 				MyOWLIndividual mInd = this.getMyOWLIndividual(oax.getObject().toStringID());
	 				ownLinks.add(new OWLLink(rel, mInd));
	 			}
	 		}

	 		Set<OWLDataPropertyAssertionAxiom> dataAxioms = o.getDataPropertyAssertionAxioms(oInd);
	 		for (OWLDataPropertyAssertionAxiom oax: dataAxioms)
	 		{
	 			if (!taxonomicProperties.contains(oax.getProperty().asOWLDataProperty().toStringID()))
	 			{
	 				OWLRelation rel = new OWLRelation(oax.getProperty().asOWLDataProperty(), this);
	 				ownLinks.add(new OWLLink(rel, oax.getObject()));
	 			}
	 		}
	 		
	 		Set<OWLAnnotationAssertionAxiom> annAxioms = new HashSet<OWLAnnotationAssertionAxiom>(EntitySearcher.getAnnotationAssertionAxioms(oInd.asOWLNamedIndividual(), o));
	 		for (OWLAnnotationAssertionAxiom oax: annAxioms)
	 		{
	 				OWLRelation rel = new OWLRelation(oax.getProperty().asOWLAnnotationProperty(), this);
	 				if (oax.getValue().asLiteral().isPresent())
	 					ownLinks.add(new OWLLink(rel, oax.getValue().asLiteral().get()));
	 		}
 		}
		return ownLinks;
	}
	
	/*private void getOWLLinks(Set<OWLConcept> classes, Set<OWLRelation> objectProperties)
	{
		double progressCounter = 0.0;
		double totalLoops = classes.size()*classes.size()*objectProperties.size();
		//In this loop we check for each concept if it has any type of relation with any other in the ontology.
		for (Iterator<OWLConcept> i = classes.iterator(); i.hasNext();)
		{
			OWLConcept c = i.next();
			Set<OWLLink> ownLinks = new HashSet<OWLLink>();
			for (Iterator<OWLConcept> j = classes.iterator(); j.hasNext();)
			{
				OWLConcept d = j.next();
				for (Iterator<OWLRelation> k = objectProperties.iterator(); k.hasNext();)
				{
					OWLRelation r = k.next();
					Set<OWLExplanation> exps = checkOWLLink(c, r, d); 
					if (exps != null)
					{
						OWLLink link = new OWLLink(r, d, exps); //All the links, inferred and not inferred, have explanations
						ownLinks.add(link);
					}
					progressCounter++;
				}
				
			}
            //Set neighbors of OWLConcepts
			c.setNeighbors(ownLinks);
			System.out.println(progressCounter*100/totalLoops + "%");
		}
	}*/
	
	public boolean isPropertyinSomePropertyChain(OWLRelation p)
	{
		for (Iterator<OWLRelation> i = propertyChains.keySet().iterator(); i.hasNext();)
		{
			OWLRelation r = i.next();
			Set<List<OWLRelation>> set = propertyChains.get(r);
			for (Iterator<List<OWLRelation>> j = set.iterator(); j.hasNext();)
			{
				List<OWLRelation> list = j.next();
				if (list.contains(p))
					return true;
			}
		}
		return false;
	}
	
	
	public Set<OWLConcept> getIsland(OWLConcept c)
	{
		return getIsland(c, new HashSet<OWLConcept>());
	}
	
	private Set<OWLConcept> getIsland(OWLConcept c, Set<OWLConcept> visited)
	{
		
		Set<OWLConcept> island = new HashSet<OWLConcept>();
		
		Collection<OWLClassExpression> superClasses = EntitySearcher.getSuperClasses(c.getOWLClass(), o);//c.getOWLClass().getSuperClasses(o);
		Stack<OWLClassExpression> stck = new Stack<OWLClassExpression>();
		stck.addAll(superClasses);
		superClasses.clear();
		double size = stck.size();
		for (int i = 0; i < size; i++)
		{
			OWLClassExpression clExp = stck.pop();
			if (clExp.getClassExpressionType() == ClassExpressionType.OBJECT_SOME_VALUES_FROM)
			{
				OWLObjectSomeValuesFrom aux = (OWLObjectSomeValuesFrom) clExp;
				OWLClass destiny = aux.getFiller().asOWLClass();
				OWLConcept destinyConcept = getOWLConcept(destiny.toStringID());
				if (!visited.contains(destinyConcept))
				{
					island.add(destinyConcept);
					visited.add(destinyConcept);
					island.addAll(getIsland(destinyConcept, visited));
				}
			}
		}
		return island;
	}
	
	private Set<OWLExplanation> checkOWLLink(OWLConcept c1, OWLRelation r, OWLConcept c2)
	{
		OWLClass a = c1.getOWLClass();
		OWLClass b = c2.getOWLClass();
		OWLObjectProperty p = r.getOWLObjectProperty();
        OWLObjectSomeValuesFrom relationAxiom = factory.getOWLObjectSomeValuesFrom(p, b);
        OWLSubClassOfAxiom linkAxiom = factory.getOWLSubClassOfAxiom(a, relationAxiom);
        
        //Maybe we have to consider not only the "some values from", but also "all values from"
        Set<OWLExplanation> explanations = null;
        if (o.containsAxiom(linkAxiom))
        {
        	explanations = Collections.emptySet();
        	return explanations;
        }
        
        /*OWLClassExpression negation = factory.getOWLObjectComplementOf(relationAxiom);
        Set<OWLClassExpression> subsumption = new HashSet<OWLClassExpression>();
        subsumption.add(a);
        subsumption.add(negation);
        OWLObjectIntersectionOf subsumptionAxiom = factory.getOWLObjectIntersectionOf(subsumption);*/
        
        if (reasoner.isEntailed(linkAxiom)) //If the axiom is explicit in the ontology does not have explanation
        //if (reasoner.getSubClasses(relationAxiom, false).containsEntity(a))
        //if (!reasoner.isSatisfiable(subsumptionAxiom))
        {
        	/*startTime = System.nanoTime();
        	explanations = getExplanations(linkAxiom);//new HashSet<OWLExplanation>();
        	estimatedTime = (System.nanoTime() - startTime)/1000000;
        	System.out.println("Classic Explanation: " + estimatedTime + " " + explanations);*/
        	/*Set<Explanation<OWLAxiom>> expAxioms = expl.getExplanations(linkAxiom, 1);
        	for (Iterator<Explanation<OWLAxiom>> i = expAxioms.iterator(); i.hasNext();)
        	{
        		OWLExplanation e;
				try {
					e = new OWLExplanation(i.next().getAxioms(), this);
					explanations.add(e);
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
        	}*/
        	//startTime = System.nanoTime();
        	explanations = getExplanations(linkAxiom);
        	if (explanations.size() > 1)
        	{
        		OWLExplanation exp = explanations.iterator().next();
        		explanations.clear();
        		explanations.add(exp);
        	}
        	//estimatedTime = (System.nanoTime() - startTime)/1000000;
        	//System.out.println("New Explanation: " + estimatedTime + " " + explanations);
        }
        return explanations;
	}
	
	private void startReasoner(OWLReasonerFactory reasonerFactory, OWLReasonerConfiguration configuration)
	{
		Logger.getLogger("org.semanticweb.elk").setLevel(Level.WARN);
		
		reasoner.precomputeInferences(InferenceType.SAME_INDIVIDUAL);
		reasoner.precomputeInferences(InferenceType.CLASS_ASSERTIONS);
        reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
        reasoner.precomputeInferences(InferenceType.OBJECT_PROPERTY_HIERARCHY);
        reasoner.precomputeInferences(InferenceType.OBJECT_PROPERTY_ASSERTIONS);
        reasoner.precomputeInferences(InferenceType.DISJOINT_CLASSES);
        ExplanationGeneratorFactory<OWLAxiom> genFac = ExplanationManager.createExplanationGeneratorFactory(reasonerFactory); //new ElkReasonerFactory());
        expl = genFac.createExplanationGenerator(o);
	}
	private void startELKReasoner(){
		OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();//Reasoner.ReasonerFactory(); //new JcelReasonerFactory(); // new JFactFactory(); //new PelletReasonerFactory(); // 
		OWLReasonerConfiguration configuration = new ElkReasonerConfiguration();
		reasoner =  reasonerFactory.createReasoner(o, configuration);
		startReasoner(reasonerFactory, configuration);
	}
	
	private void startHermiTReasoner(){
		OWLReasonerFactory reasonerFactory = (OWLReasonerFactory) new Reasoner.ReasonerFactory(); //new JcelReasonerFactory(); //ElkReasonerFactory(); new JFactFactory(); //new PelletReasonerFactory(); 
		ConsoleProgressMonitor progressMonitor = new ConsoleProgressMonitor();
		//OWLReasonerConfiguration configuration = new SimpleConfiguration(progressMonitor);
		Configuration configuration = new Configuration();
		configuration.ignoreUnsupportedDatatypes=true; 
		configuration.throwInconsistentOntologyException = false;
		reasoner =  reasonerFactory.createReasoner(o, (OWLReasonerConfiguration) configuration);
		//startReasoner(reasonerFactory, (OWLReasonerConfiguration) configuration);
	}
	
	private void startStructuralReasoner(){
		OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
		//ConsoleProgressMonitor progressMonitor = new ConsoleProgressMonitor();
		OWLReasonerConfiguration configuration = new SimpleConfiguration();
		reasoner = reasonerFactory.createReasoner(o, configuration);
		startReasoner(reasonerFactory, configuration);
	}
	
	public void disposeReasoner()
	{
		reasoner.dispose();
	}

	public Set<OWLConcept> getConcepts() {
		return new HashSet<OWLConcept>(concepts.values());
	}
	
	public Set<MyOWLIndividual> getMyOWLIndividuals() {
		return new HashSet<MyOWLIndividual>(individuals.values());
	}
	
	public void removeConcept(OWLConcept c)
	{
		c.dispose();
	}
	
	public OWLConcept getOWLConcept (String uri)
	{
		OWLConcept con = concepts.get(uri);
		/*if (con == null)
		{
			con = new OWLConcept(factory.getOWLClass(IRI.create(uri)), this);
			concepts.put(uri, con);
		}*/
		return con;
	}
	
	public MyOWLIndividual getMyOWLIndividual (String uri)
	{
		MyOWLIndividual con = individuals.get(uri);
		/*if (con == null)
		{
			con = new MyOWLIndividual(factory.getOWLNamedIndividual(IRI.create(uri)), this);
			individuals.put(uri, con);
		}*/
		return con;
	}
	
	public MyOWLLogicalEntity getMyOWLLogicalEntity(String uri)
	{
		MyOWLLogicalEntity con = concepts.get(uri);
		if (con == null)
			con = individuals.get(uri);
		return con;
	}
	
	public Set<OWLConcept> getSubConcepts(OWLConcept c)
	{
		Set<OWLClass> classes = reasoner.getSubClasses(c.getOWLClass(), false).getFlattened();
		Set<OWLConcept> subConcepts = new HashSet<OWLConcept>();
		for (Iterator<OWLClass> i = classes.iterator(); i.hasNext();)
		{
			OWLClass cl = i.next();
			subConcepts.add(this.getOWLConcept(cl.toStringID()));
		}
		return subConcepts;
	}
	
	public Set<OWLRelation> getSubRelations(OWLRelation c)
	{
		Set<OWLObjectPropertyExpression> relations = reasoner.getSubObjectProperties(c.getOWLObjectProperty(), false).getFlattened();
		Set<OWLRelation> subRelations = new HashSet<OWLRelation>();
		for (Iterator<OWLObjectPropertyExpression> i = relations.iterator(); i.hasNext();)
		{
			OWLObjectProperty cl = i.next().asOWLObjectProperty();
			subRelations.add(this.getOWLRelation(cl.toStringID()));
		}
		return subRelations;
	}
	
	
	public OWLNamedIndividual getOWLIndividual (String uri)
	{
		return factory.getOWLNamedIndividual(IRI.create(uri));
	}
	
	public boolean isSatisfiable(OWLClass cl)
	{
		return reasoner.isSatisfiable(cl);
	}
	
	public boolean isSubClassOf(OWLClass sub, OWLClass sup)
	{
		Set<OWLClass> anc = getSuperClasses(sub);
		return anc.contains(sup);
	}
	
	private Set<OWLClass> getSuperClasses(OWLClass sub)
	{
		Set<OWLClass> anc = ancestors.get(sub);
		if (anc == null)
		{
			anc = reasoner.getSuperClasses(sub, false).getFlattened();
			ancestors.put(sub, anc);
		}
		return anc;
	}
	
	private Set<OWLIndividual> getSuperCategories(OWLIndividual sub)
	{
		Set<OWLIndividual> anc = ancestorsCat.get(sub);
		if (anc == null)
		{
			anc = new HashSet<OWLIndividual>();
			Queue<OWLIndividual> q = new LinkedList<OWLIndividual>();
			q.add(sub);
			Set<OWLIndividual> processed = new HashSet<OWLIndividual>();
			while (!q.isEmpty())
			{
				OWLIndividual ind = q.poll();
				processed.add(ind);
				Collection<OWLIndividual> newInds = EntitySearcher.getObjectPropertyValues(ind, factory.getOWLObjectProperty(IRI.create("http://purl.org/dc/terms/subject")), o);
				newInds.addAll(EntitySearcher.getObjectPropertyValues(ind, factory.getOWLObjectProperty(IRI.create("http://www.w3.org/2004/02/skos/core#broader")), o));
				newInds.removeAll(processed);
				q.addAll(newInds);
				anc.addAll(newInds);
			}
			ancestorsCat.put((OWLLogicalEntity) sub, anc);
		}
		return anc;
	}
	
	public Set<OWLConcept> getAncestors(OWLConcept c)
	{
		Set<OWLClass> classes = getSuperClasses(c.getOWLClass());
		Set<OWLConcept> concepts = new HashSet<OWLConcept>();
		for (OWLClass cl: classes)
		{
			concepts.add(this.getOWLConcept(cl.toStringID()));
		}
		return concepts;
	}
	
	public boolean isOfType(OWLNamedIndividual ind, OWLClass c)
	{
		return getTypes(ind, false).contains(c);
	}

	public Set<OWLRelation> getRelations() {
		return new HashSet<OWLRelation>(relations.values());
	}
	
	public OWLRelation getOWLRelation (String uri)
	{
		return relations.get(uri);
	}

	public OWLObjectProperty getOWLObjectProperty(String uri)
	{
		return factory.getOWLObjectProperty(IRI.create(uri));
	}
	
	private <T,S> T profLCS (Set<T> setX, Set<T> setY, T x, T y)
	{
		if (x == y)
			return x;

		Set<T> common = new HashSet<T>(setX);
		common.retainAll(setY);
		
		T lcs;
		if (common.isEmpty())
			return null;
		lcs = common.iterator().next();

		int maxProf = prof(lcs);
		for (Iterator<T> i = common.iterator(); i.hasNext(); )
		{
			T aux = (T) i.next();
			
			if (prof(aux) > maxProf )
			{
				maxProf = prof(aux);
				lcs = aux;
			}
		}
		return lcs;
	}
	
	
	public Set<OWLProperty> getSuperProperties(OWLProperty x, boolean direct)
	{
		Set<OWLProperty> superProp = new HashSet<OWLProperty>();
		superProp.add(factory.getOWLTopObjectProperty());
		if (direct)
		{
			Set<OWLProperty> supProp = directSuperProperties.get(x);
			if (supProp != null)
				superProp = supProp;
			else
			{
				superProp.addAll(EntitySearcher.getSuperProperties(x, o));//x.getSuperProperties(o));
				directSuperProperties.put(x, superProp);
			}
			return superProp;
		}

		Set<OWLProperty> supProp = allSuperProperties.get(x);
		if (supProp != null)
			return supProp;
		
		Collection<OWLProperty> step = EntitySearcher.getSuperProperties(x, o);//x.getSuperProperties(o);
		List<OWLProperty> list = new ArrayList<OWLProperty>(step);
		while (list.size() > 0)
		{
			step = EntitySearcher.getSuperProperties(list.get(0), o);//list.get(0).getSuperProperties(o);
			if (!superProp.containsAll(step))
			{
				superProp.add(list.get(0));
				list.addAll(step);
			}
			list.remove(0);
			
		}
		allSuperProperties.put(x, superProp);
		return superProp;
	}
	
	
	public double taxonomicPropertySimilarity (OWLProperty x, OWLProperty y)
	{	
		if (!x.getClass().equals(y.getClass()))
			return 0;
		if (x.equals(y))
			return 1;
		Set<OWLProperty> setX = this.getSuperProperties(x, false); 
		setX.add(x);
		Set<OWLProperty> setY = this.getSuperProperties(y, false);
		setY.add(y);
		
		OWLProperty lcs = (OWLProperty) profLCS(setX, setY, x, y);
		double profLCS = prof(lcs);
		
		double dxa = dist(x, lcs);
		double dxroot = profLCS + dxa;
		double dya = dist(y, lcs);
		double dyroot = profLCS + dya;
		double dtax = (dxa + dya)/(dxroot + dyroot);
		
		return 1-dtax;
	}
	
	public Set<String> getAllTriples(MyOWLIndividual e)
	{
		Collection<OWLAnnotationAssertionAxiom> anns = EntitySearcher.getAnnotationAssertionAxioms(e.getOWLLogicalEntity(), o);
		Multimap<OWLObjectPropertyExpression, OWLIndividual> objProp = EntitySearcher.getObjectPropertyValues(e.getOWLNamedIndividual(), o);
		Multimap<OWLDataPropertyExpression, OWLLiteral> dataProp = EntitySearcher.getDataPropertyValues(e.getOWLNamedIndividual(), o);
		
		Set<String> triples = new HashSet<String>();
		
		for (OWLAnnotationAssertionAxiom a: anns)
		{
			String s = a.toString();
			//if (!a.getProperty().toStringID().contains("http://dbpedia.org/property/"))
			//{
				if (a.getValue().asLiteral().isPresent())
					triples.add(a.getProperty().toStringID() + " " + a.getValue().asLiteral().get().getLiteral().toString());
			//}
		}
		for (OWLObjectPropertyExpression a: objProp.asMap().keySet())
		{
			Collection<OWLIndividual> col = objProp.asMap().get(a);
			for (OWLIndividual i: col)
			{
				String s = a.toString() + " " + i.toString();
				//if (!s.contains("http://dbpedia.org/property/"))
					triples.add(s);
			}
		}
		for (OWLDataPropertyExpression a: dataProp.asMap().keySet())
		{
			Collection<OWLLiteral> col = dataProp.asMap().get(a);
			for (OWLLiteral i: col)
			{
				String s = a.toString() + " " + i.getLiteral().toString();
				//if (!s.contains("http://dbpedia.org/property/"))
					triples.add(s);
			}
		}
		return triples;
	}
	
	public MyOWLLogicalEntity getLCS(MyOWLLogicalEntity a, MyOWLLogicalEntity b)
	{
		if (a instanceof OWLConcept && b instanceof OWLConcept)
			return getLCS((OWLConcept) a, (OWLConcept) b);
		else
			return getLCS((MyOWLIndividual) a, (MyOWLIndividual) b);
	}
	
	public OWLConcept getLCS(OWLConcept a, OWLConcept b)
	{
		AnnotationComparison comparison = new AnnotationComparison(a, b);
		OWLConcept lcsConcept = lcas.get(comparison);//null;//
		if (lcsConcept == null)
		{
			OWLClass x = a.getOWLClass(), y = b.getOWLClass();
			Set<OWLClass> setX = getSuperClasses(x);//reasoner.getSuperClasses(x, false).getFlattened(); //this.getSuperClasses(x, false);
			if (!setX.contains(factory.getOWLThing()))
				reasoner.getSuperClasses(x, false);
			setX.add(x);
			Set<OWLClass> setY = getSuperClasses(y);//reasoner.getSuperClasses(y, false).getFlattened(); //this.getSuperClasses(y, false);
			if (!setY.contains(factory.getOWLThing()))
				getSuperClasses(y);
			setY.add(y);
			OWLClass lcs = profLCS(setX, setY, x, y);
			lcsConcept = this.getOWLConcept(lcs.toStringID());
			if (storing)
				lcas.put(comparison, lcsConcept);
		}
		
		return lcsConcept;
	}
	
	private Set<OWLClass> getSuperClasses(MyOWLIndividual ind)
	{
		Set<OWLClass> aA = ancestors.get(ind.getOWLNamedIndividual());
		if (aA == null)
		{
			aA = this.getTypes(ind.getOWLNamedIndividual(), false);
			ancestors.put(ind.getOWLNamedIndividual(), aA);
		}
		return aA;
	}
	
	public Set<OWLConcept> getDCA(MyOWLLogicalEntity a, MyOWLLogicalEntity b)
	{
		AnnotationComparison comparison = new AnnotationComparison(a, b);
		Set<OWLConcept> dcas = disAncestors.get(comparison);
		
		if (dcas == null)
		{
			Set<OWLClass> aA;
			if (a instanceof OWLConcept)
				aA = this.getSuperClasses(((OWLConcept) a).getOWLClass());
			else
			{
				MyOWLIndividual ind = (MyOWLIndividual) a;
				aA = this.getSuperClasses(ind);
			}
			Set<OWLClass> aB;
			if (b instanceof OWLConcept)
				aB = this.getSuperClasses(((OWLConcept) b).getOWLClass());
			else
			{
				MyOWLIndividual ind = (MyOWLIndividual) b;
				aB = this.getSuperClasses(ind);
			}
			Set<OWLClass> common = new HashSet<OWLClass>(aA);
			common.retainAll(aB);
			Map<OWLLogicalEntity, Integer> mapA = new HashMap<OWLLogicalEntity, Integer>();
			Map<OWLLogicalEntity, Integer> mapB = new HashMap<OWLLogicalEntity, Integer>();
			getPath(a.getOWLLogicalEntity(), mapA);
			mapA.put(a.getOWLLogicalEntity(), 1);
			getPath(b.getOWLLogicalEntity(), mapB);
			mapB.put(b.getOWLLogicalEntity(), 1);
			Map<Integer, Set<OWLClass>> pathsClasses = new HashMap<Integer, Set<OWLClass>>();
			for (OWLClass cl: common)
			{
				Integer iA = mapA.get(cl);
				Integer iB = mapB.get(cl);
				
				Set<OWLClass> aux = pathsClasses.get(Math.abs(iA - iB));
				if (aux == null)
				{
					aux = new HashSet<OWLClass>();
					pathsClasses.put(Math.abs(iA - iB), aux);
				}
				aux.add(cl);
			}
			dcas = new HashSet<OWLConcept>();
			
			for (Integer dist: pathsClasses.keySet())
			{
				Set<OWLClass> aux = pathsClasses.get(dist);
				double max = 0;
				OWLConcept maxCL = null;
				for (OWLClass cl: aux)
				{
					OWLConcept con = concepts.get(cl.toStringID());
					if (con.getIC() >= max)
					{
						max = con.getIC();
						maxCL = con;
					}
				}
				dcas.add(maxCL);
			}
			disAncestors.put(comparison, dcas);
		}
		return dcas;
	}
	
	private void getPath(OWLLogicalEntity a, Map<OWLLogicalEntity, Integer> map)
	{
		Collection<OWLClassExpression> aA;
		if (a instanceof OWLClass)
			aA = EntitySearcher.getSuperClasses((OWLClass) a, o);//((OWLClass) a).getSuperClasses(o);
		else
			aA = EntitySearcher.getTypes((OWLNamedIndividual) a, o);//((OWLNamedIndividual) a).getTypes(o);
		for (OWLClassExpression exp: aA)
		{
			if (!exp.isAnonymous())
			{
				OWLClass clA = exp.asOWLClass();
				Integer numPath = map.get(clA);
				if (numPath == null)
					numPath = 0;
				map.put(clA, numPath + 1);
				getPath(clA, map);
			}
		}
	}
	
	public OWLConcept getLCS(MyOWLIndividual a, MyOWLIndividual b)
	{
		AnnotationComparison comparison = new AnnotationComparison(a, b);
		OWLConcept lcsConcept = lcas.get(comparison);
		if (lcsConcept == null)
		{
			OWLNamedIndividual x = a.getOWLNamedIndividual(), y = b.getOWLNamedIndividual();
			Set<OWLClass> setX = reasoner.getTypes(x, false).getFlattened();
			Set<OWLClass> setY = reasoner.getTypes(y, false).getFlattened();
			OWLClass lcs = profLCS(setX, setY, setX.iterator().next(), null);
			lcsConcept = this.getOWLConcept(lcs.toStringID());
			lcas.put(comparison, lcsConcept);
		}
		return lcsConcept;
	}
	
	private double dtax(OWLConcept x, OWLConcept y)
	{
		OWLConcept lcs = getLCS(x, y);
		
		double profLCS = prof(lcs.getOWLClass());
		
		double dxa = dist(x.getOWLClass(), lcs.getOWLClass());
		double dxroot = profLCS + dxa;
		double dya = dist(y.getOWLClass(), lcs.getOWLClass());
		double dyroot = profLCS + dya;
		double num = dxa + dya;
		double den = dxroot + dyroot;
		double dtax = num/den;
		dtax = 1.0 - dtax;
		
		return dtax;
	}
	
	private double dps(OWLConcept x, OWLConcept y)
	{
		OWLConcept lcs = getLCS(x, y);
		
		double profLCS = prof(lcs.getOWLClass());
		double dxa = dist(x.getOWLClass(), lcs.getOWLClass());
		double dya = dist(y.getOWLClass(), lcs.getOWLClass());
		double dps = 1.0 - (profLCS / (profLCS + dxa + dya));
		
		return 1.0 - dps;
	}
	
	public double taxonomicClassSimilarity (OWLConcept x, OWLConcept y)
	{
		double dtax = dtax(x, y);
		double dps = dps(x, y);
		return dps;
	}
	
	public Set<OWLIndividual> getCategories(OWLNamedIndividual ind, boolean direct)
	{
		OWLObjectProperty subject = factory.getOWLObjectProperty(IRI.create("http://purl.org/dc/terms/subject"));
		OWLObjectProperty broader = factory.getOWLObjectProperty(IRI.create("http://www.w3.org/2004/02/skos/core#broader"));
		
		Collection<OWLIndividual> catInds = EntitySearcher.getObjectPropertyValues(ind, subject, o);
		if (catInds.isEmpty())
		{
			catInds = EntitySearcher.getObjectPropertyValues(ind, broader, o);
		}
		
		Set<OWLIndividual> categories = new HashSet<OWLIndividual> (catInds);
		if (ind.toStringID().toLowerCase().contains("category"))
			categories.add(ind);
		Set<OWLIndividual> newCategories = new HashSet<OWLIndividual> ();
		return categories;
		/*
		if (direct)
		{
			return categories;
		}
		else
		{
			for (OWLIndividual cat: categories)
			{
				newCategories.addAll(getSuperCategories(cat));
			}
			categories.addAll(newCategories);
		}
		return categories;*/
	}
	
	public Set<OWLClass> getTypes(OWLNamedIndividual ind, boolean direct)
	{
		Set<OWLClass> classes = new HashSet<OWLClass>();
		classes.add(factory.getOWLThing());
		Collection<OWLClassExpression> classExprs = EntitySearcher.getTypes(ind, o);//ind.getTypes(o);
		
		if (direct)
		{
			for (OWLClassExpression clExp: classExprs)
			{
				classes.add(clExp.asOWLClass());
			}
		}
		else
		{
			for (OWLClassExpression clExp: classExprs)
			{
				classes.addAll(getSuperClasses(clExp.asOWLClass()));//reasoner.getSuperClasses(clExp, false).getFlattened());
			}
			for (OWLClassExpression clExp: classExprs)
			{
				classes.add(clExp.asOWLClass());
			}
			//classes.addAll(getSuperClasses(individuals.get(ind.toStringID())));
		}
		return classes;
	}
	
	public double taxonomicIndividualSimilarity (OWLLogicalEntity x, OWLLogicalEntity y)
	{
		if (x.equals(y))
			return 1;
		Set<OWLLogicalEntity> setX = new HashSet<OWLLogicalEntity>();
		Set<OWLLogicalEntity> setY = new HashSet<OWLLogicalEntity>();
		OWLLogicalEntity lcs = null;
		if (x.isOWLNamedIndividual() && y.isOWLNamedIndividual())
		{
			//Set<OWLClass> auxX = getTypes(x.asOWLNamedIndividual(), false);
			//Set<OWLClass> auxY = getTypes(y.asOWLNamedIndividual(), false);
			Set<OWLIndividual> auxX = getCategories(x.asOWLNamedIndividual(), false);
			Set<OWLIndividual> auxY = getCategories(y.asOWLNamedIndividual(), false);
			for (OWLIndividual a: auxX)
			{
				if (a.isNamed())
					setX.add(a.asOWLNamedIndividual());
			}
			for (OWLIndividual b: auxY)
			{
				if (b.isNamed())
					setY.add(b.asOWLNamedIndividual());
			}
			//setX = /*reasoner.*/getTypes(x.asOWLNamedIndividual(), false);//.getFlattened();
			//setY = /*reasoner.*/getTypes(y.asOWLNamedIndividual(), false);//.getFlattened();
			
			if (setX.isEmpty() || setY.isEmpty())
			{
				//System.out.println("ERROR: " + x + " or " + y + " have no types.");
				return -1;
			}
			lcs = profLCS(setX, setY, setX.iterator().next(), null);
		}
		
		if (x.isOWLClass() && y.isOWLClass())
		{
			OWLClass xC = x.asOWLClass();
			OWLClass yC = y.asOWLClass();
			Set<OWLClass> auxX = getSuperClasses(xC);
			auxX.add(xC);
			Set<OWLClass> auxY = getSuperClasses(yC);
			auxY.add(yC);
			for (OWLClass a: auxX)
			{
				setX.add(a);
			}
			for (OWLClass b: auxY)
			{
				setY.add(b);
			}
			/*setX = getSuperClasses(xC);//reasoner.getSuperClasses(xC, false).getFlattened();
			setX.add(xC);
			setY = getSuperClasses(yC);//reasoner.getSuperClasses(yC, false).getFlattened();
			setY.add(yC);*/
			lcs = profLCS(setX, setY, xC, yC);
		}
		
		
		
		//=======================Only for ComparisonCosine
		//OWLClass obsolete = factory.getOWLClass(IRI.create("http://www.geneontology.org/formats/oboInOwl#ObsoleteClass"));
		/*OWLAnnotationProperty deprecated = factory.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2002/07/owl#deprecated"));
		Set<OWLAnnotation> annX = x.getAnnotations(o, deprecated);
		if (annX.iterator().next().isDeprecatedIRIAnnotation())
			System.out.println("Deprecated");*/
		//if (setX.contains(obsolete) || setY.contains(obsolete))
		//	return 0;
		//=======================END
		
		if (lcs == null)
			return 0;
		
		double profLCS = prof(lcs);
		
		double dxa = dist(x, lcs) + 4;
		double dxroot = profLCS + dxa;//dist(x, root);
		double dya = dist(y, lcs) + 4;
		double dyroot = profLCS + dya;//dist(y, root);
		double num = dxa + dya;
		double den = dxroot + dyroot;
		double dtax = num/den;
		dtax = 1.0 - dtax;
		
		/*System.out.println(lcs +  " " + profLCS);
		System.out.println(dxa + " " + dya);
		System.out.println(x + " " + y + " " + dtax);*/
		return dtax;
	}
	
	
	
	
	
	private void setDistance(OWLClass c1, OWLClass c2, int d)
	{
		Map<OWLClass, Integer> aux = conceptDistances.get(c1);
		
		if (aux == null)
		{
			aux = new HashMap<OWLClass, Integer>();
			if (storing)
				conceptDistances.put(c1, aux);
		}
		aux.put(c2, d);
	}
	
	private int getDistance(OWLClass c1, OWLClass c2)
	{
		Map<OWLClass, Integer> aux = conceptDistances.get(c1);
		
		if (aux == null)
			return -1;
		else
		{
			Integer d = aux.get(c2);
			if (d == null)
				return -1;
			else
				return d;
		}
	}
	
	//Only for DBpedia category hierarchy 
	public int getDepth(OWLIndividual c1)
	{
		int depth = 0;
		Collection<OWLIndividual> indis = EntitySearcher.getObjectPropertyValues((OWLIndividual) c1, factory.getOWLObjectProperty(IRI.create("http://purl.org/dc/terms/subject")), o);
		
		if (indis.isEmpty()) // Si est¡a vac¡io la entrada es una category y hay que devolver la prof en la jerarquia
		{
			Collection<OWLAnnotation> depths = EntitySearcher.getAnnotations((OWLEntity) c1, o, factory.getOWLAnnotationProperty(IRI.create("http://dbpedia_hierarchy.org/has_depth")));
			for (OWLAnnotation d: depths)
			{
				depth = d.getValue().asLiteral().get().parseInteger();
			}
			return depth;
		}
		depth++;
		int maxDepth = 0;
		for (OWLIndividual ind: indis)
		{
			Collection<OWLAnnotation> depths = EntitySearcher.getAnnotations((OWLEntity) ind, o, factory.getOWLAnnotationProperty(IRI.create("http://dbpedia_hierarchy.org/has_depth")));
			for (OWLAnnotation d: depths)
			{
				int currentDepth = d.getValue().asLiteral().get().parseInteger();
				if (currentDepth > maxDepth)
					maxDepth = currentDepth;
			}
		}
		depth += maxDepth;
		return depth;
	}
	
	public <T> int dist(T c1, T c2)
	{
		int depth = 0;
		if (c1 instanceof OWLClass)
		{
			int dist = getDistance((OWLClass)c1, (OWLClass)c2);
			if (dist != -1)
				return dist;
			Set<OWLClassExpression> c = new HashSet<OWLClassExpression>();
			c.add((OWLClass) c1);
			while (!c.contains(c2) && !c.isEmpty())
			{
				Set<OWLClassExpression> superClasses = new HashSet<OWLClassExpression>();
				for (Iterator<OWLClassExpression> i = c.iterator(); i.hasNext();)
				{
					OWLClassExpression aux = i.next();
					if (!aux.isAnonymous())
					{
						OWLClass cl = aux.asOWLClass();
						superClasses.addAll(EntitySearcher.getSuperClasses(cl, o));//cl.getSuperClasses(o));
					}
				}
				c = superClasses;
				depth++;				
			}
			setDistance((OWLClass)c1, (OWLClass)c2, depth);
		}
		else if (c1 instanceof OWLProperty)
		{
			Set<OWLProperty> c = new HashSet<OWLProperty>();
			c.add((OWLProperty) c1);
			while (!c.contains(c2) && !c.isEmpty())
			{
				Set<OWLProperty> superObjectProperties = new HashSet<OWLProperty>();
				for (Iterator<OWLProperty> i = c.iterator(); i.hasNext();)
				{
					OWLProperty aux = i.next();
					if (!aux.isAnonymous())
						superObjectProperties.addAll(this.getSuperProperties(aux, false));//EntitySearcher.getSuperProperties(aux, o));//aux.getSuperProperties(o));
				}
				if (c.equals(superObjectProperties))
					c.clear();
				else
					c = superObjectProperties;
				depth++;				
			}
		} else if (c1 instanceof OWLNamedIndividual)
		{
			 Set<OWLLogicalEntity> c = new HashSet<OWLLogicalEntity>();
			 //Set<OWLClass> auxClassSet = this.getTypes((OWLNamedIndividual) c1, true);//reasoner.getTypes((OWLNamedIndividual) c1, true).getFlattened();
			 Set<OWLLogicalEntity> auxSet = new HashSet<OWLLogicalEntity>();
			 /*for (OWLClass auxClas: auxClassSet)
			 {
				 auxSet.add(auxClas);
			 }*/
			 int depth1 = getDepth((OWLIndividual) c1);
			 int depth2 = getDepth((OWLIndividual) c2);
			 return depth1 - depth2;
			 /*Set<OWLIndividual> auxIndSet = new HashSet<OWLIndividual>(indis);
			 for (OWLIndividual ind: auxIndSet)
			 {
				 auxSet.add(ind.asOWLNamedIndividual());
			 }
			 for (OWLLogicalEntity cl: auxSet)
			 {
				 c.add(cl);
			 }
			 Set<OWLLogicalEntity> processedEnt = new HashSet<OWLLogicalEntity>();
			 while (!c.contains(c2) && !c.isEmpty())
			 {
				//Set<OWLClassExpression> superClasses = new HashSet<OWLClassExpression>();
				Set<OWLNamedIndividual> superCategories = new HashSet<OWLNamedIndividual>();
				for (Iterator<OWLLogicalEntity> i = c.iterator(); i.hasNext();)
				{
					OWLLogicalEntity aux = i.next();
					if (aux instanceof OWLClass)
					{
						if (!aux.asOWLClass().isAnonymous())
						{
							OWLClass cl = aux.asOWLClass();
							superClasses.addAll(EntitySearcher.getSuperClasses(cl, o));//cl.getSuperClasses(o));
						}
					}
					else if (aux instanceof OWLIndividual)
					{
						if (!aux.asOWLNamedIndividual().isAnonymous())
						{
							OWLIndividual cl = aux.asOWLNamedIndividual();
							Collection<OWLIndividual> collInd = EntitySearcher.getObjectPropertyValues(cl, factory.getOWLObjectProperty(IRI.create("http://www.w3.org/2004/02/skos/core#broader")), o);
							for (OWLIndividual ind: collInd)
							{
								superCategories.add(ind.asOWLNamedIndividual());
							}
						}
					}
					processedEnt.add(aux);
				}
				//c = superClasses;
				c.clear();
				superCategories.removeAll(processedEnt);
				for (OWLNamedIndividual cat: superCategories)
				{
					c.add(cat);
				}
				depth++;
			 }*/
		}
		else	try {
				throw new Exception("dist() does not accept objects of type " + c1.getClass());
			} catch (Exception e) {
				e.printStackTrace();
			}
		return depth;
	}
	
	public <T> int prof(T _class)
	{
		int depth = 0;
		if (_class instanceof OWLClass)
		{
			if (conceptProfs.get(_class) != null)
				return conceptProfs.get(_class);
			
			depth = dist (_class, factory.getOWLThing());// - 1;
			if (storing)
				conceptProfs.put((OWLClass) _class, depth);
		}
		else if (_class instanceof OWLProperty)
		{
			if (relationProfs.get(_class) != null)
				return relationProfs.get(_class);
			if (_class instanceof OWLDataProperty)
				depth = dist(_class, factory.getOWLTopDataProperty());
			if (_class instanceof OWLObjectProperty)
				depth = dist (_class, factory.getOWLTopObjectProperty());
			relationProfs.put((OWLProperty) _class, depth);
		} else if (_class instanceof OWLIndividual)
		{
			if (categoryProfs.get(_class) != null)
				return categoryProfs.get(_class);
			Collection<OWLAnnotation> depths = EntitySearcher.getAnnotations((OWLEntity) _class,  o, factory.getOWLAnnotationProperty(IRI.create("http://dbpedia_hierarchy.org/has_depth")));
			
			for (OWLAnnotation ann: depths)
			{
				depth = Integer.parseInt(ann.getValue().asLiteral().get().getLiteral());
			}
			//depth = dist (_class, factory.getOWLClass(IRI.create("http://www.w3.org/2004/02/skos/core#Concept")));
			categoryProfs.put((OWLIndividual) _class, depth);
		}else
			try {
				throw new Exception("prof() does not accept objects of type " + _class.getClass());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		return depth;
	}
	
	
	public String getLabel(OWLConcept c)
	{
		OWLClass x = c.getOWLClass();
		Collection<OWLAnnotation> annX = EntitySearcher.getAnnotations(x, o);//x.getAnnotations(o, label);
		OWLAnnotation a = annX.iterator().next(); 
		OWLLiteral lit = (OWLLiteral) a.getValue();
		String v = lit.getLiteral();
		return v;
	}
	
	
	
	public static void main(String[] args) throws Exception
	{
		/*String ontFile1 = "/home/traverso/Schreibtisch/test.rdf";
		MyOWLOntology oDBP = new MyOWLOntology(ontFile1, "http://dbpedia.org/ontology/");
		
		MyOWLIndividual ia = oDBP.getMyOWLIndividual("http://dbpedia.org/resource/Parable_of_the_Sower_(novel)");
		MyOWLIndividual ib = oDBP.getMyOWLIndividual("http://dbpedia.org/resource/Parable_of_the_Talents_(novel)");
		System.out.println(ia.similarity(ib));*/

		/*Iterator<OWLRelation> j = o.getRelations().iterator();
		OWLRelation x = j.next();
		OWLRelation y = j.next();
		o.similarity(x, y);
		System.out.println(x + " " + y + " " + o.similarity(x,y));*/
		
		Map<String, String> ontPrefix = new HashMap<String,String>();
		ontPrefix.put("src/main/resources/dataset3/", "http://purl.org/obo/owl/GO#");
		ontPrefix.put("src/main/resources/dataset32014/", "http://purl.obolibrary.org/obo/");
		String prefix = "src/main/resources/dataset3/";
		//String ontFile = prefix + "goProtein/goEL.owl";
		String ontFile = "resources/dbpedia_diego.nt";
		MyOWLOntology o = new MyOWLOntology(ontFile, "http://dbpedia.org/ontology/", "HermiT");//"http://purl.obolibrary.org/obo/");
		
		/*String comparisonFile = prefix + "proteinpairs.txt";
		List<ComparisonResult> comparisons = DatasetTest.readComparisonFile(comparisonFile);
		String[] files = {prefix + "bpNew"};
		InformationContent ic = new InformationContent(comparisons, files, o);		*/
		
		
		MyOWLIndividual a = o.getMyOWLIndividual("http://dbpedia.org/resource/Airavt/dump0");
		MyOWLIndividual b = o.getMyOWLIndividual("http://dbpedia.org/resource/Airavt/dump1");
		Set<MyOWLLogicalEntity> anns = new HashSet<MyOWLLogicalEntity>();
		anns.add(a);
		anns.add(b);
		o.setOWLLinks(anns);
		Set<OWLLink> neighA = a.getNeighbors();
		Set<OWLLink> neighB = b.getNeighbors();
		System.out.println(b.similarity(a));//.taxonomicSimilarity(a));
		
		OWLRelation r1 = o.getOWLRelation("http://purl.obolibrary.org/obo/RO_0002213");//"("http://purl.org/obo/owl/obo#positively_regulates");
		System.out.println(o.getPropertyChains(r1));
		OWLRelation r2 = o.getOWLRelation("http://purl.obolibrary.org/obo/RO_0002211"); //("http://purl.org/obo/owl/obo#regulates");
		System.out.println(o.getPropertyChains(r2));
		System.out.println(r1.similarity(r2));
	
		
	}

	public static Set<String> getComparableEntities(String listFile)
	{
	    try {

	        Set<String> list = new HashSet<String>();

            FileReader fr = new FileReader(listFile);
            BufferedReader bf = new BufferedReader(fr);
            String line;

            while ((line = bf.readLine()) != null)
            {
                String standardURI = line.replace("<", "").replace(">", "");
                list.add(standardURI);
            }
            bf.close();

            return list;

        }catch(Exception ex) {
            ex.printStackTrace();
        }
        return  null;
	}

}
