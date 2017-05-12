/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ranger.plugin.util;

import java.io.IOException;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.core.util.Base64;
public class PasswordUtils {

	private static final Logger LOG = LoggerFactory.getLogger(PasswordUtils.class);
	
	private static String CRYPT_ALGO = null;
	private static String password = null;
	private static char[] ENCRYPT_KEY = null;
	private static byte[] SALT = null;
	private static int ITERATION_COUNT = 0;
	private static final String LEN_SEPARATOR_STR = ":";

	public static final String DEFAULT_CRYPT_ALGO = "PBEWithMD5AndDES";
	public static final String DEFAULT_ENCRYPT_KEY = "tzL1AKl5uc4NKYaoQ4P3WLGIBFPXWPWdu1fRm9004jtQiV";
	public static final String DEFAULT_SALT = "f77aLYLo";
	public static final int DEFAULT_ITERATION_COUNT = 1000;		
	
	public static String encryptPassword(String aPassword) throws IOException {
		setPropertiesValues(aPassword);
		Map<String, String> env = System.getenv();
		String encryptKeyStr = env.get("ENCRYPT_KEY");
		char[] encryptKey;		
		if (encryptKeyStr == null) {
			encryptKey=ENCRYPT_KEY;
		}else{
			encryptKey=encryptKeyStr.toCharArray();
		}
		String saltStr = env.get("ENCRYPT_SALT");
		byte[] salt;
		if (saltStr == null) {
			salt = SALT;
		}else{
			salt=saltStr.getBytes();
		}
		String ret = null;
		String strToEncrypt = null;		
		if (aPassword == null) {
			strToEncrypt = "";
		}
		else {
			strToEncrypt = aPassword.length() + LEN_SEPARATOR_STR + password;
		}		
		try {
			Cipher engine = Cipher.getInstance(CRYPT_ALGO);
			PBEKeySpec keySpec = new PBEKeySpec(encryptKey);
			SecretKeyFactory skf = SecretKeyFactory.getInstance(CRYPT_ALGO);
			SecretKey key = skf.generateSecret(keySpec);
			engine.init(Cipher.ENCRYPT_MODE, key, new PBEParameterSpec(salt, ITERATION_COUNT));
			byte[] encryptedStr = engine.doFinal(strToEncrypt.getBytes());
			ret = new String(Base64.encode(encryptedStr));
		}
		catch(Throwable t) {
			LOG.error("Unable to encrypt password due to error", t);
			throw new IOException("Unable to encrypt password due to error", t);
		}		
		return ret;
	}

	public static String decryptPassword(String aPassword) throws IOException {
		setPropertiesValues(aPassword);
		String ret = null;
		Map<String, String> env = System.getenv();
		String encryptKeyStr = env.get("ENCRYPT_KEY");
		char[] encryptKey;		
		if (encryptKeyStr == null) {
			encryptKey=ENCRYPT_KEY;
		}else{
			encryptKey=encryptKeyStr.toCharArray();
		}
		String saltStr = env.get("ENCRYPT_SALT");
		byte[] salt;
		if (saltStr == null) {
			salt = SALT;
		}else{
			salt=saltStr.getBytes();
		}
		try {			
			byte[] decodedPassword = Base64.decode(password);
			Cipher engine = Cipher.getInstance(CRYPT_ALGO);
			PBEKeySpec keySpec = new PBEKeySpec(encryptKey);
			SecretKeyFactory skf = SecretKeyFactory.getInstance(CRYPT_ALGO);
			SecretKey key = skf.generateSecret(keySpec);
			engine.init(Cipher.DECRYPT_MODE, key,new PBEParameterSpec(salt, ITERATION_COUNT));
			String decrypted = new String(engine.doFinal(decodedPassword));
			int foundAt = decrypted.indexOf(LEN_SEPARATOR_STR);
			if (foundAt > -1) {
				if (decrypted.length() > foundAt) {
					ret = decrypted.substring(foundAt+1);
				}
				else {
					ret = "";
				}
			}
			else {
				ret = null;
			}
		}
		catch(Throwable t) {
			LOG.error("Unable to decrypt password due to error", t);
			throw new IOException("Unable to decrypt password due to error", t);
		}
		return ret;
	}
	
	public static void setPropertiesValues(String aPassword) {
		String[] crypt_algo_array = null;
		if (aPassword.contains(",")) {
			crypt_algo_array = aPassword.split(",");
		}
		if (crypt_algo_array != null && crypt_algo_array.length > 1) {
			CRYPT_ALGO = crypt_algo_array[0];
			ENCRYPT_KEY = crypt_algo_array[1].toCharArray();
			SALT = crypt_algo_array[2].getBytes();
			ITERATION_COUNT = Integer.parseInt(crypt_algo_array[3]);
			password = crypt_algo_array[4];
		} else {
			CRYPT_ALGO = DEFAULT_CRYPT_ALGO;
			ENCRYPT_KEY = DEFAULT_ENCRYPT_KEY.toCharArray();
			SALT = DEFAULT_SALT.getBytes();
			ITERATION_COUNT = DEFAULT_ITERATION_COUNT;
		}
	}
}
