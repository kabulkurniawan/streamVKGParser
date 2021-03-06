package sepses.ondemand_extractor;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONObject;

import com.jayway.jsonpath.JsonPath;


public class QueryTranslator {
	private String queryString;
    protected List<Triples> triples = new ArrayList<>();
    protected List<RegexPattern> regexpattern = new ArrayList<>();
    protected List<FilterRegex> filterregex = new ArrayList<>();
	protected ArrayList prefixes = new ArrayList<String>();
	protected String limit;

    
	public QueryTranslator(String queryString) throws Exception, ParseException{
		this.queryString = queryString;
		Object json = readJSONQuery(this.queryString);
		parseLimit(json);
		
		parsePrefixes(json);
		parseLimit(json);
		
	}
	
	
	public void parseJSONQuery(Model model) throws Exception, ParseException{
		Object json = readJSONQuery(this.queryString);

		parseTriple(json);
		parseFilter(json);
		lookupRegex(model);
		
	}
	
	public void parseTriple(Object json){
		
		List<String> subject = JsonPath.read(json, "$.where[*].triples[*].subject.value");
		List<String> predicate = JsonPath.read(json, "$.where[*].triples[*].predicate.value");
		List<String> object = JsonPath.read(json, "$.where[*].triples[*].object.value");
		List<String> objectType = JsonPath.read(json, "$.where[*].triples[*].object.termType");
		
		for(int i=0;i<subject.size();i++){
			
			if(checkIsURI(predicate.get(i))) {
					
					triples.add(new Triples(subject.get(i),"<"+predicate.get(i)+">", object.get(i)));
				
				}
		}
	}
	
	public void parseFilter(Object json){
		List<String> type = JsonPath.read(json, "$.where[*].type");
		for(int i=0;i<type.size();i++) {
			if(type.get(i).contains("filter")) {
				List<String> args = JsonPath.read(json, "$.where["+i+"].expression.args[*].value");
				filterregex.add(new FilterRegex(args.get(0),args.get(1)));
			}
		}
	}

	public void parseLimit(Object json){

		JSONObject js = (JSONObject) json;
		if(js.containsKey("limit")){
			Long limit = JsonPath.read(json, "$.limit");
			//System.out.println(limit);
			this.limit=limit.toString();
		}
		
	}
	
		
	public void parsePrefixes(Object json){
		List<String> prefix = JsonPath.read(json, "$.prefixes[*]");
		for(int i=0;i<prefix.size();i++){
			//System.out.println(prefix.get(i));
				prefixes.add(prefix.get(i));
		}
	}
	public Object readJSONQuery(String qs) throws Exception, ParseException {
		//read file

		   JSONParser jsonParser = new JSONParser();
		   Object obj = jsonParser.parse(qs);
	          
	        return obj;
	}
	

	private boolean checkIsURI(String URI) {
		String regexURI = "https?:\\/\\/(www\\.)?[a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&\\/\\/=]*)";
		String uri = parseRegex(URI,regexURI);
		if(uri!=null) {
			return true;
		}else {
			return false;
		}
	}
	
	
	
	public Model loadRegexModel(String regexMeta, String regexOntology) {
		
		Model rmModel = RDFDataMgr.loadModel(regexMeta);
		Model romodel = RDFDataMgr.loadModel(regexOntology);
		
		rmModel.add(romodel);
		//rmModel.write(System.out);
		//System.exit(0);
		return rmModel;
	
	}
	
	public String executeQuery(Model model,String uri) {
		
		String query="PREFIX regex:<http://w3id.org/sepses/vocab/ref/regex#> \r\n"
				+ "SELECT ?rp WHERE { \r\n"
				+ uri+" regex:hasRegexPattern ?rn. \r\n"
				+ "?rn regex:regexPattern ?rp. \r\n}";
		//System.out.println(query);
//		System.exit(0);
		
		String regexPattern=null;
		try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
			ResultSet result = qexec.execSelect();
			while(result.hasNext()) {				
				QuerySolution sol = result.nextSolution();
				regexPattern = sol.getLiteral("rp").toString();
			}
		}
		return regexPattern;
	}
	
	public void lookupRegex(Model model) {
		String regexPattern;
		for(int i=0;i<this.triples.size();i++) {
			//
			//System.out.println(this.triples.get(i).predicate);
			regexPattern = executeQuery(model, this.triples.get(i).predicate);
			if(regexPattern!=null) {
			 this.regexpattern.add(new RegexPattern(this.triples.get(i).predicate, regexPattern, this.triples.get(i).object));
			}
			
		}
	}
	
	
	
	public void printTriples() {
		for(int i = 0;i<this.triples.size();i++) {
		  //System.out.println(this.triples.get(i).subject+" "+this.triples.get(i).predicate+" "+this.triples.get(i).object);
		}
	}
	
	public void printFilterRegex() {
		for(int i = 0;i<this.filterregex.size();i++) {
		  System.out.println(this.filterregex.get(i).variable+" "+this.filterregex.get(i).regex);
		}
	}
	
	public void printRegexPattern() {
		for(int i = 0;i<this.regexpattern.size();i++) {
		  //System.out.println(this.regexpattern.get(i).uri+" "+this.regexpattern.get(i).regexPattern+" "+this.regexpattern.get(i).object);
		}
	}
	
	public static String parseRegex(String logline,String regex) {
    	
    	Pattern pattern = Pattern.compile(regex);
    	Matcher matcher = pattern.matcher(logline);
    	String dt = null;
    	if (matcher.find())
    	{
    	    dt= matcher.group(0);
    	}
		return dt;  
	
    
   }
	public static void main( String[] args ) throws Exception, ParseException  {
		
      String queryString = "experiment/input/query.json";
      String regexMeta =  "experiment/input/regexMeta.ttl";
      String regexOntology =  "experiment/input/regexOntology.ttl";
      String queryStr = new String(Files.readAllBytes(Paths.get(queryString)));
      QueryTranslator qt = new QueryTranslator(queryStr);
      Model m = qt.loadRegexModel(regexMeta, regexOntology);
      qt.parseJSONQuery(m);
      //qt.printTriples();
      //qt.printRegexPattern();
qt.printFilterRegex();
      //qs.executeQuery(m,"<http://purl.org/sepses/vocab/log/authLog#userName>");
      //qs.printPrefixes();
    
     
     

  }
	

}

class Triples {
	String subject,predicate,object;
	Triples(String s, String p, String o){
		this.subject=s;
		this.predicate=p;
		this.object=o;
	}
	
}	

class RegexPattern {
	String uri,regexPattern,object;
	RegexPattern(String uri, String rp, String o){
		this.uri=uri;
		this.regexPattern=rp;
		this.object=o;
	}
	
}

class Prefixes {
	String prefixValue;
	Prefixes( String prefixValue){
		this.prefixValue=prefixValue;
	}
	
}
class grokLabel {
	String grokLabel,paramValue;
	grokLabel(String gl, String pv){
		this.grokLabel=gl;
		this.paramValue=pv;
	}
	
}

class FilterRegex {
	String variable,regex;
	FilterRegex(String variable, String regex){
		this.variable=variable;
		this.regex=regex;
	}
	
}	




