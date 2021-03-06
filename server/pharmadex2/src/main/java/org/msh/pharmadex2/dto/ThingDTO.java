package org.msh.pharmadex2.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.msh.pdex2.dto.table.TableQtb;
import org.msh.pharmadex2.dto.form.AllowValidation;
import org.msh.pharmadex2.dto.form.FormFieldDTO;
import org.msh.pharmadex2.dto.form.OptionDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
/**
 * Responsible for a thing. A thing is a complex object that may consists of literals, dates, checklists, documents, dictionaries, addresses, others things, etc. 
 * @author alexk
 *
 */
@JsonIgnoreProperties(ignoreUnknown=true)
public class ThingDTO extends AllowValidation {
	//Application's data
	private String applicationUrl="";		//URL of the application, required
	private long applDictNodeId=0;			//Id of an application's dictionary node
	private String applName="";				//a name of an application from the dictionary
	private String applDescr="";				//a description of an application from the dictionary
	private long historyId=0;					//id of the current history to determine activityId, nodeId, activity name. Zero means new application
	private boolean application=false;	//load application instead the current activity
	private long activityId=0;					//id of the activity node to determine the executor
	private String activityName="";		//name of an activity to use in on screen forms
	//new amendment related
	 private String prefLabel="";				//prefLable by default
     private long modiUnitId=0;				//ID of data unit to amend
	
	//address of a thing consists of url, parentId, nodeId
	private String url="";						//root url of a thing, i.e. object.business.persons. Mandatory. It identifies things data. The active thing will be placed url-owner-thing
	private int parentIndex=0;				//a index of thing in the path of thing to which this thing is included
	private long parentId=0;					//a node id of the thing to which this thing is included
	private long nodeId=0;						//node it of a thing. If defined - ultimate address of a thing.
	private String varName="";				//variable name in the included thing

	//thing metadata
	private String title="";																													//title of the Thing to use in the breadcrumb
	private String label="";																													//label of a thing. Typically, activity name
	private List<LayoutRowDTO> layout = new ArrayList<LayoutRowDTO>();										//layout on the screen
	private boolean narrowLayout=false;	//by default two columns layout, however may be a one	//special case for some pages
	private Map<String,String> mainLabels = new HashMap<String, String>();										//prefLabel and description labels re-assignment
	private boolean readOnly=false;																									//is read only?																						

	//thing data
	private Map<String, HeadingDTO> heading = new LinkedHashMap<String, HeadingDTO>();																	//simple heading
	private Map<String,FormFieldDTO<String>> strings = new LinkedHashMap<String, FormFieldDTO<String>>();					//strings - text field, no languages 
	private Map<String, FormFieldDTO<String>> literals = new LinkedHashMap<String, FormFieldDTO<String>>();					//text fields
	private Map<String, FormFieldDTO<LocalDate>> dates = new LinkedHashMap<String, FormFieldDTO<LocalDate>>();			//date fields
	private Map<String, FormFieldDTO<Long>> numbers = new LinkedHashMap<String, FormFieldDTO<Long>>();					//numbers, only big decimals
	private Map<String, FormFieldDTO<OptionDTO>> logical = new LinkedHashMap<String, FormFieldDTO<OptionDTO>>();		//Yes,No,NA
	private Map<String,DictionaryDTO> dictionaries = new LinkedHashMap<String, DictionaryDTO>();										//classifiers (dictionaries)
	private Map<String, AddressDTO> addresses = new LinkedHashMap<String, AddressDTO>();												//addresses
	private Map<String,FileDTO> documents = new LinkedHashMap<String, FileDTO>();															//documents (files)
	private Map<String,ResourceDTO> resources = new LinkedHashMap<String, ResourceDTO>();											//documents for upload and templates
	private Map<String, ThingDTO> things = new LinkedHashMap<String, ThingDTO>();															//things included
	private Map<String,PersonDTO> persons = new LinkedHashMap<String, PersonDTO>();														//persons tables
	private Map<String,PersonSelectorDTO> personselector = new LinkedHashMap<String, PersonSelectorDTO>();					//selection from the existing persons
	private Map<String, PersonSpecialDTO> personspec = new LinkedHashMap<String, PersonSpecialDTO>();							//selection for the special persons
	private Map<String,SchedulerDTO> schedulers = new LinkedHashMap<String, SchedulerDTO>();										//with workflow should be run
	private Map<String,RegisterDTO> registers = new LinkedHashMap<String, RegisterDTO>();												//filing system registers
	private Map<String,AmendmentDTO> amendments = new LinkedHashMap<String, AmendmentDTO>();								//ameded data
	private Map<String,AtcDTO> atc = new LinkedHashMap<String, AtcDTO>();																		//atc codes
	private Map<String, LegacyDataDTO> legacy = new LinkedHashMap<String, LegacyDataDTO>();										//list of legacy applications to import
	private Map<String, IntervalDTO> intervals = new LinkedHashMap<String, IntervalDTO>();
	private ActionBarDTO actionBar= new ActionBarDTO();																										//action bar for it @depricated
	
	//The main static path - things that should be filled
	private List<ThingDTO> path = new ArrayList<ThingDTO>();								//all things path
	private int pathIndex=0;																					//the current index in the path		
	
	//auxiliary dynamic path
	private String auxPathVar="";																			//variable name that initiate path rebuild
	private List<ThingDTO> auxPath = new ArrayList<ThingDTO>();						//auxiliary path
	private int auxPathIndex=0;																			//the current index in it
	
	public String getApplicationUrl() {
		return applicationUrl;
	}
	public void setApplicationUrl(String applicationUrl) {
		this.applicationUrl = applicationUrl;
	}

	public long getApplDictNodeId() {
		return applDictNodeId;
	}
	public void setApplDictNodeId(long applDictNodeId) {
		this.applDictNodeId = applDictNodeId;
	}
	public String getApplName() {
		return applName;
	}
	public void setApplName(String applName) {
		this.applName = applName;
	}
	public String getApplDescr() {
		return applDescr;
	}
	public void setApplDescr(String applDescr) {
		this.applDescr = applDescr;
	}
	public long getHistoryId() {
		return historyId;
	}
	public void setHistoryId(long historyId) {
		this.historyId = historyId;
	}
	
	public boolean isApplication() {
		return application;
	}
	public void setApplication(boolean application) {
		this.application = application;
	}
	public long getActivityId() {
		return activityId;
	}
	public void setActivityId(long activityId) {
		this.activityId = activityId;
	}
	public String getActivityName() {
		return activityName;
	}
	public void setActivityName(String activityName) {
		this.activityName = activityName;
	}
	
	public String getPrefLabel() {
		return prefLabel;
	}
	public void setPrefLabel(String prefLabel) {
		this.prefLabel = prefLabel;
	}
	public long getModiUnitId() {
		return modiUnitId;
	}
	public void setModiUnitId(long modiUnitId) {
		this.modiUnitId = modiUnitId;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public int getParentIndex() {
		return parentIndex;
	}
	public void setParentIndex(int parentIndex) {
		this.parentIndex = parentIndex;
	}
	public long getParentId() {
		return parentId;
	}
	public void setParentId(long parentId) {
		this.parentId = parentId;
	}
	public long getNodeId() {
		return nodeId;
	}
	public void setNodeId(long nodeId) {
		this.nodeId = nodeId;
	}
	
	public String getVarName() {
		return varName;
	}
	public void setVarName(String varName) {
		this.varName = varName;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getLabel() {
		return label;
	}
	public void setLabel(String label) {
		this.label = label;
	}

	public List<LayoutRowDTO> getLayout() {
		return layout;
	}
	public void setLayout(List<LayoutRowDTO> layout) {
		this.layout = layout;
	}
	
	public boolean isNarrowLayout() {
		return narrowLayout;
	}
	public void setNarrowLayout(boolean narrowLayout) {
		this.narrowLayout = narrowLayout;
	}
	public Map<String, String> getMainLabels() {
		return mainLabels;
	}
	public void setMainLabels(Map<String, String> mainLabels) {
		this.mainLabels = mainLabels;
	}
	public boolean isReadOnly() {
		return readOnly;
	}
	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}
	
	public Map<String, HeadingDTO> getHeading() {
		return heading;
	}
	public void setHeading(Map<String, HeadingDTO> heading) {
		this.heading = heading;
	}
	public Map<String, FormFieldDTO<String>> getStrings() {
		return strings;
	}
	public void setStrings(Map<String, FormFieldDTO<String>> strings) {
		this.strings = strings;
	}
	public Map<String, FormFieldDTO<String>> getLiterals() {
		return literals;
	}
	public void setLiterals(Map<String, FormFieldDTO<String>> literals) {
		this.literals = literals;
	}
	public Map<String, FormFieldDTO<LocalDate>> getDates() {
		return dates;
	}
	public void setDates(Map<String, FormFieldDTO<LocalDate>> dates) {
		this.dates = dates;
	}
	public Map<String, FormFieldDTO<Long>> getNumbers() {
		return numbers;
	}
	public void setNumbers(Map<String, FormFieldDTO<Long>> numbers) {
		this.numbers = numbers;
	}
	public Map<String, FormFieldDTO<OptionDTO>> getLogical() {
		return logical;
	}
	public void setLogical(Map<String, FormFieldDTO<OptionDTO>> logical) {
		this.logical = logical;
	}
	public Map<String, DictionaryDTO> getDictionaries() {
		return dictionaries;
	}
	public void setDictionaries(Map<String, DictionaryDTO> dictionaries) {
		this.dictionaries = dictionaries;
	}
	public Map<String, AddressDTO> getAddresses() {
		return addresses;
	}
	public void setAddresses(Map<String, AddressDTO> addresses) {
		this.addresses = addresses;
	}
	public Map<String, FileDTO> getDocuments() {
		return documents;
	}
	public void setDocuments(Map<String, FileDTO> documents) {
		this.documents = documents;
	}
	public Map<String, ResourceDTO> getResources() {
		return resources;
	}
	public void setResources(Map<String, ResourceDTO> resources) {
		this.resources = resources;
	}
	public Map<String, ThingDTO> getThings() {
		return things;
	}
	public void setThings(Map<String, ThingDTO> things) {
		this.things = things;
	}
	public Map<String, PersonDTO> getPersons() {
		return persons;
	}
	public void setPersons(Map<String, PersonDTO> persons) {
		this.persons = persons;
	}
	public Map<String, PersonSelectorDTO> getPersonselector() {
		return personselector;
	}
	public void setPersonselector(Map<String, PersonSelectorDTO> personselector) {
		this.personselector = personselector;
	}
	public Map<String, PersonSpecialDTO> getPersonspec() {
		return personspec;
	}
	public void setPersonspec(Map<String, PersonSpecialDTO> personspec) {
		this.personspec = personspec;
	}
	public Map<String, SchedulerDTO> getSchedulers() {
		return schedulers;
	}
	public void setSchedulers(Map<String, SchedulerDTO> schedulers) {
		this.schedulers = schedulers;
	}
	
	public Map<String, RegisterDTO> getRegisters() {
		return registers;
	}
	public void setRegisters(Map<String, RegisterDTO> registers) {
		this.registers = registers;
	}
	public ActionBarDTO getActionBar() {
		return actionBar;
	}
	public void setActionBar(ActionBarDTO actionBar) {
		this.actionBar = actionBar;
	}
	public Map<String, AmendmentDTO> getAmendments() {
		return amendments;
	}
	public void setAmendments(Map<String, AmendmentDTO> amendments) {
		this.amendments = amendments;
	}
	
	public Map<String, AtcDTO> getAtc() {
		return atc;
	}
	public void setAtc(Map<String, AtcDTO> atc) {
		this.atc = atc;
	}
	
	public Map<String, LegacyDataDTO> getLegacy() {
		return legacy;
	}
	public void setLegacy(Map<String, LegacyDataDTO> legacy) {
		this.legacy = legacy;
	}
	public Map<String, IntervalDTO> getIntervals() {
		return intervals;
	}
	public void setIntervals(Map<String, IntervalDTO> intervals) {
		this.intervals = intervals;
	}
	public List<ThingDTO> getPath() {
		return path;
	}
	public void setPath(List<ThingDTO> path) {
		this.path = path;
	}
	
	public int getPathIndex() {
		return pathIndex;
	}
	public void setPathIndex(int pathIndex) {
		this.pathIndex = pathIndex;
	}
	
	public String getAuxPathVar() {
		return auxPathVar;
	}
	public void setAuxPathVar(String auxPathVar) {
		this.auxPathVar = auxPathVar;
	}
	public List<ThingDTO> getAuxPath() {
		return auxPath;
	}
	public int getAuxPathIndex() {
		return auxPathIndex;
	}
	public void setAuxPathIndex(int auxPathIndex) {
		this.auxPathIndex = auxPathIndex;
	}
	public void setAuxPath(List<ThingDTO> auxPath) {
		this.auxPath = auxPath;
	}
	/**
	 * Create thing included to path
	 * @param data
	 * @param asm
	 * @return
	 */
	public static ThingDTO createIncluded(ThingDTO data, AssemblyDTO asm) {
		ThingDTO ret = new ThingDTO();
		ret.setActivityId(data.getActivityId());
		ret.setActivityName(data.getActivityName());
		ret.setApplicationUrl(data.getApplicationUrl());
		ret.setNodeId(0);
		ret.setUrl(asm.getUrl());
		ret.setTitle(asm.getPropertyName());
		ret.setVarName(asm.getPropertyName());
		return ret;
	}
	@Override
	public String toString() {
		return "ThingDTO [applicationUrl=" + applicationUrl + ", activityId=" + activityId + ", activityName="
				+ activityName + ", url=" + url + ", nodeId=" + nodeId + ", title=" + title +  "]";
	}
	
}
