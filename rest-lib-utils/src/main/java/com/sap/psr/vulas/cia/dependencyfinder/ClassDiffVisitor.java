package com.sap.psr.vulas.cia.dependencyfinder;

import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;

import com.jeantessier.classreader.Annotation;
import com.jeantessier.classreader.AnnotationDefault_attribute;
import com.jeantessier.classreader.AnnotationElementValue;
import com.jeantessier.classreader.ArrayElementValue;
import com.jeantessier.classreader.BooleanConstantElementValue;
import com.jeantessier.classreader.ByteConstantElementValue;
import com.jeantessier.classreader.CharConstantElementValue;
import com.jeantessier.classreader.ClassElementValue;
import com.jeantessier.classreader.Class_info;
import com.jeantessier.classreader.Classfile;
import com.jeantessier.classreader.Code_attribute;
import com.jeantessier.classreader.ConstantPool;
import com.jeantessier.classreader.ConstantValue_attribute;
import com.jeantessier.classreader.Custom_attribute;
import com.jeantessier.classreader.Deprecated_attribute;
import com.jeantessier.classreader.DoubleConstantElementValue;
import com.jeantessier.classreader.Double_info;
import com.jeantessier.classreader.ElementValuePair;
import com.jeantessier.classreader.EnclosingMethod_attribute;
import com.jeantessier.classreader.EnumElementValue;
import com.jeantessier.classreader.ExceptionHandler;
import com.jeantessier.classreader.Exceptions_attribute;
import com.jeantessier.classreader.FieldRef_info;
import com.jeantessier.classreader.Field_info;
import com.jeantessier.classreader.FloatConstantElementValue;
import com.jeantessier.classreader.Float_info;
import com.jeantessier.classreader.InnerClass;
import com.jeantessier.classreader.InnerClasses_attribute;
import com.jeantessier.classreader.Instruction;
import com.jeantessier.classreader.IntegerConstantElementValue;
import com.jeantessier.classreader.Integer_info;
import com.jeantessier.classreader.InterfaceMethodRef_info;
import com.jeantessier.classreader.InvokeDynamic_info;
import com.jeantessier.classreader.LineNumber;
import com.jeantessier.classreader.LineNumberTable_attribute;
import com.jeantessier.classreader.LocalVariable;
import com.jeantessier.classreader.LocalVariableTable_attribute;
import com.jeantessier.classreader.LocalVariableType;
import com.jeantessier.classreader.LocalVariableTypeTable_attribute;
import com.jeantessier.classreader.LongConstantElementValue;
import com.jeantessier.classreader.Long_info;
import com.jeantessier.classreader.MethodHandle_info;
import com.jeantessier.classreader.MethodRef_info;
import com.jeantessier.classreader.MethodType_info;
import com.jeantessier.classreader.Method_info;
import com.jeantessier.classreader.NameAndType_info;
import com.jeantessier.classreader.Parameter;
import com.jeantessier.classreader.RuntimeInvisibleAnnotations_attribute;
import com.jeantessier.classreader.RuntimeInvisibleParameterAnnotations_attribute;
import com.jeantessier.classreader.RuntimeVisibleAnnotations_attribute;
import com.jeantessier.classreader.RuntimeVisibleParameterAnnotations_attribute;
import com.jeantessier.classreader.ShortConstantElementValue;
import com.jeantessier.classreader.Signature_attribute;
import com.jeantessier.classreader.SourceDebugExtension_attribute;
import com.jeantessier.classreader.SourceFile_attribute;
import com.jeantessier.classreader.StringConstantElementValue;
import com.jeantessier.classreader.String_info;
import com.jeantessier.classreader.Synthetic_attribute;
import com.jeantessier.classreader.UTF8_info;
import com.jeantessier.diff.ClassDifferences;
import com.jeantessier.diff.CodeDifferences;
import com.jeantessier.diff.ConstructorDifferences;
import com.jeantessier.diff.Differences;
import com.jeantessier.diff.FeatureDifferences;
import com.jeantessier.diff.FieldDifferences;
import com.jeantessier.diff.InterfaceDifferences;
import com.jeantessier.diff.MethodDifferences;
import com.jeantessier.diff.VisitorBase;
import com.sap.psr.vulas.shared.enums.ConstructType;
import com.sap.psr.vulas.shared.enums.ProgrammingLanguage;
import com.sap.psr.vulas.shared.json.model.ConstructId;
import com.sap.psr.vulas.shared.json.model.diff.ClassDiffResult;
import com.sap.psr.vulas.shared.json.model.diff.ClassModification;

/**
 * Visits all kinds of changes and creates instances of the classes contained in package
 * {@link com.sap.psr.vulas.cia.dependencyfinder.model}. Inspired from {@link com.jeantessier.diff.ClassReport}.
 *
 */
public class ClassDiffVisitor extends VisitorBase implements com.jeantessier.classreader.Visitor {

	private ClassDiffResult classDiffResult;

	private ClassDifferences differences;
	
	private Collection<FeatureDifferences> removedFields = new TreeSet<FeatureDifferences>();
    private Collection<FeatureDifferences> removedConstructors = new TreeSet<FeatureDifferences>();
    private Collection<FeatureDifferences> removedMethods = new TreeSet<FeatureDifferences>();

    private Collection<FeatureDifferences> deprecatedFields = new TreeSet<FeatureDifferences>();
    private Collection<FeatureDifferences> deprecatedConstructors = new TreeSet<FeatureDifferences>();
    private Collection<FeatureDifferences> deprecatedMethods = new TreeSet<FeatureDifferences>();

    private Collection<FieldDifferences> modifiedFields = new TreeSet<FieldDifferences>();
    private Collection<CodeDifferences> modifiedConstructors = new TreeSet<CodeDifferences>();
    private Collection<CodeDifferences> modifiedMethods = new TreeSet<CodeDifferences>();

    private Collection<FeatureDifferences> undeprecatedFields = new TreeSet<FeatureDifferences>();
    private Collection<FeatureDifferences> undeprecatedConstructors = new TreeSet<FeatureDifferences>();
    private Collection<FeatureDifferences> undeprecatedMethods = new TreeSet<FeatureDifferences>();

    private Collection<FeatureDifferences> newFields = new TreeSet<FeatureDifferences>();
    private Collection<FeatureDifferences> newConstructors = new TreeSet<FeatureDifferences>();
    private Collection<FeatureDifferences> newMethods = new TreeSet<FeatureDifferences>();
	
	public ClassDiffVisitor(com.sap.psr.vulas.shared.json.model.ConstructId _c) {
		this.classDiffResult = new ClassDiffResult();
		this.classDiffResult.setClazz(_c);
	}

	private com.sap.psr.vulas.shared.json.model.ConstructId getMethod(Method_info _mi) {
		com.sap.psr.vulas.shared.json.model.ConstructId m = new com.sap.psr.vulas.shared.json.model.ConstructId();

		m.setQname(_mi.getFullSignature());
		
		if(_mi.isPublic()) m.addAttribute("visibility", "public");
		else if(_mi.isProtected()) m.addAttribute("visibility", "protected");
		else if(_mi.isPackage()) m.addAttribute("visibility", "package");
		else if(_mi.isPrivate()) m.addAttribute("visibility", "private");

		m.addAttribute("static", _mi.isStatic());
		m.addAttribute("final", _mi.isFinal());
		m.addAttribute("synchronized", _mi.isSynchronized());
		m.addAttribute("native", _mi.isNative());
		m.addAttribute("abstract", _mi.isAbstract());
		m.addAttribute("strict", _mi.isStrict());
		m.addAttribute("synthetic", _mi.isSynthetic());
		
		if(_mi.isDeprecated())
    		m.addRelates("annotation", new ConstructId(ProgrammingLanguage.JAVA, ConstructType.INTF, "java.lang.Deprecated"));

		if (_mi.getName().equals("<init>")) {
			m.setType(ConstructType.CONS);
		}
		else if(_mi.getName().equals("<clinit>")) {
			m.setType(ConstructType.INIT);
		}
		else {
			m.setType(ConstructType.METH);
			//TODO: Can be of type CLAS or INTF
			m.addRelates("returns", new ConstructId(ProgrammingLanguage.JAVA, ConstructType.CLAS, _mi.getReturnType()));
		}

		Iterator i = _mi.getExceptions().iterator();
		while (i.hasNext()) {
			//TODO: Can be of type CLAS or INTF
			m.addRelates("throws", new ConstructId(ProgrammingLanguage.JAVA, ConstructType.CLAS, i.next().toString()));
		}

		return m;
	}
			
	public void visitClassDifferences(ClassDifferences differences) {
		this.differences = differences;

		for (Differences featureDifference : differences.getFeatureDifferences()) {
			featureDifference.accept(this);
		}
	}

	public void visitInterfaceDifferences(InterfaceDifferences differences) {
		this.differences = differences;

		for (Differences featureDifference : differences.getFeatureDifferences()) {
			featureDifference.accept(this);
		}
	}

	public void visitFieldDifferences(FieldDifferences differences) {
		// Do nothing
	}
	
	public void visitConstructorDifferences(ConstructorDifferences differences) {
        if (differences.isRemoved()) {
            removedConstructors.add(differences);
        }
    
        if (differences.isModified()) {
            modifiedConstructors.add(differences);
        }
    
        if (differences.isNew()) {
            newConstructors.add(differences);
        }

        if (isDeprecated()) {
            deprecatedConstructors.add(differences);
        }

        if (isUndeprecated()) {
            undeprecatedConstructors.add(differences);
        }
    }

    public void visitMethodDifferences(MethodDifferences differences) {
        if (differences.isRemoved()) {
            removedMethods.add(differences);
        }
    
        if (differences.isModified()) {
            modifiedMethods.add(differences);
        }
    
        if (differences.isNew()) {
            newMethods.add(differences);
        }

        if (isDeprecated()) {
            deprecatedMethods.add(differences);
        }

        if (isUndeprecated()) {
            undeprecatedMethods.add(differences);
        }
    }

	public void visitClassfiles(Collection<Classfile> classfiles) {
		// Do nothing
	}

	public void visitClassfile(Classfile classfile) {
		// Do nothing
	}

	public void visitConstantPool(ConstantPool constantPool) {
		// Do nothing
	}

	public void visitClass_info(Class_info entry) {
		// Do nothing
	}

	public void visitFieldRef_info(FieldRef_info entry) {
		// Do nothing
	}

	public void visitMethodRef_info(MethodRef_info entry) {
		// Do nothing
	}

	public void visitInterfaceMethodRef_info(InterfaceMethodRef_info entry) {
		// Do nothing
	}

	public void visitString_info(String_info entry) {
		// Do nothing
	}

	public void visitInteger_info(Integer_info entry) {
		// Do nothing
	}

	public void visitFloat_info(Float_info entry) {
		// Do nothing
	}

	public void visitLong_info(Long_info entry) {
		// Do nothing
	}

	public void visitDouble_info(Double_info entry) {
		// Do nothing
	}

	public void visitNameAndType_info(NameAndType_info entry) {
		// Do nothing
	}

	public void visitUTF8_info(UTF8_info entry) {
		// Do nothing
	}

	public void visitMethodHandle_info(MethodHandle_info entry) {
		// Do nothing
	}

	public void visitMethodType_info(MethodType_info entry) {
		// Do nothing
	}

	public void visitInvokeDynamic_info(InvokeDynamic_info entry) {
		// Do nothing
	}

	public void visitField_info(Field_info entry) {
		// Do nothing
	}

	public void visitMethod_info(Method_info entry) {
		// Do nothing
	}

	public void visitConstantValue_attribute(ConstantValue_attribute attribute) {
		attribute.getRawValue().accept(this);
	}

	public void visitCode_attribute(Code_attribute attribute) {
		// Do nothing
	}

	public void visitExceptions_attribute(Exceptions_attribute attribute) {
		// Do nothing
	}

	public void visitInnerClasses_attribute(InnerClasses_attribute attribute) {
		// Do nothing
	}

	public void visitEnclosingMethod_attribute(EnclosingMethod_attribute attribute) {
		// Do nothing
	}

	public void visitSynthetic_attribute(Synthetic_attribute attribute) {
		// Do nothing
	}

	public void visitSignature_attribute(Signature_attribute attribute) {
		// Do nothing
	}

	public void visitSourceFile_attribute(SourceFile_attribute attribute) {
		// Do nothing
	}

	public void visitSourceDebugExtension_attribute(SourceDebugExtension_attribute attribute) {
		// Do nothing
	}

	public void visitLineNumberTable_attribute(LineNumberTable_attribute attribute) {
		// Do nothing
	}

	public void visitLocalVariableTable_attribute(LocalVariableTable_attribute attribute) {
		// Do nothing
	}

	public void visitLocalVariableTypeTable_attribute(LocalVariableTypeTable_attribute attribute) {
		// Do nothing
	}

	public void visitDeprecated_attribute(Deprecated_attribute attribute) {
		// Do nothing
	}

	public void visitRuntimeVisibleAnnotations_attribute(RuntimeVisibleAnnotations_attribute attribute) {
		// Do nothing
	}

	public void visitRuntimeInvisibleAnnotations_attribute(RuntimeInvisibleAnnotations_attribute attribute) {
		// Do nothing
	}

	public void visitRuntimeVisibleParameterAnnotations_attribute(RuntimeVisibleParameterAnnotations_attribute attribute) {
		// Do nothing
	}

	public void visitRuntimeInvisibleParameterAnnotations_attribute(RuntimeInvisibleParameterAnnotations_attribute attribute) {
		// Do nothing
	}

	public void visitAnnotationDefault_attribute(AnnotationDefault_attribute attribute) {
		// Do nothing
	}

	public void visitCustom_attribute(Custom_attribute attribute) {
		// Do nothing
	}

	public void visitInstruction(Instruction instruction) {
		// Do nothing
	}

	public void visitExceptionHandler(ExceptionHandler helper) {
		// Do nothing
	}

	public void visitInnerClass(InnerClass helper) {
		// Do nothing
	}

	public void visitLineNumber(LineNumber helper) {
		// Do nothing
	}

	public void visitLocalVariable(LocalVariable helper) {
		// Do nothing
	}

	public void visitLocalVariableType(LocalVariableType helper) {
		// Do nothing
	}

	public void visitParameter(Parameter helper) {
		// Do nothing
	}

	public void visitAnnotation(Annotation helper) {
		// Do nothing
	}

	public void visitElementValuePair(ElementValuePair helper) {
		// Do nothing
	}

	public void visitByteConstantElementValue(ByteConstantElementValue helper) {
		// Do nothing
	}

	public void visitCharConstantElementValue(CharConstantElementValue helper) {
		// Do nothing
	}

	public void visitDoubleConstantElementValue(DoubleConstantElementValue helper) {
		// Do nothing
	}

	public void visitFloatConstantElementValue(FloatConstantElementValue helper) {
		// Do nothing
	}

	public void visitIntegerConstantElementValue(IntegerConstantElementValue helper) {
		// Do nothing
	}

	public void visitLongConstantElementValue(LongConstantElementValue helper) {
		// Do nothing
	}

	public void visitShortConstantElementValue(ShortConstantElementValue helper) {
		// Do nothing
	}

	public void visitBooleanConstantElementValue(BooleanConstantElementValue helper) {
		// Do nothing
	}

	public void visitStringConstantElementValue(StringConstantElementValue helper) {
		// Do nothing
	}

	public void visitEnumElementValue(EnumElementValue helper) {
		// Do nothing
	}

	public void visitClassElementValue(ClassElementValue helper) {
		// Do nothing
	}

	public void visitAnnotationElementValue(AnnotationElementValue helper) {
		// Do nothing
	}

	public void visitArrayElementValue(ArrayElementValue helper) {
		// Do nothing
	}

	public ClassDiffResult getClassDiffResult() {
		
		if (removedConstructors.size() != 0) {
            for (FeatureDifferences fd : removedConstructors) {
                //indent().append("<declaration").append(breakdownDeclaration((Method_info) fd.getOldFeature())).append(fd.isInherited() ? " inherited=\"yes\"" : "").append(">").append(fd.getOldDeclaration()).append("</declaration>").eol();
                this.classDiffResult.addRemovedContructor(this.getMethod((Method_info)fd.getOldFeature()));
            }
        }

        if (removedMethods.size() != 0) {
            for (FeatureDifferences fd : removedMethods) {
                //indent().append("<declaration").append(breakdownDeclaration((Method_info) fd.getOldFeature())).append(fd.isInherited() ? " inherited=\"yes\"" : "").append(">").append(fd.getOldDeclaration()).append("</declaration>").eol();
            	this.classDiffResult.addRemovedMethod(this.getMethod((Method_info)fd.getOldFeature()));
            }
        }

        if (deprecatedConstructors.size() != 0) {
            for (FeatureDifferences fd : deprecatedConstructors) {
                //indent().append("<declaration").append(breakdownDeclaration((Method_info) fd.getNewFeature())).append(">").append(fd.getOldDeclaration()).append("</declaration>").eol();
            	this.classDiffResult.addDeprecatedContructor(this.getMethod((Method_info)fd.getNewFeature()));
            }
        }

        if (deprecatedMethods.size() != 0) {
            for (FeatureDifferences fd : deprecatedMethods) {
                //indent().append("<declaration").append(breakdownDeclaration((Method_info) fd.getNewFeature())).append(">").append(fd.getOldDeclaration()).append("</declaration>").eol();
            	this.classDiffResult.addDeprecatedMethod(this.getMethod((Method_info)fd.getNewFeature()));
            }
        }

        if (modifiedConstructors.size() != 0) {
            for (CodeDifferences cd : modifiedConstructors) {
            	ClassModification mod = new ClassModification();
            	mod.setNewConstruct(this.getMethod((Method_info)cd.getNewFeature()));
                if (!cd.getOldDeclaration().equals(cd.getNewDeclaration())) {
                    //indent().append("<old-declaration").append(breakdownDeclaration((Method_info) cd.getOldFeature())).append(">").append(cd.getOldDeclaration()).append("</old-declaration>").eol();
                    //indent().append("<new-declaration").append(breakdownDeclaration((Method_info) cd.getNewFeature())).append(">").append(cd.getNewDeclaration()).append("</new-declaration>").eol();
                	mod.setDeclarationChanged(true);
    				mod.setOldConstruct(this.getMethod((Method_info)cd.getOldFeature()));
                }
                if (cd.isCodeDifference()) {
                    //indent().append("<modified-code").append(breakdownDeclaration((Method_info) cd.getNewFeature())).append(">").append(cd.getNewDeclaration()).append("</modified-code>").eol();
                	mod.setBodyChanged(cd.isCodeDifference());
                }
                this.classDiffResult.addModifiedContructor(mod);
            }
        }

        if (modifiedMethods.size() != 0) {
            for (CodeDifferences md : modifiedMethods) {
            	ClassModification mod = new ClassModification();
            	mod.setNewConstruct(this.getMethod((Method_info)md.getNewFeature()));
                if (!md.getOldDeclaration().equals(md.getNewDeclaration())) {
                    //indent().append("<old-declaration").append(breakdownDeclaration((Method_info) md.getOldFeature())).append(">").append(md.getOldDeclaration()).append("</old-declaration>").eol();
                    //indent().append("<new-declaration").append(breakdownDeclaration((Method_info) md.getNewFeature())).append(">").append(md.getNewDeclaration()).append("</new-declaration>").eol();
                	mod.setDeclarationChanged(true);
    				mod.setOldConstruct(this.getMethod((Method_info)md.getOldFeature()));
                }
                if (md.isCodeDifference()) {
                    //indent().append("<modified-code").append(breakdownDeclaration((Method_info) md.getNewFeature())).append(">").append(md.getNewDeclaration()).append("</modified-code>").eol();
                	mod.setBodyChanged(md.isCodeDifference());
                }
                this.classDiffResult.addModifiedMethod(mod);
            }
        }

        if (undeprecatedConstructors.size() != 0) {
            for (FeatureDifferences fd : undeprecatedConstructors) {
                //indent().append("<declaration").append(breakdownDeclaration((Method_info) fd.getNewFeature())).append(">").append(fd.getOldDeclaration()).append("</declaration>").eol();
            	this.classDiffResult.addUndeprecatedContructor(this.getMethod((Method_info)fd.getNewFeature()));
            }
        }

        if (undeprecatedMethods.size() != 0) {
            for (FeatureDifferences fd : undeprecatedMethods) {
                //indent().append("<declaration").append(breakdownDeclaration((Method_info) fd.getNewFeature())).append(">").append(fd.getOldDeclaration()).append("</declaration>").eol();
            	this.classDiffResult.addUndeprecatedMethod(this.getMethod((Method_info)fd.getNewFeature()));
            }
        }
        
        if (newConstructors.size() != 0) {
            for (FeatureDifferences fd : newConstructors) {
                //indent().append("<declaration").append(breakdownDeclaration((Method_info) fd.getNewFeature())).append(">").append(fd.getNewDeclaration()).append("</declaration>").eol();
            	this.classDiffResult.addNewContructor(this.getMethod((Method_info)fd.getNewFeature()));
            }
        }

        if (newMethods.size() != 0) {
            for (FeatureDifferences fd : newMethods) {
                //indent().append("<declaration").append(breakdownDeclaration((Method_info) fd.getNewFeature())).append(">").append(fd.getNewDeclaration()).append("</declaration>").eol();
            	this.classDiffResult.addNewMethod(this.getMethod((Method_info)fd.getNewFeature()));
            }
        }
        
		return classDiffResult;
	}
}
