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


define(function(require) {
	'use strict';

	var Backbone = require('backbone');
	var XAEnums = require('utils/XAEnums');
	var XAGlobals = require('utils/XAGlobals');
	var XAUtils = require('utils/XAUtils');
	var localization = require('utils/XALangSupport');

	var RangerPolicyROTmpl = require('hbs!tmpl/policies/RangerPolicyRO_tmpl');
	var RangerService = require('models/RangerService');

	var RangerPolicyRO = Backbone.Marionette.Layout.extend({
		_viewName: 'RangerPolicyRO',

		template: RangerPolicyROTmpl,
		templateHelpers: function() {
			return {
				PolicyDetails: this.PolicyDetails,
			};
		},
		breadCrumbs: [],

		/** Layout sub regions */
		regions: {
			//'rAuditTable'	: 'div[data-id="r_auditTable"]',
		},

		/** ui selector cache */
		ui: {

		},

		/** ui events hash */
		events: function() {
			var events = {};
			return events;
		},

		/**
		 * intialize a new AuditLayout Layout
		 * @constructs
		 */
		initialize: function(options) {
			_.extend(this, options);
			this.initializePolicy();
			this.initializePolicyDetailsObj();
		},

		initializePolicy: function() {
			var data = {
				eventTime : this.eventTime,
			};
			this.policy.fetchByEventTime({
				async: false,
				cache: false,
				data : data
			});
		},

		initializePolicyDetailsObj : function(){
			var self = this;
			var details = this.PolicyDetails = {};
			details.id = this.policy.get('id');
			details.name = this.policy.get('name');
			details.isEnabled = this.policy.get('isEnabled') ? localization.tt('lbl.ActiveStatus_STATUS_ENABLED') : localization.tt('lbl.ActiveStatus_STATUS_DISABLED');
			details.description = this.policy.get('description');
			details.isAuditEnabled = this.policy.get('isAuditEnabled') ? XAEnums.AuditStatus.AUDIT_ENABLED.label : XAEnums.AuditStatus.AUDIT_DISABLED.label;
			details.resources = [];
			_.each(this.serviceDef.get('resources'), function(def, i){
				if(!_.isUndefined(this.policy.get('resources')[def.name])){
					var resource = {},
						policyResources = this.policy.get('resources')[def.name];
					resource.label = def.label;
					resource.values = policyResources.values;
					if(def.recursiveSupported){
						resource.Rec_Exc = policyResources.isRecursive ? XAEnums.RecursiveStatus.STATUS_RECURSIVE.label : XAEnums.RecursiveStatus.STATUS_NONRECURSIVE.label;
					} else if(def.excludesSupported){
						resource.Rec_Exc = policyResources.isExcludes ? XAEnums.ExcludeStatus.STATUS_EXCLUDE.label : XAEnums.ExcludeStatus.STATUS_INCLUDE.label;
					}
					details.resources.push(resource);
				}
			}, this);
			var perm = details.permissions = this.getPermHeaders();
			perm.policyItems = this.policy.get('policyItems');
		},

		/** all events binding here */
		bindEvents: function() {},

		/** on render callback */
		onRender: function() {
			this.$el.find('#permissionsDetails table tr td:empty').html('-')
		},

		getPermHeaders : function(){
			var permList = [], 
				policyCondition = false;
			permList.unshift(localization.tt('lbl.delegatedAdmin'));
			permList.unshift(localization.tt('lbl.permissions'));
			if(!_.isEmpty(this.serviceDef.get('policyConditions'))){
				permList.unshift(localization.tt('h.policyCondition'));
				policyCondition = true;
			}
			permList.unshift(localization.tt('lbl.selectUser'));
			permList.unshift(localization.tt('lbl.selectGroup'));
			return {
				header : permList,
				policyCondition : policyCondition
			};
		},

		/** on close */
		onClose: function() {}
	});

	return RangerPolicyRO;
});