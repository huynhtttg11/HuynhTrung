package com.sap.psr.vulas.backend.repo;

import java.io.IOException;
import java.nio.file.Path;

import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceException;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.sap.psr.vulas.backend.model.Bug;
import com.sap.psr.vulas.backend.model.Library;
import com.sap.psr.vulas.backend.model.LibraryId;
import com.sap.psr.vulas.backend.model.Space;
import com.sap.psr.vulas.backend.model.Tenant;
import com.sap.psr.vulas.backend.util.ReferenceUpdater;
import com.sap.psr.vulas.shared.enums.ExportConfiguration;
import com.sap.psr.vulas.shared.json.JacksonUtil;
import com.sap.psr.vulas.shared.util.Constants;
import com.sap.psr.vulas.shared.util.FileUtil;
import com.sap.psr.vulas.shared.util.StopWatch;

public class TenantRepositoryImpl implements TenantRepositoryCustom {

	private static Logger log = LoggerFactory.getLogger(TenantRepositoryImpl.class);

	@Autowired
	TenantRepository tenantRepository;

	@Autowired
	SpaceRepository spaceRepository;
	
	
	public Boolean isTenantComplete(Tenant _tenant){
		// Check arguments
		if(_tenant==null) {
			log.error("No tenant submitted");
			return false;
		}
		else if(!_tenant.hasTenantName()) {
			log.error("Tenant creation/modification requires name, adjust the configuration accordingly");
			return false;
		}
		return true;
	}
	
	/**
	 * Returns the tenant for the given tenant token. In case a tenant token is not provided, it returns the default tenant (if existing, null otherwise)
	 * @return the tenant for the given tenant token; default tenant if token is null; null otherwise.
	 * @throws Exception 
	 */
	public Tenant getTenant(String _tenantToken) {
		Tenant tenant = null;		
		if(_tenantToken==null){
			tenant = tenantRepository.findDefault();
			if(tenant==null){
				log.error("No default tenant exists");
				throw new EntityNotFoundException("Default tenant does not exists");
			}
		}
		else{
			try {
				tenant = TenantRepository.FILTER.findOne(this.tenantRepository.findBySecondaryKey(_tenantToken));
			}
			catch(EntityNotFoundException enfe) {
				log.error("A tenant with token [" + _tenantToken + "] does not exist: " + enfe.getMessage());
				throw enfe; 
			}
		}
		
		log.info("Found " + tenant + " for token [" + _tenantToken + "]");
		return tenant;
	}
}

