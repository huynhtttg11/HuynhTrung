package com.sap.psr.vulas.monitor;

import java.util.Observable;
import java.util.Observer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * Calls a sequence methods of a given {@link ExecutionMonitor} in order upload collected
 * information to the Vulas backend. The {@link UploadScheduler} runs either once or periodically,
 * depending on the way it was constructed. 
 */
public class UploadScheduler extends Observable implements Runnable, Observer {
	
	private static final Log log = LogFactory.getLog(UploadScheduler.class);
	
	private long millis = -1;
	private int batchSize = -1;
	private boolean enabled = true;
	private ExecutionMonitor monitor = null;

	public UploadScheduler(ExecutionMonitor _monitor) { this(_monitor, -1, -1); }
	public UploadScheduler(ExecutionMonitor _monitor, long _millis, int _batch_size) { this.monitor = _monitor; this.millis = _millis; this.batchSize = _batch_size; }

	public void enabled() { this.enabled=true; }
	public void disable() { this.enabled=false; }
	public boolean isEnabled() { return this.enabled; }
	public long getInterval() { return this.millis; }
	public int getBatchSize() { return this.batchSize; }

	/**
	 * Calls a sequence of methods of a {@link ExecutionMonitor}.
	 */
	public void run() {
		// Final upload
		if(this.millis==-1) {
			
			// Stop further instrumentation and collection (that may happen in the course of the following stmts)
			this.monitor.setPaused(true);
			DynamicTransformer.getInstance().setTransformationEnabled(false);
			
			// Upload stuff
			this.monitor.uploadInformation();
			this.monitor.awaitUpload();
			this.monitor.stopGoal();
			
			// Log instrumentation stats
			InstrumentationControl.logOverallStatistics();
			
			// Notify others that the final upload took place
			this.notifyObservers();
		}
		// Periodic uploads
		else {
			while(this.isEnabled()) {
				try {
					Thread.sleep(this.millis);
					if(this.isEnabled()) {
						this.monitor.uploadInformation(this.batchSize);
						
						// Log instrumentation stats
						InstrumentationControl.logOverallStatistics();
					}
				} catch (InterruptedException e) {
					UploadScheduler.log.error("Error in periodic trace upload: " + e.getMessage());
				}
			}
		}

	}

	/**
	 * Disables periodic uploads (called by the shutdown uploader).
	 */
	public void update(Observable obj, Object arg) {
		this.disable();
		UploadScheduler.log.info("Uploader disabled");
	}
}
