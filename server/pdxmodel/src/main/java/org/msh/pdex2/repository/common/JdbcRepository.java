package org.msh.pdex2.repository.common;

import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.msh.pdex2.dto.table.HasRow;
import org.msh.pdex2.dto.table.Headers;
import org.msh.pdex2.dto.table.TableHeader;
import org.msh.pdex2.dto.table.TableRow;
import org.msh.pdex2.model.r2.Concept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


/**
 * Sometimes DTO should be created directly from result of JDBC query,
 * because traditional JPA repository became complex and/or inefficient
 * @author Alex Kurasoff
 *
 */
@Repository
public class JdbcRepository {
	private static final Logger logger = LoggerFactory.getLogger(JdbcRepository.class);
	@Autowired
	JdbcTemplate jdbcTemplate;

	/**
	 * Select rows with GroupBy phrase
	 * @param select - select, from, joins phrases
	 * @param groupByHaving groupBy and having phrases
	 * @param mainWhere expression that must been in where
	 * @param headers for additional where filters and 
	 * @return
	 */
	public List<TableRow> qtbGroupReport(String select, String groupByHaving, String mainWhere, Headers headers){
		String sql=createFullSelect(select, groupByHaving, mainWhere, headers);
		return selectQuery(sql, headers);
	}

	/**
	 * create SELECT for future use
	 * @param select main select expression, typically loaded from "query" table of the database. Should not be empty or null
	 * @param groupByHaving aux group by and having phrase.
	 * @param mainWhere should be added to "where" phrase unconditionally, e.g. "id="+ myObj.getId()
	 * @param headers table headers that also may contain search and sort conditions
	 * @return string with SQL operator
	 */
	public String createFullSelect(String select, String groupByHaving, String mainWhere, Headers headers){
		return createQtbSQL(select, mainWhere, headers.createGeneralWhere(), headers.createWhere(), groupByHaving, headers.createOrderBy());
	}

	/**
	 * Issue an SQL SELECT operator and then put result to TableRows for future use
	 * @param select
	 * @param headers
	 * @return
	 */
	public List<TableRow> selectQuery(String select, Headers headers){
		List<TableRow> ret = new ArrayList<TableRow>();
		ret = jdbcTemplate.query(select, new QtbRowMapper(headers));
		return ret;
	}


	/**
	 * Create SQL operator for qtb query - select, filters, order by
	 * @param select
	 * @param mainWhere 
	 * @param generalWhere general search field uses "or" condition
	 * @param where column's filters use "and" condition
	 * @param groupByHaving 
	 * @param orderBy
	 * @return
	 */
	private String createQtbSQL(String select, String mainWhere, List<String> generalWhere, List<String> where, String groupByHaving, List<String> orderBy) {
		String sql = select;
		String generalWherePhrase="";
		String wherePhrase = "";
		for(String s: generalWhere){
			if(generalWherePhrase.length()==0){
				generalWherePhrase="("+s+")";
			}else{
				generalWherePhrase = generalWherePhrase +" or (" + s + ")";
			}
		}
		for(String s : where){
			if(wherePhrase.length()==0){
				wherePhrase="("+s+")";
			}else{
				wherePhrase = wherePhrase +" and (" + s + ")";
			}
		}
		String orderByPhrase = "";
		for(String s : orderBy){
			if(orderByPhrase.length() == 0){
				orderByPhrase=s;
			}else{
				orderByPhrase = orderByPhrase + ", " + s;
			}
		}

		String commonWhere="";
		if(generalWherePhrase.length()>0){
			commonWhere = "(" + generalWherePhrase + ")";
		}
		if(wherePhrase.length()>0){
			if(commonWhere.length()>0){
				commonWhere = commonWhere + " and ("+wherePhrase +")";
			}else{
				commonWhere= wherePhrase;
			}
		}
		if(mainWhere.length()>0){
			if(commonWhere.length()>0){
				commonWhere = commonWhere + " and ("+mainWhere+")";
			}else{
				commonWhere = mainWhere;
			}
		}
		if(commonWhere.length()>0){
			sql = sql + " WHERE " + commonWhere;
		}

		if(groupByHaving.length()>0){
			sql = sql + " " + groupByHaving;
		}

		if(orderByPhrase.length()>0){
			sql = sql + " ORDER BY " + orderByPhrase;
		}
		return sql;
	}

	/**
	 * Drop and create table
	 * @param table
	 * @param createSQL
	 * @throws SQLException 
	 * @returns list of column names of just created table
	 */
	public List<TableHeader> dropAndCreateTable(String table, String createSQL) throws SQLException {
		List<TableHeader> ret = new ArrayList<>();
		dropTable(table);
		jdbcTemplate.execute(createSQL);
		DatabaseMetaData meta = jdbcTemplate.getDataSource().getConnection().getMetaData();
		ResultSet columns = meta.getColumns(null,null, table, null);
		while(columns.next())
		{
			String columnName = columns.getString("COLUMN_NAME");
			int dataType = columns.getInt("DATA_TYPE");
			int columnType=TableHeader.COLUMN_STRING;
			switch (dataType){
			case java.sql.Types.DATE:
				columnType=TableHeader.COLUMN_LOCALDATE;
				break;
			case java.sql.Types.BOOLEAN:
				columnType=TableHeader.COLUMN_BOOLEAN_CHECKBOX;
				break;
			case java.sql.Types.INTEGER:
			case java.sql.Types.BIGINT:
			case java.sql.Types.SMALLINT:
			case java.sql.Types.TINYINT:
				columnType=TableHeader.COLUMN_LONG;
				break;
			default:
				//String
			}	
			TableHeader header = TableHeader.instanceOf(columnName,
					columnName,
					false,
					false,
					false,
					columnType,
					0);
			ret.add(header);
		}
		return ret;
	}

	/**
	 * Insert batch of rows to the table
	 * @param table name of table
	 * @param headers names of columns along with types
	 * @param rows array of TableCell
	 */
	public void insertBatchRows(String table, List<TableHeader> headers, List<HasRow> rows) {
		String insert="insert into "+table;
		String columns = "";
		String data="";
		for(TableHeader header : headers){
			if(columns.length()==0){
				columns=header.getKey();
			}else{
				columns=columns+","+header.getKey();
			}
			if(data.length()==0){
				data="?";
			}else{
				data=data+", ?";
			}
		}
		insert = insert+ "("+columns+") values("+data+")";
		final int INSERT_BATCH_SIZE= 200;
		for (int i = 0; i < rows.size(); i += INSERT_BATCH_SIZE){
			final List<HasRow> batchList = rows.subList(i, i
					+ INSERT_BATCH_SIZE > rows.size() ? rows.size() : i
							+ INSERT_BATCH_SIZE);
			jdbcTemplate.batchUpdate(insert,
					new BatchPreparedStatementSetter() {
				@Override
				public void setValues(PreparedStatement ps, int index) throws SQLException {
					for(int i=0;i<headers.size();i++){
						Object value = batchList.get(index).getManagedRow().getRow().get(i).getOriginalValue();
						if(value!=null){
							switch(headers.get(i).getColumnType()){
							case TableHeader.COLUMN_LOCALDATE:
								if(value instanceof String) {
									logger.error("wrong raw data " + batchList.get(index).getManagedRow().getRow().get(i).getOriginalValue() );
								}
								LocalDate dt = (LocalDate) value;
								ps.setDate(i+1, Date.valueOf(dt));/// new java.sql.Date(dt.toDate().getTime()));
								break;
							case TableHeader.COLUMN_BOOLEAN_CHECKBOX:
								boolean val = (boolean) value;
								ps.setBoolean(i+1, val);
								break;
							case TableHeader.COLUMN_LONG:
								Long lval = (Long) value;
								ps.setLong(i+1, lval);
								break;
							default:
								if(value instanceof String){
									ps.setString(i+1, (String) value);
								}
							}
						}else{
							ps.setNull(i+1,0);
						}
					}
				}
				@Override
				public int getBatchSize() {
					return batchList.size();
				}
			});
		}
	}

	/**
	 * Drop a table
	 * @param table
	 */
	@Transactional(propagation=Propagation.REQUIRES_NEW)
	public void dropTable(String table) {
		jdbcTemplate.execute("drop table if exists "+table);
	}

	/**
	 * Reset AUTO_INCREMENT in forecast result tables
	 */
	@Transactional(propagation=Propagation.REQUIRES_NEW)
	public void resetAutoIncrement() {
		jdbcTemplate.execute("ALTER TABLE `stockforecast` AUTO_INCREMENT = 0");
		jdbcTemplate.execute("ALTER TABLE `dailyforecast` AUTO_INCREMENT = 0");
		jdbcTemplate.execute("ALTER TABLE `regimen` AUTO_INCREMENT = 0");
		jdbcTemplate.execute("ALTER TABLE `phase` AUTO_INCREMENT = 0");
		jdbcTemplate.execute("ALTER TABLE `regimenforecast` AUTO_INCREMENT = 0");
		jdbcTemplate.execute("ALTER TABLE `parameters` AUTO_INCREMENT = 0");
		jdbcTemplate.execute("ALTER TABLE `medication` AUTO_INCREMENT = 0");
		jdbcTemplate.execute("ALTER TABLE `demandforecast` AUTO_INCREMENT = 0");
	}

	/**
	 * Remove forecast and regimen for all quantification
	 * 
	 */
	@Transactional(propagation=Propagation.REQUIRES_NEW)
	public void deleteResultTabs(){
		jdbcTemplate.execute("DELETE FROM `collector`.`stockforecast` ");
		jdbcTemplate.execute("DELETE FROM `collector`.`dailyforecast` ");
		jdbcTemplate.execute("DELETE FROM `collector`.`regimen` ");
		jdbcTemplate.execute("DELETE FROM `collector`.`phase` ");
		jdbcTemplate.execute("DELETE FROM `collector`.`regimenforecast` ");
		jdbcTemplate.execute("DELETE FROM `collector`.`parameters` ");
		jdbcTemplate.execute("DELETE FROM `collector`.`medication` ");
		jdbcTemplate.execute("DELETE FROM `collector`.`demandforecast` ");
		resetAutoIncrement();
	}

	/**
	 * Remove/update data related to a collection
	 * E.g. remove forecast results
	 * @param sql sql operator for update, contains parameter ?
	 * @param iD
	 * @returns number of removed rows
	 */
	@Transactional(propagation=Propagation.REQUIRED)
	public int updateColectionData(String sql, long iD){
		return jdbcTemplate.update(sql, iD);
	}
	/**
	 * Remove/update data related to a quantification
	 * E.g. remove forecast results
	 * @param sql
	 * @param id
	 */
	@Transactional(propagation=Propagation.REQUIRED)
	public int updateQuantificationData(String sql, long id) {
		return jdbcTemplate.update(sql, id);
	}
	/**
	 * Prepare temporary table _dictlevel with the data of the required  dictionary level 
	 * @param parentID id of the immediate parent
	 */
	public void prepareDictionaryLevel(long parentID) {
		SimpleJdbcCall proc = new SimpleJdbcCall(jdbcTemplate);
		proc.withProcedureName("dictlevel");
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("parent", parentID);
		params.addValue("lang", LocaleContextHolder.getLocale().toString().toUpperCase());
		proc.execute(params);
	}
	/**
	 * Prepare temporary table  _prefdescription contains pref labels and descriptions
	 */
	public void preparePrefDescription() {
		SimpleJdbcCall proc = new SimpleJdbcCall(jdbcTemplate);
		proc.withProcedureName("prefdescription");
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("lang", LocaleContextHolder.getLocale().toString().toUpperCase());
		proc.execute(params);
		
	}

	/**
	 * Call stored procedure to prepare the file list
	 * @param id
	 * @param thingNodeId
	 * @param locale
	 */
	public void prepareFileList(long dictRootId, long thingNodeId) {
		SimpleJdbcCall proc = new SimpleJdbcCall(jdbcTemplate);
		proc.withProcedureName("filelist");
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("dictrootid", dictRootId);
		params.addValue("thingid", thingNodeId);
		params.addValue("lang", LocaleContextHolder.getLocale().toString().toUpperCase());
		proc.execute(params);
	}
	/**
	 * Persons related to the node to temporary table _persons
	 * @param nodeId
	 */
	public void persons(long nodeId) {
		SimpleJdbcCall proc = new SimpleJdbcCall(jdbcTemplate);
		proc.withProcedureName("persons");
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("parent", nodeId);
		params.addValue("lang", LocaleContextHolder.getLocale().toString().toUpperCase());
		proc.execute(params);
	}
	/**
	 * Load list of activities from the database to table workflow_activities
	 * @param id
	 */
	public void workflowActivities(long id) {
		SimpleJdbcCall proc = new SimpleJdbcCall(jdbcTemplate);
		proc.withProcedureName("workflow_activities");
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("rootid", id);
		params.addValue("lang", LocaleContextHolder.getLocale().toString().toUpperCase());
		proc.execute(params);
	}
	/**
	 * All possible executors for activity
	 * @param actConfigId
	 */
	public void executorsActivity(long actConfigId) {
		SimpleJdbcCall proc = new SimpleJdbcCall(jdbcTemplate);
		proc.withProcedureName("executors_activity");
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("actconfigid", actConfigId);
		params.addValue("lang", LocaleContextHolder.getLocale().toString().toUpperCase());
		proc.execute(params);
		
	}
	/**
	 * Read register's records by regester's url
	 * @param url
	 */
	public void readRegisterByUrl(String url) {
		SimpleJdbcCall proc = new SimpleJdbcCall(jdbcTemplate);
		proc.withProcedureName("site_registers");
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("url", url);
		proc.execute(params);
	}
	/**
	 * Get all opened activities by user's email
	 * @param email
	 */
	public void myActivities(String email) {
		SimpleJdbcCall proc = new SimpleJdbcCall(jdbcTemplate);
		proc.withProcedureName("activities");
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("appl", null);
		params.addValue("go", false);
		params.addValue("exec", email);
		params.addValue("lang", LocaleContextHolder.getLocale().toString().toUpperCase());
		proc.execute(params);
		
	}
	/**
	 * Get all opened monitoring activities by user's email
	 * @param email
	 */
	public void myMonitoring(String email) {
		SimpleJdbcCall proc = new SimpleJdbcCall(jdbcTemplate);
		proc.withProcedureName("monitoring");
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("exec", email);
		params.addValue("lang", LocaleContextHolder.getLocale().toString().toUpperCase());
		proc.execute(params);
	}
	/**
	 * Get registered objects
	 * @param email - owner's email - null means all
	 * @param rootUrl - root URL, i.e., ws.site - null means all
	 */
	public void registerdObjects(String email, String rootUrl) {
		SimpleJdbcCall proc = new SimpleJdbcCall(jdbcTemplate);
		proc.withProcedureName("registered");
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("email", email);
		params.addValue("url", rootUrl);
		params.addValue("lang", LocaleContextHolder.getLocale().toString().toUpperCase());
		proc.execute(params);
	}
	/**
	 * Remove concept using stored proc
	 * @param node
	 */
	@Transactional
	public void removeConcept(Concept node) {
		SimpleJdbcCall proc = new SimpleJdbcCall(jdbcTemplate);
		proc.withProcedureName("remove_branch");
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("root", node.getID());
		proc.execute(params);
		
	}
	/**
	 * Report sites
	 * @param dataUrl, site data url, i.e. pharmacy.site
	 * @param dictStageUrl life cycle stage dictionary - guest, host, shutdown (dictionary.guest.applications, dictionary.host.applications, dictionary.shutdown.applications)
	 * @param addressUrl - under which addresses of the site are stored, i.e. 'pharamcy.site.address'
	 * @param ownerUrl - under which owners of the site are stored, i.e. pharmacy.site.owners
	 * @param inspectAppUrl under which inspection applications are stored, i.e. application.pharmacy.inspection
	 * @param renewAppUrl under which renewal applications are stored, i.e. application.pharmacy.renew
	 */
	public void report_sites(String dataUrl, String dictStageUrl, String addressUrl, String ownerUrl,
			String inspectAppUrl, String renewAppUrl) {
			SimpleJdbcCall proc = new SimpleJdbcCall(jdbcTemplate);
			proc.withProcedureName("report_sites");
			MapSqlParameterSource params = new MapSqlParameterSource();
			params.addValue("site_url", dataUrl);
			params.addValue("dict_stage_url", dictStageUrl);
			params.addValue("addr_url", addressUrl);
			params.addValue("owner_url", ownerUrl);
			params.addValue("appl_inspection_url", inspectAppUrl);
			params.addValue("appl_renew_url", renewAppUrl);
			params.addValue("lang", LocaleContextHolder.getLocale().toString().toUpperCase());
			proc.execute(params);
		
	}


}