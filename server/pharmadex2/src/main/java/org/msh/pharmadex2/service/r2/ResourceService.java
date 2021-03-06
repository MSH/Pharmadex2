package org.msh.pharmadex2.service.r2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.msh.pdex2.dto.table.TableQtb;
import org.msh.pdex2.dto.table.TableRow;
import org.msh.pdex2.exception.ObjectNotFoundException;
import org.msh.pdex2.i18n.Messages;
import org.msh.pdex2.model.r2.Concept;
import org.msh.pdex2.model.r2.FileResource;
import org.msh.pdex2.model.r2.History;
import org.msh.pdex2.model.r2.Thing;
import org.msh.pdex2.model.r2.ThingDoc;
import org.msh.pdex2.repository.common.JdbcRepository;
import org.msh.pdex2.services.r2.ClosureService;
import org.msh.pharmadex2.controller.common.DocxView;
import org.msh.pharmadex2.dto.AssemblyDTO;
import org.msh.pharmadex2.dto.FileResourceDTO;
import org.msh.pharmadex2.dto.PersonSelectorDTO;
import org.msh.pharmadex2.dto.ResourceDTO;
import org.msh.pharmadex2.service.common.BoilerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Responsible for all operations with resources
 * and close related things like sites, persons, etc.
 * @author alexk
 *
 */
@Service
public class ResourceService {
	private static final Logger logger = LoggerFactory.getLogger(ResourceService.class);
	@Autowired
	private Messages messages;
	@Autowired
	private ClosureService closureServ;
	@Autowired
	private LiteralService literalServ;
	@Autowired
	private BoilerService boilerServ;
	@Autowired
	private JdbcRepository jdbcRepo;
	@Autowired
	ResolverService resolverServ;
	/**
	 * Read a logo from resource or default one
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public Resource logo() throws ObjectNotFoundException {
		Concept node=fileNode("images.design", "resources.system.logo");
		if(node != null) {
			FileResource fres=boilerServ.fileResourceByNode(node);
			return new ByteArrayResource(fres.getFile());
		}else {
			return new ByteArrayResource(messages.loadNmraLogo().getBytes());
		}
	}
	
	/**
	 * Load NMRA footer
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public Resource footer() throws ObjectNotFoundException {
		Concept node=fileNode("images.design", "resources.system.logo.footer");
		if(node != null) {
			FileResource fres=boilerServ.fileResourceByNode(node);
			return new ByteArrayResource(fres.getFile());
		}else {
			return new ByteArrayResource(messages.loadNmraLogo().getBytes());
		}
	}
	
	
	/**
	 * Read a file resource as ByteArrayResource
	 * @param url
	 * @param varName
	 * @return null, if not found
	 * @throws ObjectNotFoundException 
	 */
	private Concept fileNode(String url, String varName) throws ObjectNotFoundException {
		Concept node = resourceNode(url);
		Thing thing = boilerServ.thingByNode(node);
		for(ThingDoc td :thing.getDocuments()) {
			String dictNodeUrl = literalServ.readValue("url", td.getDictNode());
			if(dictNodeUrl.equalsIgnoreCase(varName)) {
				return td.getConcept();
			}
		}
		return null;
	}
	/**
	 * Create a table to allow the usage of the resources
	 * assumed the headers have been created
	 * @param res
	 * @param resDto 
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ResourceDTO table(AssemblyDTO res, ResourceDTO resDto) throws ObjectNotFoundException {
		Concept node = resourceNode(res.getUrl());
		Thing thing=boilerServ.thingByNode(node, new Thing());
		if(thing.getDocuments().size()>0) {
			ThingDoc td = thing.getDocuments().iterator().next();
			TableQtb table = resDto.getTable();
			String select ="select * from resource_read";
			Concept dictRoot = closureServ.getParent(td.getDictNode());
			jdbcRepo.resource_read(dictRoot.getID());
			List<TableRow> rows= jdbcRepo.qtbGroupReport(select, "", "url='"+td.getDocUrl()+"'", table.getHeaders());
			TableQtb.tablePage(rows, table);
			table.setSelectable(false);
		}
		return resDto;
	}
	/**
	 * load a node of resource
	 * @param url
	 * @return node or throw the exception
	 * @throws ObjectNotFoundException 
	 */
	private Concept resourceNode(String url) throws ObjectNotFoundException {
		Concept root = closureServ.loadRoot("configuration.resources");
		String lang = LocaleContextHolder.getLocale().toString().toUpperCase();
		List<Concept> all_Langs = literalServ.loadOnlyChilds(root);
		for(Concept langNode : all_Langs) {
			if(langNode.getIdentifier().equalsIgnoreCase(lang)) {
				List<Concept> allNodes = literalServ.loadOnlyChilds(langNode);
				for(Concept node : allNodes) {
					if(node.getIdentifier().equalsIgnoreCase(url) && node.getActive()) {
						return node;
					}
				}
			}
		}
		throw new ObjectNotFoundException("resourceNode. Node not found for resource url "+url,logger);
	}


	/**
	 * Prepare Download a file resource.
	 * File concept id in in resDto.nodeId
	 * The current application history id is in resDto.historyId
	 * @param resDto
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public ResourceDTO prepareResourceDownload(ResourceDTO resDto) throws ObjectNotFoundException {
		if(resDto.getNodeId()>0) {
			Concept node=closureServ.loadConceptById(resDto.getNodeId());
			FileResource fres=boilerServ.fileResourceByNode(node);
			resDto.setFileName(node.getLabel());
			resDto.setFileSize(fres.getFileSize());
			resDto.setContentDisp("inline");
			resDto.setMediaType(fres.getMediatype());
			resDto.setFileId(fres.getID());
			return resDto;
		}else {
			throw new ObjectNotFoundException("resourceDownload. File node id is ZERO",logger);
		}

	}
	/**
	 * Real process a file from resources and downlaod it
	 * @param fres
	 * @return
	 * @throws ObjectNotFoundException 
	 * @throws IOException 
	 */
	@Transactional
	public Resource fileResolve(ResourceDTO fres) throws ObjectNotFoundException, IOException {
		FileResource file = boilerServ.fileResourceById(fres.getFileId());
		if(fres.getFileName().toUpperCase().endsWith(".DOCX")) {
			InputStream stream = new ByteArrayInputStream(file.getFile());
			DocxView dx = new DocxView(stream,boilerServ);
			logger.trace("init model");
			Map<String,Object> model = dx.initModel();
			logger.trace("resolve model");
			model = resolverServ.resolveModel(model,fres);
			stream.reset();
			DocxView px = new DocxView(stream,boilerServ);
			logger.trace("resolve document");
			px.resolveDocument(model, true);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			px.getDoc().write(out);
			byte[] arr = out.toByteArray();
			px.getDoc().close();
			return new ByteArrayResource(arr);

		}else {
			return new ByteArrayResource(file.getFile());
		}
	}

	/**
	 * For current persons are resources.
	 * Frankly, this controller is less bloated ...
	 * @param dto
	 * @return
	 * @throws ObjectNotFoundException 
	 */
	@Transactional
	public PersonSelectorDTO personSelectorTable(PersonSelectorDTO dto) throws ObjectNotFoundException {
		TableQtb table = dto.getTable();
		if(table.getHeaders().getHeaders().size()==0) {
			table.setHeaders(boilerServ.headersPersonSelector(table.getHeaders()));
		}
		//save selection
		List<Long> selected = boilerServ.saveSelectedRows(table);
		//get data
		if(dto.getHistoryId()>0) {
			History his = boilerServ.historyById(dto.getHistoryId());
			String lang = LocaleContextHolder.getLocale().toString().toUpperCase();
			String where = "appldataid='"+his.getApplicationData().getID() +
					//"' and personrooturl='"+dto.getPersonUrl()+
					"' and lang='"+lang+"'";
			List<TableRow> rows =jdbcRepo.qtbGroupReport("select * from personlist","", where, table.getHeaders());
			TableQtb.tablePage(rows, table);
			//restore selections
			table=boilerServ.selectedRowsRestore(selected, table);
		}
		return dto;
	}

}
