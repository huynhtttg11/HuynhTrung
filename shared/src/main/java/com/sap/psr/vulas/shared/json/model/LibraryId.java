package com.sap.psr.vulas.shared.json.model;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Human-readable library ID, for instance, a Maven artifact identifier.
 * Can be sorted using group, artifact, timestamp and version (if timestamp is null).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LibraryId implements Serializable, Comparable<Object> {

	private static final long serialVersionUID = 1L;

	private static Log log = LogFactory.getLog(LibraryId.class);
	
	
	
	@JsonIgnore
	private Long id;

	@JsonProperty("group")
	private String mvnGroup;

	private String artifact;
	
	private String version;
	
		
	
	public LibraryId() { super(); }
	
	/**
	 * @param group
	 * @param artifact
	 * @param version
	 */
	public LibraryId(String group, String artifact, String version) {
		super();
		this.mvnGroup = group;
		this.artifact = artifact;
		this.version = version;
	}
	
	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getMvnGroup() { return mvnGroup; }
	public void setMvnGroup(String group) { this.mvnGroup = group; }

	public String getArtifact() { return artifact; }
	public void setArtifact(String artifact) { this.artifact = artifact; }

	public String getVersion() { return version; }
	public void setVersion(String version) { this.version = version; }

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((artifact == null) ? 0 : artifact.hashCode());
		result = prime * result + ((mvnGroup == null) ? 0 : mvnGroup.hashCode());
		result = prime * result + ((version == null) ? 0 : version.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LibraryId other = (LibraryId) obj;
		if (artifact == null) {
			if (other.artifact != null)
				return false;
		} else if (!artifact.equals(other.artifact))
			return false;
		if (mvnGroup == null) {
			if (other.mvnGroup != null)
				return false;
		} else if (!mvnGroup.equals(other.mvnGroup))
			return false;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}
	
	@Override
	public final String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("[").append(this.getMvnGroup()).append("|").append(this.getArtifact()).append("|").append(this.getVersion()).append("]");
		return builder.toString();
	}
	
	/**
	 * Compares this library ID with the provided library ID using, in that order,
	 * group, artifact, or version.
	 */
	@Override
	public int compareTo(Object _other) {
		if(_other instanceof LibraryId) {
			final LibraryId other_libid = (LibraryId)_other;
			int result = this.getMvnGroup().compareToIgnoreCase(other_libid.getMvnGroup());
			if(result==0)
				result = this.getArtifact().compareToIgnoreCase(other_libid.getArtifact());
			if(result==0){
				Version v = new Version(this.getVersion());
				result = v.compareTo(new Version(other_libid.getVersion()));
			}
			return result;
		}
		else {
			throw new IllegalArgumentException("Expected object of type LibraryId, got [" + _other.getClass().getName() + "]");
		}
	}
	

	


}
