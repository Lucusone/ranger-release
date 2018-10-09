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

package org.apache.ranger.db;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.NoResultException;

import org.apache.ranger.biz.RangerPolicyRetriever;
import org.apache.ranger.common.db.BaseDao;
import org.apache.ranger.entity.XXPolicyRefResource;
import org.springframework.stereotype.Service;

@Service
public class XXPolicyRefResourceDao extends BaseDao<XXPolicyRefResource>{

	public XXPolicyRefResourceDao(RangerDaoManagerBase daoManager)  {
		super(daoManager);
	}

	public List<XXPolicyRefResource> findByPolicyId(Long policyId) {
		if(policyId == null) {
			return new ArrayList<XXPolicyRefResource>();
		}
		try {
			return getEntityManager()
					.createNamedQuery("XXPolicyRefResource.findByPolicyId", tClass)
					.setParameter("policyId", policyId).getResultList();
		} catch (NoResultException e) {
			return new ArrayList<XXPolicyRefResource>();
		}
	}

	public List<XXPolicyRefResource> findByResourceDefID(Long resourceDefId) {
		if (resourceDefId == null) {
			return new ArrayList<XXPolicyRefResource>();
		}
		try {
			return getEntityManager().createNamedQuery("XXPolicyRefResource.findByResourceDefId", tClass)
					.setParameter("resourceDefId", resourceDefId).getResultList();
		} catch (NoResultException e) {
			return new ArrayList<XXPolicyRefResource>();
		}
	}

	 @SuppressWarnings("unchecked")
	    public List<RangerPolicyRetriever.PolicyTextNameMap> findUpdatedResourceNamesByPolicy(Long policyId) {
	        List<RangerPolicyRetriever.PolicyTextNameMap> ret = new ArrayList<>();
	        if (policyId != null) {
	            List<Object[]> rows = (List<Object[]>) getEntityManager()
	                    .createNamedQuery("XXPolicyRefResource.findUpdatedResourceNamesByPolicy")
	                    .setParameter("policy", policyId)
	                    .getResultList();
	            if (rows != null) {
	                for (Object[] row : rows) {
	                    ret.add(new RangerPolicyRetriever.PolicyTextNameMap((Long)row[0], (String)row[1], (String)row[2]));
	                }
	            }
	        }
	        return ret;
	    }

		@SuppressWarnings("unchecked")
		public List<RangerPolicyRetriever.PolicyTextNameMap> findUpdatedResourceNamesByService(Long serviceId) {
	        List<RangerPolicyRetriever.PolicyTextNameMap> ret = new ArrayList<>();
	        if (serviceId != null) {
	            List<Object[]> rows = (List<Object[]>) getEntityManager()
	                    .createNamedQuery("XXPolicyRefResource.findUpdatedResourceNamesByService")
	                    .setParameter("service", serviceId)
	                    .getResultList();
	            if (rows != null) {
	                for (Object[] row : rows) {
	                    ret.add(new RangerPolicyRetriever.PolicyTextNameMap((Long)row[0], (String)row[1], (String)row[2]));
	                }
	            }
	        }
	        return ret;
	    }
}
