package com.sap.psr.vulas.backend.component;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sap.psr.vulas.backend.model.Application;
import com.sap.psr.vulas.backend.model.GoalExecution;
import com.sap.psr.vulas.backend.model.VulnerableDependency;
import com.sap.psr.vulas.backend.model.view.Views;
import com.sap.psr.vulas.backend.repo.ApplicationRepository;
import com.sap.psr.vulas.backend.repo.GoalExecutionRepository;
import com.sap.psr.vulas.shared.enums.ExportFormat;
import com.sap.psr.vulas.shared.json.JacksonUtil;
import com.sap.psr.vulas.shared.json.JsonBuilder;
import com.sap.psr.vulas.shared.util.StopWatch;
import com.sap.psr.vulas.shared.util.StringUtil;

@Component(value="csvProducerThread")
@Scope("prototype")
@Transactional(readOnly=true, propagation=Propagation.REQUIRED)
public class ApplicationExporterThread implements Runnable {

	private static Logger log = LoggerFactory.getLogger(ApplicationExporterThread.class);

	private final DateFormat dateFormat  = new SimpleDateFormat("dd/MM/yyyy");

	private static final String lb = System.getProperty("line.separator");

	@Autowired
	GoalExecutionRepository gexeRepository;

	@Autowired
	ApplicationRepository appRepository;

	private String separator = ";";

	private String[] includeSpaceProperties = null;

	private String[] includeGoalConfiguration = null;

	private String[] includeGoalSystemInfo = null;

	private String[] bugs = null;

	private StringBuffer buffer = new StringBuffer();
	
	private HashMap<Long, HashMap<String, Boolean>> affectedApps = null;

	private List<Application> apps = null;

	private ExportFormat format = null;
	
	private boolean includeAllBugs = false;
	
	private boolean includeExemptions = false;
	
	public ApplicationExporterThread setSeparator(String separator) {
		this.separator = separator;
		return this;
	}

	public ApplicationExporterThread setIncludeSpaceProperties(String[] includeSpaceProperties) {
		this.includeSpaceProperties = includeSpaceProperties;
		return this;
	}

	public ApplicationExporterThread setIncludeGoalConfiguration(String[] includeGoalConfiguration) {
		this.includeGoalConfiguration = includeGoalConfiguration;
		return this;
	}

	public ApplicationExporterThread setIncludeGoalSystemInfo(String[] includeGoalSystemInfo) {
		this.includeGoalSystemInfo = includeGoalSystemInfo;
		return this;
	}

	public ApplicationExporterThread setApps(List<Application> apps) {
		this.apps = apps;
		return this;
	}	

	public ApplicationExporterThread setBugs(String[] bugs) {
		this.bugs = bugs;
		return this;
	}
	
	public HashMap<Long, HashMap<String, Boolean>> getAffectedApps() {
		return affectedApps;
	}

	public ApplicationExporterThread setAffectedApps(HashMap<Long, HashMap<String, Boolean>> affectedApps) {
		this.affectedApps = affectedApps;
		return this;
	}	

	public ExportFormat getFormat() {
		return format;
	}

	public void setFormat(ExportFormat format) {
		this.format = format;
	}

	public StringBuffer getBuffer() {
		return buffer;
	}
	
	public boolean isIncludeAllBugs() {
		return includeAllBugs;
	}

	public ApplicationExporterThread setIncludeAllBugs(boolean includeAllBugs) {
		this.includeAllBugs = includeAllBugs;
		return this;
	}

	public boolean isIncludeExemptions() {
		return includeExemptions;
	}

	public ApplicationExporterThread setIncludeExemptions(boolean includeExemptions) {
		this.includeExemptions = includeExemptions;
		return this;
	}

	@Transactional(readOnly=true, propagation=Propagation.REQUIRED) // Needed in order to lazy load properties when called async
	public void run() {
		// Show progress
		final StopWatch sw = new StopWatch("Worker thread: Produce [" + this.format + "] for [" + apps.size() + "] apps");
		sw.setTotal(this.apps.size());
		sw.start();
		
		// Separate emails by ; unless it is the CSV column separator
		final String email_separator = separator.equals(";") ? "," : ";";
		
		for(Application a: this.apps) {
			
			// Always produce both and decide at the end
			final StringBuffer csv = new StringBuffer();
			final JsonBuilder json = new JsonBuilder().startObject();
			
			try {
				// Space
				csv.append(a.getSpace().getSpaceToken()).append(separator).append(a.getSpace().getSpaceName()).append(separator);
				json.startObjectProperty("workspace");
				json.appendObjectProperty("token", a.getSpace().getSpaceToken())
				    .appendObjectProperty("name", a.getSpace().getSpaceName());
				json.startArrayProperty("owners");
				if(a.getSpace().getSpaceOwners()!=null && a.getSpace().getSpaceOwners().size()>0) {
					int i = 0;
					for(String o: a.getSpace().getSpaceOwners()) {
						json.appendToArray(o);
						// Email addresses in CSV (comma or semicolon-separated)
						if(i++>0) csv.append(email_separator);
						csv.append(o);
					}
				}
				json.endArray();
				csv.append(separator);
				
				if(includeSpaceProperties!=null && includeSpaceProperties.length>0) {
					for(String p: includeSpaceProperties) {
						final String value = a.getSpace().getPropertyValue(p);
						csv.append(value==null?"":value).append(separator);
						json.appendObjectProperty(p, value);
					}
				}
				
				json.endObject();

				// Application
				csv.append(a.getId()).append(separator).append(a.getMvnGroup()).append(separator).append(a.getArtifact()).append(separator).append(a.getVersion()).append(separator);
				csv.append(this.dateFormat.format(a.getCreatedAt().getTime()));
				//entry.append(separator).append(a.countDependencies()).append(separator).append(a.countConstructs());
				json.startObjectProperty("app");
				json.appendObjectProperty("id", a.getId().toString())
			    	.appendObjectProperty("group", a.getMvnGroup())
			    	.appendObjectProperty("artifact", a.getArtifact())
			    	.appendObjectProperty("version", a.getVersion());
				if(a.getCreatedAt()!=null)
					json.appendObjectProperty("createdAt", this.dateFormat.format(a.getCreatedAt().getTime()));
				else
					json.appendObjectProperty("createdAt", (String)null);
				json.endObject();
				
				// Is the current app affected by the given bugs
				if(!StringUtil.isEmptyOrContainsEmptyString(this.bugs) && this.affectedApps!=null) {
					json.startObjectProperty("vulns");
					final HashMap<String, Boolean> affected_app = this.affectedApps.get(a.getId());
					for(String b: this.bugs) {
						Boolean affected = false;
						if(affected_app!=null && affected_app.containsKey(b)) {
							affected = affected_app.get(b); // Can be true or null
						}
						if(affected==null) {
							csv.append(separator).append("unconfirmed");
							json.appendObjectProperty(b, "unconfirmed");
						}
						else if(affected==true) {
							csv.append(separator).append("affected");
							json.appendObjectProperty(b, "affected");
						}
						else {
							csv.append(separator).append("not affected");
							json.appendObjectProperty(b, "not affected");
						}
					}
					json.endObject();
				}
				
				// Include all vulnerable dependencies of the current app (JSON format only)
				if(ExportFormat.JSON.equals(this.getFormat()) && this.includeAllBugs) {
					json.startArrayProperty("vulnerableDependencies");
					final TreeSet<VulnerableDependency> vd_all = this.appRepository.findAppVulnerableDependencies(a, this.includeExemptions, false);
					for(VulnerableDependency vd: vd_all) {
						json.appendJsonToArray(JacksonUtil.asJsonString(vd, null, Views.Default.class));
					}
					json.endArray();
				}

				// Stuff from goal execution
				final GoalExecution latest_goal_exe = gexeRepository.findLatestGoalExecution(a, null);
				if(latest_goal_exe!=null) {
					csv.append(separator).append(this.dateFormat.format(latest_goal_exe.getCreatedAt().getTime())).append(separator).append(latest_goal_exe.getClientVersion());
					json.startObjectProperty("lastGoalExecution");
					json.appendObjectProperty("timestamp", this.dateFormat.format(latest_goal_exe.getCreatedAt().getTime()))
					    .appendObjectProperty("client", latest_goal_exe.getClientVersion());
					
					// Goal config
					if(!StringUtil.isEmptyOrContainsEmptyString(this.includeGoalConfiguration)) {
						//log.info("Get goal configuration for " + latest_goal_exe); //TODO: Delete
						for(String p: includeGoalConfiguration) {
							final String prop = latest_goal_exe.getConfiguration(p);
							csv.append(separator).append(prop==null?"":prop);
							json.appendObjectProperty(p, prop);
						}
					}

					// Sys info
					if(!StringUtil.isEmptyOrContainsEmptyString(includeGoalSystemInfo)) {
						//log.info("Get system info for " + latest_goal_exe); //TODO: Delete
						for(String p: includeGoalSystemInfo) {
							final String prop = latest_goal_exe.getSystemInfo(p);
							csv.append(separator).append(prop==null?"":prop);
							json.appendObjectProperty(p, prop);
						}
					}
					json.endObject();
				} else {
					csv.append(separator).append("").append(separator).append("").append(separator).append("");
					json.appendObjectProperty("lastGoalExecution", (String)null);
					
					// Goal config
					if(!StringUtil.isEmptyOrContainsEmptyString(this.includeGoalConfiguration))
						for(String p: includeGoalConfiguration)
							csv.append(separator).append("");

					// Sys info
					if(!StringUtil.isEmptyOrContainsEmptyString(includeGoalSystemInfo))
						for(String p: includeGoalSystemInfo)
							csv.append(separator).append("");
				}

				csv.append(lb);
				json.endObject();
				
				// Append to buffer according to format
				if(ExportFormat.CSV.equals(this.format))
					buffer.append(csv);
				else
					buffer.append(buffer.length()==0?"":",").append(json.toString());
				
				sw.progress();
			} catch (Exception e) {
				log.error("[" + e.getClass().getName() + "] while appending data for app " + a + ", entry [" + csv.toString() + "] will not be appended to [" + this.format + "]: " + e.getMessage());
			}
		}
		sw.stop();
	}
}