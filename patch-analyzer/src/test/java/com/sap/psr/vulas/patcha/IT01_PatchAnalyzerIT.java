package com.sap.psr.vulas.patcha;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.sap.psr.vulas.ConstructChange;
import com.sap.psr.vulas.core.util.CoreConfiguration;
import com.sap.psr.vulas.shared.categories.RequiresNetwork;
import com.sap.psr.vulas.shared.categories.Slow;
import com.sap.psr.vulas.shared.util.VulasConfiguration;

public class IT01_PatchAnalyzerIT {

	// Disable upload for JUnit tests
	static {
		//VulasConfiguration.getGlobal().setProperty(CoreConfiguration.UPLOAD_ENABLED, Boolean.valueOf(false));
		VulasConfiguration.getGlobal().setProperty(CoreConfiguration.BACKEND_CONNECT, CoreConfiguration.ConnectType.OFFLINE);
	}

	/**
	 * Analyzes the patch for CVE-2013-0239.
	 */
	@Test
	@Category({Slow.class, RequiresNetwork.class})
	public void testSvnRepo() {
		try {
			final PatchAnalyzer pa = new PatchAnalyzer("http://svn.apache.org/repos/asf/cxf/", "CVE-2013-0239");
			final Map<String,String> revs = pa.searchCommitLog("CVE-2013-0239", null);
			
			// Only one commit with id 1438424
			assertTrue("Search must yield only 1 result", revs.size()==1);
			final String rev = revs.keySet().iterator().next();
			assertEquals("1438424", rev);

			// Compare files and loop identified changes
			final Set<ConstructChange> changes = pa.identifyConstructChanges(rev);
			
			int i=0;
			for(ConstructChange cc: changes) {
				System.out.println(++i + " change [" + cc + "]");
			}
			
			// Check that all changes have been found
			assertEquals("9 changes are done in this commit", 9, changes.size());
			
			//final String json = pa.toJSON(revs.keySet().toArray(new String[revs.keySet().size()]));
			// Check for json equality
		} catch (Exception e) {
			System.out.println(e.getMessage());
			assertTrue(false);
		}
	}
}
