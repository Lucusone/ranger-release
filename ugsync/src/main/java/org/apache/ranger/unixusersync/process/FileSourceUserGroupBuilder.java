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

package org.apache.ranger.unixusersync.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.ranger.unixusersync.config.UserGroupSyncConfig;
import org.apache.ranger.usergroupsync.UserGroupSink;
import org.apache.ranger.usergroupsync.UserGroupSource;

import com.google.gson.stream.JsonReader;

public class FileSourceUserGroupBuilder  implements UserGroupSource {
	private static final Logger LOG = Logger.getLogger(FileSourceUserGroupBuilder.class) ;

	private Map<String,List<String>> user2GroupListMap     = new HashMap<String,List<String>>();
	private UserGroupSyncConfig      config                = UserGroupSyncConfig.getInstance();
	private String                   userGroupFilename     = null;
	private long                     usergroupFileModified = 0 ;


	public static void main(String[] args) throws Throwable {
		FileSourceUserGroupBuilder filesourceUGBuilder = new FileSourceUserGroupBuilder() ;

		if (args.length > 0) {
			filesourceUGBuilder.setUserGroupFilename(args[0]);
		}
		
		filesourceUGBuilder.init();

		UserGroupSink ugSink = UserGroupSyncConfig.getInstance().getUserGroupSink();
		LOG.info("initializing sink: " + ugSink.getClass().getName());
		ugSink.init();

		filesourceUGBuilder.updateSink(ugSink);
		
		if ( LOG.isDebugEnabled()) {
			filesourceUGBuilder.print(); 
		}
	}
	
	@Override
	public void init() throws Throwable {
		if(userGroupFilename == null) {
			userGroupFilename = config.getUserSyncFileSource();
		}

		buildUserGroupInfo();
	}
	
	@Override
	public boolean isChanged() {
		long TempUserGroupFileModifedAt = new File(userGroupFilename).lastModified() ;
		if (usergroupFileModified != TempUserGroupFileModifedAt) {
			return true ;
		}
		return false;
	}

	@Override
	public void updateSink(UserGroupSink sink) throws Throwable {
		buildUserGroupInfo();

		for (Map.Entry<String, List<String>> entry : user2GroupListMap.entrySet()) {
		    String       user   = entry.getKey();
		    List<String> groups = entry.getValue();

		    sink.addOrUpdateUser(user, groups);
		}
	}

	private void setUserGroupFilename(String filename) {
		userGroupFilename = filename;
	}

	private void print() {
		for(String user : user2GroupListMap.keySet()) {
			LOG.debug("USER:" + user) ;
			List<String> groups = user2GroupListMap.get(user) ;
			if (groups != null) {
				for(String group : groups) {
					LOG.debug("\tGROUP: " + group) ;
				}
			}
		}
	}

	public void buildUserGroupInfo() throws Throwable {
		buildUserGroupList();
		if ( LOG.isDebugEnabled()) {
			print(); 
		}
	}
	
	public void buildUserGroupList() throws Throwable {
		if (userGroupFilename == null){
			throw new Exception("User Group Source File is not Configured. Please maintain in unixauthservice.properties or pass it as command line argument for org.apache.ranger.unixusersync.process.FileSourceUserGroupBuilder");
		}
	
		File f = new File(userGroupFilename);
		
		if (f.exists() && f.canRead()) {
			
			Map<String,List<String>> tmpUser2GroupListMap = new HashMap<String,List<String>>();
			
			JsonReader jsonReader = new JsonReader(new BufferedReader(new FileReader(f)));
			
			jsonReader.setLenient(true);
			
			jsonReader.beginArray();
			
			while (jsonReader.hasNext() ) {
				Map<String, List<String>> usergroupMap = getUserGroupMap(jsonReader);
				
				for(String user : usergroupMap.keySet()) {
					List<String> groups = usergroupMap.get(user) ;
					tmpUser2GroupListMap.put(user,groups);
				}		
			}
			
			jsonReader.endArray();
			
			jsonReader.close();

			user2GroupListMap     = tmpUser2GroupListMap;
			
			usergroupFileModified = f.lastModified() ;
			
		} else {
			throw new Exception("User Group Source File " + userGroupFilename + "doesn't not exist or readable");
		}
	}
	
	
	public Map<String, List<String>> getUserGroupMap(JsonReader jsonReader) throws Exception {
		
		Map<String, List<String>> ret = new HashMap<String, List<String>>();
		String user = null ;
		List<String> groups = new ArrayList<String>();
		
		jsonReader.beginObject();
		
		while ( jsonReader.hasNext()) {
			
			String name = jsonReader.nextName();
			
			if ( name.equals("user")) {
				user = jsonReader.nextString();
			} else if ( name.equals("groups")) {
				groups = getGroups(jsonReader);
			} else {
				StringBuilder sb = new StringBuilder();
				sb.append("User Group Source JSON array should have following **user** and **groups** as name tag e.g");
				sb.append("[ {\"user\":\"userid1\",\"groups\":[\"groupid1\",\"groupid2\"]},");
				sb.append("[ {\"user\":\"userid2\",\"groups\":[\"groupid1\",\"groupid2\"]}..]");
				throw new Exception(sb.toString());
			}
			
			if ( user != null ) {
				ret.put(user, groups);
			}
		}
		
		jsonReader.endObject();
		
		return ret;
	}
	
	public List<String> getGroups(JsonReader reader) throws IOException {
		List<String> ret = new ArrayList<String>();
		
		reader.beginArray();
		
		while(reader.hasNext()) {
			ret.add(reader.nextString());
		}
		
		reader.endArray();
		
		return ret;
	}
}