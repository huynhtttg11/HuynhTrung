package com.sap.psr.vulas.cia.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import com.jeantessier.dependencyfinder.cli.JarJarDiff;
import com.sap.psr.vulas.FileAnalyzer;
import com.sap.psr.vulas.cia.util.FileAnalyzerFetcher;
import com.sap.psr.vulas.cia.util.HeaderEcho;
import com.sap.psr.vulas.cia.dependencyfinder.JarDiffCmd;
import com.sap.psr.vulas.cia.util.HeaderEcho;
import com.sap.psr.vulas.cia.util.RepoException;
import com.sap.psr.vulas.cia.util.RepositoryDispatcher;
import com.sap.psr.vulas.java.JarAnalyzer;
import com.sap.psr.vulas.java.JavaId;
import com.sap.psr.vulas.python.PythonId;
import com.sap.psr.vulas.python.PythonArchiveAnalyzer;
import com.sap.psr.vulas.shared.enums.ConstructType;
import com.sap.psr.vulas.shared.enums.ProgrammingLanguage;
import com.sap.psr.vulas.shared.json.model.Artifact;
import com.sap.psr.vulas.shared.json.model.ConstructId;
import com.sap.psr.vulas.shared.json.model.diff.JarDiffResult;
import com.sap.psr.vulas.shared.util.FileUtil;
import com.sap.psr.vulas.shared.util.StopWatch;

@RestController
@CrossOrigin("*")
@RequestMapping("/artifacts")
public class ArtifactController {
	
	private static Logger log = LoggerFactory.getLogger(ArtifactController.class);

	
	/**
	 * Returns the artifact version for the given SHA1. Returns 404 if the SHA1 is not known by the configured external services.
	 */
	@RequestMapping(value = "/{sha1}", method = RequestMethod.GET, produces = {"application/json;charset=UTF-8"})
	public ResponseEntity<Artifact> isWellknownSha1(@PathVariable String sha1) {
		try {
			RepositoryDispatcher r = new RepositoryDispatcher();
			final Artifact doc = r.getArtifactForDigest(sha1);
			if(doc==null)
				return new ResponseEntity<Artifact>(HttpStatus.NOT_FOUND); 
			else
				return new ResponseEntity<Artifact>(doc, HttpStatus.OK);
		}
		catch(RepoException e) {
			ArtifactController.log.error(e.getMessage(), e);
			return new ResponseEntity<Artifact>(HttpStatus.INTERNAL_SERVER_ERROR);
		}catch(InterruptedException e) {
			ArtifactController.log.error(e.getMessage(), e);
			return new ResponseEntity<Artifact>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	
	/**
	 * For the given group and artifact identifier, it returns all artifact versions (optionally having the required classifier and packaging) 
	 * @param classifier (optional, default none)
	 * @param packaging (optional, default none)
	 * @param skipResponseBody (optional, default false)
	 */
	@RequestMapping(value = "/{mvnGroup:.+}/{artifact:.+}", method = RequestMethod.GET, produces = {"application/json;charset=UTF-8"})
	public ResponseEntity<Set<Artifact>> getArtifactVersions(@PathVariable String mvnGroup, @PathVariable String artifact, 
			@RequestParam(value="classifier", required=false, defaultValue="") String classifierFilter, 
			@RequestParam(value="packaging", required=false, defaultValue="") String packagingFilter,
			@RequestParam(value="skipResponseBody", required=false, defaultValue="false") Boolean skipResponseBody,
			@RequestHeader(value="X-Vulas-Echo", required=false, defaultValue="") String echo) {

		// Echo
		final HttpHeaders headers = HeaderEcho.getHeaders(echo);
		try {
			RepositoryDispatcher r = new RepositoryDispatcher();
			Set<Artifact>	response = r.getAllArtifactVersions(mvnGroup, artifact, classifierFilter, packagingFilter);
			if(response==null){
				//TODO: does it ever happen that response is null?
				return new ResponseEntity<Set<Artifact>>(headers,HttpStatus.BAD_REQUEST);
			}
			if(response.isEmpty())
				return new ResponseEntity<Set<Artifact>>(headers,HttpStatus.NOT_FOUND);
			if(skipResponseBody)
				return new ResponseEntity<Set<Artifact>>(headers,HttpStatus.OK);
			else
				return new ResponseEntity<Set<Artifact>>(response, headers, HttpStatus.OK);
		}
		catch(Exception e) {
			log.error("Error: " + e.getMessage(), e);
			e.getStackTrace();
			return new ResponseEntity<Set<Artifact>>(headers,HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Returns the latest version for the given group and artifact identifier.
	 * @param classifierFilter (optional, default none)
	 * @param packagingFilter (optional, default none)
	 * @param skipResponseBody (optional, default false)
	 */
	@RequestMapping(value = "/{mvnGroup:.+}/{artifact:.+}/latest", method = RequestMethod.GET, produces = {"application/json;charset=UTF-8"})
	public ResponseEntity<Artifact> getLatestArtifactVersion(@PathVariable String mvnGroup, @PathVariable String artifact, 
			@RequestParam(value="classifierFilter", required=false, defaultValue="") String classifierFilter, 
			@RequestParam(value="packagingFilter", required=false, defaultValue="") String packagingFilter,
			@RequestParam(value="skipResponseBody", required=false, defaultValue="false") Boolean skipResponseBody) {
		try {
			
			RepositoryDispatcher r = new RepositoryDispatcher();
			Artifact response = r.getLatestArtifactVersion(mvnGroup, artifact, classifierFilter, packagingFilter);
			
			if(response==null)
				return new ResponseEntity<Artifact>(HttpStatus.NOT_FOUND);
			if(skipResponseBody)
				return new ResponseEntity<Artifact>(HttpStatus.OK);
			else
				return new ResponseEntity<Artifact>(response, HttpStatus.OK);
		}
		catch(Exception e) {
			log.error("Error: " + e.getMessage(), e);
			e.getStackTrace();
			return new ResponseEntity<Artifact>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}		

	/**
	 * Returns all artifact versions with the given group and artifact identifier and version greater than the requested one.
	 * @param classifierFilter (optional, default none)
	 * @param packagingFilter (optional, default none)
	 * @param skipResponseBody (optional, default false)
	 */
	@RequestMapping(value = "/{mvnGroup:.+}/{artifact:.+}/greaterThan/{version:.+}", method = RequestMethod.GET, produces = {"application/json;charset=UTF-8"})
	public ResponseEntity<Set<Artifact>> getGreaterArtifactVersions(@PathVariable String mvnGroup, @PathVariable String artifact,@PathVariable String version,
			@RequestParam(value="classifierFilter", required=false, defaultValue="") String classifierFilter, 
			@RequestParam(value="packagingFilter", required=false, defaultValue="") String packagingFilter,
			@RequestParam(value="skipResponseBody", required=false, defaultValue="false") Boolean skipResponseBody) {
		try {
			RepositoryDispatcher r = new RepositoryDispatcher();
			
			Set<Artifact> response = r.getGreaterArtifactVersions(mvnGroup, artifact, version, classifierFilter, packagingFilter);
			if(response==null)
				return new ResponseEntity<Set<Artifact>>(HttpStatus.BAD_REQUEST);
			if(response.isEmpty())
				return new ResponseEntity<Set<Artifact>>(HttpStatus.NOT_FOUND);
			if(skipResponseBody)
				return new ResponseEntity<Set<Artifact>>(HttpStatus.OK);
			else
				return new ResponseEntity<Set<Artifact>>(response, HttpStatus.OK);
		}
		catch(Exception e) {
			log.error("Error: " + e.getMessage(), e);
			return new ResponseEntity<Set<Artifact>>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}		

	/**
	 * Returns the artifact versions with the given group, artifact, and version identifier (optionally packaging, classifier). If the language is provided, 
	 * it searches for the artifact only in the configured services supporting it.
 	 * @param classifier (optional, default none)
	 * @param packaging (optional, default none)
	 * @param skipResponseBody (optional, default false)
	 * @param lang (optional, default none)
	 */
	@RequestMapping(value = "/{mvnGroup:.+}/{artifact:.+}/{version:.+}", method = RequestMethod.GET, produces = {"application/json;charset=UTF-8"})
	public ResponseEntity<Artifact> getArtifactVersion(@PathVariable String mvnGroup, @PathVariable String artifact,@PathVariable String version,  
			@RequestParam(value="classifier", required=false, defaultValue="") String classifierFilter, 
			@RequestParam(value="packaging", required=false, defaultValue="") String packagingFilter,
			@RequestParam(value="skipResponseBody", required=false, defaultValue="false") Boolean skipResponseBody,
			@RequestParam(value="lang", required=false, defaultValue="") ProgrammingLanguage lang) {
		try {
			
			RepositoryDispatcher r = new RepositoryDispatcher();

			Artifact response = r.getArtifactVersion(mvnGroup,artifact,version,classifierFilter,packagingFilter,lang);
		
			if(response==null)
				return new ResponseEntity<Artifact>(HttpStatus.NOT_FOUND);
			if(skipResponseBody)
				return new ResponseEntity<Artifact>(HttpStatus.OK);
			else
				return new ResponseEntity<Artifact>(response, HttpStatus.OK);
		}
		catch(Exception e) {
			log.error("Error: " + e.getMessage(), e);
			return new ResponseEntity<Artifact>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}		
		

	/**
	 * Downloads the artifact having the given group, artifact, version, packaging and classifier (if any). If the language is provided, 
	 * it searches for the artifact only in the configured services supporting it.
 	 * @param classifier (optional, default none)
	 * @param lang (mandatory, default none)
	 */
	@RequestMapping(value = "/{mvnGroup:.+}/{artifact:.+}/{version:.+}/{packaging:.+}", method = RequestMethod.GET)
	public void getArtifact(@PathVariable String mvnGroup, @PathVariable String artifact, @PathVariable String version, @PathVariable String packaging, 
			@RequestParam(value="classifier", required=false, defaultValue="") String classifier, HttpServletResponse response,
			@RequestParam(value="lang", required=true, defaultValue="JAVA") ProgrammingLanguage lang) {

		// The artifact whose JAR is to be downloaded
		final Artifact a = new Artifact(mvnGroup,artifact,version);
		a.setClassifier(classifier.equals("")?null:classifier);
		a.setPackaging(packaging);
		a.setProgrammingLanguage(lang);
	
		// Headers
		response.setContentType(a.getContentType());      
		response.setHeader("Content-Disposition", "attachment; filename=" + a.getM2Filename()); 

		// Get it!
		try {
			RepositoryDispatcher r = new RepositoryDispatcher();
			final Path file = r.downloadArtifact(a);
			IOUtils.copy(new FileInputStream(file.toFile()), response.getOutputStream());
			  response.flushBuffer();
		} catch (FileNotFoundException e) {
			ArtifactController.log.error("Cannot download artifact ["+mvnGroup+":"+artifact+":"+version+":"+packaging+"]: does not exist");
			response.setStatus(404);
			try {
				response.sendError(HttpServletResponse.SC_NOT_FOUND, "Artifact does not exists");
			} catch (IOException e1) {
				throw new RuntimeException("IOError writing file to output stream");
			}
		} catch (NullPointerException e) {
			ArtifactController.log.error("Cannot download artifact ["+mvnGroup+":"+artifact+":"+version+":"+packaging+"]: does not exist");
			response.setStatus(404);
			try {
				response.sendError(HttpServletResponse.SC_NOT_FOUND, "Artifact does not exists");
			} catch (IOException e1) {
				throw new RuntimeException("IOError writing file to output stream");
			}
		} catch (IOException e) {
			throw new RuntimeException("IOError writing file to output stream");
		} catch (Exception e) {
			if(e.getMessage().equals("java.io.FileNotFoundException")){
				ArtifactController.log.error("Cannot download artifact ["+mvnGroup+":"+artifact+":"+version+":"+packaging+"]: does not exist");
				response.setStatus(404);
				try {
					response.sendError(HttpServletResponse.SC_NOT_FOUND, "Artifact does not exists");
				} catch (IOException e1) {
					throw new RuntimeException("IOError writing file to output stream");
				}
			}
			
			throw new RuntimeException("IOError writing file to output stream");
		}
			
		
	}
	
	/**
	 * Returns the list of constructs contained in the artifact having the given group, artifact, version, packaging. If the language is provided, 
	 * it searches for the artifact only in the configured services supporting it.
	 * @param type (optional, default "") filter on the @ConstructType 
	 * @param lang (mandatory, default JAVA)
	 */

	@RequestMapping(value = "/{mvnGroup:.+}/{artifact:.+}/{version:.+}/{packaging}/constructIds", method = RequestMethod.GET, produces = {"application/json;charset=UTF-8"})
	public ResponseEntity<Set<ConstructId>> getConstructIds(@PathVariable String mvnGroup, @PathVariable String artifact, @PathVariable String version,@PathVariable String packaging,
			@RequestParam(value="type", required=false, defaultValue="") ConstructType type,
			@RequestParam(value="lang", required=true, defaultValue="JAVA") ProgrammingLanguage lang) {
		
		log.info("Get all constructIds of type ["+type+"] for archive: " + mvnGroup + ":" + artifact + ":" + version, true);

		
		try{
			//check that the artifact exists (with the requested packaging)
			RepositoryDispatcher r = new RepositoryDispatcher();
			Artifact response = r.getArtifactVersion(mvnGroup, artifact, version, null, packaging, lang);
		
			if(response==null)
				return new ResponseEntity<Set<ConstructId>>(HttpStatus.NOT_FOUND);
			else{
				
				Artifact a = new Artifact(mvnGroup,artifact,version);
				a.setPackaging(packaging);
				a.setProgrammingLanguage(lang);
				
				FileAnalyzer fa = FileAnalyzerFetcher.read(a);
							
				final Set<com.sap.psr.vulas.ConstructId> constructs_in = fa.getConstructs().keySet();
				final Set<com.sap.psr.vulas.shared.json.model.ConstructId> constructs_out = new TreeSet<ConstructId>();
				for(com.sap.psr.vulas.ConstructId c: constructs_in) {
					com.sap.psr.vulas.shared.json.model.ConstructId cshared = null;
					if(lang==ProgrammingLanguage.JAVA)
						cshared = JavaId.toSharedType(c);
					else
						cshared = PythonId.toSharedType(c);
					if(type==null || type.equals("") || cshared.getType().toString().equals(type.toString()))
						constructs_out.add(cshared);
				}
				return new ResponseEntity<Set<ConstructId>>(constructs_out, HttpStatus.OK);
			}
		} catch (Exception e) {
			ArtifactController.log.error(e.getMessage(), e);
			if(e.getCause() instanceof IllegalArgumentException)
				return new ResponseEntity<Set<ConstructId>>(HttpStatus.BAD_REQUEST);
			return new ResponseEntity<Set<ConstructId>>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Given the list of ConstructId in the HTTP Request body, this method returns those intersecting the artifact having the given group,
	 * artifact, version, packaging; 404 otherwise.
	 * @param lang (mandatory, default JAVA)
	 */
	@RequestMapping(value = "/{mvnGroup:.+}/{artifact:.+}/{version:.+}/{packaging}/constructIds/intersect", method = RequestMethod.POST, 
			consumes = {"application/json;charset=UTF-8"},
			produces = {"application/json;charset=UTF-8"})
	public ResponseEntity<List<ConstructId>> intersect(@PathVariable String mvnGroup, @PathVariable String artifact, @PathVariable String version, @PathVariable String packaging,
			@RequestBody ConstructId[] cids, @RequestParam(value="lang", required=true, defaultValue="JAVA") ProgrammingLanguage lang) {
	

			try {
		
				//check that the artifact exists (with the requested packaging)
				RepositoryDispatcher r = new RepositoryDispatcher();
				Artifact response = r.getArtifactVersion(mvnGroup, artifact, version, null, packaging, lang);
			
				if(response==null)
					return new ResponseEntity<List<ConstructId>>(HttpStatus.NOT_FOUND);
				else{
					 Artifact a = new Artifact(mvnGroup,artifact,version);
					 a.setPackaging(packaging);
					 a.setProgrammingLanguage(lang);
										
					//FileAnalyzer fa = fileAnalyzerCache.get(a);
					FileAnalyzer fa = FileAnalyzerFetcher.read(a);
					
					List<com.sap.psr.vulas.shared.json.model.ConstructId> constructs = null;
									
					if(fa instanceof JarAnalyzer){
						constructs = ((JarAnalyzer)fa).getSharedConstructs();
					}
					else{
						constructs = ((PythonArchiveAnalyzer)fa).getSharedConstructs();
					}
	
					List<ConstructId> intersection = new ArrayList<ConstructId>();
					for(int i=0;i<cids.length;i++){
						if(constructs.contains(cids[i]))
							intersection.add(cids[i]);
					}
					return new ResponseEntity<List<ConstructId>>(intersection,HttpStatus.OK);
				}
				
			} catch (Exception e) {
				// there exists cases (e.g., jetty-all) where getArtifactVersion returns 200 but the artifact cannot be downloaded
				// so we return 404
				if(e.getCause() instanceof FileNotFoundException){
					ArtifactController.log.error("Cannot download artifact ["+mvnGroup+":"+artifact+":"+version+":"+packaging+"]: does not exist");
					return new ResponseEntity<List<ConstructId>>(HttpStatus.NOT_FOUND);
				}
				// IllegalArgumentException is thrown by the check on the packaging to be download. 
				if(e.getCause() instanceof IllegalArgumentException)
					return new ResponseEntity<List<ConstructId>>(HttpStatus.BAD_REQUEST);
				ArtifactController.log.error(e.getMessage(), e);
				//we print (for now) the stack trace to understand where we may go wrong
				return new ResponseEntity<List<ConstructId>>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
	}

		
	
	@RequestMapping(value = "/diff", method = RequestMethod.POST, consumes = {"application/json;charset=UTF-8"}, produces = {"application/json"})
	public ResponseEntity<JarDiffResult> diffJarArtifacts(@RequestBody Artifact[] artifacts) {
		// Check args
		if(artifacts==null || artifacts.length!=2) {
			log.error("Exactly two artifacts are required as input, got [" + (artifacts==null?null:artifacts.length) + "]");
			return new ResponseEntity<JarDiffResult>(HttpStatus.BAD_REQUEST);
		}
		if(!artifacts[0].getPackaging().equalsIgnoreCase("jar") || !artifacts[1].getPackaging().equalsIgnoreCase("jar")) {
			log.error("Artifact " + artifacts[0] + " and " + artifacts[1] + " must be of packaging JAR");
			return new ResponseEntity<JarDiffResult>(HttpStatus.BAD_REQUEST);
		}
		boolean dowloaded_first=false;
		try {
			RepositoryDispatcher r = new RepositoryDispatcher();
			
			final Path old_jar = r.downloadArtifact(artifacts[0]);
			dowloaded_first=true;
			final Path new_jar = r.downloadArtifact(artifacts[1]); 
			
			final JarDiffCmd cmd = new JarDiffCmd(artifacts[0], old_jar, artifacts[1], new_jar);
			
			final String[] args = new String[] { "-old", old_jar.toString(), "-new", new_jar.toString(), "-name", "xyz", "-code" };
			cmd.run(args);
			final JarDiffResult result = cmd.getResult();
			
			if(result!=null) {
				return new ResponseEntity<JarDiffResult>(result, HttpStatus.OK); 
			}
			else {
				return new ResponseEntity<JarDiffResult>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
		} catch (IllegalArgumentException ie) {
			return new ResponseEntity<JarDiffResult>(HttpStatus.BAD_REQUEST);
		}
		catch(FileNotFoundException fnfe){
			if (!dowloaded_first)
				log.warn(artifacts[0] + " cannot be retrieved");
			else
				log.warn(artifacts[1] + " cannot be retrieved");
			return new ResponseEntity<JarDiffResult>(HttpStatus.NOT_FOUND);
		}
		catch(Exception e) {
			log.error("Error: " + e.getMessage(), e);
			return new ResponseEntity<JarDiffResult>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 
	 */
	@RequestMapping(value = "/diffxml", method = RequestMethod.POST, consumes = {"application/json;charset=UTF-8"}, produces = {"text/xml"})
	public ResponseEntity<String> diffJarArtifactsXml(@RequestBody Artifact[] artifacts) {
		// Check args
		if(artifacts==null || artifacts.length!=2) {
			log.error("Exactly two artifacts are required as input, got [" + (artifacts==null?null:artifacts.length) + "]");
			return new ResponseEntity<String>(HttpStatus.BAD_REQUEST);
		}
		if(!artifacts[0].getPackaging().equalsIgnoreCase("jar") || !artifacts[1].getPackaging().equalsIgnoreCase("jar")) {
			log.error("Artifact " + artifacts[0] + " and " + artifacts[1] + " must be of packaging JAR");
			return new ResponseEntity<String>(HttpStatus.BAD_REQUEST);
		}

		try {
			RepositoryDispatcher r = new RepositoryDispatcher();
			final Path old_jar = r.downloadArtifact(artifacts[0]);
			final Path new_jar = r.downloadArtifact(artifacts[1]); 
			final String label = artifacts[0].getLibId().getArtifact() + "-" + artifacts[0].getLibId().getVersion() + "--" + artifacts[1].getLibId().getArtifact() + "-" + artifacts[1].getLibId().getVersion(); 
			final File diff_xml = File.createTempFile(label + "-", ".xml");

			if(old_jar!=null && new_jar!=null) {
				final String[] args = new String[] { "-old", old_jar.toString(), "-new", new_jar.toString(), "-name", label, "-out", diff_xml.toPath().toAbsolutePath().toString(), "-code" };
				JarJarDiff.main(args);
				log.info("Write diff to [" + diff_xml.toPath() + "]");
				return new ResponseEntity<String>(FileUtil.readFile(diff_xml.toPath()), HttpStatus.OK); 
			}
			else {
				return new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
		} catch (IllegalArgumentException ie) {
			return new ResponseEntity<String>(HttpStatus.BAD_REQUEST);
		}
		catch(Exception e) {
			log.error("Error: " + e.getMessage(), e);
			return new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
//	/**
//	 * Returns true if group, artifact, version are specified,
//	 * classifier is one of 'sources', 'javadoc', 'test-sources', and 'tests', and
//	 * packaging is one of 'pom', 'jar' and 'war'.
//	 * Without all this information, a download is not possible.
//	 * @param _doc
//	 * @return
//	 */
//	private boolean isReadyForDownload(Artifact _a) {//
//		//TODO Move to artifact? 
//		return _a.getLibId().getMvnGroup()!=null && !_a.getLibId().getMvnGroup().equals("")  &&
//				_a.getLibId().getArtifact()!=null && !_a.getLibId().getArtifact().equals("")  && 
//						_a.getLibId().getVersion()!=null && !_a.getLibId().getVersion().equals("")  &&
//				(_a.getClassifier()==null || _a.getClassifier().equals("javadoc") || _a.getClassifier().equals("sources") || 
//						_a.getClassifier().equals("test-sources") || _a.getClassifier().equals("tests") || _a.getClassifier().equals("site")|| 
//						_a.getClassifier().equals("jar")) &&
//				_a.getPackaging()!=null && (_a.getPackaging().equalsIgnoreCase("pom") || _a.getPackaging().equalsIgnoreCase("jar") ||
//						_a.getPackaging().equalsIgnoreCase("war") || _a.getPackaging().equalsIgnoreCase("xml") || _a.getPackaging().equalsIgnoreCase("sdist") );  
//	}
	
}