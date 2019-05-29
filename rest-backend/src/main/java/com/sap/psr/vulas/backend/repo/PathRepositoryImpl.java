package com.sap.psr.vulas.backend.repo;


import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceException;

import org.springframework.beans.factory.annotation.Autowired;

import com.sap.psr.vulas.backend.model.Application;
import com.sap.psr.vulas.backend.model.ConstructId;
import com.sap.psr.vulas.backend.model.Library;
import com.sap.psr.vulas.backend.model.Path;
import com.sap.psr.vulas.backend.model.PathNode;
import com.sap.psr.vulas.backend.util.ReferenceUpdater;
import com.sap.psr.vulas.shared.util.StopWatch;

public class PathRepositoryImpl implements PathRepositoryCustom {
	
	@Autowired
	ApplicationRepository appRepository;

	@Autowired
	LibraryRepository libRepository;

	@Autowired
	ConstructIdRepository cidRepository;

	@Autowired
	PathRepository pathRepository;

	@Autowired
	ReferenceUpdater refUpdater;

	@Autowired
	DependencyRepository depRepository;

	@Autowired
	BugRepository bugRepository;

	/**
	 * 
	 * @param bug
	 * @return the saved bug
	 */
	public List<Path> customSave(Application _app, Path[] _paths) throws PersistenceException {
		final StopWatch sw = new StopWatch("Save [" + _paths.length + "] paths for app " + _app).start();
		
		// To be returned
		List<Path> paths = new ArrayList<Path>();

		// Helpers needed for updating the properties of the provided traces
		Path managed_path = null;
		ConstructId managed_cid = null;
		Library managed_lib = null;

		for(Path provided_path: _paths) {

			// Update constructs and libs of the nodes of the path
			for(PathNode node: provided_path.getPath()) {
				try {
					managed_cid = ConstructIdRepository.FILTER.findOne(this.cidRepository.findConstructId(node.getConstructId().getLang(), node.getConstructId().getType(), node.getConstructId().getQname()));
				} catch (EntityNotFoundException e) {
					managed_cid = this.cidRepository.save(node.getConstructId());
				}
				node.setConstructId(managed_cid);

				if(node.getLib()!=null) {
					try {
						managed_lib = LibraryRepository.FILTER.findOne(this.libRepository.findByDigest(node.getLib().getDigest()));
						node.setLib(managed_lib);
					} catch (EntityNotFoundException e) {
						//TODO: What if the library does not exist
						node.setLib(null);
					}
				}
			}

			// Set managed objects (and throw exception if any of them cannot be found)
			try {
				provided_path.setApp(_app);
				provided_path.setBug(BugRepository.FILTER.findOne(this.bugRepository.findByBugId(provided_path.getBug().getBugId())));
				
				// Infer start and end construct and library
				provided_path.inferStartConstructId();
				provided_path.inferEndConstructId();
				provided_path.inferLibrary();				
			} catch (EntityNotFoundException e1) {
				throw new PersistenceException("Referenced entity not found: " + e1);
			}

			// Create or update
			try {
				managed_path = PathRepository.FILTER.findOne(this.pathRepository.findPath(provided_path.getApp(), provided_path.getBug(), provided_path.getSource(), provided_path.getStartConstructId(), provided_path.getEndConstructId()));

				// Found existing path
				provided_path.setId(managed_path.getId());
			} catch (EntityNotFoundException e1) {}			

			// Save
			try {
				managed_path = this.pathRepository.save(provided_path);
				paths.add(managed_path);
			} catch (Exception e) {
				throw new PersistenceException("Error while saving path " + provided_path + ": " + e.getMessage());
			}
		}
		sw.stop();
		return paths;
	}
}
