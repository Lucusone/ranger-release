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

package org.apache.ranger.tagsync.source.atlas;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.apache.atlas.typesystem.EntityImpl;
import org.apache.atlas.typesystem.IdImpl;
import org.apache.atlas.typesystem.TraitImpl;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.atlas.typesystem.api.Entity;
import org.apache.atlas.typesystem.api.Trait;
import org.apache.ranger.admin.client.datatype.RESTResponse;
import org.apache.ranger.plugin.util.RangerRESTClient;
import org.apache.ranger.plugin.util.RangerRESTUtils;
import org.apache.ranger.tagsync.process.TagSyncConfig;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;


// class AtlasUtil

@SuppressWarnings("unchecked")
public class AtlasUtility {

	private static final Log LOG = LogFactory.getLog(AtlasUtility.class);

	// Atlas APIs

	public static final String API_ATLAS_TYPES = "api/atlas/types";
	public static final String API_ATLAS_ENTITIES = "api/atlas/entities?type=";
	public static final String API_ATLAS_ENTITY = "api/atlas/entities/";
	public static final String API_ATLAS_TYPE = "api/atlas/types/";

	public static final String RESULTS_ATTRIBUTE = "results";
	public static final String DEFINITION_ATTRIBUTE = "definition";
	public static final String VALUES_ATTRIBUTE = "values";
	public static final String TRAITS_ATTRIBUTE = "traits";
	public static final String TYPE_NAME_ATTRIBUTE = "typeName";
	public static final String TRAIT_TYPES_ATTRIBUTE = "traitTypes";
	public static final String SUPER_TYPES_ATTRIBUTE = "superTypes";
	public static final String ATTRIBUTE_DEFINITIONS_ATTRIBUTE = "attributeDefinitions";
	public static final String NAME_ATTRIBUTE = "name";

	private Type mapType = new TypeToken<Map<String, Object>>() {
	}.getType();

	private RangerRESTClient restClient;
	private Map<String, Entity> entities = new LinkedHashMap<>();


	// ----- Constructor ------------------------------------------------------

	public AtlasUtility(Properties properties) {

		String url = TagSyncConfig.getAtlasEndpoint(properties);
		String sslConfigFileName = TagSyncConfig.getAtlasSslConfigFileName(properties);

		if (LOG.isDebugEnabled()) {
			LOG.debug("Initializing RangerRestClient with (url=" + url + ", sslConfigFileName" + sslConfigFileName + ")");
		}

		restClient = new RangerRESTClient(url, sslConfigFileName);

		if (LOG.isDebugEnabled()) {
			LOG.debug("Initialized RangerRestClient with (url=" + url + ", sslConfigFileName=" + sslConfigFileName + ")");
		}
	}

	// update the set of entities with current from Atlas
	public void refreshAllEntities() {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> TagAtlasSource.refreshAllEntities()");
		}

		try {
			entities.clear();
			entities.putAll(getAllEntities());
		} catch (IOException e) {
			LOG.error("getAllEntities() failed", e);
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug("<== TagAtlasSource.refreshAllEntities()");
		}
	}

	// ----- AtlasUtility ------------------------------------------------------

	public Map<String, Entity> getAllEntities() throws IOException {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> TagAtlasSource.getAllEntities()");
		}
		Map<String, Entity> entities = new LinkedHashMap<>();

		Map<String, Object> typesResponse = atlasAPI(API_ATLAS_TYPES);

		List<String> types = getAttribute(typesResponse, RESULTS_ATTRIBUTE, List.class);

		for (String type : types) {

			Map<String, Object> entitiesResponse = atlasAPI(API_ATLAS_ENTITIES + type);

			List<String> guids = getAttribute(entitiesResponse, RESULTS_ATTRIBUTE, List.class);

			for (String guid : guids) {

				if (StringUtils.isNotBlank(guid)) {

					Map<Trait, Map<String, ? extends Trait>> traitSuperTypes = new HashMap<>();

					Map<String, Object> entityResponse = atlasAPI(API_ATLAS_ENTITY + guid);

					if (entityResponse.containsKey(DEFINITION_ATTRIBUTE)) {
						String definitionJSON = getAttribute(entityResponse, DEFINITION_ATTRIBUTE, String.class);

						LOG.info("{");
						LOG.info("	\"entity-id\":" + guid + ",");
						LOG.info("	\"entity-definition\":" + definitionJSON);
						LOG.info("}");

						Map<String, Object> definition = new Gson().fromJson(definitionJSON, mapType);

						Map<String, Object> values = getAttribute(definition, VALUES_ATTRIBUTE, Map.class);
						Map<String, Object> traits = getAttribute(definition, TRAITS_ATTRIBUTE, Map.class);
						String typeName = getAttribute(definition, TYPE_NAME_ATTRIBUTE, String.class);

						LOG.info("Received entity(typeName=" + typeName + ", id=" + guid + ")");


						Map<String, TraitImpl> traitMap = new HashMap<>();

						if (MapUtils.isNotEmpty(traits)) {

							LOG.info("Traits for entity(typeName=" + typeName + ", id=" + guid + ") ------ ");

							for (Map.Entry<String, Object> entry : traits.entrySet()) {

								Map<String, Object> trait = (Map<String, Object>) entry.getValue();

								Map<String, Object> traitValues = getAttribute(trait, VALUES_ATTRIBUTE, Map.class);
								String traitTypeName = getAttribute(trait, TYPE_NAME_ATTRIBUTE, String.class);

								Map<String, TraitImpl> superTypes = getTraitSuperTypes(getTraitType(traitTypeName), traitValues);

								TraitImpl trait1 = new TraitImpl(traitTypeName, traitValues, superTypes);

								traitSuperTypes.put(trait1, superTypes);

								traitMap.put(entry.getKey(), trait1);


								LOG.info("			Trait(typeName=" + traitTypeName + ")");

							}
						} else {
							LOG.info("No traits for entity(typeName=" + typeName + ", id=" + guid + ")");
						}
						EntityImpl entity = new EntityImpl(new IdImpl(guid, 0), typeName, values, traitMap);

						showEntity(entity);

						entities.put(guid, entity);

					}
				}
			}
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> TagAtlasSource.getAllEntities()");
		}
		return entities;
	}


	// ----- helper methods ----------------------------------------------------

	private Map<String, Object> getTraitType(String traitName)
			throws IOException {

		Map<String, Object> typeResponse = atlasAPI(API_ATLAS_TYPE + traitName);

		if (typeResponse.containsKey(DEFINITION_ATTRIBUTE)) {
			String definitionJSON = getAttribute(typeResponse, DEFINITION_ATTRIBUTE, String.class);

			Map<String, Object> definition = new Gson().fromJson(definitionJSON, mapType);

			List traitTypes = getAttribute(definition, TRAIT_TYPES_ATTRIBUTE, List.class);

			if (traitTypes.size() > 0) {
				return (Map<String, Object>) traitTypes.get(0);
			}
		}
		return null;
	}

	private Map<String, TraitImpl> getTraitSuperTypes(Map<String, Object> traitType, Map<String, Object> values)
			throws IOException {

		Map<String, TraitImpl> superTypes = new HashMap<>();

		if (traitType != null) {

			List<String> superTypeNames = getAttribute(traitType, SUPER_TYPES_ATTRIBUTE, List.class);

			for (String superTypeName : superTypeNames) {

				Map<String, Object> superTraitType = getTraitType(superTypeName);

				if (superTraitType != null) {
					List<Map<String, Object>> attributeDefinitions = (List) superTraitType.get(ATTRIBUTE_DEFINITIONS_ATTRIBUTE);

					Map<String, Object> superTypeValues = new HashMap<>();
					for (Map<String, Object> attributeDefinition : attributeDefinitions) {

						String attributeName = attributeDefinition.get(NAME_ATTRIBUTE).toString();
						if (values.containsKey(attributeName)) {
							superTypeValues.put(attributeName, values.get(attributeName));
						}
					}

					superTypes.put(superTypeName,
							//new TraitImpl(getTraitSuperTypes(superTraitType, superTypeValues), superTypeValues, superTypeName));
							new TraitImpl(superTypeName, superTypeValues, getTraitSuperTypes(superTraitType, superTypeValues)));
				}
			}
		}
		return superTypes;
	}


	/*
		private Map<String, Object> atlasAPI(String endpoint) throws IOException {
			InputStream in = streamProvider.readFrom(atlasEndpoint + endpoint, "GET", (String) null, Collections.<String, String>emptyMap());
			return new Gson().fromJson(IOUtils.toString(in, "UTF-8"), mapType);
		}
		*/


	private Map<String, Object> atlasAPI(String endpoint) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> TagAtlasSource.atlasAPI(" + endpoint + ")");
		}
		// Create a REST client and perform a get on it
		Map<String, Object> ret = new HashMap<String, Object>();

		WebResource webResource = restClient.getResource(endpoint);

		ClientResponse response = webResource.accept(RangerRESTUtils.REST_MIME_TYPE_JSON).get(ClientResponse.class);

		if (response != null && response.getStatus() == 200) {
			ret = response.getEntity(ret.getClass());
		} else {
			LOG.error("Atlas REST call returned with response={" + response + "}");

			RESTResponse resp = RESTResponse.fromClientResponse(response);
			LOG.error("Error getting Atlas Entity. request=" + webResource.toString()
					+ ", response=" + resp.toString());
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug("<== TagAtlasSource.atlasAPI(" + endpoint + ")");
		}
		return ret;
	}

	private <T> T getAttribute(Map<String, Object> map, String name, Class<T> type) {
		return type.cast(map.get(name));
	}

	public void showEntity(Entity entity) {

		LOG.debug("Entity-id	:" + entity.getId());

		LOG.debug("Type:		" + entity.getTypeName());

		LOG.debug("----- Values -----");

		for (Map.Entry<String, Object> entry : entity.getValues().entrySet()) {
			LOG.debug("		Name:	" + entry.getKey() + "");
			Object value = entry.getValue();
			LOG.debug("		Value:	" + getValue(value, entities.keySet()));
		}

		LOG.debug("----- Traits -----");

		for (String traitName : entity.getTraits().keySet()) {
			LOG.debug("		Name:" + entity.getId() + ", trait=" + traitName + ">" + traitName);
		}

	}

	public void showTrait(Entity entity, String traitId) {

		String[] traitNames = traitId.split(",");

		Trait trait = entity.getTraits().get(traitNames[0]);

		for (int i = 1; i < traitNames.length; ++i) {
			trait = trait.getSuperTypes().get(traitNames[i]);
		}

		String typeName = trait.getTypeName();

		LOG.debug("Trait " + typeName + " for Entity id=" + entity.getId());

		LOG.debug("Type: " + typeName);

		LOG.debug("----- Values ------");

		for (Map.Entry<String, Object> entry : trait.getValues().entrySet()) {
			LOG.debug("Name:" + entry.getKey());
			Object value = entry.getValue();
			LOG.debug("Value:" + getValue(value, entities.keySet()));
		}

		LOG.debug("Super Traits");


		for (String traitName : trait.getSuperTypes().keySet()) {
			LOG.debug("Name=" + entity.getId() + "&trait=" + traitId + "," + traitName + ">" + traitName);
		}
	}

	// resolve the given value if necessary
	private String getValue(Object value, Set<String> ids) {
		if (value == null) {
			return "";
		}
		String idString = getIdValue(value, ids);
		if (idString != null) {
			return idString;
		}

		idString = getIdListValue(value, ids);
		if (idString != null) {
			return idString;
		}

		return value.toString();
	}

	// get an id from the given value; return null if the value is not an id type
	private String getIdValue(Object value, Set<String> ids) {
		if (value instanceof Map) {
			Map map = (Map) value;
			if (map.size() == 3 && map.containsKey("id")) {
				String id = map.get("id").toString();
				if (ids.contains(id)) {
					return id;
				}
			}
		}
		return null;
	}

	// get an id list from the given value; return null if the value is not an id list type
	private String getIdListValue(Object value, Set<String> ids) {
		if (value instanceof List) {
			List list = (List) value;
			if (list.size() > 0) {
				StringBuilder sb = new StringBuilder();
				for (Object o : list) {
					String idString = getIdValue(o, ids);
					if (idString == null) {
						return value.toString();
					}
					if (sb.length() > 0) {
						sb.append(", ");
					}
					sb.append(idString);
				}
				return sb.toString();
			}
		}
		return null;
	}
}



