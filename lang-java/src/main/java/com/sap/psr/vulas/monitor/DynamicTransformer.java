package com.sap.psr.vulas.monitor;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Paths;
import java.security.ProtectionDomain;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sap.psr.vulas.core.util.CoreConfiguration;
import com.sap.psr.vulas.java.JarAnalyzer;
import com.sap.psr.vulas.java.JarWriter;
import com.sap.psr.vulas.java.JavaId;
import com.sap.psr.vulas.java.JavaMethodId;
import com.sap.psr.vulas.monitor.touch.ConstructIdUtil;
import com.sap.psr.vulas.monitor.trace.ConstructUsage;
import com.sap.psr.vulas.shared.util.VulasConfiguration;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;

/**
 * Uses the {@link ClassVisitor} to dynamically instrument Java constructors and methods
 * during the class loading process. When executed, the injected code will call the
 * {@link ExecutionMonitor} to collect and upload runtime information to the Vulas backend.
 * <p>
 * The name has been chosen to contrast with static instrumentation, which can be done
 * with help of the Vulas Maven plugin (goal vulas:instr).
 */
public class DynamicTransformer implements ClassFileTransformer {

	// ====================================== STATIC MEMBERS

	private static final Log log = LogFactory.getLog(DynamicTransformer.class);

	private static DynamicTransformer instance = null;

	// ====================================== INSTANCE MEMBERS

	private String id = new Double(Math.random()).toString();

	private LoaderHierarchy loaderHierarchy = new LoaderHierarchy();

	private InstrumentationControl instrControl = null;

	/**
	 * Determines whether classes will be instrumented or not.
	 * Checked in {@link DynamicTransformer#transform(ClassLoader, String, Class, ProtectionDomain, byte[])}.
	 * Will be changed to true only after a successful instantiation, otherwise
	 * the transform method will called to early (which results in StackOverflow problems).
	 */
	private boolean transformationEnabled = false;

	private DynamicTransformer() throws IllegalStateException {
		this.instrControl = InstrumentationControl.getInstance(this.getClass().getSimpleName());
		
		try {
			if(!CoreConfiguration.existsInBackend(CoreConfiguration.getAppContext()))
				throw new IllegalStateException("Application " + CoreConfiguration.getAppContext() + " does not exist in backend");
		} catch (ConfigurationException e) {
			throw new IllegalStateException("Error while reading configuration: " + e.getMessage());
		}
		
		// Freeze a couple of classes
		this.freezeClasses();
	}

	// ====================================== INSTANCE METHODS

	/**
	 * Called during the construction in order to have some classes frozen.
	 * @return
	 */
	private final void freezeClasses() {
		try {
			final JavaMethodId jmi = JavaId.parseMethodQName("com.sap.Test.test()"); 
			final ConstructUsage cu = new ConstructUsage(jmi, null, -1);
			final Loader l = new Loader(this.getClass().getClassLoader());
			final Configuration cfg = VulasConfiguration.getGlobal().getConfiguration();
			ConstructIdUtil.getInstance();
			final JarWriter jw = new JarWriter(Paths.get(DynamicTransformer.class.getClassLoader().getResource(DynamicTransformer.class.getName().replace('.', '/') + ".class").toString()));
			final JarAnalyzer ja = new JarAnalyzer();
			ja.analyze(Paths.get(DynamicTransformer.class.getClassLoader().getResource(DynamicTransformer.class.getName().replace('.', '/') + ".class").toString()).toFile());
		}
		// Getting an exception does not matter in the context of freezing some classes
		catch(Exception e) {;}
	}

	public boolean isTransformationEnabled() { return transformationEnabled; }

	public void setTransformationEnabled(boolean transformationEnabled) { this.transformationEnabled = transformationEnabled; }

	public LoaderHierarchy getLoaderHierarchy() { return this.loaderHierarchy; }

	/**
	 * Adds instrumentation code to all constructors and methods of all classes (thereby considering certain
	 * blacklists and whitelists read from the configuration file).
	 * <p>
	 * The method is called by the JRE class loading process and returns the instrumented bytecode for a given
	 * class.
	 * @return the instrumented bytecode
	 */
	public byte[] transform(ClassLoader loader, String className,
			Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
			byte[] classfileBuffer) throws IllegalClassFormatException {

		byte[] byteCode = classfileBuffer;

		// All methods of current class
		Loader l = null;
		CtClass c = null;
		ClassVisitor cv = null;

		final String loader_classname = loader.getClass().getName();
		final String dot_classname    = className.replace('/', '.');

		//07.08.2015, HP: Added in order to load class definition for stacktrace transformation
		if(loader.getParent()!=null)  // && loader!=null
			this.loaderHierarchy.add(loader);

		// We are not interested in instrumenting classes in the following cases:
		// - loader==null || loader.getParent()==null: The class in question is loaded by the bootstrap loader, which loads class definitions from JRE_HOME/lib/ext.
		// - loader is instance of sun.reflect.DelegatingClassLoader (created for the optimization of java.lang.reflect calls) or javax.management.remote.rmi.NoCallStackClassLoader (?)
		// - classname is blacklisted
		if(loader.getParent()!=null) {// && // && loader!=null

			// Let's add the loader to the class loader hierarchy
			l = this.loaderHierarchy.add(loader);

			// Is the tracer (already) supposed to instrument?
			// If not we spare us the expensive instrumentation (but assume it happened already before, for all JARs, so that the callback happens nevertheless)
			if(this.isTransformationEnabled()) {

				// Blacklisted class according to the configuration parameters "instr.blacklist.classes.jre/custom"?
				final boolean is_blacklisted_class = this.instrControl.isBlacklistedClass(dot_classname);

				// Class is blacklisted
				if(!is_blacklisted_class) {
					try {
						ClassPool cp = l.getClassPool(); //ClassPool.getDefault();
						c = cp.get(dot_classname);

						// Blacklisted JAR according to the configuration parameters "instr.blacklist.jars/dirs"?
						final boolean is_blacklisted_jar = this.instrControl.isBlacklistedJar(c.getURL());

						// Instrument methods and constructors of classes (but not interfaces)
						if(!is_blacklisted_jar && !c.isInterface()) {
							cv = new ClassVisitor(c);
							if(!cv.isInstrumented()) {
								cv.visitMethods(true);
								cv.visitConstructors(true);
								cv.finalizeInstrumentation();
								byteCode = cv.getBytecode();
								this.instrControl.updateInstrumentationStatistics(cv.getJavaId(), new Boolean(true));
								DynamicTransformer.log.debug("Class [" + dot_classname + "] now instrumented");
							}
							else {
								this.instrControl.updateInstrumentationStatistics(cv.getJavaId(), null);
								DynamicTransformer.log.debug("Class [" + dot_classname + "] already instrumented");
							}
						}
					}
					catch (IOException ioe) {
						DynamicTransformer.log.error("I/O exception while instrumenting class [" + dot_classname + "]: " + ioe.getMessage());
						this.instrControl.updateInstrumentationStatistics(cv.getJavaId(), new Boolean(false));
					}
					catch (CannotCompileException cce) {
						DynamicTransformer.log.warn("Cannot compile instrumented class [" + dot_classname + "]: " + cce.getMessage());
						this.instrControl.updateInstrumentationStatistics(cv.getJavaId(), new Boolean(false));
					}
					// Covers the following problems with Javassist and Java 8: "java.io.IOException: invalid constant type: 15"
					catch (Exception e) {
						DynamicTransformer.log.warn(e.getClass().getName() + " occured while instrumenting class [" + dot_classname + "]: " + e.getMessage());
						this.instrControl.updateInstrumentationStatistics(cv.getJavaId(), new Boolean(false));
					}
				}
			}
		}
		return byteCode;
	}

	public String toString() {
		final StringBuffer b = new StringBuffer();
		b.append("DynamicTransformer [id=").append(this.id);
		b.append(", instrumentation enabled=").append(this.isTransformationEnabled());
		b.append("]");
		return b.toString();
	}

	// ====================================== STATIC METHODS

	/**
	 * Singleton method: Creates (if necessary) and returns the single instance that can be created for this class.
	 * @return
	 */
	public static synchronized DynamicTransformer getInstance() {
		if(DynamicTransformer.instance==null) {
			// Disable trace collection during the instantiation process. As we use a couple of OSS components
			// ourselves, we may end up in an endless loop and StackOverflow exceptions otherwise
			//DynamicTransformer.TRACE_ENABLED = false;

			DynamicTransformer.instance = new DynamicTransformer();

			// Now that the instance has been created, we enable tracing again
			instance.setTransformationEnabled(true);
		}
		return DynamicTransformer.instance;
	}

	/**
	 * Returns true if the Singleton has been created.
	 * @param agentArgs
	 * @param inst
	 */
	public static synchronized boolean isInstantiated() { return DynamicTransformer.instance!=null; }

	public static void premain(String agentArgs, Instrumentation inst) {
		// Create monitor, which will register upload scheduler and start goal execution
		final ExecutionMonitor m = ExecutionMonitor.getInstance();

		// Create and register transformer, which will inject the byte code using instrumentors
		final DynamicTransformer t = DynamicTransformer.getInstance();
		inst.addTransformer(t);

		DynamicTransformer.log.info(t + " registered via JVM option -javaagent");
	}
}
