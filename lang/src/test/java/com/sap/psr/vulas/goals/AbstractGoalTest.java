package com.sap.psr.vulas.goals;

import org.junit.After;
import org.junit.Before;

import com.jayway.restassured.RestAssured;
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
	
	protected VulasConfiguration vulasConfiguration = null;

	/**
	 * Before every test:
	 * - Create sample tenant, space and application
	 * - Start HTTP server and register a corresponding backend URL (unless there's one already)
	 */
	@Before
	public void start() {
		server = new StubServer().run();
		RestAssured.port = server.getPort();
		
		testTenant = this.buildTestTenant();
		testSpace = this.buildTestSpace();
		testApp = this.buildTestApplication();
		
		this.vulasConfiguration = new VulasConfiguration();
	}

	@After
	public void stop() {
		server.stop();
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
		vulasConfiguration.setProperty(VulasConfiguration.getServiceUrlKey(Service.BACKEND), b.toString());
	}
}
