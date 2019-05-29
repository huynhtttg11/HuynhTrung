package com.sap.psr.vulas.monitor;

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
		server.stop();
		System.clearProperty(CoreConfiguration.APP_CTX_GROUP);
		System.clearProperty(CoreConfiguration.APP_CTX_ARTIF);
		System.clearProperty(CoreConfiguration.APP_CTX_VERSI);
		System.clearProperty(VulasConfiguration.getServiceUrlKey(Service.BACKEND));
		
		VulasConfiguration.getGlobal().setProperty(CoreConfiguration.APP_CTX_GROUP, null, null, false);
		VulasConfiguration.getGlobal().setProperty(CoreConfiguration.APP_CTX_ARTIF, null, null, false);
		VulasConfiguration.getGlobal().setProperty(CoreConfiguration.APP_CTX_VERSI, null, null, false);
		VulasConfiguration.getGlobal().setProperty(VulasConfiguration.getServiceUrlKey(Service.BACKEND), null, null, false);
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
		VulasConfiguration.getGlobal().setProperty(VulasConfiguration.getServiceUrlKey(Service.BACKEND), b.toString());
		System.setProperty(VulasConfiguration.getServiceUrlKey(Service.BACKEND), b.toString());
	}
}
