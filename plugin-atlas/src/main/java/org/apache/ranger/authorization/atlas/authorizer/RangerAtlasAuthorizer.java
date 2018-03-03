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

package org.apache.ranger.authorization.atlas.authorizer;


import org.apache.atlas.authorize.AtlasAdminAccessRequest;
import org.apache.atlas.authorize.AtlasAuthorizationException;
import org.apache.atlas.authorize.AtlasEntityAccessRequest;
import org.apache.atlas.authorize.AtlasTypeAccessRequest;
import org.apache.atlas.authorize.AtlasAuthorizer;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ranger.audit.model.AuthzAuditEvent;
import org.apache.ranger.plugin.audit.RangerDefaultAuditHandler;
import org.apache.ranger.plugin.model.RangerServiceDef;
import org.apache.ranger.plugin.policyengine.RangerAccessRequestImpl;
import org.apache.ranger.plugin.policyengine.RangerAccessResourceImpl;
import org.apache.ranger.plugin.policyengine.RangerAccessResult;
import org.apache.ranger.plugin.service.RangerBasePlugin;
import org.apache.ranger.plugin.util.RangerPerfTracer;

import static org.apache.ranger.services.atlas.RangerServiceAtlas.RESOURCE_TYPE_CATEGORY;
import static org.apache.ranger.services.atlas.RangerServiceAtlas.RESOURCE_TYPE_NAME;
import static org.apache.ranger.services.atlas.RangerServiceAtlas.RESOURCE_ENTITY_TYPE;
import static org.apache.ranger.services.atlas.RangerServiceAtlas.RESOURCE_ENTITY_CLASSIFICATION;
import static org.apache.ranger.services.atlas.RangerServiceAtlas.RESOURCE_ENTITY_ID;
import static org.apache.ranger.services.atlas.RangerServiceAtlas.RESOURCE_SERVICE;

import java.util.*;


public class RangerAtlasAuthorizer implements AtlasAuthorizer {
    private static final Log LOG      = LogFactory.getLog(RangerAtlasAuthorizer.class);
    private static final Log PERF_LOG = RangerPerfTracer.getPerfLogger("atlasauth.request");

    private static volatile RangerBasePlugin atlasPlugin = null;

    @Override
    public void init() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> RangerAtlasPlugin.init()");
        }

        RangerBasePlugin plugin = atlasPlugin;

        if (plugin == null) {
            synchronized (RangerAtlasPlugin.class) {
                plugin = atlasPlugin;

                if (plugin == null) {
                    plugin = new RangerAtlasPlugin();

                    plugin.init();
                    plugin.setResultProcessor(new RangerDefaultAuditHandler());

                    atlasPlugin = plugin;
                }
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("<== RangerAtlasPlugin.init()");
        }
    }

    @Override
    public void cleanUp() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> cleanUp ");
        }
    }

    @Override
    public boolean isAccessAllowed(AtlasAdminAccessRequest request) throws AtlasAuthorizationException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> isAccessAllowed(" + request + ")");
        }

        final boolean    ret;
        RangerPerfTracer perf = null;

        try {
            if (RangerPerfTracer.isPerfTraceEnabled(PERF_LOG)) {
                perf = RangerPerfTracer.getPerfTracer(PERF_LOG, "RangerAtlasAuthorizer.isAccessAllowed(" + request + ")");
            }

            String                   action         = request.getAction() != null ? request.getAction().getType() : null;
            RangerAccessResourceImpl rangerResource = new RangerAccessResourceImpl(Collections.singletonMap(RESOURCE_SERVICE, "*"));
            RangerAccessRequestImpl  rangerRequest  = new RangerAccessRequestImpl(rangerResource, action, request.getUser(), request.getUserGroups());

            rangerRequest.setClientIPAddress(request.getClientIPAddress());
            rangerRequest.setAccessTime(request.getAccessTime());
            rangerRequest.setAction(action);
            rangerRequest.setClusterName(getClusterName());

            ret = checkAccess(rangerRequest);
        } finally {
            RangerPerfTracer.log(perf);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== isAccessAllowed(" + request + "): " + ret);
        }

        return ret;
    }

    @Override
    public boolean isAccessAllowed(AtlasEntityAccessRequest request) throws AtlasAuthorizationException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> isAccessAllowed(" + request + ")");
        }

        boolean          ret  = false;
        RangerPerfTracer perf = null;
        RangerAtlasAuditHandler auditHandler = new RangerAtlasAuditHandler(request, getServiceDef());

        try {
            if (RangerPerfTracer.isPerfTraceEnabled(PERF_LOG)) {
                perf = RangerPerfTracer.getPerfTracer(PERF_LOG, "RangerAtlasAuthorizer.isAccessAllowed(" + request + ")");
            }

            final String            action         = request.getAction() != null ? request.getAction().getType() : null;
            final Set<String>       entityTypes    = request.getEntityTypeAndAllSuperTypes();
            final String            entityId       = request.getEntityId();
            final String            classification = request.getClassification() != null ? request.getClassification().getTypeName() : null;
            RangerAccessRequestImpl rangerRequest  = new RangerAccessRequestImpl();

            rangerRequest.setAccessType(action);
            rangerRequest.setAction(action);
            rangerRequest.setUser(request.getUser());
            rangerRequest.setUserGroups(request.getUserGroups());
            rangerRequest.setClientIPAddress(request.getClientIPAddress());
            rangerRequest.setAccessTime(request.getAccessTime());
            rangerRequest.setClusterName(getClusterName());

            final Set<String> classificationsToAuthorize;

            if (classification != null) {
                if (request.getEntityClassifications() == null) {
                    classificationsToAuthorize = Collections.singleton(classification);
                } else {
                    classificationsToAuthorize = new HashSet<>(request.getEntityClassifications());

                    classificationsToAuthorize.add(classification);
                }
            } else {
                classificationsToAuthorize = request.getEntityClassifications();
            }

            if (CollectionUtils.isNotEmpty(classificationsToAuthorize)) {
                // check authorization for each classification
                for (String classificationToAuthorize : classificationsToAuthorize) {
                    RangerAccessResourceImpl rangerResource = new RangerAccessResourceImpl();

                    rangerResource.setValue(RESOURCE_ENTITY_TYPE, entityTypes);
                    rangerResource.setValue(RESOURCE_ENTITY_CLASSIFICATION, request.getClassificationTypeAndAllSuperTypes(classificationToAuthorize));
                    rangerResource.setValue(RESOURCE_ENTITY_ID, entityId);

                    rangerRequest.setResource(rangerResource);

                    ret = checkAccess(rangerRequest, auditHandler);

                    if (!ret) {
                        break;
                    }
                }
            } else { // no classifications to authorize
                RangerAccessResourceImpl rangerResource = new RangerAccessResourceImpl();

                rangerResource.setValue(RESOURCE_ENTITY_TYPE, entityTypes);
                rangerResource.setValue(RESOURCE_ENTITY_CLASSIFICATION, Collections.<String>emptySet());
                rangerResource.setValue(RESOURCE_ENTITY_ID, entityId);

                rangerRequest.setResource(rangerResource);

                ret = checkAccess(rangerRequest, auditHandler);
            }
        } finally {
            auditHandler.flushAudit();

            RangerPerfTracer.log(perf);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== isAccessAllowed(" + request + "): " + ret);
        }

        return ret;
    }

    @Override
    public boolean isAccessAllowed(AtlasTypeAccessRequest request) throws AtlasAuthorizationException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> isAccessAllowed(" + request + ")");
        }

        final boolean    ret;
        RangerPerfTracer perf = null;

        try {
            if (RangerPerfTracer.isPerfTraceEnabled(PERF_LOG)) {
                perf = RangerPerfTracer.getPerfTracer(PERF_LOG, "RangerAtlasAuthorizer.isAccessAllowed(" + request + ")");
            }

            final String typeName     = request.getTypeDef() != null ? request.getTypeDef().getName() : null;
            final String typeCategory = request.getTypeDef() != null && request.getTypeDef().getCategory() != null ? request.getTypeDef().getCategory().name() : null;
            final String action       = request.getAction() != null ? request.getAction().getType() : null;

            RangerAccessResourceImpl rangerResource = new RangerAccessResourceImpl();

            rangerResource.setValue(RESOURCE_TYPE_NAME, typeName);
            rangerResource.setValue(RESOURCE_TYPE_CATEGORY, typeCategory);

            RangerAccessRequestImpl rangerRequest = new RangerAccessRequestImpl(rangerResource, action, request.getUser(), request.getUserGroups());
            rangerRequest.setClientIPAddress(request.getClientIPAddress());
            rangerRequest.setAccessTime(request.getAccessTime());
            rangerRequest.setClusterName(getClusterName());
            rangerRequest.setAction(action);


            ret = checkAccess(rangerRequest);
        } finally {
            RangerPerfTracer.log(perf);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== isAccessAllowed(" + request + "): " + ret);
        }

        return ret;
    }

    private String getClusterName() {
        RangerBasePlugin plugin = atlasPlugin;

        return plugin != null ? plugin.getClusterName() : null;
    }

    private RangerServiceDef getServiceDef() {
        RangerBasePlugin plugin = atlasPlugin;

        return plugin != null ? plugin.getServiceDef() : null;
    }

    private boolean checkAccess(RangerAccessRequestImpl request) {
        boolean          ret    = false;
        RangerBasePlugin plugin = atlasPlugin;

        if (plugin != null) {
            RangerAccessResult result = plugin.isAccessAllowed(request);

            ret = result != null && result.getIsAllowed();
        } else {
            LOG.warn("RangerAtlasPlugin not initialized. Access blocked!!!");
        }

        return ret;
    }

    private boolean checkAccess(RangerAccessRequestImpl request, RangerAtlasAuditHandler auditHandler) {
        boolean          ret    = false;
        RangerBasePlugin plugin = atlasPlugin;

        if (plugin != null) {
            RangerAccessResult result = plugin.isAccessAllowed(request, auditHandler);

            ret = result != null && result.getIsAllowed();
        } else {
            LOG.warn("RangerAtlasPlugin not initialized. Access blocked!!!");
        }

        return ret;
    }

    class RangerAtlasPlugin extends RangerBasePlugin {
        RangerAtlasPlugin() {
            super("atlas", "atlas");
        }
    }

    class RangerAtlasAuditHandler extends RangerDefaultAuditHandler {
        private final Map<Long, AuthzAuditEvent> auditEvents;
        private final String                     resourcePath;
        private       boolean                    denyExists = false;


        public RangerAtlasAuditHandler(AtlasEntityAccessRequest request, RangerServiceDef serviceDef) {
            Collection<String> classifications    = request.getEntityClassifications();
            String             strClassifications = classifications == null ? "[]" : classifications.toString();

            if (request.getClassification() != null) {
                strClassifications += ("," + request.getClassification().getTypeName());
            }

            RangerAccessResourceImpl rangerResource = new RangerAccessResourceImpl();

            rangerResource.setServiceDef(serviceDef);
            rangerResource.setValue(RESOURCE_ENTITY_TYPE, request.getEntityType());
            rangerResource.setValue(RESOURCE_ENTITY_CLASSIFICATION, strClassifications);
            rangerResource.setValue(RESOURCE_ENTITY_ID, request.getEntityId());

            auditEvents  = new HashMap<>();
            resourcePath = rangerResource.getAsString();
        }

        @Override
        public void processResult(RangerAccessResult result) {
            if (denyExists) { // nothing more to do, if a deny already encountered
                return;
            }

            AuthzAuditEvent auditEvent = super.getAuthzEvents(result);

            if (auditEvent != null) {
                // audit event might have list of entity-types and classification-types; overwrite with the values in original request
                if (resourcePath != null) {
                    auditEvent.setResourcePath(resourcePath);
                }

                if (!result.getIsAllowed()) {
                    denyExists = true;

                    auditEvents.clear();
                }

                auditEvents.put(auditEvent.getPolicyId(), auditEvent);
            }
        }


        public void flushAudit() {
            if (auditEvents != null) {
                for (AuthzAuditEvent auditEvent : auditEvents.values()) {
                    logAuthzAudit(auditEvent);
                }
            }
        }
    }
}
