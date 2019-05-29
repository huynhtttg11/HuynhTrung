package com.sap.psr.vulas.cia.dependencyfinder;

import java.util.Iterator;

import com.jeantessier.classreader.Class_info;
import com.jeantessier.classreader.Classfile;
import com.jeantessier.diff.ClassDifferences;
import com.jeantessier.diff.Differences;
import com.jeantessier.diff.InterfaceDifferences;
import com.jeantessier.diff.PackageDifferences;
import com.jeantessier.diff.ProjectDifferences;
import com.jeantessier.diff.VisitorBase;
import com.sap.psr.vulas.shared.enums.ConstructType;
import com.sap.psr.vulas.shared.enums.ProgrammingLanguage;
import com.sap.psr.vulas.shared.json.model.Artifact;
import com.sap.psr.vulas.shared.json.model.ConstructId;
import com.sap.psr.vulas.shared.json.model.diff.JarDiffResult;

/**
 * Visits all kinds of changes and creates instances of the classes contained in package
 * {@link com.sap.psr.vulas.cia.dependencyfinder.model}. Inspired from {@link com.jeantessier.diff.Report}.
 * 
 *
 */
public class JarDiffVisitor extends VisitorBase {
	
	private JarDiffResult jarDiffResult = new JarDiffResult();
		
	public JarDiffVisitor(Artifact _old, Artifact _new) {
		this.jarDiffResult.setOldLibId(_old.getLibId());
		this.jarDiffResult.setNewLibId(_new.getLibId());
	}
	
	public JarDiffResult getJarDiffResult() {
		return jarDiffResult;
	}

	public void setJarDiffResult(JarDiffResult jarDiffResult) {
		this.jarDiffResult = jarDiffResult;
	}

	public void visitProjectDifferences(ProjectDifferences differences) {
        for (Differences packageDifference : differences.getPackageDifferences()) {
            packageDifference.accept(this);
        }
    }

    public void visitPackageDifferences(PackageDifferences differences) {
        if (differences.isRemoved()) {
        	jarDiffResult.addDeletedPackage(new com.sap.psr.vulas.shared.json.model.ConstructId(ProgrammingLanguage.JAVA, ConstructType.PACK, differences.getName()));
        }    
        for (Differences classDiffenrence : differences.getClassDifferences()) {
            classDiffenrence.accept(this);
        }
        if (differences.isNew()) {
            jarDiffResult.addNewPackage(new com.sap.psr.vulas.shared.json.model.ConstructId(ProgrammingLanguage.JAVA, ConstructType.PACK, differences.getName()));
        }
    }
    
    private com.sap.psr.vulas.shared.json.model.ConstructId getClass(Classfile _classfile) {
    	com.sap.psr.vulas.shared.json.model.ConstructId c = new com.sap.psr.vulas.shared.json.model.ConstructId();
    	
    	c.setLang(ProgrammingLanguage.JAVA);
    	c.setQname(_classfile.getClassName());
    	
    	if(_classfile.isDeprecated())
    		c.addRelates("annotation", new ConstructId(ProgrammingLanguage.JAVA, ConstructType.INTF, "java.lang.Deprecated"));
    	
    	c.addAttribute("final", _classfile.isFinal());
    	c.addAttribute("synthetic", _classfile.isSynthetic());
    	c.addAttribute("super", _classfile.isSuper());
    	
    	// Visibility
    	if(_classfile.isPublic())
    		c.addAttribute("visibility", "public");
    	else if(_classfile.isPackage())
    		c.addAttribute("visibility", "package");
    	
    	// Extends and Implements
    	if (_classfile.isInterface()) {
    		c.setType(ConstructType.INTF);
            Iterator<? extends Class_info> i = _classfile.getAllInterfaces().iterator();
            while (i.hasNext()) {
            	c.addRelates("extends", new ConstructId(ProgrammingLanguage.JAVA, ConstructType.INTF, i.next().toString()));
            }
        } else {
        	c.setType(ConstructType.CLAS);
        	c.addRelates("extends", new ConstructId(ProgrammingLanguage.JAVA, ConstructType.CLAS, _classfile.getSuperclassName()));
        	c.addAttribute("abstract", _classfile.isAbstract());
            Iterator<? extends Class_info> i = _classfile.getAllInterfaces().iterator();
            while (i.hasNext()) {
                c.addRelates("implements", new ConstructId(ProgrammingLanguage.JAVA, ConstructType.INTF,i.next().toString()));
            }
        }
    	
    	return c;
    }

    public void visitClassDifferences(ClassDifferences differences) {
        if (differences.isRemoved()) {
        	jarDiffResult.addDeletedClass(this.getClass(differences.getOldClass()));
        }
    
        if (differences.isModified()) {
            ClassDiffVisitor visitor = new ClassDiffVisitor(this.getClass(differences.getNewClass()));
            differences.accept(visitor);
            jarDiffResult.addModifiedClass(visitor.getClassDiffResult());
        }
    
        if (differences.isNew()) {
        	jarDiffResult.addNewClass(this.getClass(differences.getNewClass()));
        }

        if (isDeprecated()) {
        	jarDiffResult.addDeprecatedClass(this.getClass(differences.getNewClass()));
        }

        if (isUndeprecated()) {
        	jarDiffResult.addUndeprecatedClass(this.getClass(differences.getNewClass()));
        }
    }

    public void visitInterfaceDifferences(InterfaceDifferences differences) {
        if (differences.isRemoved()) {
            jarDiffResult.addDeletedInterface(this.getClass(differences.getOldClass()));
        }
    
        if (differences.isModified()) {
        	ClassDiffVisitor visitor = new ClassDiffVisitor(this.getClass(differences.getNewClass()));
            differences.accept(visitor);
            jarDiffResult.addModifiedInterface(visitor.getClassDiffResult());
        }
    
        if (differences.isNew()) {
            jarDiffResult.addNewInterface(this.getClass(differences.getNewClass()));
        }

        if (isDeprecated()) {
            jarDiffResult.addDeprecatedInterface(this.getClass(differences.getNewClass()));
        }

        if (isUndeprecated()) {
            jarDiffResult.addUndeprecatedInterface(this.getClass(differences.getNewClass()));
        }
    }
}
