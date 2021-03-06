package org.msh.pharmadex2.service.r2;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.msh.pdex2.dto.table.Headers;
import org.msh.pdex2.dto.table.TableCell;
import org.msh.pdex2.dto.table.TableHeader;
import org.msh.pdex2.dto.table.TableQtb;
import org.msh.pdex2.dto.table.TableRow;
import org.msh.pdex2.exception.ObjectNotFoundException;
import org.msh.pdex2.i18n.Messages;
import org.msh.pdex2.model.r2.Assembly;
import org.msh.pdex2.model.r2.Concept;
import org.msh.pdex2.model.r2.FileResource;
import org.msh.pdex2.model.r2.History;
import org.msh.pdex2.model.r2.Register;
import org.msh.pdex2.model.r2.Scheduler;
import org.msh.pdex2.model.r2.Thing;
import org.msh.pdex2.model.r2.ThingAmendment;
import org.msh.pdex2.model.r2.ThingAtc;
import org.msh.pdex2.model.r2.ThingDict;
import org.msh.pdex2.model.r2.ThingDoc;
import org.msh.pdex2.model.r2.ThingLegacyData;
import org.msh.pdex2.model.r2.ThingPerson;
import org.msh.pdex2.model.r2.ThingRegister;
import org.msh.pdex2.model.r2.ThingScheduler;
import org.msh.pdex2.model.r2.ThingThing;
import org.msh.pdex2.repository.common.JdbcRepository;
import org.msh.pdex2.repository.r2.FileResourceRepo;
import org.msh.pdex2.repository.r2.HistoryRepo;
import org.msh.pdex2.repository.r2.ThingRepo;
import org.msh.pdex2.services.r2.ClosureService;
import org.msh.pharmadex2.dto.AddressDTO;
import org.msh.pharmadex2.dto.AddressValuesDTO;
import org.msh.pharmadex2.dto.AmendmentDTO;
import org.msh.pharmadex2.dto.AssemblyDTO;
import org.msh.pharmadex2.dto.AtcDTO;
import org.msh.pharmadex2.dto.DictValuesDTO;
import org.msh.pharmadex2.dto.DictionaryDTO;
import org.msh.pharmadex2.dto.FileDTO;
import org.msh.pharmadex2.dto.HeadingDTO;
import org.msh.pharmadex2.dto.IntervalDTO;
import org.msh.pharmadex2.dto.LegacyDataDTO;
import org.msh.pharmadex2.dto.PersonDTO;
import org.msh.pharmadex2.dto.PersonSelectorDTO;
import org.msh.pharmadex2.dto.PersonSpecialDTO;
import org.msh.pharmadex2.dto.RegisterDTO;
import org.msh.pharmadex2.dto.ResourceDTO;
import org.msh.pharmadex2.dto.SchedulerDTO;
import org.msh.pharmadex2.dto.ThingDTO;
import org.msh.pharmadex2.dto.ThingValuesDTO;
import org.msh.pharmadex2.dto.WorkflowParamDTO;
import org.msh.pharmadex2.dto.auth.UserDetailsDTO;
import org.msh.pharmadex2.dto.form.FormFieldDTO;
import org.msh.pharmadex2.dto.form.OptionDTO;
import org.msh.pharmadex2.service.common.BoilerService;
import org.msh.pharmadex2.service.common.DtoService;
import org.msh.pharmadex2.service.common.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * CRUD for any activity
 * @author alexk
 *
 */
@Service
public class ThingService {
	private static final Logger logger = LoggerFactory.getLogger(ThingService.class);
	@Autowired
	private ClosureService closureServ;
	@Autowired
	private LiteralService literalServ;
	@Autowired
	private AssemblyService assemblyServ;
	@Autowired
	private DictService dictServ;
	@Autowired
	private BoilerService boilerServ;
	@Autowired
	private Messages messages;
	@Autowired
	private AccessControlService accessControlServ;
	@Autowired
	private ValidationService validServ;
	@Autowired
	private FollowUpService followUpServ;
	@Autowired
	private ThingRepo thingRepo;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private DtoService dtoServ;
	@Autowired
	private JdbcRepository jdbcRepo;
	@Autowired
	private FileResourceRepo fileRepo;
	@Autowired
	private HistoryRepo historyRepo;
	@Autowired
	private ResourceService resourceServ;
	@Autowired
	private AmendmentService amendServ;
	@Autowired
	private LegacyDataService legacyServ;
	@Autowired
	private AtcInnExcService atcInnExcServ;
	@Autowired
	private RegisterService registerServ;
	@PersistenceContext
	EntityManager entityManager;

	/**
	 * Create a new thing
	 * @param data
	 * @param user
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ThingDTO createThing(ThingDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		if(accessControlServ.createAllowed(data, user)) {
			//let's start
			//data.setReadOnly(false);
			if(data.getVarName().length()==0) {
				data.setTitle(messages.get("RegState.NEW_APPL"));
			}else {
				data.setTitle(messages.get(data.getVarName()));
			}
			data = createContent(data,user);
			if(data.getModiUnitId()>0) {
				//amendment
				FormFieldDTO<String> pref = data.getLiterals().get("prefLabel");
				if(pref != null && pref.isReadOnly()) {
					FormFieldDTO<String> pref1= FormFieldDTO.of(data.getPrefLabel());
					pref1.setReadOnly(true);
					data.getLiterals().put("prefLabel",pref1);
				}
			}
		}else {
			throw new ObjectNotFoundException("User is not allowed to initiate application. User is "+user.getEmail(),logger);
		}
		return data;
	}
	/**
	 * Create all content in thing DTO
	 * @param data
	 * @param user 
	 * @return
	 * @throws ObjectNotFoundException
	 */
	@Transactional
	public ThingDTO createContent(ThingDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		//logger.trace("START CONTENT");
		List<Assembly> assemblies =assemblyServ.loadDataConfiguration(data.getUrl());
		//literals
		List<AssemblyDTO> headings = assemblyServ.auxHeadings(data.getUrl(),assemblies);
		data.getHeading().clear();
		for(AssemblyDTO head : headings) {
			HeadingDTO dto = new HeadingDTO();
			dto.setValue(messages.get(head.getPropertyName()));
			dto.setUrl(head.getUrl());
			data.getHeading().put(head.getPropertyName(), dto);
		}
		//logger.trace("literals{");
		List<String> exts=boilerServ.variablesExtensions(data);

		data.getStrings().clear();
		List<AssemblyDTO> strings = assemblyServ.auxStrings(data.getUrl(),assemblies);
		data=dtoServ.createStrings(data,strings);

		List<AssemblyDTO> literals = assemblyServ.auxLiterals(data.getUrl());
		data=dtoServ.createLiterals(data, literals);
		//dates
		List<AssemblyDTO> dates=assemblyServ.auxDates(data.getUrl(),assemblies);
		data=dtoServ.createDates(data, dates);
		//numbers
		List<AssemblyDTO> numbers=assemblyServ.auxNumbers(data.getUrl(),assemblies);
		data=dtoServ.createNumbers(data,numbers);
		//logical
		List<AssemblyDTO> logicals=assemblyServ.auxLogicals(data.getUrl(),assemblies);
		data=dtoServ.createLogicals(data,logicals);
		//logger.trace("}");
		//dictionaries
		List<AssemblyDTO> dictionaries = assemblyServ.auxDictionaries(data.getUrl(),assemblies);
		//logger.trace("dictionaries{");
		data=createDictonaries(data, dictionaries);
		//logger.trace("}");
		//addresses
		List<AssemblyDTO> addresses = assemblyServ.auxAddresses(data.getUrl(),assemblies);
		//logger.trace("addresses{");
		data=createAddresses(data, addresses);
		//logger.trace("}");
		//files
		List<AssemblyDTO> documents = assemblyServ.auxDocuments(data.getUrl(),assemblies);
		//logger.trace("documents{");
		data=createDocuments(documents, data,user);
		//logger.trace("}");
		//resources
		List<AssemblyDTO> resources = assemblyServ.auxResources(data.getUrl(),assemblies);
		data=createResources(resources,data);
		//things
		List<AssemblyDTO> things = assemblyServ.auxThings(data.getUrl(),assemblies);
		data=createThings(things,data);
		//persons
		List<AssemblyDTO> persons =assemblyServ.auxPersons(data.getUrl(),assemblies);
		data=createPersons(persons, data);
		//person selectors
		//List<AssemblyDTO> personselectors = assemblyServ.auxPersonSelector(data.getUrl());
		//data=createPersonSelectors(personselectors,data);
		//List<AssemblyDTO> personspecial = assemblyServ.auxPersonSpecials(data.getUrl());
		//data = createPersonSpecial(personspecial, data);
		//Schedulers
		List<AssemblyDTO> schedulers = assemblyServ.auxSchedulers(data.getUrl(),assemblies);
		data=createSchedulers(schedulers, data);
		//Registers
		//logger.trace("registers{");
		List<AssemblyDTO> registers = assemblyServ.auxRegisters(data.getUrl(),assemblies);
		data=registerServ.createRegisters(registers,data);
		//logger.trace("}");
		//Amendments
		//List<AssemblyDTO> amendments = assemblyServ.auxAmendments(data.getUrl());
		//data=createAmendments(amendments,data,user);
		//ATC codes
		List<AssemblyDTO> atc = assemblyServ.auxAtc(data.getUrl(),assemblies);
		data=createAtc(atc,data);
		//Legacy data
		List<AssemblyDTO> legacy = assemblyServ.auxLegacyData(data.getUrl(), assemblies);
		data=createLegacy(legacy, data);
		//Intervals
		List<AssemblyDTO> intervals = assemblyServ.auxIntervals(data,"intervals");
		data=createIntervals(intervals, data);
		//logger.info("CONTENT done intervals");
		//layout
		data=createLayout(data);
		//main labels rewrite
		data.getMainLabels().clear();
		data.getMainLabels().putAll(assemblyServ.mainLabelsByUrl(data.getUrl()));
		//logger.trace("END content");
		return data;
	}
	/**
	 * Create date intervals DTO
	 * @param intervals
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private ThingDTO createIntervals(List<AssemblyDTO> intervals, ThingDTO data) throws ObjectNotFoundException {
		data.getIntervals().clear();
		for(AssemblyDTO interval : intervals) {
			IntervalDTO dto = new IntervalDTO();
			dto.setVarname(interval.getPropertyName());
			data.getIntervals().put(interval.getPropertyName(), dto);
		}
		if(data.getNodeId()!=0) {
			//read data from the node
			Concept node = closureServ.loadConceptById(data.getNodeId());
			for(AssemblyDTO interval :intervals) {
				IntervalDTO dto = data.getIntervals().get(interval.getPropertyName());
				LocalDate from=dtoServ.readDate(node, interval.getPropertyName()+"_from");
				LocalDate to=dtoServ.readDate(node, interval.getPropertyName()+"_to");
				dto.getFrom().setValue(from);
				dto.getTo().setValue(to);
			}


		}
		return data;
	}
	/**
	 * Create the legacy data component
	 * @param legacy
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private ThingDTO createLegacy(List<AssemblyDTO> legacy, ThingDTO data) throws ObjectNotFoundException {
		data.getLegacy().clear();
		for(AssemblyDTO assm : legacy) {
			data.getLegacy().put(assm.getPropertyName(),legacyServ.create(assm));
		}
		if(data.getLegacy().size()>0) {
			if(data.getNodeId()>0) {
				Concept node = closureServ.loadConceptById(data.getNodeId());
				Thing thing = boilerServ.thingByNode(node);
				for(ThingLegacyData tdl : thing.getLegacyData()) {
					LegacyDataDTO dto = data.getLegacy().get(tdl.getVarName());
					dto=legacyServ.load(tdl.getConcept(), dto);
				}
			}
		}
		return data;
	}
	/**
	 * Create ATC codes lookup table and load selected codes 
	 * @param atc
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private ThingDTO createAtc(List<AssemblyDTO> atcs, ThingDTO data) throws ObjectNotFoundException {
		data.getAtc().clear();
		for(AssemblyDTO atc : atcs) {
			AtcDTO dto = new AtcDTO();				//only one is possibl
			dto.setUrl(atc.getUrl());
			dto.setVarName(atc.getPropertyName());
			dto.setReadOnly(atc.isReadOnly());
			dto=atcInnExcServ.createAtc(dto, data);
			data.getAtc().put(dto.getVarName(),dto);
		}

		return data;
	}
	/**
	 * Create data for amendment component, i.e. a pointer to concept (thing) amended by this
	 * @deprecated
	 * @param amendments
	 * @param data
	 * @param user 
	 * @return
	 * @throws ObjectNotFoundException
	 *  
	 */
	@Transactional
	private ThingDTO createAmendments(List<AssemblyDTO> amendments, ThingDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		//read a configuration
		data.getAmendments().clear();
		AmendmentDTO dto = new AmendmentDTO();	//only one is possible
		for(AssemblyDTO asm : amendments) {
			dto.setUrl(asm.getUrl());
			dto.setVarName(asm.getPropertyName());
			dto.setPattern(asm.getFileTypes());
			data.getAmendments().put(dto.getVarName(),dto);
			break;		//only one is allowed
		}
		//load a concept if one
		if(data.getNodeId()>0) {
			Concept node = closureServ.loadConceptById(data.getNodeId());
			Thing thing = new Thing();
			thing = boilerServ.thingByNode(node, thing);
			for(ThingAmendment ta :thing.getAmendments()) {
				if(dto.getUrl().equalsIgnoreCase(ta.getUrl())) {
					dto.setDataNodeId(ta.getConcept().getID());
				}
				break;										//only one is possible
			}
		}
		return data;
	}
	/**
	 * Create special person
	 * @param personspecial
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException
	 */
	@Transactional
	private ThingDTO createPersonSpecial(List<AssemblyDTO> personspecial, ThingDTO data) throws ObjectNotFoundException {
		//it is possible only one Special Person for a thing
		if(personspecial.size()==1) {
			AssemblyDTO adto = personspecial.get(0);
			PersonSpecialDTO ps = new PersonSpecialDTO();
			if(data.getPersonspec().size()==1) {
				ps=data.getPersonspec().get(adto.getPropertyName());
			}
			if(ps!=null) {
				ps.setVarName(adto.getPropertyName());				//variable for it
				//object's data
				ps.setParentId(data.getParentId());
				//person data
				ps.setPersonDataId(data.getNodeId());
				//url of the unit of person's data. The root unit are allowed
				ps.setPresonDataUrl(data.getUrl());
				//restrict person's selection to this root person url, empty means all!
				ps.setRestrictByURl(adto.getDictUrl());
				//this component data 						
				ps.setNodeUrl(adto.getAuxDataUrl());
				data.getPersonspec().put(adto.getPropertyName(), ps);
				//load it
				ps=personList(ps);
				//mark it
				if(data.getNodeId()>0) {
					Concept pconc= closureServ.loadConceptById(data.getNodeId());
					if(pconc.getLabel() != null) {
						String persIdStr= pconc.getLabel();
						try {
							Long persId= new Long(persIdStr);
							Concept pers = closureServ.loadConceptById(persId);
							ps.setSelectedName(literalServ.readPrefLabel(pers));
						} catch (NumberFormatException e) {
							//nothing to do
						}
					}
					for(TableRow row : ps.getTable().getRows()) {
						Concept node = closureServ.loadConceptById(row.getDbID());
						if(conceptBelongToNode(node, pconc)) {
							row.setSelected(true);
						}
					}
				}
			}
		}
		return data;
	}
	/**
	 * Concept is belong to node if this concept is node itself or related thing 
	 * @param node
	 * @param pconc
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private boolean conceptBelongToNode(Concept node, Concept pconc) throws ObjectNotFoundException {
		if(node.getID()==pconc.getID()) {
			return true;
		}
		Thing th = boilerServ.thingByNode(node);
		for(ThingThing tt : th.getThings()) {
			if(tt.getConcept().getID()==pconc.getID()) {
				return true;
			}
		}
		return false;
	}
	/**
	 * Load a list of all persons in this application data
	 * @param data
	 * @return
	 */
	public PersonSpecialDTO personList(PersonSpecialDTO data) {
		TableQtb table = data.getTable();
		table.getHeaders().getHeaders().clear();
		table.getHeaders().getHeaders().add(TableHeader.instanceOf(
				"pref",
				"prefLabel",
				true,
				false,
				false,
				TableHeader.COLUMN_LINK,
				0));
		jdbcRepo.persons_application(data.getParentId());
		long selected = 0;
		for(TableRow row : table.getRows()) {
			if (row.getSelected()) {
				selected = row.getDbID();
			}
		}
		String where="";
		if(data.getRestrictByURl().length()>0) {
			where="url='"+data.getRestrictByURl()+"'";
		}
		List<TableRow> rows = jdbcRepo.qtbGroupReport("select * from persons_application", "", where, table.getHeaders());
		TableQtb.tablePage(rows, table);
		for(TableRow row : table.getRows()) {
			row.setSelected(row.getDbID()==selected);
		}
		table.setHeaders(boilerServ.translateHeaders(table.getHeaders()));
		return data;
	}

	/**
	 * Create or load schedulers
	 * @param schedulers
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ThingDTO createSchedulers(List<AssemblyDTO> schedulers, ThingDTO data) throws ObjectNotFoundException {
		//prepare data
		data.getSchedulers().clear();
		//load empty
		for(AssemblyDTO ad : schedulers) {
			SchedulerDTO sc = new SchedulerDTO();
			sc.setNodeId(data.getNodeId());
			sc.setVarName(ad.getPropertyName());
			sc.setDataUrl(ad.getUrl());
			sc.setProcessUrl(ad.getAuxDataUrl());
			sc.setCreatedAt(LocalDate.now());
			sc.getSchedule().setValue(LocalDate.now().plusMonths(ad.getMax().intValue()));
			data.getSchedulers().put(ad.getPropertyName(), sc);
		}
		if(data.getSchedulers().size()>0) {
			//thing, history, node, application data
			Thing thing=new Thing();
			History his = new History();
			Concept applData = new Concept();
			if(data.getHistoryId()>0) {
				his=boilerServ.historyById(data.getHistoryId());
				applData = amendServ.initialApplicationData(his.getApplicationData());
			}
			if(data.getNodeId()>0) {
				Concept node = closureServ.loadConceptById(data.getNodeId());
				thing = boilerServ.thingByNode(node);
			}

			//resolve, if it is possible
			for(String key : data.getSchedulers().keySet()) {
				SchedulerDTO sdto = data.getSchedulers().get(key);
				if(thing.getID()>0) {
					sdto = followUpServ.schedulerFromThing(thing,sdto);
				}
				if(followUpServ.isEmpty(sdto)) {
					if(applData.getID()>0) {
						sdto=followUpServ.schedulerFromSchedulers(applData, sdto.getDataUrl(),sdto);
					}
				}
			}
		}
		return data;
	}

	/**
	 * Load person lists using main data node and person data url 
	 * @param personselectors 
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private ThingDTO createPersonSelectors(List<AssemblyDTO> personselectors, ThingDTO data) throws ObjectNotFoundException {
		data.getPersonselector().clear();
		for(AssemblyDTO ad : personselectors) {
			PersonSelectorDTO dto = new PersonSelectorDTO();
			dto.setHistoryId(data.getHistoryId());
			dto.setPersonUrl(ad.getUrl());
			dto=amendServ.personSelectorTable(dto);										//the amended data has precedence
			if(dto.getTable().getRows().size()==0) {
				dto=resourceServ.personSelectorTable(dto);								//not found in amended data
			}
			List<Long> selected = boilerServ.saveSelectedRows(dto.getTable());
			if(selected.size()==0) {
				if(dto.getTable().getRows().size()>0) {
					dto.getTable().getRows().get(0).setSelected(true);			//for lazy testers
				}
			}
			data.getPersonselector().put(ad.getPropertyName(),dto);
		}
		return data;
	}
	/**
	 * Create resources table
	 * Headers are the same as for documents table
	 * @param resources
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private ThingDTO createResources(List<AssemblyDTO> resources, ThingDTO data) throws ObjectNotFoundException {
		data.getResources().clear();
		for(AssemblyDTO res : resources) {
			ResourceDTO resDto = new ResourceDTO();
			resDto.setHistoryId(data.getHistoryId());					//link to all application data
			resDto.getTable().setHeaders(createDocTableHeaders(resDto.getTable().getHeaders()));
			resDto =  resourceServ.table(res,resDto);
			data.getResources().put(res.getPropertyName(),resDto);
		}
		return data;
	}
	/**
	 *Create person records
	 * @param persons
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private ThingDTO createPersons(List<AssemblyDTO> persons, ThingDTO data) throws ObjectNotFoundException {
		if(persons.size()>0) {
			data.getPersons().clear();
			for(AssemblyDTO pers : persons) {
				PersonDTO dto = new PersonDTO();
				dto.setDictUrl(pers.getDictUrl());
				dto.setUrl(pers.getAuxDataUrl());
				dto.setReadOnly(pers.isReadOnly());
				dto.setRequired(pers.isRequired());
				dto.setVarName(pers.getPropertyName());
				dto.setThingNodeId(data.getNodeId());
				dto.setAmendedNodeId(data.getModiUnitId());
				dto =createPersTable(dto, data.isReadOnly());
				data.getPersons().put(pers.getPropertyName(),dto);
			}
			data=amendServ.personToRemove(data);
		}
		return data;
	}
	/**
	 * Create a table with persons
	 * @param data
	 * @param dto 
	 * @param readOnly 
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private PersonDTO createPersTable(PersonDTO dto, boolean readOnly) throws ObjectNotFoundException {
		//list of persons to add
		if(dto.getTable().getHeaders().getHeaders().size()==0) {
			dto.getTable().setHeaders(personHeaders(dto.getTable().getHeaders()));
		}
		if(dto.getThingNodeId()>0) {
			jdbcRepo.persons(dto.getThingNodeId());
			List<TableRow> rows = jdbcRepo.qtbGroupReport("select * from _persons", "","", dto.getTable().getHeaders());
			TableQtb.tablePage(rows, dto.getTable());
			dto.getTable().setSelectable(!(readOnly || dto.isReadOnly()));
		}
		//List of persons to remove
		if(dto.getAmendedNodeId()>0) {
			dto=createToRemoveTable(dto);
		}
		return dto;
	}
	/**
	 * Create headers for person table
	 * @param headers
	 * @return
	 */
	private Headers personHeaders(Headers headers) {
		headers.getHeaders().add((TableHeader.instanceOf(
				"pref",
				"global_name",
				true,
				true,
				true,
				TableHeader.COLUMN_LINK,
				0)));
		headers=boilerServ.translateHeaders(headers);
		headers.getHeaders().get(0).setSort(true);
		headers.getHeaders().get(0).setSortValue(TableHeader.SORT_ASC);
		return headers;
	}
	/**
	 * Create addresses
	 * @param data
	 * @param addresses
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private ThingDTO createAddresses(ThingDTO data, List<AssemblyDTO> addresses) throws ObjectNotFoundException {
		data.getAddresses().clear();
		if(addresses.size()>0) {
			Concept thingNode = new Concept();
			Thing thing = new Thing();
			if(data.getNodeId()>0) {
				thingNode=closureServ.loadConceptById(data.getNodeId());
				thing = boilerServ.thingByNode(thingNode,thing);
			}
			for(AssemblyDTO addr: addresses) {
				data.getAddresses().put(addr.getPropertyName(), createAddress(data, thing, addr));
			}
		}
		return data;
	}
	/**
	 * Create an address DTO from the configuration and, possible, data stored in things in a tree
	 * @param data 
	 * @param node Thing's node, maybe null
	 * @param configuration data
	 * @return
	 * @throws ObjectNotFoundException
	 */
	@Transactional
	public AddressDTO createAddress(ThingDTO data, Thing thing, AssemblyDTO assm) throws ObjectNotFoundException {
		AddressDTO addr = new AddressDTO();
		addr.setVarName(assm.getPropertyName());
		addr.setUrl(assm.getUrl());
		addr.getDictionary().setUrl(assemblyServ.adminUnitsDict());
		addr.getDictionary().getPrevSelected().clear();
		addr.getDictionary().setReadOnly(data.isReadOnly());
		addr.getDictionary().setSelectedOnly(data.isReadOnly());
		for(ThingThing th : thing.getThings()) {
			if(th.getUrl().equalsIgnoreCase(addr.getUrl())
					&& th.getVarname().equalsIgnoreCase(addr.getVarName())) {
				Concept addrNode= th.getConcept();
				Thing addrThing = new Thing();
				addrThing = boilerServ.thingByNode(addrNode, addrThing);
				if(addrThing.getDictionaries().size()>0){
					ThingDict td = addrThing.getDictionaries().iterator().next();
					addr.getDictionary().getPrevSelected().add(td.getConcept().getID());
				}
				String loc = addrNode.getLabel();
				if(loc != null && loc.length() > 0) {
					addr.setMarker(dtoServ.createLocationDTO(loc));
				}
				addr.setNodeId(th.getConcept().getID());
			}
		}
		logger.trace("addresses(createDi){");
		addr.setDictionary(dictServ.createDictionary(addr.getDictionary()));
		logger.trace("}");
		dictServ.loadHomeLocation(addr);
		return addr;
	}
	/**
	 * Create things DTO for this thing DTO
	 * Try to read ones if existing
	 * @param asms - assemblyDTOs
	 * @param data supposed that data.url is defined
	 * @TODO creation table of things!!!!!
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private ThingDTO createThings(List<AssemblyDTO> asms, ThingDTO data) throws ObjectNotFoundException {
		data.getThings().clear();
		for(AssemblyDTO asm: asms) {
			ThingDTO dto = ThingDTO.createIncluded(data, asm);
			dto.setTitle(messages.get(asm.getPropertyName()));
			dto.setVarName(asm.getPropertyName());
			dto.setApplicationUrl(data.getApplicationUrl());
			data.getThings().put(asm.getPropertyName(), dto);
		}
		Thing thing = new Thing();
		if(data.getNodeId()>0) {
			Concept node=closureServ.loadConceptById(data.getNodeId());
			thing = boilerServ.thingByNode(node,thing);
		}
		for(ThingThing th : thing.getThings()) {
			Set<String> keys = data.getThings().keySet();
			for(String key : keys) {
				if(th.getUrl().equalsIgnoreCase(data.getThings().get(key).getUrl()) 
						&& th.getVarname().equalsIgnoreCase(key)) {
					data.getThings().get(key).setNodeId(th.getConcept().getID());
					data.getThings().get(key).setVarName(key);
				}
			}
		}
		return data;
	}


	/**
	 * Prepare files objects
	 * @param files
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private ThingDTO createDocuments(List<AssemblyDTO> files, ThingDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		data.getDocuments().clear();
		//prepare files
		for(AssemblyDTO asm : files) {
			FileDTO fdto=new FileDTO();
			fdto.setAccept(asm.getFileTypes());
			fdto.setReadOnly(data.isReadOnly());
			fdto.setUrl(asm.getUrl());
			fdto.setDictUrl(asm.getDictUrl());
			fdto.setVarName(asm.getPropertyName());
			fdto.setThingUrl(data.getUrl());
			fdto.setThingNodeId(data.getNodeId());
			fdto = removeOrphans(fdto);
			fdto = createDocTable(fdto,user);
			fdto=createDocUploaded(fdto,user);
			data.getDocuments().put(asm.getPropertyName(),fdto);
		}
		return data;
	}
	/**
	 * Remove files, concepts and ThingDocs for files uploaded for this url, variable, but with null thing
	 * @param fdto
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private FileDTO removeOrphans(FileDTO fdto) throws ObjectNotFoundException {
		List<ThingDoc> tds = boilerServ.thingDocsByUrl(fdto.getUrl());
		for(ThingDoc td : tds) {
			if(td.getVarName().equalsIgnoreCase(fdto.getVarName())) {
				Thing thing = boilerServ.thingByThingDoc(td,false);
				if(thing.getID()==0) {
					closureServ.removeNode(td.getConcept());
					break;
				}
			}
		}
		return fdto;
	}
	/**
	 * create list of uploaded 
	 * @param data
	 * @param fdto
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private FileDTO createDocUploaded(FileDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		data.getLinked().clear();
		if(data.getThingNodeId()>0) {
			//take from linked
			Concept thingConc = closureServ.loadConceptById(data.getThingNodeId());
			Thing thing = new Thing();
			thing = boilerServ.thingByNode(thingConc, thing);
			for(ThingDoc td : thing.getDocuments()) {
				if(data.getVarName().equalsIgnoreCase(td.getVarName())) {
					long dictItemId = td.getDictNode().getID();
					long fileNodeId = td.getConcept().getID();
					data.getLinked().put(dictItemId,fileNodeId);
				}
			}
		}else {
			//take from the database
			Headers th = new Headers();
			Concept dictRoot = closureServ.loadRoot(data.getDictUrl());
			th.getHeaders().add(TableHeader.instanceOf("ID", TableHeader.COLUMN_LONG));	//dictNodeId
			th.getHeaders().add(TableHeader.instanceOf("nodeId", TableHeader.COLUMN_LONG)); //nodeId
			jdbcRepo.prepareFileList(dictRoot.getID(),0,data.getUrl(), data.getVarName(), user.getEmail());
			List<TableRow> rows = jdbcRepo.qtbGroupReport("select * from _filelist", "", "", th);
			for(TableRow row : rows) {
				Long dictNodeId = (Long) row.getRow().get(0).getOriginalValue();
				Long fileNodeId = (Long) row.getRow().get(1).getOriginalValue();
				data.getLinked().put(dictNodeId, fileNodeId);
			}
		}
		return data;
	}
	/**
	 * create a table with list of documents
	 * @param fdto
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private FileDTO createDocTable(FileDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		if(data.getTable().getHeaders().getHeaders().size()==0) {
			data.getTable().setHeaders(createDocTableHeaders(data.getTable().getHeaders()));
		}
		Concept dict = closureServ.loadRoot(data.getDictUrl());
		Concept node = new Concept();
		if(data.getThingNodeId()>0) {
			node=closureServ.loadConceptById(data.getThingNodeId());
		}
		Thing thing = new Thing();
		thing = boilerServ.thingByNode(node, thing);
		String email="";
		jdbcRepo.prepareFileList(dict.getID(),thing.getID(),data.getUrl(), data.getVarName(), email);
		List<TableRow> rows = jdbcRepo.qtbGroupReport("select * from _filelist", "","", data.getTable().getHeaders());
		TableQtb.tablePage(rows, data.getTable());
		for(TableRow row : rows) {
			TableCell cell = row.getCellByKey("filename");
			if(cell.getOriginalValue()==null || cell.getValue().length()==0) {
				if(data.isReadOnly()) {
					cell.setValue("");
					cell.setOriginalValue("");
				}else {
					cell.setValue(messages.get("upload_file"));
					cell.setOriginalValue(messages.get("upload_file"));
				}
			}
		}
		boilerServ.translateRows(data.getTable());
		data.getTable().setSelectable(false);
		return data;
	}

	/**
	 * Document's table
	 * @param headers
	 * @return
	 */
	private Headers createDocTableHeaders(Headers headers) {
		headers.getHeaders().clear();
		headers.getHeaders().add(TableHeader.instanceOf(
				"filename",
				"filename",
				true,
				true,
				true,
				TableHeader.COLUMN_I18LINK,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"pref",
				"global_name",
				true,
				true,
				true,
				TableHeader.COLUMN_STRING,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"description",
				"description",
				true,
				true,
				true,
				TableHeader.COLUMN_STRING,
				0));
		headers=boilerServ.translateHeaders(headers);
		headers.getHeaders().get(1).setSortValue(TableHeader.SORT_ASC);
		return headers;
	}
	/**
	 * Ask configuration for form layout
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private ThingDTO createLayout(ThingDTO data) throws ObjectNotFoundException {
		data.getLayout().clear();
		data.getLayout().addAll(assemblyServ.formLayout(data.getUrl()));
		return data;
	}

	/**
	 * Create a set of dictionaries in the application's activity
	 * @param data - application's activity
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private ThingDTO createDictonaries(ThingDTO data, List<AssemblyDTO> dictas) throws ObjectNotFoundException {
		data.getDictionaries().clear();
		//restore dictionaries
		for(AssemblyDTO dicta : dictas) {
			DictionaryDTO dict = new DictionaryDTO();
			dict.setUrl(dicta.getUrl());
			dict.setVarName(dicta.getPropertyName());
			dict.setRequired(dicta.isRequired());
			dict.setMult(dicta.isMult());
			dict.setReadOnly(data.isReadOnly());
			dict.setSelectedOnly(data.isReadOnly());
			data.getDictionaries().put(dicta.getPropertyName(), dict);
		}
		if(dictas.size()>0) {
			//restore selections if ones
			if(data.getNodeId()>0) {
				Concept node = closureServ.loadConceptById(data.getNodeId());
				Thing thing = new Thing();
				thing= boilerServ.thingByNode(node,thing);
				for(ThingDict adict :thing.getDictionaries()) {
					for(String key :data.getDictionaries().keySet()) {
						DictionaryDTO dict = data.getDictionaries().get(key);
						if(adict.getUrl().equalsIgnoreCase(dict.getUrl())
								&& adict.getVarname().equalsIgnoreCase(dict.getVarName())) {
							dict.getPrevSelected().add(adict.getConcept().getID());
						}
					}
				}
			}
			//load data
			for(String key : data.getDictionaries().keySet()) {
				data.getDictionaries().put(key, dictServ.createDictionary(data.getDictionaries().get(key)));
			}
		}
		return data;
	}

	/**
	 * Save a thing
	 * @TODO should be reconsidered and compared with ApplServer.thingSaveUnderPArent
	 * @param data
	 * @param user
	 * @return
	 * @throws ObjectNotFoundException 
	 * @throws JsonProcessingException 
	 */
	@Transactional
	public ThingDTO save(ThingDTO data, UserDetailsDTO user) throws ObjectNotFoundException, JsonProcessingException {
		data = validServ.thing(data,true);
		if(data.isValid() || !data.isStrict()) {
			data.setStrict(true);									//to ensure the next
			if(accessControlServ.writeAllowed(data, user)) {
				data = checkUrlChange(data);
				Concept node = new Concept();
				if(data.getNodeId()==0) {
					node = createNode(data.getUrl(), user.getEmail());
				}else {
					node=closureServ.loadConceptById(data.getNodeId());
				}
				data.setNodeId(node.getID());
				//thing
				Thing thing = new Thing();
				thing = boilerServ.thingByNode(node, thing);
				thing.setConcept(node);
				thing.setUrl(data.getUrl());

				//store data under the node and thing
				data = storeDataUnderThing(data, user, node, thing);
				//check amend
				if(data.getModiUnitId()>0) {
					boolean found=false;
					for(ThingAmendment ta : thing.getAmendments()) {
						found=ta.getConcept().getID()==data.getModiUnitId();
					}
					if(!found) {
						ThingAmendment ta=new ThingAmendment();
						Concept modiConcept = closureServ.loadConceptById(data.getModiUnitId());
						ta.setConcept(modiConcept);
						ta.setUrl("amendment");
						ta.setVarName("amendment");
						ta.setApplicationData(amendServ.amendmentApplicationByAmendmentUnit(modiConcept));
						thing.getAmendments().clear();
						thing.getAmendments().add(ta);
					}
				}
				thing = thingRepo.save(thing);

				/////////////////// Store data, end /////////////////////////////

				//application and activity
				if(data.getHistoryId()==0 && data.getParentId()==0) {
					if(data.getParentIndex()==-1) {
						data=createApplication(data,user.getEmail(),node);
					}
				}

				//person special may change parent ID
				data = storePersonSpecial(data, thing);
				//attach to the parent auxiliary things
				if(data.getParentId()>0) {
					Concept incl=closureServ.loadConceptById(data.getParentId());
					Concept email=closureServ.getParent(incl);
					Concept root = closureServ.getParent(email);
					List<Assembly> assemblies =assemblyServ.loadDataConfiguration(root.getIdentifier());
					List<AssemblyDTO> things = assemblyServ.auxThings(root.getIdentifier(),assemblies);
					List<AssemblyDTO> persons = assemblyServ.auxPersons(root.getIdentifier(),assemblies);
					Thing inclThing = boilerServ.thingByNode(incl);
					for(AssemblyDTO th :things) {
						if(th.getPropertyName().equalsIgnoreCase(data.getVarName())) {
							saveToThings(data, node, inclThing);
							break;
						}
					}
					for(AssemblyDTO th :persons) {
						if(th.getPropertyName().equalsIgnoreCase(data.getVarName())) {
							saveToPersons(data, node, inclThing);
							break;
						}
					}
				}
				if(data.getHistoryId()>0) {
					for(ThingDTO dto : data.getPath()) {
						dto.setHistoryId(data.getHistoryId());
						dto.setActivityId(data.getActivityId());
					}
					History his = boilerServ.historyById(data.getHistoryId());
					if(his.getDataUrl()!=null) {
						if(data.getUrl().equalsIgnoreCase(his.getDataUrl())) {
							his.setActivityData(node);
							boilerServ.saveHistory(his);
						}
					}
				}
			}else {
				throw new ObjectNotFoundException("Write access denied. URL "+data.getApplicationUrl() +" user "+user.getEmail());
			}
		}
		//title
		FormFieldDTO<String> lit = data.getLiterals().get("prefLabel");
		FormFieldDTO<String> str=data.getStrings().get("prefLabel");
		String title = data.getTitle();
		if(str!=null) {
			if(str.getValue()!=null && str.getValue().length()>0) {
				title=str.getValue();
			}
		}
		if(lit!=null) {
			if(lit.getValue()!=null && lit.getValue().length()>0) {
				title=lit.getValue();
			}
		}
		data.setTitle(title);
		return data;
	}
	/**
	 * Store all components
	 * @param data
	 * @param user
	 * @param node
	 * @param thing
	 * @return
	 * @throws ObjectNotFoundException
	 */
	@Transactional
	public ThingDTO storeDataUnderThing(ThingDTO data, UserDetailsDTO user, Concept node, Thing thing)
			throws ObjectNotFoundException {
		data = storeLegacy(thing, data);				//should be first, because may change literals
		data = storeStrings(node,data);
		data = storeLiterals(node, data);
		data = storeDates(node,data);
		data = storeNumbers(node, data);
		data = storeLogical(node,data);
		data = storeDictionaries(thing,data);
		data = storeDocuments(thing, data,user);
		data = storeAddresses(user, node, thing,data);
		data = storeAmended(user, node, thing, data);
		data = storeSchedule(user, thing, data);
		data = storeRegister(user, thing, data);
		data = storeAtc(thing, data);
		data = storeIntervals(node, data);
		data=amendServ.storePersonToRemove(data, thing);

		return data;
	}

	/**
	 * Store data for all intervals defined
	 * @param node
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private ThingDTO storeIntervals(Concept node, ThingDTO data) throws ObjectNotFoundException {
		DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
		for(String key :data.getIntervals().keySet()) {
			LocalDate from = data.getIntervals().get(key).getFrom().getValue();
			LocalDate to = data.getIntervals().get(key).getTo().getValue();
			if(from!= null) {
				literalServ.createUpdateLiteral(key+"_from", formatter.format(from), node);
			}
			if(to!= null) {
				literalServ.createUpdateLiteral(key+"_to", formatter.format(to), node);
			}
		}
		return data;
	}
	/**
	 * Store legacy data selected
	 * @param thing
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private ThingDTO storeLegacy(Thing thing, ThingDTO data) throws ObjectNotFoundException {
		thing.getLegacyData().clear();
		for(String key :data.getLegacy().keySet()) {
			ThingLegacyData tld = new ThingLegacyData();
			LegacyDataDTO dto = data.getLegacy().get(key);
			if(dto.getSelectedNode()>0) {
				Concept node=closureServ.loadConceptById(dto.getSelectedNode());
				tld.setConcept(node);
				tld.setUrl(dto.getUrl());
				tld.setVarName(dto.getVarName());
				thing.getLegacyData().add(tld);
				//tune variables, we understand that legacy data component is only one :)
				FormFieldDTO<String> prefLabelDTO = data.getLiterals().get("prefLabel");
				if(prefLabelDTO==null) {
					prefLabelDTO = data.getStrings().get("prefLabel");
				}
				if(prefLabelDTO != null) {
					String prefLabel=literalServ.readPrefLabel(node);
					prefLabelDTO.setValue(prefLabel);
				}
				//alt label may be configured as altLabel or another name
				FormFieldDTO<String> altLabelDTO = data.getLiterals().get(dto.getAltLabel());
				if(altLabelDTO==null) {
					altLabelDTO=data.getStrings().get(dto.getAltLabel());
				}
				if(altLabelDTO != null) {
					String altLabel=literalServ.readValue("altLabel", node);		//here altLabel is right
					altLabelDTO.setValue(altLabel);
				}
			}
		}
		return data;
	}
	/**
	 * Store ATC codes
	 * @param thing
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private ThingDTO storeAtc(Thing thing, ThingDTO data) throws ObjectNotFoundException {
		if(data.getAtc().size()==1) {
			Set<String> keySet = data.getAtc().keySet();
			AtcDTO dto=data.getAtc().get(keySet.iterator().next());
			thing.getAtcodes().clear();
			for(TableRow row :dto.getSelectedtable().getRows()) {
				ThingAtc ta = new ThingAtc();
				Concept conc = closureServ.loadConceptById(row.getDbID());
				ta.setAtc(conc);
				ta.setVarname(dto.getVarName());
				thing.getAtcodes().add(ta);
			}
		}
		return data;
	}
	/**
	 * Store which object should be amended 
	 * @deprecated
	 * @param user
	 * @param node
	 * @param thing
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private ThingDTO storeAmended(UserDetailsDTO user, Concept node, Thing thing, ThingDTO data) throws ObjectNotFoundException {
		if(data.getAmendments().size()==1) {
			Set<String> keySet = data.getAmendments().keySet();
			AmendmentDTO dto = data.getAmendments().get(keySet.iterator().next());
			dto=validServ.amendment(dto);		//always mandatory, always strict
			if(dto.isValid()) {
				data.setValid(true);
				data = amendServ.save(user, node, thing, data, dto);
			}else {
				data.setValid(false);
			}
		}
		return data;
	}
	/**
	 * Component Person Special may change the parent ID
	 * @param data
	 * @param thing 
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private ThingDTO storePersonSpecial(ThingDTO data, Thing thing) throws ObjectNotFoundException {
		Set<String> keyset = data.getPersonspec().keySet();
		if(keyset.size()==1) {
			PersonSpecialDTO dto = data.getPersonspec().get(keyset.iterator().next());
			//save under the current person Id
			Concept pconc = new Concept();
			for(TableRow row : dto.getTable().getRows()) {
				if(row.getSelected()) {
					pconc=closureServ.loadConceptById(row.getDbID());
					break;
				}
			}
			if(pconc.getID()>0) {
				Thing th = boilerServ.thingByNode(pconc);
				Concept conc = closureServ.loadConceptById(data.getNodeId());
				conc.setLabel(pconc.getID()+"");				//save Person ID to the label of node. Acceptable
				ThingThing link= new ThingThing();
				for(ThingThing tt :th.getThings()) {
					if(tt.getUrl().equalsIgnoreCase(dto.getPresonDataUrl())) {
						link=tt;
					}
				}
				if(link.getID()==0) {
					link.setConcept(conc);
					link.setUrl(data.getUrl());
					link.setVarname(data.getVarName());
					th.getThings().add(link);
					th=thingRepo.save(th);
				}
			}
			//set new parent ID
			data.setParentId(dto.getParentId());
		}

		return data;
	}
	/**
	 * Sometimes the data URL may be changed, this we should re-save the data under the new url
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private ThingDTO checkUrlChange(ThingDTO data) throws ObjectNotFoundException {
		if(data.getNodeId()>0) {
			Concept node = closureServ.loadConceptById(data.getNodeId());
			List<Concept> parents = closureServ.loadParents(node);
			if(parents.get(0).getID()!=node.getID()) {
				if(!parents.get(0).getIdentifier().equalsIgnoreCase(data.getUrl())) {
					jdbcRepo.removeConcept(node);
					data.setNodeId(0l);
				}
			}
		}
		return data;
	}
	/**
	 * Attach the node to persons
	 * @param data
	 * @param node
	 * @param attachTo
	 */
	@Transactional
	private void saveToPersons(ThingDTO data, Concept node, Thing attachTo) {
		//save 
		boolean found = false;
		for(ThingPerson tp : attachTo.getPersons()) {
			if(tp.getConcept().getID()==data.getNodeId()) {
				found=true;
				break;
			}
		}
		if(!found) {
			ThingPerson link = new ThingPerson();
			link.setConcept(node);
			link.setPersonUrl(data.getUrl());
			link.setVarName(data.getVarName());
			attachTo.getPersons().add(link);
		}
		attachTo=thingRepo.save(attachTo);
		//
	}
	/**
	 * Attach this data as a thing to ThingThing
	 * @param data
	 * @param node
	 * @param attachTo
	 */
	@Transactional
	public void saveToThings(ThingDTO data, Concept node, Thing attachTo) {
		ThingThing link = new ThingThing();
		for(ThingThing tt : attachTo.getThings()) {
			if(tt.getVarname().equalsIgnoreCase(data.getVarName())) {
				link=tt;
				break;
			}
		}
		link.setConcept(node);
		link.setUrl(data.getUrl());
		link.setVarname(data.getVarName());
		attachTo.getThings().add(link);
		attachTo=thingRepo.save(attachTo);
	}

	/**
	 * Create the first activity in application and a history of it
	 * @param data
	 * @param email
	 * @param applicationData
	 * @return the first activity in the application
	 * @throws ObjectNotFoundException
	 * @throws JsonProcessingException 
	 */
	@Transactional
	public ThingDTO createApplication(ThingDTO data, String email, Concept applicationData) throws ObjectNotFoundException, JsonProcessingException {
		//create an application.
		Concept application = createNode(data.getApplicationUrl(), email);
		///do we need a special checklist for an applicant?
		String checklistUrl="dictionary.selfcheck.general";
		Concept aDictNode=closureServ.loadConceptById(data.getApplDictNodeId());
		String specUrl=literalServ.readValue("checklisturl", aDictNode);
		if(specUrl.length()>0) {
			checklistUrl=specUrl;
		}
		WorkflowParamDTO param = new WorkflowParamDTO();
		param.setChecklistUrl(checklistUrl);
		application.setLabel(objectMapper.writeValueAsString(param));
		application = closureServ.save(application);

		//create a history
		Concept applConfig = closureServ.loadRoot("configuration."+data.getApplicationUrl());
		History history = new History();
		history.setApplDict(aDictNode);
		history.setApplConfig(applConfig);
		history.setApplication(application);
		history.setApplicationData(applicationData);
		history.setActivityData(applicationData);
		history.setActivity(application);						//the first activity in application is the application itself
		history.setCome(boilerServ.localDateTimeToDate(LocalDateTime.now()));
		history =historyRepo.save(history);
		data.setHistoryId(history.getID());
		data.setActivityId(application.getID());
		data.setActivityName(messages.get("init"));
		return data;
	}
	/**
	 * Store all addresses to own addresses tree
	 * @param data
	 * @param user
	 * @param node
	 * @param thing
	 * @throws ObjectNotFoundException
	 */
	@Transactional
	public ThingDTO storeAddresses(UserDetailsDTO user, Concept node, Thing thing, ThingDTO data)
			throws ObjectNotFoundException {
		for(String addr:data.getAddresses().keySet()){
			AddressDTO addrDTO = data.getAddresses().get(addr);
			saveAddressAsThing(node, addrDTO,user, thing);
		}
		return data;
	}

	/**
	 * Store a schedule data to the scheduler table
	 * @param user
	 * @param node
	 * @param thing
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ThingDTO storeSchedule(UserDetailsDTO user, Thing thing, ThingDTO data) throws ObjectNotFoundException {
		if(data.getActivityId()>0) {													//we need activity ID to get a history record
			List<History> his = boilerServ.historyByActivityNode(closureServ.loadConceptById(data.getActivityId()));
			if(his.size()>0) {																//we need a history record to access application data
				thing.getSchedulers().clear();									//do not replace :)
				for(String key :data.getSchedulers().keySet()) {			//for each scheduler...
					SchedulerDTO dto = data.getSchedulers().get(key);
					Scheduler sch = new Scheduler();
					Concept schedConc = new Concept();
					if(dto.getConceptId()>0) {
						//load existing scheduler and concept
						schedConc = closureServ.loadConceptById(dto.getConceptId());
						sch = boilerServ.schedulerByNode(schedConc);
					}else {
						// or create a new one
						Concept root = closureServ.loadRoot(dto.getDataUrl());
						Concept owner = closureServ.saveToTree(root, user.getEmail());
						schedConc = closureServ.save(schedConc);
						schedConc.setIdentifier(schedConc.getID()+"");
						schedConc=closureServ.saveToTree(owner, schedConc);
						dto.setConceptId(schedConc.getID());
						sch.setConcept(schedConc);
						sch.setProcessUrl(dto.getProcessUrl());
					}
					ThingScheduler tch = new ThingScheduler();
					tch.setConcept(schedConc);
					tch.setUrl(dto.getDataUrl());
					tch.setVarName(key);
					thing.getSchedulers().add(tch);
					sch.setScheduled(boilerServ.localDateToDate(dto.getSchedule().getValue()));
					sch=boilerServ.saveSchedule(sch);
				}
				//and save it at last
				thing=boilerServ.saveThing(thing);
			}else {
				throw new ObjectNotFoundException("storeSchedule. History record is not defined",logger);
			}
		}
		return data;
	}

	/**
	 * Store registers
	 * @param user
	 * @param thing
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ThingDTO storeRegister(UserDetailsDTO user, Thing thing, ThingDTO data) throws ObjectNotFoundException {
		if(data.getRegisters().keySet().size()>0) {
			thing.getRegisters().clear();
			for(String key :data.getRegisters().keySet()) {			//for each register
				RegisterDTO regDto = data.getRegisters().get(key);
				//register node and record
				Concept regNode = new Concept();
				Register reg = new Register();
				if(regDto.getNodeID()>0) {
					regNode=closureServ.loadConceptById(regDto.getNodeID());
					reg=boilerServ.registerByConcept(regNode);
				}
				if(regDto.empty()) {//create new
					regDto = registerServ.askNewNumber(regDto);
				}
				Concept root = closureServ.loadRoot(regDto.getUrl());
				Concept owner = closureServ.saveToTree(root, user.getEmail());
				regNode = closureServ.save(regNode);
				regNode.setIdentifier(regNode.getID()+"");
				regNode=closureServ.saveToTree(owner, regNode);
				regDto.setNodeID(regNode.getID());
				//determine application data
				ThingRegister thre = new ThingRegister();
				thre.setConcept(regNode);
				thre.setUrl(regDto.getUrl());
				thre.setVarName(key);
				thing.getRegisters().add(thre);
				reg.setConcept(regNode);
				reg.setRegister(regDto.getReg_number().getValue());
				reg.setRegisteredAt(boilerServ.localDateToDate(regDto.getRegistration_date().getValue()));
				reg.setValidTo(boilerServ.localDateToDate(regDto.getExpiry_date().getValue()));
				//save all
				reg=boilerServ.saveRegister(reg);
			}
		}
		return data;
	}

	/**
	 * Store documents attached
	 * @param data
	 * @param thing
	 * @throws ObjectNotFoundException
	 */
	@Transactional
	public ThingDTO storeDocuments(Thing thing, ThingDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		for(String key : data.getDocuments().keySet()) {
			FileDTO docDto = data.getDocuments().get(key);
			for(Long dictNodeId : docDto.getLinked().keySet()) {
				if(docDto.getLinked().get(dictNodeId)>0){
					ThingDoc td = new ThingDoc();
					Concept fileNode =closureServ.loadConceptById(docDto.getLinked().get(dictNodeId));
					docDto.setNodeId(fileNode.getID());
					td=boilerServ.thingDocByNode(fileNode);
					Concept dictNode = td.getDictNode();
					FileResource fr = boilerServ.fileResourceByNode(fileNode);
					fr.setActivityData(thing.getConcept());
					td.setConcept(fileNode);
					td.setDictNode(dictNode);
					td.setDictUrl(docDto.getDictUrl());
					td.setDocUrl(docDto.getUrl());
					td.setVarName(key);
					thing.getDocuments().add(td);
					fr=fileRepo.save(fr);
				}
			}
			//docDto.getLinked().clear();
		}
		//createDocuments(assemblyServ.auxDocuments(data.getUrl()), data,user);
		return data;
	}

	/**
	 * Store dictionaries selected
	 * @param data
	 * @param thing
	 * @throws ObjectNotFoundException
	 */
	@Transactional
	public ThingDTO storeDictionaries(Thing thing, ThingDTO data) throws ObjectNotFoundException {
		if(thing.getID()>0) {
			entityManager.refresh(thing);
			thing.getDictionaries().clear();
			boilerServ.saveThing(thing);
			//entityManager.flush();
		}
		Set<Long> selected = new HashSet<Long>();
		for(String key : data.getDictionaries().keySet()) {
			DictionaryDTO dict = data.getDictionaries().get(key);
			selected.clear();
			selected.addAll(dict.getPrevSelected());
			if(selected.size()==0 && dict.getSelection().getValue().getId()>0) {
				selected.add(dict.getSelection().getValue().getId());
			}
			for(Long id : selected) {
				if(id != 0) {
					Concept dictItem = closureServ.loadConceptById(id);
					ThingDict thingDict = new ThingDict();
					thingDict.setUrl(dict.getUrl());
					thingDict.setConcept(dictItem);
					thingDict.setVarname(dict.getVarName());
					thing.getDictionaries().add(thingDict);
				}
			}
		}
		return data;
	}

	@Transactional
	public ThingDTO storeDates(Concept node, ThingDTO data) throws ObjectNotFoundException {
		DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
		for(String key :data.getDates().keySet()) {
			LocalDate dt = data.getDates().get(key).getValue();
			if(dt!= null) {
				literalServ.createUpdateLiteral(key, formatter.format(dt), node);
			}
		}
		return data;
	}
	/**
	 * Store numbers as literals
	 * @param node
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ThingDTO storeNumbers(Concept node, ThingDTO data) throws ObjectNotFoundException {
		DecimalFormat df = new DecimalFormat();
		df.setMaximumFractionDigits(0);
		df.setMinimumFractionDigits(0);
		df.setGroupingUsed(false);
		df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
		for(String key :data.getNumbers().keySet()) {
			Long num = data.getNumbers().get(key).getValue();
			if(num==null) {
				num=0l;
			}
			literalServ.createUpdateLiteral(key, df.format(num), node);
		}
		return data;
	}
	/**
	 * Store logicals values as string representation of enum's ord
	 * @param node
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ThingDTO storeLogical(Concept node, ThingDTO data) throws ObjectNotFoundException {
		for(String key :data.getLogical().keySet()) {
			OptionDTO opt = data.getLogical().get(key).getValue();
			literalServ.createUpdateLiteral(key, opt.getId()+"", node);
		}
		return data;
	}
	/**
	 * Store literals under the node
	 * @param data
	 * @param node
	 * @throws ObjectNotFoundException
	 */
	@Transactional
	public ThingDTO storeLiterals(Concept node, ThingDTO data) throws ObjectNotFoundException {
		for(String key : data.getLiterals().keySet()) {
			literalServ.createUpdateLiteral(key, data.getLiterals().get(key).getValue(), node);
			if(key.equalsIgnoreCase("prefLabel") && data.getParentIndex()==-1) {
				String newTitle= data.getLiterals().get(key).getValue();
				if(newTitle.length()>0) {
					if(data.getVarName().length()==0) {
						data.setTitle(newTitle);
					}else {
						data.setTitle(messages.get(data.getVarName()));
					}
				}
			}
		}
		return data;
	}
	/**
	 * Store strings - the same values for any language unconditionally
	 * @param node
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ThingDTO storeStrings(Concept node, ThingDTO data) throws ObjectNotFoundException {
		for(String key : data.getStrings().keySet()) {
			literalServ.createUpdateString(key, data.getStrings().get(key).getValue(), node);
			if(key.equalsIgnoreCase("prefLabel") && data.getParentIndex()==-1) {
				String newTitle= data.getStrings().get(key).getValue();
				if(newTitle.length()>0) {
					if(data.getVarName().length()==0) {
						data.setTitle(newTitle);
					}else {
						data.setTitle(messages.get(data.getVarName()));
					}
				}
			}
		}
		return data;
	}

	/**
	 * Save an address as a thing
	 * link to a dictionary and node.getLabel() as coordinates
	 * @param parentThingNode
	 * @param data
	 * @param parentThing 
	 * @throws ObjectNotFoundException
	 */
	@Transactional
	public AddressDTO saveAddressAsThing(Concept parentThingNode, AddressDTO data, UserDetailsDTO user, Thing parentThing) throws ObjectNotFoundException {
		//determine a node and Thing record
		Concept node = new Concept();
		Thing thing = new Thing();
		if(data.getNodeId()==0) {
			Concept root = closureServ.loadRoot(data.getUrl());
			Concept owner = closureServ.saveToTree(root, user.getEmail());
			node = closureServ.save(node);
			node.setIdentifier(node.getID()+"");
			node=closureServ.saveToTree(owner, node);
		}else {
			node=closureServ.loadConceptById(data.getNodeId());
		}
		try {
			thing=boilerServ.thingByNode(node,thing);
		} catch (Exception e) {
			//nothing to do
		}
		//prepare data to store
		node.setLabel(data.getMarker().gisLocation());
		thing.getDictionaries().clear();
		thing.setUrl(data.getUrl());
		Set<Long> selected = dictServ.selectedItems(data.getDictionary());
		if(selected.size()>0) {
			ThingDict dict = new ThingDict();
			dict.setUrl(data.getDictionary().getUrl());
			Concept dictNode = closureServ.loadConceptById(selected.iterator().next());
			dict.setConcept(dictNode);
			dict.setVarname(data.getVarName());
			thing.getDictionaries().add(dict);
			dictServ.storePath(dictNode,node);
		}
		boolean found=false;
		for(ThingThing th : parentThing.getThings()) {
			if(th.getUrl().equalsIgnoreCase(data.getUrl())
					&& th.getVarname().equalsIgnoreCase(data.getVarName())) {
				th.setConcept(node);
				found=true;
			}
		}
		if(!found) {
			ThingThing th = new ThingThing();
			th.setUrl(data.getUrl());
			th.setVarname(data.getVarName());
			th.setConcept(node);
			parentThing.getThings().add(th);
		}
		//store it
		node=closureServ.save(node);
		data.setNodeId(node.getID());
		thing.setConcept(node);
		thing=thingRepo.save(thing);
		parentThing.setConcept(parentThingNode);
		parentThing = thingRepo.save(parentThing);
		return data;
	}


	/**
	 * Create a node in the tree under the user's eMail 
	 * @param treeUrl
	 * @param eMAil
	 * @return
	 * @throws ObjectNotFoundException
	 */
	@Transactional
	public Concept createNode(String treeUrl, String eMail) throws ObjectNotFoundException {
		Concept root = closureServ.loadRoot(treeUrl);
		Concept eMailNode = closureServ.saveToTree(root, eMail);
		Concept node = new Concept();
		node = closureServ.save(node);
		node.setIdentifier(node.getID()+"");
		node = closureServ.saveToTree(eMailNode, node);
		return node;
	}

	/**
	 * load a thing
	 * @param data
	 * @param user
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ThingDTO loadThing(ThingDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		if(data.getNodeId()>0) {
			Concept node= closureServ.loadConceptById(data.getNodeId());
			Thing thing = new Thing();
			thing = boilerServ.thingByNode(node,thing);
			//determine URL
			if(data.getUrl().length()==0) {
				data.setUrl(thing.getUrl());
			}
			if(accessControlServ.readAllowed(data,user)) {
				data.setReadOnly(!accessControlServ.writeAllowed(data, user) || data.isReadOnly());
				if(data.getActivityId()>0) {
					Concept activity=closureServ.loadConceptById(data.getActivityId());
					String prefLabel = literalServ.readPrefLabel(node);
					if(prefLabel.length()>0 && data.getParentIndex()<1) {
						data.setTitle(prefLabel);
					}else {
						data.setTitle(messages.get(data.getVarName()));
					}
					data.setActivityName(activity.getLabel());
				}
				data=createContent(data,user);
				data.setStrings(dtoServ.readAllStrings(data.getStrings(),node));
				data.setLiterals(dtoServ.readAllLiterals(data.getLiterals(), node));
				data.setDates(dtoServ.readAllDates(data.getDates(),node));
				data.setNumbers(dtoServ.readAllNumbers(data.getNumbers(),node));
				data.setLogical(dtoServ.readAllLogical(data.getLogical(), node));
				//compare with amended, if needed
				data=amendServ.diffMark(data);
			}else {
				throw new ObjectNotFoundException("loadThing. Read access dened for user "+user.getEmail(),logger);
			}
		}else {
			throw new ObjectNotFoundException("loadThing. Node  is not defined",logger);
		}
		return data;
	}

	/**
	 * Create a path to fill-out form
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ThingDTO path(ThingDTO data) throws ObjectNotFoundException {
		//application name and description
		if(data.getApplDictNodeId()>0) {
			Concept adictNode = closureServ.loadConceptById(data.getApplDictNodeId());
			data.setApplName(literalServ.readPrefLabel(adictNode));
			data.setApplDescr(literalServ.readDescription(adictNode));
			data.setUrl(literalServ.readValue("dataurl", adictNode));
			data.setApplicationUrl(literalServ.readValue("applicationurl", adictNode));
		}
		//is it existing application?
		if(data.getHistoryId()>0) {
			History his = boilerServ.historyById(data.getHistoryId());
			if(his.getActivity()!=null) {
				data.setActivityId(his.getActivity().getID());
				data.setActivityName(messages.get(his.getActivity().getLabel()));
			}
			if(data.isApplication()) {
				if(his.getApplication()!= null) {
					data.setNodeId(his.getApplicationData().getID());
				}
			}else {
				if(his.getActivityData()!=null){
					data.setNodeId(his.getActivityData().getID());
				}
			}
			if(data.getNodeId()==0) {
				throw new ObjectNotFoundException("path. Can't get nodeId on the existig activity/application",logger);
			}
		}else {
			//amendment related
			if(data.getModiUnitId()>0) {
				data.getLiterals().put("prefLabel", FormFieldDTO.of(data.getPrefLabel()));
			}
		}
		//breadcrumb related things
		data.setTitle(messages.get("RegState.NEW_APPL"));
		if(data.getNodeId()>0) {
			Concept node= closureServ.loadConceptById(data.getNodeId());
			Thing thing = new Thing();
			thing = boilerServ.thingByNode(node,thing);
			data.setUrl(thing.getUrl());
			data.setTitle(literalServ.readPrefLabel(node));
			if(thing.getAmendments().size()==1) {
				data.setModiUnitId(thing.getAmendments().iterator().next().getConcept().getID());
			}
		}
		data.getPath().clear();
		List<ThingDTO> path = createPath(data, new ArrayList<ThingDTO>(),-1);
		data.getPath().addAll(path);
		for(ThingDTO dto : data.getPath()) {
			dto.setModiUnitId(data.getModiUnitId());
			dto.setApplDictNodeId(data.getApplDictNodeId());
		}
		return data;
	}

	/**
	 * Create a path recursive
	 * @param dto thing dto to include to the path
	 * @param path path itself
	 * @param parentIndex index of thing dto, -1 is no parent 
	 * @return
	 * @throws ObjectNotFoundException
	 */
	@Transactional
	public List<ThingDTO> createPath(ThingDTO dto, List<ThingDTO> path, int parentIndex) throws ObjectNotFoundException {
		dto.setParentIndex(parentIndex);
		if(path.size()==0) {
			path.add(deepCloneThing(dto));	//will be included to path property of the dto
		}
		List<Assembly> assemblies =assemblyServ.loadDataConfiguration(dto.getUrl());
		List<AssemblyDTO> things = assemblyServ.auxThings(dto.getUrl(),assemblies);
		dto = createThings(things, dto);
		Set<String> keys = dto.getThings().keySet();	//variables names
		int nextParIndex = path.size()-1;
		for(String key : keys) {
			ThingDTO dto1 = dto.getThings().get(key);
			path.add(dto1);
			path = createPath(dto1,path,nextParIndex);
		}
		return path;
	}

	/**
	 * Create a deep clone of the ThingDTO
	 * @param dto
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	public ThingDTO deepCloneThing(ThingDTO dto) throws ObjectNotFoundException {
		ThingDTO deepCopy;
		try {
			deepCopy = objectMapper
					.readValue(objectMapper.writeValueAsString(dto), ThingDTO.class);
			return deepCopy;
		} catch (JsonProcessingException e) {
			throw new ObjectNotFoundException(e,logger);
		}

	}
	/**
	 * Load a linked file or an empty record ready to upload
	 * @param data
	 * @param user
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public FileDTO fileLoad(FileDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		Long nodeId = data.getLinked().get(data.getDictNodeId());
		if(nodeId==null) {
			nodeId=0l;
		}
		data.setNodeId(nodeId);
		if(data.getNodeId()>0) {
			//load data from existing
			Concept fileNode = closureServ.loadConceptById(data.getNodeId());
			Optional<FileResource> fro = fileRepo.findByConcept(fileNode);
			if(fro.isPresent()) {
				data.setFileName(fileNode.getLabel());
				data.setFileSize(fro.get().getFileSize());
				data.setMediaType(fro.get().getMediatype());
			}else {
				throw new ObjectNotFoundException("fileLoad File Resource by concept is not found. Concept id is "+data.getNodeId(),logger);
			}
		}
		return data;
	}

	/**
	 * Save a file to the user's storage
	 * @param data
	 * @param user
	 * @param fileBytes
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public FileDTO fileSave(FileDTO data, UserDetailsDTO user, byte[] fileBytes) throws ObjectNotFoundException {
		data=validServ.file(data, fileBytes);
		if(data.isValid()) {
			String email = user.getEmail();
			if((data.getFileName().length()==0 || fileBytes.length>1) 
					&& validServ.eMail(email) 
					&& data.getDictNodeId()>0) {
				//determine node ID
				long fileNodeId = data.getNodeId();
				Concept node = new Concept();
				if(fileNodeId==0 ) {
					//create a new file node and store it to data
					Concept root = closureServ.loadRoot(data.getUrl());
					Concept owner=closureServ.saveToTree(root, user.getEmail());
					node = closureServ.save(node);
					node.setIdentifier(node.getID()+"");
					node=closureServ.saveToTree(owner, node);
					data.getLinked().put(data.getDictNodeId(),node.getID());
				}else {
					node = closureServ.loadConceptById(data.getNodeId());
				}
				Concept parent = closureServ.getParent(node);
				if(accessControlServ.sameEmail(parent.getIdentifier(), user.getEmail()) || accessControlServ.isSupervisor(user)) {
					parent.setIdentifier(user.getEmail());
					parent=closureServ.save(parent);
					//dictionary item
					Concept dictItem = closureServ.loadConceptById(data.getDictNodeId());
					//file name
					node.setLabel(data.getFileName());
					//file data
					FileResource fres = new FileResource();
					Optional<FileResource> freso = fileRepo.findByConcept(node);
					if(freso.isPresent()) {
						fres=freso.get();
					}
					fres.setClassifier(dictItem);
					fres.setConcept(node);
					fres.setFile(fileBytes);
					fres.setFileSize(data.getFileSize());
					fres.setMediatype(data.getMediaType());
					fres=fileRepo.save(fres);
					ThingDoc tdoc = boilerServ.loadThingDocByFileNode(node);
					if(data.getThingNodeId()>0) {
						Concept thingConc = closureServ.loadConceptById(data.getThingNodeId());
						Thing thing = new Thing();
						thing = boilerServ.thingByNode(thingConc, thing);
						if(thing.getID()>0) {
							for(ThingDoc td : thing.getDocuments()) {
								if(td.getDictNode().getID()==data.getDictNodeId() 
										&& td.getVarName().toUpperCase().equalsIgnoreCase(data.getVarName())) {
									tdoc=td;
									break;
								}
							}
							tdoc.setConcept(node);
							tdoc.setDictNode(dictItem);
							tdoc.setDictUrl(data.getDictUrl());
							tdoc.setDocUrl(data.getUrl());
							tdoc.setVarName(data.getVarName());
							if(tdoc.getID()==0) {
								thing.getDocuments().add(tdoc);
							}
							thing=thingRepo.save(thing);
						}else { //thing is not saved yet
							tdoc.setConcept(node);
							tdoc.setDictNode(dictItem);
							tdoc.setDictUrl(data.getDictUrl());
							tdoc.setDocUrl(data.getUrl());
							tdoc.setVarName(data.getVarName());
							tdoc=boilerServ.saveThingDoc(tdoc);
						}
					}else {		//node is not defined yet		
						tdoc.setConcept(node);
						tdoc.setDictNode(dictItem);
						tdoc.setDictUrl(data.getDictUrl());
						tdoc.setDocUrl(data.getUrl());
						tdoc.setVarName(data.getVarName());
						tdoc=boilerServ.saveThingDoc(tdoc);
					}
				}else {
					throw new ObjectNotFoundException("fileSave Access denied "+ user.getEmail()+"/"+data.getUrl(), logger);
				}
			}else {
				throw new ObjectNotFoundException("fileSave File is empty or eMAil/url/classifier is bad "
						+ user.getEmail()+"/"+data.getUrl()+"/"+data.getDictNodeId(), logger);
			}
		}
		return data;
	}

	/**
	 * reload a table with files
	 * @param data
	 * @param user
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	public FileDTO thingFiles(FileDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		data=createDocTable(data,user);
		return data;
	}
	/**
	 * download a file as a file :)
	 * @param nodeId	file concept id
	 * @return
	 * @throws ObjectNotFoundException
	 */
	@Transactional
	public ResponseEntity<Resource> fileDownload(long nodeId) throws ObjectNotFoundException {
		Concept fileNode = closureServ.loadConceptById(nodeId);
		Optional<FileResource> freso = fileRepo.findByConcept(fileNode);
		if(freso.isPresent()) {
			FileResource fres=freso.get();
			String fileName = fileNode.getLabel();
			Resource res = new ByteArrayResource(fres.getFile());

			String mediaType = fres.getMediatype();
			String typeOpen = "inline";
			if(mediaType == null || mediaType.length() == 0) {
				mediaType = "application/octet-stream";
				typeOpen = "attachment";
			}

			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType(mediaType))
					.contentLength(fres.getFileSize())
					.header(HttpHeaders.CONTENT_DISPOSITION, typeOpen + "; filename=\"" + fileName +"\"")
					.header("filename", fileName)
					.body(res);
		}else {
			throw new ObjectNotFoundException(" load. File not found. Node id is "+fileNode.getID());
		}
	}
	/**
	 *Any ThingDTO at any given moment may has only one auxiliary dynamic path
	 *This path represents a loop in a main path to add/edit multiply things
	 *The auxiliary path is differ from the main path, because it calculates dynamically, depends of a user's choice
	 * For current, the auxiliary path is possible only for Person  
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ThingDTO auxPath(ThingDTO data) throws ObjectNotFoundException {
		if(data.getNodeId()>0) {
			//create/load a core thing for auxiliary path (only for persons yet)
			PersonDTO pdto = data.getPersons().get(data.getAuxPathVar());
			if(pdto != null) {
				data=auxPathPerson(pdto,data);
			}
			return data;
		}else {
			throw new ObjectNotFoundException("auxPath. Thing node ID is ZERO", logger);
		}
	}

	/**
	 * 
	 * @param pdto
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private ThingDTO auxPathPerson(PersonDTO personDTO, ThingDTO data) throws ObjectNotFoundException {
		//load a thing
		Concept node = closureServ.loadConceptById(data.getNodeId());
		Thing thing = boilerServ.thingByNode(node);
		//try to found selected items in the dictionary
		List<Long> selected = new ArrayList<Long>();
		for(ThingDict tdict : thing.getDictionaries()) {
			//if(tdict.getUrl().equalsIgnoreCase(personDTO.getDictUrl())) {
			selected.add(tdict.getConcept().getID());
			//}
		}
		//load a core thing in auxiliary path
		if(selected.size()<=1) {
			long dictNodeId=0;
			if(selected.size()==1) {
				dictNodeId=selected.get(0);
			}
			//core ThingDTO
			AssemblyDTO coreAssembly = assemblyServ.auxPathConfig(data, dictNodeId,data.getAuxPathVar());
			ThingDTO coreDTO= ThingDTO.createIncluded(data, coreAssembly);
			coreDTO.setParentId(data.getNodeId());
			coreDTO.setNodeId(personDTO.getNodeId());
			coreDTO.setTitle(messages.get(personDTO.getVarName()));
			coreDTO.setHistoryId(data.getHistoryId());
			coreDTO.setVarName(personDTO.getVarName());
			//calculate path and place it to auxiliary path of the thing
			List<ThingDTO> path = createPath(coreDTO, new ArrayList<ThingDTO>(),-1);
			data.getAuxPath().clear();
			data.getAuxPath().addAll(path);
			return data;
		}else {
			throw new ObjectNotFoundException("auxPath. Wrong dictionary selection. Only one is allowed "
					+ personDTO.getDictUrl()+"/"+selected.size());
		}
	}
	/**
	 * Aux data url
	 * @param data
	 * @param dicts
	 * @return empty string if not found
	 * @throws ObjectNotFoundException
	 */
	@Transactional
	public String  auxDataUrl(ThingDTO data, Set<String> dicts) throws ObjectNotFoundException {
		//default data url
		String auxDataUrl=assemblyServ.auxDataUrl(data.getUrl(), data.getAuxPathVar());
		//url from dictionary
		if(dicts.size()==1) {
			DictionaryDTO dict = data.getDictionaries().get(dicts.iterator().next());
			//get selection
			Set<Long>selected=dictServ.selectedItems(dict);
			if(selected.size()>0) {
				Concept dictNode=closureServ.loadConceptById(selected.iterator().next());
				String lit = literalServ.readValue("URL", dictNode);
				if(lit.length()>0) {
					auxDataUrl=lit;
				}
			}
		}
		return auxDataUrl;
	}


	/**
	 * Reload person's table
	 * @param data
	 * @param user
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public PersonDTO personTableLoad(PersonDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		data=createPersTable(data,data.isReadOnly());
		return data;
	}
	/**
	 * Create table to remove
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private PersonDTO createToRemoveTable(PersonDTO data) throws ObjectNotFoundException {
		if(data.getRtable().getHeaders().getHeaders().size()==0) {
			data.getRtable().setHeaders(personHeaders(data.getRtable().getHeaders()));
		}
		if(data.getRtable().getRows().size()==0) {
			Concept amended = closureServ.loadConceptById(data.getAmendedNodeId());
			Thing thinga = boilerServ.thingByNode(amended);
			for(ThingPerson tp : thinga.getPersons()) {
				String pref = literalServ.readPrefLabel(tp.getConcept());
				TableRow row = TableRow.instanceOf(tp.getConcept().getID());
				row.getRow().add(TableCell.instanceOf("prefLabel", pref));
				data.getRtable().getRows().add(row);
			}
		}
		return data;
	}
	/**
	 * Check access to thing, extract values
	 * @param user
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	public ThingValuesDTO thingValuesExtract(UserDetailsDTO user, ThingDTO thing,  ThingValuesDTO data) throws ObjectNotFoundException {
		data.setNodeId(thing.getNodeId());
		data.setParentId(thing.getParentId());
		data.setUrl(thing.getUrl());

		data.getStrings().clear();
		for(String key :thing.getStrings().keySet()) {
			data.getStrings().put(key.toUpperCase(), thing.getStrings().get(key).getValue());
		}
		for(String key :thing.getLiterals().keySet()) {
			data.getLiterals().put(key.toUpperCase(), thing.getLiterals().get(key).getValue());
		}
		data.getLiterals().clear();
		for(String key :thing.getLiterals().keySet()) {
			data.getLiterals().put(key.toUpperCase(), thing.getLiterals().get(key).getValue());
		}
		data.getNumbers().clear();
		for(String key :thing.getNumbers().keySet()) {
			data.getNumbers().put(key.toUpperCase(), thing.getNumbers().get(key).getValue());
		}
		data.getDates().clear();
		for(String key :thing.getDates().keySet()) {
			data.getDates().put(key.toUpperCase(), thing.getDates().get(key).getValue());
		}
		data.getDictionaries().clear();
		for(String key :thing.getDictionaries().keySet()) {
			data.getDictionaries().put(key.toUpperCase(), dictionaryData(thing.getDictionaries().get(key)));
		}
		data.getAddresses().clear();
		for(String key :thing.getAddresses().keySet()) {
			data.getAddresses().put(key.toUpperCase(), addressData(thing.getAddresses().get(key)));
		}
		data.getPersonselection().clear();
		for(String key :thing.getPersonselector().keySet()) {
			Long selected=0l;
			for(TableRow row : thing.getPersonselector().get(key).getTable().getRows()) {
				if(row.getSelected()) {
					selected=row.getDbID();
					break;
				}
			}
			if(selected>0l) {
				data.getPersonselection().put(key,selected);
			}
		}
		data.getSchedulers().clear();
		for(String key :thing.getSchedulers().keySet()) {
			data.getSchedulers().put(key.toUpperCase(), thing.getSchedulers().get(key));
		}
		data.getRegisters().clear();
		for(String key :thing.getRegisters().keySet()) {
			data.getRegisters().put(key.toUpperCase(), thing.getRegisters().get(key));
		}

		return data;
	}
	/**
	 * Extract data from a address
	 * @param addressDTO
	 * @return
	 */
	private AddressValuesDTO addressData(AddressDTO addressDTO) {
		AddressValuesDTO ret = new AddressValuesDTO();
		ret.setGisCoordinates(addressDTO.getMarker().gisLocation());
		ret.setAdminUnits(dictionaryData(addressDTO.getDictionary()));
		return ret;
	}
	/**
	 * Extract data from a dictionary
	 * @param dictionaryDTO
	 * @return
	 */
	private DictValuesDTO dictionaryData(DictionaryDTO dictionaryDTO) {
		DictValuesDTO ret = new DictValuesDTO();
		ret.getSelected().addAll(dictServ.selectedItems(dictionaryDTO));
		ret.setUrl(dictionaryDTO.getUrl());
		return ret;
	}
	/**
	 * Load special person data for selected person and aux url
	 * @param data
	 * @param user
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public PersonSpecialDTO personSpecialLoad(PersonSpecialDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		//who is selected
		long personId = 0;
		for(TableRow row : data.getTable().getRows()) {
			if(row.getSelected()) {
				personId = row.getDbID();
				break;
			}
		}
		if(personId>0) {
			if(accessControlServ.personAllowed(personId, user)) {
				//TODO load person data related to the person selected
				Concept pconc = closureServ.loadConceptById(personId);
				Thing thing = boilerServ.thingByNode(pconc);
				Concept dconc = new Concept();
				if(thing.getUrl().equalsIgnoreCase(data.getPresonDataUrl())) {
					dconc=pconc;
				}else {
					for(ThingThing tt : thing.getThings()) {
						if(tt.getUrl().equalsIgnoreCase(data.getPresonDataUrl())) {
							dconc=tt.getConcept();
							break;
						}
					}
				}
				data.setPersonDataId(dconc.getID());
			}else {
				throw new ObjectNotFoundException("personSpecialLoad. access to person denied ID is ",logger);
			}
		}else {
			data.setSelected(false);
		}
		return data;	
	}

	/**
	 * Place a help on the thing, using the validator and the configurator
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	public ThingDTO help(ThingDTO data) throws ObjectNotFoundException {
		data=validServ.thing(data,false);
		return data;
	}
	/**
	 * Suspend a person (owner, pharmacist, etc.)
	 * @param data
	 * @param user
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public PersonDTO personSuspend(PersonDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		long personId=0;
		for(TableRow row :data.getTable().getRows()) {
			if(row.getSelected()) {
				personId=row.getDbID();
			}
		}
		if(personId>0) {
			Concept person=closureServ.loadConceptById(personId);
			person.setActive(false);
			closureServ.save(person);
		}
		return data;
	}
}

