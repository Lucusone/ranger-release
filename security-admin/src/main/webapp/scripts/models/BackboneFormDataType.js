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

	var FormDataType = Backbone.Model.extend({
		type : [ 'string', 'boolean', 'int' ],
		getFormElements : function(configs, enums, attrs, form) {
			var samelevelFieldCreated = [];
			_.each(configs, function(v, k,config) {
				if (v != null) {
					var formObj = {}, fieldName;
					switch (v.type) {
						case 'string':
							if($.inArray(v.level, samelevelFieldCreated) >= 0){
								return;
							}
							if(v.excludesSupported || v.recursiveSupported || v.lookupSupported){
								var resourceOpts = {};
								formObj.type = 'Resource';
								formObj['excludeSupport']= v.excludesSupported;
								formObj['recursiveSupport'] = v.recursiveSupported;
								formObj.name = v.name;
//								formObj.level = v.level;
								//checkParentHideShow field
								formObj.fieldAttrs = { 'data-name' : 'field-'+v.name, 'parent' : v.parent };
								formObj['resourceOpts'] = {'data-placeholder': v.label };
								
								if(!_.isUndefined(v.lookupSupported) && v.lookupSupported ){
									var opts = { 
													'type' : v.name,
													'lookupURL' 		: "service/plugins/services/lookupResource/"+form.rangerService.get('name')
												};
									resourceOpts['select2Opts'] = form.getPlugginAttr(true, opts);
									formObj['resourceOpts'] = resourceOpts; 
								}
								//same level resources check 
								var optionsAttrs = _.filter(config,function(field){ if(field.level == v.level) return field;})
								if(optionsAttrs.length > 1){
									var optionsTitle = _.map(optionsAttrs,function(field){ return field.name;});
									formObj['sameLevelOpts'] = optionsTitle;
									samelevelFieldCreated.push(v.level);
									fieldName = 'sameLevel'+v.level;
									formObj['title'] = '';
									formObj['resourcesAtSameLevel'] = true;

									// formView is used to listen form events
									formObj['formView'] = form;
								}
							}else{
								formObj.type = 'Text';
							}
							break;
						case 'bool':
							if(!_.isUndefined(v.subType) && !_.isEmpty(v.subType)){
								formObj.type = 'Select';
								var subType = v.subType.split(':')
								formObj.options = [subType[0].substr(0, subType[0].length - 4), subType[1].substr(0, subType[1].length - 5)];
								//to set default value 
								if(form.model.isNew()){
									if(!_.isUndefined(v.defaultValue) && v.defaultValue === "false"){
										form.model.set(v.name, subType[1].substr(0, subType[1].length - 5))
									}
								}
							}else{
								formObj.type = 'Checkbox';
								formObj.options = {	y : 'Yes',n : 'No'};
							}
							break;
						case 'int':formObj.type = 'Number';break;
						case 'enum':
							var enumObj = _.find(enums, function(e) {return e && e.name == v.subType;});
							formObj.type = 'Select';
//							formObj.options = _.pluck(_.compact(enumObj.elements),'label');
							formObj.options = _.map((enumObj.elements), function(obj) {
								return { 'label' : obj.label, 'val': obj.name};
							});
							break;
						case 'path' : 
							formObj.type = 'Resource';
							formObj['excludeSupport']= v.excludesSupported;
							formObj['recursiveSupport'] = v.recursiveSupported;
							formObj['name'] = v.name;
							formObj['editorAttrs'] = {'data-placeholder': v.label };
							if(!_.isUndefined(v.lookupSupported) && v.lookupSupported ){
								var options = {
										'containerCssClass' : v.name,
										'lookupURL' : "service/plugins/services/lookupResource/"+form.rangerService.get('name')
										};
								form.pathFieldName = v.name;
								form.initilializePathPlugin = true;
							}
							formObj['initilializePathPlugin'] = true;
							break;
						case 'password':formObj.type = 'Password';break;
						default:formObj.type = 'Text';
						break;
					}
					if(_.isUndefined(formObj.title)){
						formObj.title = v.label || v.name;
					}
					formObj.validators = [];
					if (_.has(v, 'mandatory') && v.mandatory && v.type != 'bool') {
						formObj.validators.push('required');
						formObj.title = formObj.title + " *"
					}
					if(form.model.isNew()){
						if(_.has(v, 'defaultValue') && !_.isEmpty(v.defaultValue) && v.type != 'bool'){
							form.model.set(v.name, v.defaultValue)
						}
					}
					
					formObj['class'] = 'serviceConfig';
					if(_.isUndefined(fieldName)){
						fieldName = v.name;
					}
					attrs[fieldName] = formObj;
				}
			});
			return attrs;

		}
	});
	return FormDataType;

});
