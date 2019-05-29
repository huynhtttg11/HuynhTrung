package com.sap.psr.vulas.shared.enums;

import java.security.MessageDigest;

import com.sap.psr.vulas.shared.util.FileUtil;

/**
 * Enumeration of different digest algorithms, to be used when computing the digest of libraries with {@link FileUtil#getDigest(java.io.File, DigestAlgorithm)}.
 * @see {@link MessageDigest}
 */
public enum DigestAlgorithm {
	SHA1((byte)10), SHA256((byte)20), MD5((byte)30);

	private byte value;

	private DigestAlgorithm(byte _value) { this.value = _value; }

	public String toString() {
		if(this.value==10) return "SHA1";
		else if(this.value==20) return "SHA256";
		else if(this.value==30) return "MD5";
		else throw new IllegalArgumentException("[" + this.value + "] is not a valid digest algorithm");
	}
}
