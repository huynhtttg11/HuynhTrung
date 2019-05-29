package com.sap.psr.vulas.backend.requests;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sap.psr.vulas.core.util.CoreConfiguration;
import com.sap.psr.vulas.goals.GoalContext;
import com.sap.psr.vulas.shared.util.VulasConfiguration;

public abstract class AbstractHttpRequest implements HttpRequest {

	private static final Log log = LogFactory.getLog(AbstractHttpRequest.class);

	protected long ms = -1;

	/** Null in case the request does not exist on disk. */
	private String objFile = null;

	/** Goal context, required to set the Http headers. */
	protected transient GoalContext context = null;
	
	protected AbstractHttpRequest() {
		this.ms = System.nanoTime();
	}

	public String getObjectFilename() {
		return this.getFilename() + ".obj";
	}

	public Path getObjectPath() {
		return Paths.get(this.getVulasConfiguration().getDir(CoreConfiguration.UPLOAD_DIR).toString(), this.getObjectFilename());
	}
	
	@Override
	public HttpRequest setGoalContext(GoalContext _ctx) {
		this.context = _ctx;
		return this;
	}
	
	@Override
	public GoalContext getGoalContext() {
		return this.context;
	}
	
	protected VulasConfiguration getVulasConfiguration() {
		if(this.context!=null && this.context.getVulasConfiguration()!=null)
			return this.context.getVulasConfiguration();
		else
			return VulasConfiguration.getGlobal();
	}

	/**
	 * First calls {@link HttpRequest#savePayloadToDisk()}, then serializes the request and writes it to disk.
	 */
	@Override
	public final void saveToDisk() throws IOException {

		// Subclasses can write additional stuff to disk (e.g., payloads)
		this.savePayloadToDisk();

		// Then save the request itself
		final File obj_file  = this.getObjectPath().toFile();
		this.objFile = getObjectFilename();
		final ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(obj_file));
		oos.writeObject(this);
		oos.close();
		AbstractHttpRequest.log.info("Request object written to [" + obj_file + "]");
	}

	/**
	 * Calls {@link HttpRequest#loadPayloadToDisk()}.
	 */
	@Override
	public final void loadFromDisk() throws IOException {
		this.loadPayloadFromDisk();
	}

	/**
	 * First calls {@link HttpRequest#deletePayloadToDisk()}, then deletes the saved request itself.
	 */
	@Override
	public final void deleteFromDisk() throws IOException {
		if(this.getVulasConfiguration().getConfiguration().getBoolean(CoreConfiguration.UPLOAD_DEL_AFTER, true)) {
			this.deletePayloadFromDisk();
			if(this.objFile!=null)
				this.getObjectPath().toFile().deleteOnExit();
		}
	}

	/**
	 * Just calls the default method {@link ObjectOutputStream#defaultWriteObject()}.
	 * @param out
	 * @throws IOException
	 */
	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject(); 
	}

	/**
	 * Calls the default method {@link ObjectInputStream#defaultReadObject()}.
	 * @param in
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
	}
}
