package com.sap.psr.vulas.python.sign;

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sap.psr.vulas.Construct;
import com.sap.psr.vulas.FileAnalysisException;
import com.sap.psr.vulas.FileAnalyzerFactory;
import com.sap.psr.vulas.python.Python3FileAnalyzer;
import com.sap.psr.vulas.shared.enums.ConstructType;
import com.sap.psr.vulas.shared.enums.DigestAlgorithm;
import com.sap.psr.vulas.shared.enums.ProgrammingLanguage;
import com.sap.psr.vulas.shared.json.model.ConstructId;
import com.sap.psr.vulas.sign.Signature;
import com.sap.psr.vulas.sign.SignatureChange;
import com.sap.psr.vulas.sign.SignatureFactory;

/**
 * Creates construct signatures for several Python constructs. The signature is simply a digest computed over
 * the respective construct body.
 * TODO: Strip comments and the like?
 * TODO: Check the digest algo used by the SW Heritage API
 */
public class PythonSignatureFactory implements SignatureFactory {

	private static final Log log = LogFactory.getLog(PythonSignatureFactory.class);

	/**
	 * Returns true if the given {@link ConstructId} is of type Java method or Java constructor.
	 */
	@Override
	public boolean isSupportedConstructId(ConstructId _id) {
		return 	_id!=null &&
				ProgrammingLanguage.PY.equals(_id.getLang()) &&
				( ConstructType.FUNC.equals(_id.getType()) || ConstructType.MODU.equals(_id.getType()) ||
				  ConstructType.METH.equals(_id.getType()) || ConstructType.CONS.equals(_id.getType()) );
	}

	@Override
	public Signature createSignature(Construct _construct) {
		if(_construct!=null && _construct.getContent()!=null)
			return new PythonConstructDigest(_construct.getContent(), DigestAlgorithm.SHA1);
		else
			return null;
	}

	@Override
	public Signature createSignature(ConstructId _cid, File _file) {
		if(!_cid.getLang().equals(ProgrammingLanguage.PY))
			throw new IllegalArgumentException("Programming language [" + _cid.getLang() + "] not supported");
		
		Signature signature = null;
		
		// Compute signature from the entire file
		if(_cid.getType().equals(ConstructType.MODU)) {
			signature = new PythonConstructDigest(_file.toPath(), DigestAlgorithm.SHA1);
		}
		// For all others, parse the file, and compute the digest for the construct body
		else {
			try {
				final Python3FileAnalyzer fa = (Python3FileAnalyzer)FileAnalyzerFactory.buildFileAnalyzer(_file, new String[] { "py" });
				final Construct c = fa.getConstruct(_cid);
				if(c==null) {
					throw new IllegalStateException("Construct [" + _cid +"] cannot be found in file [" + _file + "]");
				}
				else {
					signature = new PythonConstructDigest(c.getContent(), DigestAlgorithm.SHA1);
				}
			} catch (IllegalArgumentException e) {
				log.error(e);
			} catch (FileAnalysisException e) {
				log.error(e);
			}
		}
		return signature;
	}

	@Override
	public SignatureChange computeChange(Construct _from, Construct _to) {
		// TODO Auto-generated method stub
		return null;
	}
}
