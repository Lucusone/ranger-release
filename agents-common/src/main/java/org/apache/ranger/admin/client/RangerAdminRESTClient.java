/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

 package org.apache.ranger.admin.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.ranger.admin.client.datatype.GrantRevokeData;
import org.apache.ranger.admin.client.datatype.RESTResponse;
import org.apache.ranger.authorization.hadoop.config.RangerConfiguration;
import org.apache.ranger.authorization.hadoop.utils.RangerCredentialProvider;
import org.apache.ranger.authorization.utils.StringUtil;


public class RangerAdminRESTClient implements RangerAdminClient {
	private static final Log LOG = LogFactory.getLog(RangerAdminRESTClient.class);

	public static final String RANGER_PROP_POLICYMGR_URL                         = "xasecure.policymgr.url";
	public static final String RANGER_PROP_POLICYMGR_SSLCONFIG_FILENAME          = "xasecure.policymgr.sslconfig.filename";

	public static final String RANGER_POLICYMGR_CLIENT_KEY_FILE                  = "xasecure.policymgr.clientssl.keystore";	
	public static final String RANGER_POLICYMGR_CLIENT_KEY_FILE_PASSWORD         = "xasecure.policymgr.clientssl.keystore.password";	
	public static final String RANGER_POLICYMGR_CLIENT_KEY_FILE_TYPE             = "xasecure.policymgr.clientssl.keystore.type";
	public static final String RANGER_POLICYMGR_CLIENT_KEY_FILE_CREDENTIAL       = "xasecure.policymgr.clientssl.keystore.credential.file";
	public static final String RANGER_POLICYMGR_CLIENT_KEY_FILE_CREDENTIAL_ALIAS = "sslKeyStore";
	public static final String RANGER_POLICYMGR_CLIENT_KEY_FILE_TYPE_DEFAULT     = "jks";	

	public static final String RANGER_POLICYMGR_TRUSTSTORE_FILE                  = "xasecure.policymgr.clientssl.truststore";	
	public static final String RANGER_POLICYMGR_TRUSTSTORE_FILE_PASSWORD         = "xasecure.policymgr.clientssl.truststore.password";	
	public static final String RANGER_POLICYMGR_TRUSTSTORE_FILE_TYPE             = "xasecure.policymgr.clientssl.truststore.type";	
	public static final String RANGER_POLICYMGR_TRUSTSTORE_FILE_CREDENTIAL       = "xasecure.policymgr.clientssl.truststore.credential.file";
	public static final String RANGER_POLICYMGR_TRUSTSTORE_FILE_CREDENTIAL_ALIAS = "sslTrustStore";
	public static final String RANGER_POLICYMGR_TRUSTSTORE_FILE_TYPE_DEFAULT     = "jks";	

	public static final String RANGER_SSL_KEYMANAGER_ALGO_TYPE						  = "SunX509" ;
	public static final String RANGER_SSL_TRUSTMANAGER_ALGO_TYPE						  = "SunX509" ;
	public static final String RANGER_SSL_CONTEXT_ALGO_TYPE						      = "SSL" ;
	
	public static final String REST_EXPECTED_MIME_TYPE = "application/json" ;

	private static final String REST_URL_PATH_POLICYLIST        = "/service/assets/policyList/";
	private static final String REST_URL_PATH_GRANT             = "/service/assets/resources/grant";
	private static final String REST_URL_PATH_REVOKE            = "/service/assets/resources/revoke";
	private static final String REST_URL_PARAM_LASTUPDATED_TIME = "epoch";
	private static final String REST_URL_PARAM_POLICY_COUNT     = "policyCount";
	private static final String REST_URL_PARAM_AGENT_NAME       = "agentId";

	private String  mUrl               = null;
	private String  mSslConfigFileName = null;
	private boolean mIsSSL             = false;

	private String mKeyStoreURL     = null;
	private String mKeyStoreAlias   = null;
	private String mKeyStoreFile    = null;
	private String mKeyStoreType    = null;
	private String mTrustStoreURL   = null;
	private String mTrustStoreAlias = null;
	private String mTrustStoreFile  = null;
	private String mTrustStoreType  = null;


	public RangerAdminRESTClient() {
		mUrl               = RangerConfiguration.getInstance().get(RANGER_PROP_POLICYMGR_URL);
		mSslConfigFileName = RangerConfiguration.getInstance().get(RANGER_PROP_POLICYMGR_SSLCONFIG_FILENAME);

		init();
	}

	public RangerAdminRESTClient(String url, String sslConfigFileName) {
		mUrl               = url;
		mSslConfigFileName = sslConfigFileName;

		init();
	}

	@Override
	public String getPolicies(String repositoryName, long lastModifiedTime, int policyCount, String agentName) {
		String ret    = null;
		Client client = null;

		try {
			client = buildClient();

			WebResource webResource = client.resource(mUrl + REST_URL_PATH_POLICYLIST + repositoryName)
						.queryParam(REST_URL_PARAM_LASTUPDATED_TIME, String.valueOf(lastModifiedTime))
						.queryParam(REST_URL_PARAM_POLICY_COUNT, String.valueOf(policyCount))
						.queryParam(REST_URL_PARAM_AGENT_NAME, agentName);

			ClientResponse response = webResource.accept(REST_EXPECTED_MIME_TYPE).get(ClientResponse.class);

			if(response != null && response.getStatus() == 200) {
				ret = response.getEntity(String.class);
			}
		} finally {
			destroy(client);
		}

		return ret;
	}

	@Override
	public void grantPrivilege(GrantRevokeData grData) throws Exception {
		Client client = null;

		try {
			client = buildClient();

			WebResource webResource = client.resource(mUrl + REST_URL_PATH_GRANT);

			ClientResponse response = webResource.accept(REST_EXPECTED_MIME_TYPE).type(REST_EXPECTED_MIME_TYPE).post(ClientResponse.class, grData.toJson());

			if(response == null || response.getStatus() != 200) {
				RESTResponse resp = RESTResponse.fromClientResponse(response);

				throw new Exception(resp.getMessage());
			}
		} finally {
			destroy(client);
		}
	}

	@Override
	public void revokePrivilege(GrantRevokeData grData) throws Exception {
		Client client = null;
		
		try {
			client = buildClient();

			WebResource webResource = client.resource(mUrl + REST_URL_PATH_REVOKE);

			ClientResponse response = webResource.accept(REST_EXPECTED_MIME_TYPE).type(REST_EXPECTED_MIME_TYPE).post(ClientResponse.class, grData.toJson());

			if(response == null || response.getStatus() != 200) {
				RESTResponse resp = RESTResponse.fromClientResponse(response);

				throw new Exception(resp.getMessage());
			}
		} finally {
			destroy(client);
		}
	}
	private void init() {
		mIsSSL = StringUtil.containsIgnoreCase(mUrl, "https");

		InputStream in =  null ;

		try {
			Configuration conf = new Configuration() ;

			in = getFileInputStream(mSslConfigFileName) ;

			if (in != null) {
				conf.addResource(in);
			}

			mKeyStoreURL   = conf.get(RANGER_POLICYMGR_CLIENT_KEY_FILE_CREDENTIAL);
			mKeyStoreAlias = RANGER_POLICYMGR_CLIENT_KEY_FILE_CREDENTIAL_ALIAS;
			mKeyStoreType  = conf.get(RANGER_POLICYMGR_CLIENT_KEY_FILE_TYPE, RANGER_POLICYMGR_CLIENT_KEY_FILE_TYPE_DEFAULT);
			mKeyStoreFile  = conf.get(RANGER_POLICYMGR_CLIENT_KEY_FILE);

			mTrustStoreURL   = conf.get(RANGER_POLICYMGR_TRUSTSTORE_FILE_CREDENTIAL);
			mTrustStoreAlias = RANGER_POLICYMGR_TRUSTSTORE_FILE_CREDENTIAL_ALIAS;
			mTrustStoreType  = conf.get(RANGER_POLICYMGR_TRUSTSTORE_FILE_TYPE, RANGER_POLICYMGR_TRUSTSTORE_FILE_TYPE_DEFAULT);
			mTrustStoreFile  = conf.get(RANGER_POLICYMGR_TRUSTSTORE_FILE);
		}
		catch(IOException ioe) {
			LOG.error("Unable to load SSL Config FileName: [" + mSslConfigFileName + "]", ioe);
		}
		finally {
			close(in, mSslConfigFileName);
		}
	}

	private synchronized Client buildClient() {
		Client client = null;

		if (mIsSSL) {
			KeyManager[]   kmList     = getKeyManagers();
			TrustManager[] tmList     = getTrustManagers();
			SSLContext     sslContext = getSSLContext(kmList, tmList);
			ClientConfig   config     = new DefaultClientConfig();

			HostnameVerifier hv = new HostnameVerifier() {
				public boolean verify(String urlHostName, SSLSession session) {
					return session.getPeerHost().equals(urlHostName);
				}
			};

			config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, new HTTPSProperties(hv, sslContext));

			client = Client.create(config);
		}

		if(client == null) {
			client = Client.create();
		}

		return client;
	}

	private KeyManager[] getKeyManagers() {
		KeyManager[] kmList = null;

		String keyStoreFilepwd = getCredential(mKeyStoreURL, mKeyStoreAlias);

		if (!StringUtil.isEmpty(mKeyStoreFile) && !StringUtil.isEmpty(keyStoreFilepwd)) {
			InputStream in =  null ;

			try {
				in = getFileInputStream(mKeyStoreFile) ;

				if (in != null) {
					KeyStore keyStore = KeyStore.getInstance(mKeyStoreType);

					keyStore.load(in, keyStoreFilepwd.toCharArray());

					KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(RANGER_SSL_KEYMANAGER_ALGO_TYPE);

					keyManagerFactory.init(keyStore, keyStoreFilepwd.toCharArray());

					kmList = keyManagerFactory.getKeyManagers();
				} else {
					LOG.error("Unable to obtain keystore from file [" + mKeyStoreFile + "]");
				}
			} catch (KeyStoreException e) {
				LOG.error("Unable to obtain from KeyStore", e);
			} catch (NoSuchAlgorithmException e) {
				LOG.error("SSL algorithm is available in the environment", e);
			} catch (CertificateException e) {
				LOG.error("Unable to obtain the requested certification ", e);
			} catch (FileNotFoundException e) {
				LOG.error("Unable to find the necessary SSL Keystore and TrustStore Files", e);
			} catch (IOException e) {
				LOG.error("Unable to read the necessary SSL Keystore and TrustStore Files", e);
			} catch (UnrecoverableKeyException e) {
				LOG.error("Unable to recover the key from keystore", e);
			} finally {
				close(in, mKeyStoreFile);
			}
		}

		return kmList;
	}

	private TrustManager[] getTrustManagers() {
		TrustManager[] tmList = null;

		String trustStoreFilepwd = getCredential(mTrustStoreURL, mTrustStoreAlias);

		if (!StringUtil.isEmpty(mTrustStoreFile) && !StringUtil.isEmpty(trustStoreFilepwd)) {
			InputStream in =  null ;

			try {
				in = getFileInputStream(mTrustStoreFile) ;

				if (in != null) {
					KeyStore trustStore = KeyStore.getInstance(mTrustStoreType);

					trustStore.load(in, trustStoreFilepwd.toCharArray());

					TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(RANGER_SSL_TRUSTMANAGER_ALGO_TYPE);

					trustManagerFactory.init(trustStore);

					tmList = trustManagerFactory.getTrustManagers();
				} else {
					LOG.error("Unable to obtain keystore from file [" + mTrustStoreFile + "]");
				}
			} catch (KeyStoreException e) {
				LOG.error("Unable to obtain from KeyStore", e);
			} catch (NoSuchAlgorithmException e) {
				LOG.error("SSL algorithm is available in the environment", e);
			} catch (CertificateException e) {
				LOG.error("Unable to obtain the requested certification ", e);
			} catch (FileNotFoundException e) {
				LOG.error("Unable to find the necessary SSL Keystore and TrustStore Files", e);
			} catch (IOException e) {
				LOG.error("Unable to read the necessary SSL Keystore and TrustStore Files", e);
			} finally {
				close(in, mTrustStoreFile);
			}
		}
		
		return tmList;
	}
	
	private SSLContext getSSLContext(KeyManager[] kmList, TrustManager[] tmList) {
		try {
			if(kmList != null && tmList != null) {
				SSLContext sslContext = SSLContext.getInstance(RANGER_SSL_CONTEXT_ALGO_TYPE);
	
				sslContext.init(kmList, tmList, new SecureRandom());
				
				return sslContext;
			}
		} catch (NoSuchAlgorithmException e) {
			LOG.error("SSL algorithm is available in the environment", e);
		} catch (KeyManagementException e) {
			LOG.error("Unable to initials the SSLContext", e);
		}
		
		return null;
	}

	private String getCredential(String url, String alias) {
		char[] credStr = RangerCredentialProvider.getInstance().getCredentialString(url, alias);

		return credStr == null ? null : new String(credStr);
	}

	private InputStream getFileInputStream(String fileName)  throws IOException {
		InputStream in = null ;

		if(! StringUtil.isEmpty(fileName)) {
			File f = new File(fileName) ;

			if (f.exists()) {
				in = new FileInputStream(f) ;
			}
			else {
				in = ClassLoader.getSystemResourceAsStream(fileName) ;
			}
		}

		return in ;
	}

	private void close(InputStream str, String filename) {
		if (str != null) {
			try {
				str.close() ;
			} catch (IOException excp) {
				LOG.error("Error while closing file: [" + filename + "]", excp) ;
			}
		}
	}

	private void destroy(Client client) {
		if(client != null) {
			client.destroy();
		}
	}
}
