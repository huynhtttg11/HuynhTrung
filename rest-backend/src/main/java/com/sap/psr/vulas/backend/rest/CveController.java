package com.sap.psr.vulas.backend.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.sap.psr.vulas.backend.cve.Cve;
import com.sap.psr.vulas.backend.cve.CveReader2;
import com.sap.psr.vulas.backend.model.Bug;
import com.sap.psr.vulas.backend.repo.BugRepository;
import com.sap.psr.vulas.shared.util.StopWatch;
import com.sap.psr.vulas.shared.util.StringUtil;
import com.sap.psr.vulas.shared.util.VulasConfiguration;

@RestController
@CrossOrigin("*")
@RequestMapping("/cves")
public class CveController {
	
	private static Logger log = LoggerFactory.getLogger(CveController.class);
	
	private static String CACHE_REFRESH_ALL = "vulas.backend.cveCache.refetchAllMs";
	private static String CACHE_REFRESH_SNG = "vulas.backend.cveCache.refetchSingleMs";
	
	private Thread cveCacheFetch = null;
	
	/**
	 * Creates a thread pre-fetching the CVEs for all bugs.
	 * This thread shall be started by using the REST endpoint {@link #startRefresh()}.
	 * Note: If it would be started right away, multiple backend instances would update the database in parallel.
	 */
	@Autowired
	CveController(BugRepository bugRepository) {
		final BugRepository bug_repo = bugRepository;
		
		// Refresh CVE cache
		final long refresh_all = VulasConfiguration.getGlobal().getConfiguration().getLong(CACHE_REFRESH_ALL, -1);
		final long refresh_sng = VulasConfiguration.getGlobal().getConfiguration().getLong(CACHE_REFRESH_SNG, 60000);
		
		if(refresh_all==-1) {
			log.warn("Periodic update of cached CVE data: Disabled");
		}
		else {
			log.info("Periodic update of cached CVE data: Every " + StringUtil.msToMinString(refresh_all));
			this.cveCacheFetch = new Thread(new Runnable() {
				public void run() {
					boolean force = false;
					
					while(true) {
						
						// Loop all bugs
						final StopWatch sw = new StopWatch("Refresh of cached CVE data").setTotal(bug_repo.count()).start();
						for(Bug b: bug_repo.findAll()) {
							try {
								final boolean update_happened = bug_repo.updateCachedCveData(b, force);
								if(update_happened)
									Thread.sleep(new Double(refresh_sng + Math.random() * refresh_sng).longValue());
							} catch (InterruptedException e) {
								CveController.log.error("Interrupted exception while refreshing cached CVE data of bug [" + b.getBugId() + "]: " + e.getMessage());
							}
							sw.progress();
						}						
						sw.stop();
						
						// Wait before entering the loop another time
						try {
							Thread.sleep(new Double(refresh_all + Math.random() * refresh_all).longValue());
						} catch (InterruptedException e) {
							CveController.log.error("Interrupted exception while refreshing cached CVE data: " + e.getMessage());
						}
						
						// Force refresh
						force = true;
					}
				}
			}, "CveCacheFetch");
			this.cveCacheFetch.setPriority(Thread.MIN_PRIORITY);
		}
	}
	
	/**
	 * Returns the {@link Cve} with the given ID, e.g., CVE-2014-0050.
	 * @param digest
	 * @return 404 {@link HttpStatus#NOT_FOUND} if library with given digest does not exist, 200 {@link HttpStatus#OK} if the library is found
	 */
	@RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = {"application/json;charset=UTF-8"})
	public ResponseEntity<Cve> getCve(@PathVariable String id) {
		try {
			final Cve cve = CveReader2.read(id);
			return new ResponseEntity<Cve>(cve, HttpStatus.OK);
		}
		catch(Exception enfe) {
			return new ResponseEntity<Cve>(HttpStatus.NOT_FOUND);
		}
	}
	
	/**
	 * Returns the {@link Cve} with the given ID, e.g., CVE-2014-0050.
	 * @param digest
	 * @return 404 {@link HttpStatus#NOT_FOUND} if library with given digest does not exist, 200 {@link HttpStatus#OK} if the library is found
	 */
	@RequestMapping(value = "/refreshCache", method = RequestMethod.POST)
	public ResponseEntity<Boolean> startRefresh() {
		boolean started = false;
		try {
			if(this.cveCacheFetch==null) {
				log.info("CVE cache refresh disabled");
			}
			else if(this.cveCacheFetch.isAlive()) {
				log.info("CVE cache refresh already running");
			}
			else {
				this.cveCacheFetch.start();
				started = true;
				log.info("CVE cache refresh started");
			}
			return new ResponseEntity<Boolean>(started, HttpStatus.OK);
		}
		catch(Exception e) {
			log.error("Exception when starting CVE cache refresh: " + e.getMessage(), e);
			return new ResponseEntity<Boolean>(started, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}