package org.msh.pharmadex2.service.r2;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.msh.pdex2.dto.table.Headers;
import org.msh.pdex2.dto.table.TableCell;
import org.msh.pdex2.dto.table.TableHeader;
import org.msh.pdex2.dto.table.TableQtb;
import org.msh.pdex2.dto.table.TableRow;
import org.msh.pdex2.exception.ObjectNotFoundException;
import org.msh.pdex2.i18n.Messages;
import org.msh.pdex2.model.old.User;
import org.msh.pdex2.model.r2.Checklistr2;
import org.msh.pdex2.model.r2.Concept;
import org.msh.pdex2.model.r2.History;
import org.msh.pdex2.model.r2.Scheduler;
import org.msh.pdex2.model.r2.Thing;
import org.msh.pdex2.model.r2.ThingDict;
import org.msh.pdex2.model.r2.ThingScheduler;
import org.msh.pdex2.model.r2.ThingThing;
import org.msh.pdex2.repository.common.JdbcRepository;
import org.msh.pdex2.repository.r2.Checklistr2Repo;
import org.msh.pharmadex2.dto.ActivityDTO;
import org.msh.pharmadex2.dto.ActivitySubmitDTO;
import org.msh.pharmadex2.dto.ApplicationHistoryDTO;
import org.msh.pharmadex2.dto.ApplicationOrActivityDTO;
import org.msh.pharmadex2.dto.ApplicationSelectDTO;
import org.msh.pharmadex2.dto.ApplicationsDTO;
import org.msh.pharmadex2.dto.CheckListDTO;
import org.msh.pharmadex2.dto.DictionaryDTO;
import org.msh.pharmadex2.dto.QuestionDTO;
import org.msh.pharmadex2.dto.ThingDTO;
import org.msh.pharmadex2.dto.auth.UserDetailsDTO;
import org.msh.pharmadex2.dto.form.OptionDTO;
import org.msh.pharmadex2.service.common.BoilerService;
import org.msh.pharmadex2.service.common.DtoService;
import org.msh.pharmadex2.service.common.EntityService;
import org.msh.pharmadex2.service.common.UserService;
import org.msh.pharmadex2.service.common.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application related services
 * @author alexk
 *
 */
@Service
public class ApplicationService {
	private static final Logger logger = LoggerFactory.getLogger(ApplicationService.class);
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
	private JdbcRepository jdbcRepo;
	@Autowired
	private Checklistr2Repo checklistRepo;
	@Autowired
	private AccessControlService accServ;
	@Autowired
	private DtoService dtoServ;
	@Autowired
	private ValidationService validServ;
	@Autowired
	private EntityService entityServ;
	@Autowired
	private UserService userServ;
	@Autowired
	private ThingService thingServ;
	@Autowired
	private Messages messages;
	@Autowired
	private SystemService systemServ;
	/**
	 * Provide access to guest applications
	 * @param data
	 * @param user
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	public ApplicationSelectDTO guestApplications(ApplicationSelectDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		if(data.getAppListDictionary().getUrl().length()>0) {
			//dictionary exists
			List<Long> selected = data.getAppListDictionary().getPrevSelected();
			if(selected.size()==1) {
				long nodeId=selected.get(0);
				Concept node = closureServ.loadConceptById(nodeId);
				String appUrl = literalServ.readValue("applicationurl", node);
				String applTitle=literalServ.readValue("prefLabel", node);
				if(appUrl.length()>0) {
					data=createGuestApplications(appUrl, applTitle, user.getEmail(),data);
					data.getApplications().setDictItemId(nodeId);
				}
			}else {
				data=cleanGuestApplication(data);
			}
		}else {
			String dictUrl = assemblyServ.guestDictUrl();
			data.getAppListDictionary().setUrl(dictUrl);
			data.setAppListDictionary(dictServ.createDictionary(data.getAppListDictionary()));
		}
		//fine tune the dictionary
		data.getAppListDictionary().setMult(false);
		return data;
	}
	/**
	 * recreate only a table for selected applications
	 * @param data
	 * @param user
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ApplicationsDTO applicatonsTable(ApplicationsDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		if(data.getDictItemId()>0) {
			Concept dictItem= closureServ.loadConceptById(data.getDictItemId());
			String appUrl = literalServ.readValue("applicationurl", dictItem);
			data.setTable(createApplicationsTable(appUrl, user.getEmail(), data.getTable()));
		}
		return data;
	}
	/**
	 * Create guest applications table
	 * @param appUrl
	 * @param email
	 * @param data 
	 * @param string 
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private ApplicationSelectDTO createGuestApplications(String appUrl, String applTitle
			, String email, ApplicationSelectDTO data) throws ObjectNotFoundException {
		data.getApplications().setUrl(appUrl);
		data.getApplications().setTitle(applTitle);
		data.getApplications().setTable(createApplicationsTable(appUrl, email, data.getApplications().getTable()));
		return data;
	}

	/**
	 * Empty Gueat application table
	 * @param data
	 * @return
	 */
	private ApplicationSelectDTO cleanGuestApplication(ApplicationSelectDTO data) {
		data.getApplications().setUrl("");
		data.getApplications().getTable().getRows().clear();
		return data;
	}

	/**
	 * Create a table with list of applications
	 * @param appUrl
	 * @param email
	 * @param table
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private TableQtb createApplicationsTable(String appUrl, String email, TableQtb table) throws ObjectNotFoundException {
		if(table.getHeaders().getHeaders().size()==0) {
			table.setHeaders(createHeaders(table.getHeaders(),true));
		}
		String select="select * from activity_data";
		String where="go is null and applurl='" + appUrl + "' and executive='"+email +"' and lang='"+LocaleContextHolder.getLocale().toString().toUpperCase()+"'";
		List<TableRow> rows =jdbcRepo.qtbGroupReport(select, "", where, table.getHeaders());
		TableQtb.tablePage(rows, table);
		table=boilerServ.translateRows(table);
		table.setSelectable(false);
		return table;
	}

	/**
	 * Create headers for application list
	 * @param headers
	 * @param present present or scheduled
	 * @return
	 */
	private Headers createHeaders(Headers headers, boolean present) {
		headers.getHeaders().add(TableHeader.instanceOf(
				"come", 
				"scheduled",
				true,
				true,
				true,
				TableHeader.COLUMN_LOCALDATETIME,
				0));
		if(present) {
			headers.getHeaders().add(TableHeader.instanceOf(
					"days", 
					"days",
					true,
					true,
					true,
					TableHeader.COLUMN_LONG,
					0));
		}
		headers.getHeaders().add(TableHeader.instanceOf(
				"applurl", 
				"prod_app_type",
				true,
				false,
				false,
				TableHeader.COLUMN_I18,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"activityurl", 
				"activity",
				true,
				false,
				false,
				TableHeader.COLUMN_I18LINK,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"applicant", 
				"applicant",
				true,
				true,
				true,
				TableHeader.COLUMN_STRING,
				0));

		headers.getHeaders().add(TableHeader.instanceOf(
				"pref", 
				"global_name",
				true,
				true,
				true,
				TableHeader.COLUMN_STRING,
				0));

		headers.getHeaders().get(0).setSortValue(TableHeader.SORT_ASC);
		headers= boilerServ.translateHeaders(headers);
		return headers;
	}

	/**
	 * Create headers for application list
	 * @param headers
	 * @param present present or scheduled
	 * @return
	 */
	private Headers monitoringHeaders(Headers headers, boolean present) {
		headers.getHeaders().add(TableHeader.instanceOf(
				"Come", 
				"scheduled",
				true,
				true,
				true,
				TableHeader.COLUMN_LOCALDATE,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"pref",
				"prefLabel",
				true,
				true,
				true,
				TableHeader.COLUMN_STRING,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"applicant",
				"applicant",
				true,
				true,
				true,
				TableHeader.COLUMN_STRING,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"workflow",
				"prod_app_type",
				true,
				true,
				true,
				TableHeader.COLUMN_LINK,
				0));
		headers=boilerServ.translateHeaders(headers);
		headers.getHeaders().get(0).setSortValue(TableHeader.SORT_ASC);
		return headers;
	}

	/**
	 * Headers for history table
	 * @param headers
	 * @return
	 */
	private Headers historyHeaders(Headers headers) {
		headers.getHeaders().add(TableHeader.instanceOf(
				"come",
				"global_date",
				true,
				true,
				true,
				TableHeader.COLUMN_LOCALDATETIME,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"go",
				"done",
				true,
				true,
				true,
				TableHeader.COLUMN_LOCALDATETIME,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"days",
				"days",
				true,
				true,
				true,
				TableHeader.COLUMN_LONG,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"activityurl",
				"activity",
				true,
				true,
				true,
				TableHeader.COLUMN_I18,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"executive",
				"persons",
				true,
				true,
				true,
				TableHeader.COLUMN_STRING,
				0));

		headers=boilerServ.translateHeaders(headers);
		headers.getHeaders().get(0).setSort(true);
		headers.getHeaders().get(0).setSortValue(TableHeader.SORT_ASC);
		headers.setPageSize(Integer.MAX_VALUE);
		return headers;
	}



	/**
	 * Create histroy table
	 * @param user 
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ApplicationHistoryDTO historyTable(UserDetailsDTO user, ApplicationHistoryDTO data) throws ObjectNotFoundException {
		if(data.getApplDictNodeId()>0) {
			Concept dictNode = closureServ.loadConceptById(data.getApplDictNodeId());
			data.setApplName(literalServ.readPrefLabel(dictNode));
		}
		if(data.getHistoryId()>0) {
			History his = boilerServ.historyById(data.getHistoryId());
			Concept dictNode = his.getApplDict();
			data.setApplName(literalServ.readPrefLabel(dictNode));
			data.setApplDictNodeId(dictNode.getID());
			TableQtb table = data.getTable();
			if(table.getHeaders().getHeaders().size()==0) {
				table.setHeaders(historyHeaders(table.getHeaders()));
			}
			String select = "select * from activity_data";
			String where = "days>=0 and actConfigID is not null "
					+ "and applNodeId="+his.getApplication().getID() +" and lang='"+LocaleContextHolder.getLocale().toString().toUpperCase()+"'";
			List<TableRow> rows=jdbcRepo.qtbGroupReport(select, "", where, table.getHeaders());
			TableQtb.tablePage(rows, table);
			boilerServ.translateRows(table);
			table.setSelectable(accServ.isSupervisor(user));
			data.setTable(table);
			return data;
		}
		return data;
	}

	/**
	 * Create or load a checklist
	 * @param data
	 * @param user
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public CheckListDTO checklistLoad(CheckListDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		//current activity?
		if(data.getHistoryId()>0) {
			History his = boilerServ.historyById(data.getHistoryId());
			if(his.getActivity() != null) {
				data.setActivityNodeId(his.getActivity().getID());
				data.setApplNodeId(his.getApplication().getID());
				//determine owner and application's url
				Concept executor = closureServ.getParent(his.getActivity());
				//set access
				data.setReadOnly(!accServ.sameEmail(executor.getIdentifier(), user.getEmail()));

				//dictionary and data
				String dictUrl = his.getActivity().getLabel();
				Concept dictRoot = closureServ.loadRoot(dictUrl);
				data.setDictUrl(dictUrl);
				data.setTitle(literalServ.readPrefLabel(dictRoot));
				List<Checklistr2> stored = checklistRepo.findAllByActivity(his.getActivity());
				List<Long> dictIds = new ArrayList<Long>();
				for(Checklistr2 chl : stored) {
					dictIds.add(chl.getDictItem().getID());
				}
				List<OptionDTO> plainDictionary = dictServ.loadPlain(dictUrl);
				data.getQuestions().clear();
				for(OptionDTO odto : plainDictionary) {
					int index = dictIds.indexOf(odto.getId());
					if(index>-1) {
						data.getQuestions().add(dtoServ.question(stored.get(index), odto));
					}else {
						data.getQuestions().add(QuestionDTO.of(odto));
					}
				}
			}else {
				throw new ObjectNotFoundException("checkListLoad.Activity not defined in the history", logger);
			}
		}else {
			throw new ObjectNotFoundException("checkListLoad. History not found. ID is ZERO", logger);
		}
		return data;
	}

	/**
	 * We will save all checklist questions, regardless completion
	 * @param data
	 * @param user
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public CheckListDTO checklistSave(CheckListDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		if(data.getActivityNodeId()>0) {
			data = validServ.checklist(data);
			Concept activity = closureServ.loadConceptById(data.getActivityNodeId());
			Concept applData = closureServ.loadConceptById(data.getApplNodeId());
			List<Checklistr2> existed = checklistRepo.findAllByActivity(activity);
			List<Checklistr2> tosave = new ArrayList<Checklistr2>();
			for(QuestionDTO qdto :data.getQuestions()) {
				Checklistr2 chl = entityServ.checklist(qdto,activity,applData);
				tosave.add(chl);
			}
			checklistRepo.deleteAll(existed);
			checklistRepo.saveAll(tosave);
		}else {
			throw new ObjectNotFoundException("checklisSave Activity ID is zero",logger);
		}
		return data;
	}

	/**
	 * Submit an application to the NMRA from an applicant
	 * @param data
	 * @param user
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public CheckListDTO submit(CheckListDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		if(data.getHistoryId()>0) {
			//get workflow configuration root
			History curHis = boilerServ.historyById(data.getHistoryId());
			Concept applRoot = closureServ.loadParents(curHis.getApplication()).get(0);
			String applUrl = applRoot.getIdentifier();
			Concept configRoot = closureServ.loadRoot("configuration."+applUrl);
			List<Concept> nextActs = loadActivities(configRoot);
			data = (CheckListDTO) validServ.workflowConfig(nextActs, configRoot, data);
			if(data.isValid()) {
				if(nextActs.size()>0) {
					//close this activity and run others
					curHis=closeActivity(curHis,false);
					activityTrackRun(null,curHis,applUrl, user.getEmail());				//tracking by an applicant
					activityMonitoringRun(null,curHis,applUrl);						//monitoring by the all supervisors
					activitiesBackgroundRun(null,nextActs,curHis);		//all background activities
					Concept firstActivity = new Concept();
					for(Concept act : nextActs) {
						if(!validServ.isActivityBackground(act)) {
							firstActivity=act;
							break;
						}
					}
					if(firstActivity.getID()>0) {
						activityForExecutors(null,firstActivity, curHis);						//the first foreground activity
					}
					//	
				}else {
					//TODO close an application
				}
			}
			return data;
		}else {
			throw new ObjectNotFoundException("submit. History record id is ZERO",logger);
		}
	}
	/**


	/**
	 * Run background activity for supervisor to monitoring this application
	 * @param scheduled - null neans now
	 * @param curHis
	 * @param applUrl
	 * @throws ObjectNotFoundException 
	 */
	private void activityMonitoringRun(Date scheduled, History curHis, String applUrl) throws ObjectNotFoundException {
		//get all supervisors
		List<String> svEmails = new ArrayList<String>();
		List<User> supers = userServ.loadUsersByRole("ROLE_ADMIN");
		for(User sv : supers) {
			if(sv.getEnabled()) {
				svEmails.add(sv.getEmail());
			}
		}
		if(svEmails.size()>0) {
			//activity control
			for(String email : svEmails) {
				//activity control
				Concept activity = createActivityNode("activity.monitor", email);
				//data always null
				Concept activityData=null;
				//open a history record
				openHistory(scheduled,curHis, null, activity, activityData,"");		//there is no activity configuration for application itself
			}
		}else {
			throw new ObjectNotFoundException("runMonitoringActivity. Supervisors not found",logger);
		}

	}
	/**
	 * Run tracking activity for the current user (an Applicant)
	 * @param scheduled  when scheduled, null means now
	 * @param curHis
	 * @param applUrl
	 * @param user
	 * @throws ObjectNotFoundException 
	 */
	private void activityTrackRun(Date scheduled, History curHis, String applUrl, String usersEmail) throws ObjectNotFoundException {
		//activity control
		Concept activity = createActivityNode("activity.trace", usersEmail);
		//data always null
		Concept activityData=null;
		//open a history record
		openHistory(scheduled,curHis, null, activity, activityData,"");		//there is no activity configuration for application itself

	}
	/**
	 * Run activities in background
	 * @param scheduled null means now
	 * @param nextActs
	 * @param curHis
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private void activitiesBackgroundRun(Date scheduled, List<Concept> nextActs, History curHis) throws ObjectNotFoundException {
		for(Concept actConf :nextActs) {
			if(validServ.isActivityBackground(actConf)) {
				activityForExecutors(scheduled,actConf, curHis);
			}
		}
	}
	/**
	 * Run an activity for all executors defined
	 * @param scheduled - null means now
	 * @param actConf
	 * @param curHis
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private void activityForExecutors(Date scheduled, Concept actConf, History curHis) throws ObjectNotFoundException {
		//determine the executive(s)
		List<String> executors = findExecutors(actConf, curHis);
		for(String email :executors) {
			activityCreate(scheduled,actConf, curHis, email,"");		//no notes, because it is the first activity
		}
	}
	/**
	 * Create an activity for a user
	 * @param scheduled if null, now 
	 * @param actConf activity configuration concept
	 * @param curHis current history record
	 * @param email email on an executor
	 * @param notes notes from the previous step (empty string if none)
	 * @throws ObjectNotFoundException
	 */
	@Transactional
	public void activityCreate(Date scheduled, Concept actConf, History curHis, String email, String notes) throws ObjectNotFoundException {
		//cancel all activities with the same configuration
		List<History> allHis = boilerServ.historyAll(curHis.getApplication());
		if(allHis!=null) {
			for(History h : allHis) {
				if(h.getActConfig()!=null) {
					if(h.getActConfig().getID()==actConf.getID()) {
						Concept exec = closureServ.getParent(h.getActivity());
						boolean sameExec = accServ.sameEmail(exec.getIdentifier(), email);
						if(h.getActivityData() != null || sameExec) {
							closeActivity(h, true);
						}
					}
				}
			}
		}
		//create an activity
		String actUrlConf = literalServ.readValue("activityurl", actConf);
		Concept activity = createActivityNode("activity."+actUrlConf, email);
		String checkListDict = literalServ.readValue("checklisturl", actConf);
		activity.setLabel(checkListDict);
		activity=closureServ.save(activity);
		//open a history record
		openHistory(scheduled,curHis, actConf, activity, null, notes);		//activity data is not defined yet
	}
	/**
	 * Open history record for activity given
	 * @param scheduled null means now
	 * @param curHis current history record
	 * @param activity new activity concept
	 * @param activityData new activity data, if one
	 * @param actConf new activity configuration concept
	 * @param prevNotes notes, from the previous step
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private History openHistory(Date scheduled, History curHis, Concept actConf, Concept activity, Concept activityData, String prevNotes) throws ObjectNotFoundException {
		History his =new History();
		his.setApplDict(curHis.getApplDict());
		his.setApplConfig(curHis.getApplConfig());
		his.setActConfig(actConf);
		his.setActivity(activity);
		his.setActivityData(activityData);
		his.setApplication(curHis.getApplication());
		his.setApplicationData(curHis.getApplicationData());
		if(scheduled==null) {
			his.setCome(new Date());
		}else {
			his.setCome(scheduled);
		}
		his.setDataUrl(literalServ.readValue("dataurl", actConf));
		his.setPrevNotes(prevNotes);
		his=boilerServ.saveHistory(his);
		return his;
	}
	/**
	 * Create activity control (activity)
	 * @param actConf
	 * @param eMail
	 * @throws ObjectNotFoundException
	 */
	public Concept createActivityNode(String url, String eMail) throws ObjectNotFoundException {
		Concept root = closureServ.loadRoot(url);
		Concept exec = new Concept();
		exec.setIdentifier(eMail);
		exec=closureServ.saveToTree(root, exec);
		Concept activity=new Concept();
		activity = closureServ.save(activity);
		activity.setIdentifier(activity.getID()+"");
		activity=closureServ.saveToTree(exec, activity);
		return activity;
	}
	/**
	 * Find executors. Apply application type and territory restrictions
	 * Allows to assign an applicant
	 * @param actConf
	 * @param curHis 
	 * @return list of executor's emails
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public List<String> findExecutors(Concept actConf, History curHis) throws ObjectNotFoundException {
		Thing thing = boilerServ.loadThingByNode(actConf);
		//Roles
		List<Concept> roleNodes = new ArrayList<Concept>();
		for(ThingDict td :thing.getDictionaries()) {
			if(td.getVarname().equalsIgnoreCase("executives")) {
				if(td.getConcept().getIdentifier().equalsIgnoreCase("APPLICANT")) {
					List<String> ret = new ArrayList<String>();
					ret.add(accServ.applicantEmailByApplication(curHis.getApplication()));
					return ret;																										//!!!!!!!! applicant is a special case
				}
				roleNodes.add(td.getConcept());
			}
		}
		//Responsibilities
		List<Concept> parents = closureServ.loadParents(curHis.getApplication());
		List<Concept> respNodes = dictServ.guestWorkflows(parents.get(0).getIdentifier());
		//Territory
		String addrUrl = literalServ.readValue("addressurl", actConf);
		Concept addr = new Concept();
		Thing appThing = boilerServ.loadThingByNode(curHis.getApplicationData());
		if(addrUrl.length()>0) {
			for(ThingThing tt : appThing.getThings()) {
				if(tt.getUrl().equalsIgnoreCase(addrUrl)) {
					addr=tt.getConcept();
					break;
				}
			}
		}
		//ask user service
		List<String> ret = userServ.findUsers(roleNodes, respNodes, addr);
		return ret;
	}



	/**
	 * Close this history record
	 * de-activate the current activity
	 * @param curHis
	 * @param cancelled 
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public History closeActivity(History curHis, boolean cancelled) throws ObjectNotFoundException {
		curHis.setGo(new Date());
		if(cancelled) {
			closureServ.removeNode(curHis.getActivityData());
			curHis.setActivityData(null);
		}
		curHis.setCancelled(cancelled);
		curHis = boilerServ.saveHistory(curHis);
		return curHis;
	}
	/**
	 * Load all activities in the workflow
	 * @param root root activity in the configuration
	 * @return
	 */
	@Transactional
	public List<Concept> loadActivities(Concept root) {
		List<Concept> ret = new ArrayList<Concept>();
		if(root!=null) {
			//load all
			List<Concept> all = new ArrayList<Concept>();
			List<Concept> childs = literalServ.loadOnlyChilds(root);
			all.add(root);
			while(childs.size()>0) {
				if(childs.get(0).getActive()) {
					all.add(childs.get(0));
				}
				childs=literalServ.loadOnlyChilds(childs.get(0));
			}
			ret.addAll(all);
		}
		return ret;
	}
	/**
	 * Application or workflow activity?
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ApplicationOrActivityDTO applOrAct(ApplicationOrActivityDTO data) throws ObjectNotFoundException {
		History his = boilerServ.historyById(data.getHistoryId());
		if(his.getActivityData()!=null) {
			data.setApplication(his.getActivityData().getID()==his.getApplicationData().getID());
		}else {
			data.setApplication(false);
		}
		return data;
	}
	/**
	 * All finished activities for revise
	 * This activity for job
	 * @param data
	 * @param user
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ActivityDTO activityLoad(ActivityDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		data.getPath().clear();
		data.getApplication().clear();
		data.getData().clear();
		History curHis = boilerServ.historyById(data.getHistoryId());
		List<History> allHis = boilerServ.historyAll(curHis.getApplication());	//suppose right sort order by Come

		if(accServ.applicationAllowed(allHis,user)) {
			//application is compiled as a list of data items
			ThingDTO applDTO = createApplication(curHis);
			applDTO =thingServ.path(applDTO);
			data.setApplication(applDTO.getPath());
			//all completed activities
			ThingDTO dto = new ThingDTO();
			for(History his : allHis) {
				if(his.getGo() != null && his.getID()!=curHis.getID()) {
					dto = createActivity(user, his, true);
					ThingDTO dt =createLoadActivityData(data, his, true);
					data.getPath().add(dto);
					data.getData().add(dt);
					//notes from the previous step
					String psn="";
					if(his.getPrevNotes()!=null) {
						psn=his.getPrevNotes().trim();
					}
					data.getNotes().add(psn);
					data.getCancelled().add(his.getCancelled());
				}
			}
			//the current activity, if checklist is defined
			if(curHis.getActConfig()!=null) {
				data.setBackground(validServ.isActivityBackground(curHis.getActConfig()));
				String dictUrl = curHis.getActivity().getLabel();
				if(dictUrl!=null && dictUrl.toUpperCase().startsWith("DICTIONAR")) {
					dto = createActivity(user, curHis,false);
					ThingDTO dt =createLoadActivityData(data, curHis, false);
					data.getPath().add(dto);
					data.getData().add(dt);
					//notes from the previous step
					String psn="";
					if(curHis.getPrevNotes()!=null) {
						psn=curHis.getPrevNotes().trim();
					}
					data.getNotes().add(psn);
					data.getCancelled().add(curHis.getCancelled());
				}
			}

			return data;
		}else {
			throw new ObjectNotFoundException("activityLoad. Access denied.",logger);
		}

	}
	/**
	 * Create or a data for an activity if one
	 * @param data
	 * @param history
	 * @param readOnly
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	private ThingDTO createLoadActivityData(ActivityDTO data, History history, boolean readOnly) throws ObjectNotFoundException {
		ThingDTO dto =new ThingDTO();
		if(history.getDataUrl()!=null && history.getDataUrl().length()>0) {
			dto.setUrl(history.getDataUrl());
			dto.setActivityId(history.getActivity().getID());
			dto.setHistoryId(history.getID());
			Concept root = closureServ.loadRoot(dto.getUrl());
			dto.setParentId(root.getID());
			if(history.getActivityData()!=null) {
				dto.setNodeId(history.getActivityData().getID());
			}
			dto=thingServ.createContent(dto);
			if(dto.getNodeId()>0) {
				Concept node=closureServ.loadConceptById(dto.getNodeId());
				dto.setLiterals(dtoServ.readAllLiterals(dto.getLiterals(), node));
				dto.setDates(dtoServ.readAllDates(dto.getDates(),node));
				dto.setNumbers(dtoServ.readAllNumbers(dto.getNumbers(),node));
				dto.setLogical(dtoServ.readAllLogical(dto.getLogical(), node));
			}
			dto.setReadOnly(readOnly);
		}
		return dto;
	}
	/**
	 * Create a minimal application ThingDTO from a history
	 * @param history
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ThingDTO createApplication(History history) throws ObjectNotFoundException {
		ThingDTO ret = new ThingDTO();
		ret.setApplication(true);
		ret.setActivityId(history.getActivity().getID());
		ret.setApplicationUrl(boilerServ.url(history.getApplication()));
		ret.setNodeId(history.getApplicationData().getID());
		ret.setHistoryId(history.getID());
		ret.setApplDictNodeId(history.getApplDict().getID());
		return ret;
	}
	/**
	 * Create a data for activity listed in the history record
	 * @param user
	 * @param his
	 * @param readOnly
	 * @return
	 * @throws ObjectNotFoundException
	 */
	@Transactional
	public ThingDTO createActivity(UserDetailsDTO user, History his, boolean readOnly) throws ObjectNotFoundException {
		ThingDTO dto =new ThingDTO();
		dto.setReadOnly(readOnly);
		String title = literalServ.readPrefLabel(his.getActConfig());
		if(title.length()==0) {
			title=literalServ.readPrefLabel(his.getActivityData());
		}
		if(title.length()==0) {
			title=messages.get("activity.trace");
		}
		dto.setTitle(title);
		Concept exec = closureServ.getParent(his.getApplication());
		Concept appRoot = closureServ.getParent(exec);
		dto.setApplicationUrl(appRoot.getIdentifier());
		dto.setHistoryId(his.getID());
		dto.setNodeId(his.getApplicationData().getID());
		dto.setApplication(true);
		//dto = thingServ.createContent(dto);
		return dto;
	}
	/**
	 * Load my activities. Suit for all, except an applicant
	 * @param data
	 * @param user
	 * @return
	 */
	public ApplicationsDTO myActivities(ApplicationsDTO data, UserDetailsDTO user) {
		data = presentActivities(data, user);
		data = scheduledActivities(data,user);
		return data;
	}
	/**
	 * Activities to execute now
	 * @param data
	 * @param user
	 * @return
	 */
	public ApplicationsDTO presentActivities(ApplicationsDTO data, UserDetailsDTO user) {
		TableQtb table = data.getTable();
		if(table.getHeaders().getHeaders().size()==0) {
			table.setHeaders(myHeaders(table.getHeaders()));
		}
		jdbcRepo.myActivities(user.getEmail());
		String select="select * from _activities";
		String where="Come<=curdate() + INTERVAL 2 DAY";
		List<TableRow> rows =jdbcRepo.qtbGroupReport(select, "", where, table.getHeaders());
		TableQtb.tablePage(rows, table);
		table=boilerServ.translateRows(table);
		table.setSelectable(false);
		data.setTable(table);
		return data;
	}
	/**
	 * Headers for my actual and my scheduled activities 
	 * @param headers
	 * @return
	 */
	private Headers myHeaders(Headers headers) {
		headers.getHeaders().clear();
		headers.getHeaders().add(TableHeader.instanceOf(
				"Come",
				"scheduled",
				true,
				true,
				true,
				TableHeader.COLUMN_LOCALDATE,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"pref",
				"prefLabel",
				true,
				true,
				true,
				TableHeader.COLUMN_STRING,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"applicant",
				"applicant",
				true,
				true,
				true,
				TableHeader.COLUMN_STRING,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"workflow",
				"prod_app_type",
				true,
				true,
				true,
				TableHeader.COLUMN_STRING,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"activity",
				"activity",
				true,
				true,
				true,
				TableHeader.COLUMN_LINK,
				0));
		headers=boilerServ.translateHeaders(headers);
		headers.getHeaders().get(0).setSortValue(TableHeader.SORT_ASC);
		return headers;
	}
	/**
	 * Create scheduled activities table
	 * @param data
	 * @param user
	 * @return
	 */
	private ApplicationsDTO scheduledActivities(ApplicationsDTO data, UserDetailsDTO user) {
		TableQtb table = data.getScheduled();
		if(table.getHeaders().getHeaders().size()==0) {
			table.setHeaders(myHeaders(table.getHeaders()));
		}
		jdbcRepo.myActivities(user.getEmail());
		String select="select * from _activities";
		String where="Come>curdate()";
		List<TableRow> rows =jdbcRepo.qtbGroupReport(select, "", where, table.getHeaders());
		TableQtb.tablePage(rows, table);
		table=boilerServ.translateRows(table);
		table.setSelectable(false);
		data.setScheduled(table);
		return data;
	}


	/**
	 * The same as my activities, however only monitoring activities
	 * @param data
	 * @param user
	 * @return
	 */
	public ApplicationsDTO myMonitoring(ApplicationsDTO data, UserDetailsDTO user) {
		data=presentMonitoring(data, user);
		data=sheduledMonitoring(data,user);
		return data;
	}
	/**
	 * Get scheduled, future monitoring data
	 * @param data
	 * @param user
	 * @return
	 */
	public ApplicationsDTO sheduledMonitoring(ApplicationsDTO data, UserDetailsDTO user) {
		TableQtb table = data.getScheduled();
		if(table.getHeaders().getHeaders().size()==0) {
			table.setHeaders(monitoringHeaders(table.getHeaders(),false));
		}
		jdbcRepo.myMonitoring(user.getEmail());
		String where="Come>curdate()";
		List<TableRow> rows =jdbcRepo.qtbGroupReport("select * from _monitoring", "", where, table.getHeaders());
		TableQtb.tablePage(rows, table);
		table.setSelectable(false);
		data.setScheduled(table);
		return data;
	}


	/**
	 * Get actual monitoring data
	 * @param data
	 * @param user
	 * @return
	 */
	public ApplicationsDTO presentMonitoring(ApplicationsDTO data, UserDetailsDTO user) {
		TableQtb table = data.getTable();
		if(table.getHeaders().getHeaders().size()==0) {
			table.setHeaders(monitoringHeaders(table.getHeaders(),true));
		}
		jdbcRepo.myMonitoring(user.getEmail());
		String where="Come<=curdate()";
		List<TableRow> rows =jdbcRepo.qtbGroupReport("select * from _monitoring", "", where, table.getHeaders());
		TableQtb.tablePage(rows, table);
		table=boilerServ.translateRows(table);
		table.setSelectable(false);
		data.setTable(table);
		return data;
	}

	/**
	 * Create data for activity submit form
	 * Send-submit is not here
	 * @param user 
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ActivitySubmitDTO submitCreateData(UserDetailsDTO user, ActivitySubmitDTO data) throws ObjectNotFoundException {
		data.setApplicant(accServ.isApplicant(user));
		if(data.getHistoryId()>0) {
			if(data.isReload()) {
				data.getActions().getRows().clear();
				data.getExecs().getRows().clear();
				data.getNextJob().getRows().clear();
				data.setReload(false);
			}
			if(data.isReloadExecs()) {
				data.getExecs().getRows().clear();
				data.setReloadExecs(false);
			}
			History his = boilerServ.historyById(data.getHistoryId());
			Concept userConc = closureServ.getParent(his.getActivity());
			if(accServ.isMyActivity(his.getActivity(), user) || accServ.isSupervisor(user)) {
				if(data.getActions().getRows().size()==0) {
					data=createActions(user, data);
				}
				//determine the selected activity
				int selected=-1;
				for(TableRow row : data.getActions().getRows()) {
					if(row.getSelected()) {
						Long ls= new Long(row.getDbID());
						selected=ls.intValue();
					}
				}
				//				systemDictNode(root, "0", messages.get("continue"));
				//				systemDictNode(root, "1", messages.get("route_action"));
				//				systemDictNode(root, "2", messages.get("newactivity"));
				//				systemDictNode(root, "3", messages.get("cancel"));
				//				systemDictNode(root, "4", messages.get("approve"));
				//				systemDictNode(root, "5", messages.get("reject"));
				//				systemDictNode(root, "6", messages.get("reassign"));
				data.getScheduled().getRows().clear();
				switch(selected) {
				case 0:
					data=nextJobChoice(his,user,data);
					data=executorsNextChoice(his,user,data);
					break;
				case 1:
					data.getNextJob().getRows().clear();
					data=executorsThisChoice(his,user,data);
					break;
				case 2:
					data=nextJobChoice(his,user,data);
					data=executorsNextChoice(his,user,data);
					break;
				case 3:
					data.getNextJob().getRows().clear();
					data.getExecs().getRows().clear();
					break;
				case 4:
					data.getNextJob().getRows().clear();
					data.getExecs().getRows().clear();
					data=scheduled(his,data);
					break;
				case 5:
					data.getNextJob().getRows().clear();
					data.getExecs().getRows().clear();
					break;
				case 6:
					data.getNextJob().getRows().clear();
					data=executorsThisChoice(his,user,data);
					break;
				default:
					data.getExecs().getRows().clear();
					data.getNextJob().getRows().clear();
				}
				return data;
			}else {
				throw new ObjectNotFoundException("submitCreateData. Access denied. current_user/should_be " + user.getEmail()+"/"+userConc.getIdentifier(),logger);
			}
		}else {
			throw new ObjectNotFoundException("submitCreateData. History ID is ZERO",logger);
		}
	}
	/**
	 * Create list of scheduled runs in the host lifecycle stage
	 * Approve has been selected
	 * @param his - current history record
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ActivitySubmitDTO scheduled(History his, ActivitySubmitDTO data) throws ObjectNotFoundException {
		//any data may contain the scheduler(s)
		TableQtb table = data.getScheduled();
		if(table.getHeaders().getHeaders().size()==0) {
			table.setHeaders(headersSchedule(table.getHeaders()));
		}
		table.getRows().clear();
		String nextStage = systemServ.nextStageByApplDict(his,true);
		List<History> allHis = boilerServ.historyAll(his.getApplication());
		for(History h : allHis) {
			if(!h.getCancelled()) {		//don't mind cancelled!
				if(h.getActivityData()!=null) {
					Thing th = boilerServ.loadThingByNode(h.getActivityData());
					for(ThingScheduler ts :th.getSchedulers()){
						Scheduler sch = boilerServ.loadSchedulerByNode(ts.getConcept());
						String process = sch.getProcessUrl();
						LocalDate sched = boilerServ.convertToLocalDateViaMilisecond(sch.getScheduled());
						TableRow row = TableRow.instanceOf(ts.getID());		//we need only unique long
						row.getRow().add(TableCell.instanceOf("stages",nextStage));
						row.getRow().add(TableCell.instanceOf("processes",process));
						row.getRow().add(TableCell.instanceOf("scheduled",sched,LocaleContextHolder.getLocale()));
						table.getRows().add(row);
					}
				}
			}
		}
		table.setSelectable(false);
		boilerServ.translateRows(table);
		return data;
	}
	/**
	 * Headers for scheduled table
	 * @param headers
	 * @return
	 */
	private Headers headersSchedule(Headers headers) {
		headers.getHeaders().clear();
		headers.getHeaders().add(TableHeader.instanceOf(
				"stages",
				"stages",
				true,
				false,
				false,
				TableHeader.COLUMN_I18,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"processes",
				"processes",
				true,
				false,
				false,
				TableHeader.COLUMN_I18,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"scheduled",
				"scheduled",
				true,
				false,
				false,
				TableHeader.COLUMN_LOCALDATE,
				0));
		headers=boilerServ.translateHeaders(headers);
		return headers;
	}
	/**
	 * create executor's choice for this activity. To re-assign
	 * @param his
	 * @param user
	 * @param data
	 * @return
	 */
	private ActivitySubmitDTO executorsThisChoice(History his, UserDetailsDTO user, ActivitySubmitDTO data) {
		data.setExecs(executorsTable(his.getActConfig().getID(), data.getExecs()));
		return data;
	}
	/**
	 * Propose all possible executors for activity selected
	 * @param his
	 * @param user
	 * @param data
	 * @return
	 */
	private ActivitySubmitDTO executorsNextChoice(History his, UserDetailsDTO user, ActivitySubmitDTO data) {
		Long nextActConfId = data.nextActivity(); 
		if(nextActConfId>0) {
			data.setExecs(executorsTable(nextActConfId, data.getExecs()));
		}else {
			data.getExecs().getRows().clear();
		}
		return data;
	}
	/**
	 * Propose  all possible foreground activities, mark next
	 * @param his
	 * @param user
	 * @param data
	 * @return
	 */
	private ActivitySubmitDTO nextJobChoice(History his, UserDetailsDTO user, ActivitySubmitDTO data) {
		if(data.getNextJob().getRows().size()==0) {
			data.setNextJob(nextJobsTable(his, data.getNextJob()));
		}
		return data;
	}
	/**
	 * Create a set of allowed actions using dictionary.system.submit
	 * @param user
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ActivitySubmitDTO createActions(UserDetailsDTO user, ActivitySubmitDTO data) throws ObjectNotFoundException {
		History curHis = boilerServ.historyById(data.getHistoryId());
		List<String> allowed = new ArrayList<String>();
		if(data.isSupervisor()) {
			//NMRA supervisor
			if(data.isSupervisor()) {
				data=validServ.actionCancel(curHis,data);
				if(data.isValid()) {
					allowed.add("3");
				}
				data=validServ.actionNew(curHis,data);
				if(data.isValid()) {
					allowed.add("2");
				}
				data=validServ.actionReassign(curHis,data);
				if(data.isValid()) {
					allowed.add("6");
				}
			}else {
				throw new ObjectNotFoundException("createAction. Supervisor access denied for "+user.getEmail(),logger);
			}
		}else {
			if(data.isApplicant()) {
				data=validServ.submitNext(curHis,user, data);
				if(data.isValid()) {
					allowed.add("0");
				}
			}else {
				data=validServ.submitNext(curHis,user, data);
				if(data.isValid()) {
					allowed.add("0");
				}
				data=validServ.submitRoute(curHis,user,data);
				if(data.isValid()) {
					allowed.add("1");
				}
				data=validServ.submitAddActivity(curHis,user,data);
				if(data.isValid()) {
					allowed.add("2");
				}
				data=validServ.submitApproveReject(curHis,user,data);
				if(data.isValid()) {
					allowed.add("4");
					allowed.add("5");
				}
			}
		}

		DictionaryDTO actDict = systemServ.submitActionDictionary();
		TableQtb dictTable = actDict.getTable();
		List<Concept> items = new ArrayList<Concept>();
		for(TableRow row : dictTable.getRows()) {
			Concept conc = closureServ.loadConceptById(row.getDbID());
			if(allowed.contains(conc.getIdentifier())) {
				items.add(conc);
			}
		}
		data=actionsTable(items, data);
		data.clearErrors();
		return data;
	}

	/**
	 * Table contains all submit actions possible in this case
	 * @param items
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ActivitySubmitDTO actionsTable(List<Concept> items, ActivitySubmitDTO data) throws ObjectNotFoundException {
		TableQtb table = data.getActions();
		if(table.getHeaders().getHeaders().size()==0) {
			table.setHeaders(headersActions(table.getHeaders()));
		}
		for(Concept item: items) {
			TableRow row = new TableRow();
			try {
				long dbID = Long.parseLong(item.getIdentifier());
				row.setDbID(dbID);
				String pref = literalServ.readPrefLabel(item);
				String descr=literalServ.readDescription(item);
				row.getRow().add(TableCell.instanceOf("pref", pref));
				row.getRow().add(TableCell.instanceOf("description", descr));
				//if(dbID==0) {
				//	row.setSelected(true);
				//}
				table.getRows().add(row);
				table.setSelectable(!data.isApplicant());
			} catch (NumberFormatException e) {
				throw new ObjectNotFoundException("actionsTable. Invalid action code code/id "+
						item.getIdentifier()+"/"+item.getID(),logger);
			}
		}
		return data;
	}
	/**
	 * Headers for actions table
	 * @param headers
	 * @return
	 */
	private Headers headersActions(Headers headers) {
		headers.getHeaders().add(TableHeader.instanceOf(
				"pref",
				"label_actions",
				true,
				false,
				false,
				TableHeader.COLUMN_STRING,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"description",
				"description",
				true,
				false,
				false,
				TableHeader.COLUMN_STRING,
				0));
		headers.setPageSize(50);
		boilerServ.translateHeaders(headers);
		return headers;
	}
	/**
	 * Table contains all possible executors of the next activity
	 * @param nextActConfId
	 * @param execTable
	 * @return
	 */
	private TableQtb executorsTable(long nextActConfId, TableQtb execTable) {
		if(execTable.getRows().size()==0) {
			execTable.setHeaders(headersExecutors(execTable.getHeaders()));
			jdbcRepo.executorsActivity(nextActConfId);
			List<TableRow> rows = jdbcRepo.qtbGroupReport("select * from executors_activity", "", "", execTable.getHeaders());
			execTable.setSelectable(true);
			for(TableRow row : rows) {
				row.setSelected(true);
			}
			TableQtb.tablePage(rows, execTable);
		}
		return execTable;
	}
	/**
	 * Headers for Executor's table
	 * @param headers
	 * @return
	 */
	private Headers headersExecutors(Headers headers) {
		headers.getHeaders().clear();
		headers.getHeaders().add(TableHeader.instanceOf(
				"uname",
				"global_name",
				true,
				false,
				false,
				TableHeader.COLUMN_STRING,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"orgname",
				"organizationauthority",
				true,
				false,
				false,
				TableHeader.COLUMN_STRING,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"email",
				"executor_email",
				true,
				false,
				false,
				TableHeader.COLUMN_STRING,
				0));
		headers=boilerServ.translateHeaders(headers);
		headers.setPageSize(Integer.MAX_VALUE);
		return headers;
	}
	/**
	 * Create nextJob table if needed
	 * @param his activity configuration root
	 * @param nextJob table with all foreground activities in this application
	 * @return
	 */
	@Transactional
	private TableQtb nextJobsTable(History his, TableQtb nextJob) {
		if(nextJob.getRows().size()==0) {
			nextJob.setHeaders(headersNextJob(nextJob.getHeaders()));
			jdbcRepo.workflowActivities(his.getApplConfig().getID());
			List<TableRow> rows = jdbcRepo.qtbGroupReport("select * from workflow_activities", "",
					"bg!=1", nextJob.getHeaders());
			nextJob.setSelectable(true);
			TableQtb.tablePage(rows, nextJob);
			//mark next activity
			for(int i=0; i<nextJob.getRows().size(); i++) {
				if(nextJob.getRows().get(i).getDbID()==his.getActConfig().getID()) {
					if(i+1<nextJob.getRows().size()) {
						nextJob.getRows().get(i+1).setSelected(true);
						break;
					}
				}
			}
		}
		return nextJob;
	}
	/**
	 * headers for next
	 * @param headers
	 * @return
	 */
	private Headers headersNextJob(Headers headers) {
		headers.getHeaders().clear();
		headers.getHeaders().add(TableHeader.instanceOf(
				"pref",
				"activity",
				true,
				false,
				false,
				TableHeader.COLUMN_STRING,
				0));
		headers.getHeaders().add(TableHeader.instanceOf(
				"descr",
				"description",
				true,
				false,
				false,
				TableHeader.COLUMN_STRING,
				0));
		headers=boilerServ.translateHeaders(headers);
		headers.setPageSize(Integer.MAX_VALUE);
		return headers;
	}
	/**
	 * Is selected activity traced or monitoring (not configurable) activity
	 * @param user
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ActivityDTO activityHistoryIsMonitoring(UserDetailsDTO user, ActivityDTO data) throws ObjectNotFoundException {
		if(data.getHistoryId()>0) {
			data.clearErrors();
			History his = boilerServ.historyById(data.getHistoryId());
			if(his.getActConfig()==null) {
				data.setIdentifier(messages.get("error_finishmonia"));
				data.setValid(false);
			}
			return data;
		}else {
			throw new ObjectNotFoundException("activityHistoryIsMonitoring. History ID is ZERO",logger);
		}
	}

	/**
	 * Perform a submit action required by a NMRA executor or supervisor user 
	 * @param user
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ActivitySubmitDTO submitSend(UserDetailsDTO user, ActivitySubmitDTO data) throws ObjectNotFoundException {
		History curHis=boilerServ.historyById(data.getHistoryId());
		if(accServ.isActivityExecutor(curHis.getActivity(), user) || accServ.isSupervisor(user)) {
			int actCode = data.actionSelected();
			if(data.isApplicant()) {
				if(actCode != 0) {
					throw new ObjectNotFoundException("submitSend. Access denied. Activity 0",logger);
				}
			}
			/*systemDictNode(root, "0", messages.get("continue"));
				systemDictNode(root, "1", messages.get("route_action"));
				systemDictNode(root, "2", messages.get("newactivity"));
				systemDictNode(root, "3", messages.get("cancel"));
				systemDictNode(root, "4", messages.get("approve"));
				systemDictNode(root, "5", messages.get("reject"));
				systemDictNode(root, "6", messages.get("reassign"));*/
			switch(actCode){
			case 0:			//NMRA executor continue workflow from the activity selected
				data=validServ.submitNext(curHis, user, data);
				if(accServ.isApplicant(user) && data.executors().size()==0){
					data=submitNext(curHis, user, data);
				}else {
					data=validServ.submitNextData(curHis, user, data);
					data=submitNext(curHis, user, data);
				}
				return data;
			case 1:		//NMRA executor route the activity to other executor
				data=validServ.submitRoute(curHis, user, data);
				data=validServ.submitRouteData(curHis, user, data);
				data=submitRoute(curHis, user, data);
				return data;
			case 2:		//NMRA executor initiate an additional activity
				data=validServ.submitAddActivity(curHis, user, data);
				data=validServ.submitAddActivityData(curHis, user, data);
				data=submitAddActivity(curHis,user,data);
				return data;
			case 3:
				data=validServ.actionCancel(curHis, data);
				data=validServ.actionCancelData(curHis, data);
				data=actionCancel(curHis, data);
				return data;
			case 4:
				data=validServ.submitApproveReject(curHis, user, data);
				data=validServ.submitApproveRejectData(curHis, user, data);
				data=submitApprove(curHis, user, data);
				return data;
			case 5:
				data=validServ.submitApproveReject(curHis, user, data);
				data=validServ.submitApproveRejectData(curHis, user, data);
				data=submitReject(curHis, user, data);
				return data;
			case 6:
				data=validServ.actionReassign(curHis, data);
				data=validServ.submitRouteData(curHis, user, data);
				data=submitRoute(curHis, user, data);
				return data;
			default:
				data.setIdentifier(messages.get("pleaseselectaction"));
				data.setValid(false);
				return data;
			}
		}else {
			throw new ObjectNotFoundException("submitSend. Access denied",logger);
		}
	}

	/**
	 * Reject an application and move it to archive
	 * @param curHis
	 * @param user
	 * @param data
	 * @return
	 */
	private ActivitySubmitDTO submitReject(History curHis, UserDetailsDTO user, ActivitySubmitDTO data) {
		// TODO MOCK!!!!
		data.setIdentifier(messages.get("EPERM_ABOUT"));
		data.setValid(false);
		return data;
	}
	/**
	 * Submit to approve or reject
	 * @TODO not implemented yet
	 * @param curHis
	 * @param user
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ActivitySubmitDTO submitApprove(History curHis, UserDetailsDTO user, ActivitySubmitDTO data) throws ObjectNotFoundException {
		if(systemServ.isGuest(curHis)) {
			data=submitGuest(curHis,data);
			return data;
		}
		if(systemServ.isHost(curHis)) {
			data=submitHost(curHis,data);
			return data;
		}
		data.setIdentifier(messages.get("invalidstage"));
		data.setValid(false);
		return data;
	}
	/**
	 * Submit Host application, create another scheduled host application
	 * Currently it is the same as submit guest
	 * @param curHis
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private ActivitySubmitDTO submitHost(History curHis, ActivitySubmitDTO data) throws ObjectNotFoundException {
		data=submitGuest(curHis, data);
		return data;
	}
	/**
	 * Submit guest application
	 * @param curHis
	 * @param data 
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ActivitySubmitDTO submitGuest(History curHis, ActivitySubmitDTO data) throws ObjectNotFoundException {										
		data = runScheduledGuestHost(curHis, data);
		if(data.isValid()) {
			cancellActivities(curHis);
		}
		return data;
	}

	/**
	 * Run scheduled activities as well as related trace and monitoring ones
	 * @param curHis
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private ActivitySubmitDTO runScheduledGuestHost(History curHis, ActivitySubmitDTO data) throws ObjectNotFoundException { 
		for(TableRow row :data.getScheduled().getRows()) {
			ThingScheduler ts = boilerServ.thingSchedulerById(row.getDbID());
			Scheduler sch = boilerServ.loadSchedulerByNode(ts.getConcept());
			Concept dictConc = systemServ.hostDictNode(sch.getProcessUrl());
			data=createHostApplication(curHis,sch,dictConc, data);
			if(!data.isValid()) {
				return data;
			}
		}
		return data;
	}
	/**
	 * Create a new host application and the first activity for it
	 * @param prevHis 
	 * @param sch
	 * @param dictConc
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private ActivitySubmitDTO createHostApplication(History prevHis, Scheduler sch, Concept dictConc, ActivitySubmitDTO data) throws ObjectNotFoundException {
		String applUrl=sch.getProcessUrl();
		String applicantEmail = accServ.applicantEmailByApplication(prevHis.getApplication());
		Concept applRoot = closureServ.loadRoot(sch.getProcessUrl());
		Concept owner= new Concept();
		owner.setIdentifier(applicantEmail);
		owner=closureServ.saveToTree(applRoot, owner);
		Concept applConc=new Concept();
		applConc = closureServ.save(applConc);
		applConc.setIdentifier(applConc.getID()+"");
		applConc=closureServ.saveToTree(owner, applConc);
		Concept configRoot = closureServ.loadRoot("configuration."+applUrl);
		List<Concept> nextActs = loadActivities(configRoot);
		data = (ActivitySubmitDTO) validServ.workflowConfig(nextActs, configRoot, data);
		if(data.isValid()) {
			if(nextActs.size()>0) {
				//close this activity and run others
				History curHis=createHostHistorySample(prevHis, applConc, dictConc, configRoot);
				activityTrackRun(sch.getScheduled(),curHis,applUrl, applicantEmail);				//tracking by an applicant
				activityMonitoringRun(sch.getScheduled(),curHis,applUrl);								//monitoring by the all supervisors
				activitiesBackgroundRun(sch.getScheduled(),nextActs,curHis);						//all background activities
				Concept firstActivity = new Concept();
				for(Concept act : nextActs) {
					if(!validServ.isActivityBackground(act)) {
						firstActivity=act;
						break;
					}
				}
				if(firstActivity.getID()>0) {
					activityForExecutors(sch.getScheduled(),firstActivity, curHis);							//the first foreground activity
				}
				//	
			}else {
				throw new ObjectNotFoundException("createHostApplications."+messages.get("badconfiguration") + data.getIdentifier()
				+applUrl,logger);
			}
		}else {
			throw new ObjectNotFoundException("createHostApplications. "+data.getIdentifier() + " "+ applUrl,logger);
		}
		return data;
	}
	/**
	 * Create a sample for history in host stage
	 * It is a sample for the real history records
	 * @param prevHis
	 * @param applConc
	 * @param dictConc
	 * @param configRoot
	 * @return
	 */
	private History createHostHistorySample(History prevHis, Concept applConc, Concept applDict, Concept applConfig) {
		History his = new History();
		his.setApplConfig(applConfig);
		his.setApplDict(applDict);
		his.setApplication(applConc);
		his.setApplicationData(prevHis.getApplicationData());
		return his;
	}
	/**
	 * Cancel all opened activities for the current application, but close the current
	 * @param curHis
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public void cancellActivities(History curHis) throws ObjectNotFoundException {
		List<History> allHis = boilerServ.historyAll(curHis.getApplication());
		for(History his : allHis) {
			if(his.getID()!=curHis.getID() && his.getGo()==null) {
				closeActivity(his, true);
			}
		}
		closeActivity(curHis,false);
	}
	/**
	 *Cancel and activity
	 * @param curHis
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private ActivitySubmitDTO actionCancel(History curHis, ActivitySubmitDTO data) throws ObjectNotFoundException {
		closeActivity(curHis, true);
		return data;
	}
	/**
	 * re-route this activity to others executors. Data will be lost!
	 * @param curHis
	 * @param user
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ActivitySubmitDTO submitRoute(History curHis, UserDetailsDTO user, ActivitySubmitDTO data) throws ObjectNotFoundException {
		if(data.isValid()) {
			List<Long> executors = data.executors();
			//create activities
			for(Long execId : executors) {
				Concept userConc = closureServ.loadConceptById(execId);
				activityCreate(null,curHis.getActConfig(), curHis, userConc.getIdentifier(), data.getNotes().getValue());
			}
			closeActivity(curHis, true);
		}
		return data;
	}

	/**
	 * Submit to the next activity selected by user
	 * @param curHis
	 * @param user
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	private ActivitySubmitDTO submitNext(History curHis, UserDetailsDTO user, ActivitySubmitDTO data) throws ObjectNotFoundException {
		if(data.isValid()) {
			if(accServ.isApplicant(user)) {
				//restore next activity and executor's list
				data.getNextJob().getRows().clear();
				data.getExecs().getRows().clear();
				data=nextJobChoice(curHis,user,data);
				data=executorsNextChoice(curHis,user,data);
			}
			closeActivity(curHis,false);
			submitAddActivity(curHis,user, data);
		}
		return data;
	}
	/**
	 * Add activities for all executors listed
	 * @param curHis
	 * @param user 
	 * @param data
	 * @throws ObjectNotFoundException
	 */
	public ActivitySubmitDTO submitAddActivity(History curHis, UserDetailsDTO user, ActivitySubmitDTO data) throws ObjectNotFoundException {
		if(data.isValid()) {
			List<Long> executors = data.executors();
			long actConfId = data.nextActivity();
			Concept actConf = closureServ.loadConceptById(actConfId);
			if(executors.size()>0) {
				for(Long execId : executors) {
					Concept userConc = closureServ.loadConceptById(execId);
					activityCreate(null,actConf, curHis, userConc.getIdentifier(), data.getNotes().getValue());
				}
			}else {
				//user sends activity to himself
				activityCreate(null,actConf, curHis,user.getEmail(), data.getNotes().getValue());
			}
		}
		return data;
	}

	/**
	 * Try to done parallel activity or inform that this activity is the last (data.done=false)
	 * @param user
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ActivityDTO activityDone(UserDetailsDTO user, ActivityDTO data) throws ObjectNotFoundException {
		if(data.getHistoryId()>0) {
			History his = boilerServ.historyById(data.getHistoryId());
			if(accServ.isActivityExecutor(his.getActivity(), user) || accServ.isSupervisor(user)) {
				if(firstExecutor(his)){
					data.setDone(false);
				}else {
					his=closeActivity(his,false);
					data.setDone(true);
				}
				return data;
			}else {
				throw new ObjectNotFoundException("activityBackgroundDone. Access is denied for "+ user.getEmail(),logger);
			}

		}else {
			throw new ObjectNotFoundException("activityDone. History ID is ZERO",logger);
		}
	}

	/**
	 * Done the background activity
	 * @param user
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ActivityDTO activityBackgroundDone(UserDetailsDTO user, ActivityDTO data) throws ObjectNotFoundException {
		if(data.getHistoryId()>0) {
			History curHis=boilerServ.historyById(data.getHistoryId());
			if(accServ.isActivityExecutor(curHis.getActivity(), user) || accServ.isSupervisor(user)) {
				curHis=closeActivity(curHis,false);
			}else {
				throw new ObjectNotFoundException("activityBackgroundDone. Access is denied for "+ user.getEmail(),logger);
			}
		}else {
			throw new ObjectNotFoundException("activityBackgroundDone. History ID is ZERO",logger);
		}
		return data;
	}

	/**
	 * Am first completed the concurrent activity?
	 * @param his
	 * @return
	 */
	@Transactional
	public boolean firstExecutor(History curHis) {
		List<History> allHis = boilerServ.historyAll(curHis.getApplication());
		boolean hasActivities = false;
		for(History his :allHis) {
			if(his.getGo()==null) {
				hasActivities=true;
				break;
			}
		}
		for(History his : allHis) {
			if(his.getID()!=curHis.getID()) {																//not this
				if(his.getGo()!=null) {																		//closed
					if(his.getActConfig()!=null && curHis.getActConfig()!=null) {		//safe to check :)
						if(his.getActConfig().getID()==curHis.getActConfig().getID()) {	//someone done it
							return hasActivities;
						}
					}
				}
			}
		}
		return true;
	}
	/**
	 * Save a thing
	 * @param data
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ThingDTO thingSaveUnderParent(ThingDTO data, UserDetailsDTO user) throws ObjectNotFoundException {
		data = validServ.thing(data);
		if(data.isValid() || !data.isStrict() && accServ.writeAllowed(data, user)) {
			data.setStrict(true);									//to ensure the next
			Concept node = new Concept();
			if(data.getNodeId()==0) {
				if(data.getParentId()>0) {
					Concept parent = closureServ.loadConceptById(data.getParentId());
					node = closureServ.save(node);
					node.setIdentifier(node.getID()+"");
					node=closureServ.saveToTree(parent, node);
				}else {
					throw new ObjectNotFoundException("thingSave. Parent node is ZERO",logger);
				}
			}else {
				node=closureServ.loadConceptById(data.getNodeId());
			}
			data.setNodeId(node.getID());

			//thing
			Thing thing = new Thing();
			thing = boilerServ.loadThingByNode(node, thing);
			thing.setConcept(node);
			thing.setUrl(data.getUrl());

			//store data under the node and thing
			data = thingServ.storeLiterals(node, data);
			data = thingServ.storeDates(node,data);
			data = thingServ.storeNumbers(node, data);
			data = thingServ.storeLogical(node, data);
			data = thingServ.storeDictionaries(thing,data);
			data = thingServ.storeDocuments(thing, data);
			data = thingServ.storeSchedule(user, node, thing, data);
			data = thingServ.storeRegister(user, thing, data);
			//title
			String title = literalServ.readPrefLabel(node);
			if(title.length()>3) {
				data.setTitle(title);
			}
			//store a thing
			thing=boilerServ.saveThing(thing);
			if(data.getHistoryId()>0) {
				History his = boilerServ.historyById(data.getHistoryId());
				his.setActivityData(node);
				his=boilerServ.saveHistory(his);
			}
		}

		return data;
	}

}