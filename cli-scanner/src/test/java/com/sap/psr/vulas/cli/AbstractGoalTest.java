package com.sap.psr.vulas.cli;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;

import com.jayway.restassured.RestAssured;
import com.sap.psr.vulas.core.util.CoreConfiguration;
import com.sap.psr.vulas.shared.connectivity.Service;
import com.sap.psr.vulas.shared.json.model.Application;
import com.sap.psr.vulas.shared.json.model.Space;
import com.sap.psr.vulas.shared.json.model.Tenant;
import com.sap.psr.vulas.shared.util.StringUtil;
import com.sap.psr.vulas.shared.util.VulasConfiguration;
import com.xebialabs.restito.server.StubServer;

public class AbstractGoalTest {

	protected StubServer server;
	
	protected Tenant testTenant;
	protected Space testSpace;
	protected Application testApp;

	@Before
	public void start() {
		server = new StubServer().run();
		RestAssured.port = server.getPort();
		
		testTenant = this.buildTestTenant();
		testSpace = this.buildTestSpace();
		testApp = this.buildTestApplication();
		
		// Clear and set properties
		this.clearVulasProperties();
		
		// App context
		System.setProperty(CoreConfiguration.APP_CTX_GROUP, testApp.getMvnGroup());
		System.setProperty(CoreConfiguration.APP_CTX_ARTIF, testApp.getArtifact());
		System.setProperty(CoreConfiguration.APP_CTX_VERSI, testApp.getVersion());

		// Identify app code
		System.setProperty(CoreConfiguration.BACKEND_CONNECT, CoreConfiguration.ConnectType.READ_WRITE.toString());
		
		// Identify app code
		System.setProperty(CoreConfiguration.APP_PREFIXES, "com.acme");
	}

	@After
	public void stop() {
		this.clearVulasProperties();
		server.stop();
	}
	
	private void clearVulasProperties() {
		final ArrayList<String> l = new ArrayList<String>();
		for(Object k: System.getProperties().keySet())
			if(k instanceof String && ((String)k).startsWith("vulas."))
				l.add((String)k);
		for(String k: l)
			System.clearProperty(k);
	}

	protected Tenant buildTestTenant() {
		final String rnd = StringUtil.getRandonString(6);
		return new Tenant("tenant-token-" + rnd, "tenant-name-" + rnd);
	}
	
	protected Space buildTestSpace() {
		final String rnd = StringUtil.getRandonString(6);
		return new Space("space-token-" + rnd, "space-name-" + rnd, "space-description");
	}
	
	protected Application buildTestApplication() {
		final String rnd = StringUtil.getRandonString(6);
		return new Application("app-group-" + rnd, "app-artifact-" + rnd, "app-version");
	}
	
	protected void configureBackendServiceUrl(StubServer _ss) {
		final StringBuffer b = new StringBuffer();
		b.append("http://localhost:").append(_ss.getPort()).append("/backend");
		System.setProperty(VulasConfiguration.getServiceUrlKey(Service.BACKEND), b.toString());
	}
}
