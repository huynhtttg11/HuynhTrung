package com.sap.psr.vulas.shared.util;

import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.validation.constraints.NotNull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class AbstractFileSearch extends SimpleFileVisitor<Path> {
	
	private static Log log = null;
	private static final Log getLog() {
		if(AbstractFileSearch.log==null)
			AbstractFileSearch.log = LogFactory.getLog(AbstractFileSearch.class);
		return AbstractFileSearch.log;
	}
	
	private final Set<Path> files = new TreeSet<Path>();
	
	protected boolean foundFile(Path _p) { return this.files.contains(_p); }
	
	protected void addFile(Path _p) { this.files.add(_p); }
	
	/**
	 * Clears previous search results.
	 */
	public void clear() { this.files.clear(); }
	
	/**
	 * Starts searching for files in the given paths, with unlimited depth.
	 * @param _path the FS path to search in
	 * @returns a set of paths for all files found
	 */
	public Set<Path> search(Set<Path> _paths) { return this.search(_paths, java.lang.Integer.MAX_VALUE); }
	
	/**
	 * Starts searching for files in the given paths.
	 * @param _paths one or multiple FS paths to search in
	 * @param _depth the depth of nested directories to search in
	 * @returns a set of paths for all files found
	 */
	public Set<Path> search(Set<Path> _paths, int _depth) {
		for(Path p: _paths)
			this.search(p, _depth);
		AbstractFileSearch.getLog().info("Found a total of [" + this.files.size() + "] files in [" + _paths.size() + "] paths");
		return this.files;
	}
	
	/**
	 * Starts searching for files in the given path, with unlimited depth.
	 * @param _path the FS path to search in
	 * @returns a set of paths for all files found
	 */
	public Set<Path> search(Path _p) { return this.search(_p, java.lang.Integer.MAX_VALUE); }
	
	/**
	 * Starts searching for files in the given path, up to the specified depth.
	 * @param _path the FS path to search in
	 * @param _depth the depth of nested directories to search in
	 * @returns a set of paths for all files found
	 */
	public Set<Path> search(@NotNull Path _p, int _depth) {
		try {
			if(Files.isDirectory(_p))
				Files.walkFileTree(_p, new HashSet<FileVisitOption>(), _depth, this);
			else if(Files.isRegularFile(_p))
				this.visitFile(_p, Files.readAttributes(_p, BasicFileAttributes.class));
		} catch (Exception e) {
			AbstractFileSearch.getLog().error("Error while analyzing path [" + _p + "]: " + e.getMessage());
        }
		if(_p.isAbsolute())
			AbstractFileSearch.getLog().info("Found [" + this.files.size() + "] files in absolute path [" + _p.toAbsolutePath() + "]");
		else
			AbstractFileSearch.getLog().info("Found [" + this.files.size() + "] files in relative path [" + _p + "], i.e., absolute path [" + _p.toAbsolutePath() + "]");
		return this.files;
	}
}
