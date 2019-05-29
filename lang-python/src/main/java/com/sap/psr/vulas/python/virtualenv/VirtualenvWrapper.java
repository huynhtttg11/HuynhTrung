package com.sap.psr.vulas.python.virtualenv;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sap.psr.vulas.python.ProcessWrapperException;
import com.sap.psr.vulas.python.pip.PipInstalledPackage;
import com.sap.psr.vulas.python.pip.PipWrapper;
import com.sap.psr.vulas.python.pip.PyWrapper;
import com.sap.psr.vulas.python.utils.PythonConfiguration;
import com.sap.psr.vulas.shared.util.DirUtil;
import com.sap.psr.vulas.shared.util.FileUtil;
import com.sap.psr.vulas.shared.util.VulasConfiguration;

public class VirtualenvWrapper {

	private static final String SETUP_PY = "setup.py";
	
	private static final boolean IS_WIN = System.getProperty("os.name").contains("Windows");

	private final static Log log = LogFactory.getLog(VirtualenvWrapper.class);

	private Path pathToVirtualenvExecutable = null;

	private Path pathToPythonProject = null;
	
	private String projectName = null;

	private Path pathToVirtualenv = null;
	
	private Set<PipInstalledPackage> installedPackages = null;

	/**
	 * Assumes that the virtualenv executable is part of the PATH environment variable.
	 */
	public VirtualenvWrapper(Path _path_to_python_project) throws IllegalArgumentException, ProcessWrapperException {
		this(Paths.get("virtualenv"), _path_to_python_project);
	}

	/**
	 * Creates a virtual environment for the project located at the given path.
	 * This path is expected to contain a setup.py file.
	 * @param _path_to_virtualenv
	 */
	public VirtualenvWrapper(Path _path_to_virtualenv, Path _path_to_python_project) throws IllegalArgumentException, ProcessWrapperException {

		// Check it is a directory with a file "setup.py"
		if(!FileUtil.isAccessibleDirectory(_path_to_python_project))
			throw new IllegalArgumentException("Project path [" + _path_to_python_project + "] does not point to an accessible directory");
		if(!DirUtil.containsFile(_path_to_python_project.toFile(), SETUP_PY))
			throw new IllegalArgumentException("Project path [" + _path_to_python_project + "] does not contain the file [" + SETUP_PY + "]");

		this.pathToVirtualenvExecutable = _path_to_virtualenv;
		this.pathToPythonProject = _path_to_python_project.toAbsolutePath();
		this.projectName = this.pathToPythonProject.getName(this.pathToPythonProject.getNameCount()-1).toString();

		// Create the virtualenv
		try {
			this.pathToVirtualenv = FileUtil.createTmpDir("vulas-virtualenv-" + this.projectName + "-").toAbsolutePath();
		} catch (IOException e) {
			throw new ProcessWrapperException("Cannot create tmp directory: " + e.getMessage());
		}
		this.createVirtualenvForProject(this.pathToVirtualenv);
		
		// Copy the project folder
		this.copyProjectDirectory();
		
		// Create Vulas directories vulas/log and vulas/download
		
		// Call PIP install
		final Path prj_path = Paths.get(this.pathToVirtualenv.toString(), this.projectName);
		installedPackages = this.getPipWrapper().installPackages(prj_path);
		
		// Call setup
		//final Path setup_path = Paths.get(this.pathToVirtualenv.toString(), this.projectName, SETUP_PY);
		//this.getPyWrapper().runScript(setup_path, Arrays.asList(new String[] {"install"}));
	}
	
	public String getProjectName() { return projectName; }

	public Path getPathToVirtualenv() { return pathToVirtualenv; }
	
	public Set<PipInstalledPackage> getInstalledPackages() {
		return this.installedPackages;
	}
	
	public PipWrapper getPipWrapper() throws ProcessWrapperException {
		if(IS_WIN)
			return new PipWrapper(Paths.get(this.pathToVirtualenv.toString(), "Scripts", "pip"), this.pathToVirtualenv);
		else
			return new PipWrapper(Paths.get(this.pathToVirtualenv.toString(), "bin", "pip"), this.pathToVirtualenv);
	}
	
	public PyWrapper getPyWrapper() throws ProcessWrapperException {
		if(IS_WIN)
			return new PyWrapper(Paths.get(this.pathToVirtualenv.toString(), "Scripts", "python"), this.pathToVirtualenv);
		else
			return new PyWrapper(Paths.get(this.pathToVirtualenv.toString(), "bin", "python"), this.pathToVirtualenv);
	}

	/**
	 * Creates a virtual environment for the project located at the given path in a temporary directory.
	 * @param _project_path
	 */
	private void createVirtualenvForProject(Path _path_to_virtual_env) throws ProcessWrapperException {
		Path ve = null;
		try {
			log.info("Create virtualenv in [" + _path_to_virtual_env + "]");
			
			// Create the virtual env inside this temp directory
			ProcessBuilder pb = null;
			if(VulasConfiguration.getGlobal().isEmpty(PythonConfiguration.PY_PY_PATH)) {
				pb = new ProcessBuilder(this.pathToVirtualenvExecutable.toString(), _path_to_virtual_env.toString());
			} else {
				pb = new ProcessBuilder(this.pathToVirtualenvExecutable.toString(), "--python", VulasConfiguration.getGlobal().getConfiguration().getString(PythonConfiguration.PY_PY_PATH), _path_to_virtual_env.toString());
			}

			// Create temp. directory for out and err streams
			final Path out = Paths.get(_path_to_virtual_env.toString(), "virtualenv-out.txt");
			final Path err = Paths.get(_path_to_virtual_env.toString(), "virtualenv-err.txt");

			// Redirect out and err
			pb.redirectOutput(out.toFile());
			pb.redirectError(err.toFile());


			// Start and wait
			final Process process = pb.start();
			final int exit_code = process.waitFor();

			// Success
			if(exit_code!=0) {
				final String error_msg = FileUtil.readFile(err);
				throw new ProcessWrapperException(error_msg);
			}
		} catch (IOException e) {
			throw new ProcessWrapperException(e.getMessage(), e);
		} catch (InterruptedException e) {
			throw new ProcessWrapperException(e.getMessage(), e);
		}
	}
	
	private Path copyProjectDirectory() throws ProcessWrapperException {
		Path new_dir = null;
		try {
			Files.walkFileTree(this.pathToPythonProject, new HashSet<FileVisitOption>(), Integer.MAX_VALUE, new CopyFileVisitor(this.pathToPythonProject, this.pathToVirtualenv));
		} catch (Exception e) {
			new_dir = null;
			log.error("Cannot copy project dir [" + this.pathToPythonProject + "] to virtual env [" + this.pathToVirtualenvExecutable + "]:" + e.getMessage());
			throw new ProcessWrapperException("Cannot copy project dir [" + this.pathToPythonProject + "] to virtual env [" + this.pathToVirtualenvExecutable + "]:" + e.getMessage());
		}
		return new_dir;
	}
	
	private static class CopyFileVisitor extends SimpleFileVisitor<Path> {
		
		private final static Log log = LogFactory.getLog(CopyFileVisitor.class);
		
		private Path src = null;
		private Path tgt = null;
		
		public CopyFileVisitor(Path _src, Path _tgt) {
			this.src = _src;
			this.tgt = _tgt;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			final Path relp = this.src.getParent().relativize(dir.toAbsolutePath());
			final Path newp = Paths.get(tgt.toString(), relp.toString());
			try {
				Files.createDirectories(newp);
			} catch (Exception e) {
				log.error("Cannot copy [" + dir + "] to [" + newp + "]: " + e.getMessage());
			}
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			final Path relp = this.src.getParent().relativize(file);
			final Path newf = Paths.get(this.tgt.toString(), relp.toString());
			try {
				Files.copy(file, newf);
			} catch (Exception e) {
				log.error("Cannot copy [" + file + "] to [" + newf + "]: " + e.getMessage());
			}
			return FileVisitResult.CONTINUE;
		}			
	}
}
