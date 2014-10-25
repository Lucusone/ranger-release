#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

PROPFILE=$PWD/install.properties
propertyValue=''

. $PROPFILE
if [ ! $? = "0" ];then	
	log "$PROPFILE file not found....!!"; 
	exit 1; 
fi

DB_HOST="${db_host}"

usage() {
  [ "$*" ] && echo "$0: $*"
  sed -n '/^##/,/^$/s/^## \{0,1\}//p' "$0"
  exit 2
} 2>/dev/null

log() {  
   local prefix="[$(date +%Y/%m/%d\ %H:%M:%S)]: "
   echo "${prefix} $@" >> $LOGFILE
   echo "${prefix} $@" 
} 

check_ret_status(){
	if [ $1 -ne 0 ]; then
		log "[E] $2"; 
		exit 1; 
	fi
}

check_ret_status_for_groupadd(){
# 9 is the response if the group exists
    if [ $1 -ne 0 ] && [ $1 -ne 9 ]; then
        log "[E] $2";
        exit 1;
    fi
}

is_command () {
    log "[I] check if command $1 exists"
    type "$1" >/dev/null 
}

get_distro(){
	log "[I] Checking distribution name.."
	ver=$(cat /etc/*{issues,release,version} 2> /dev/null)
	if [[ $(echo $ver | grep DISTRIB_ID) ]]; then
	    DIST_NAME=$(lsb_release -si)
	else
	    DIST_NAME=$(echo $ver | cut -d ' ' -f 1 | sort -u | head -1)
	fi
	export $DIST_NAME
	log "[I] Found distribution : $DIST_NAME"

}
#Get Properties from File
#$1 -> propertyName $2 -> fileName $3 -> variableName
getPropertyFromFile(){
	validateProperty=$(sed '/^\#/d' $2 | grep "^$1"  | tail -n 1) # for validation	
	if  test -z "$validateProperty" ; then log "[E] '$1' not found in $2 file while getting....!!"; exit 1; fi
	value=`sed '/^\#/d' $2 | grep "^$1"  | tail -n 1 | cut -d "=" -f2-`	
	#echo 'value:'$value
	#validate=$(sed '/^\#/d' $2 | grep "^$1"  | tail -n 1 | cut -d "=" -f2-) # for validation
	#if  test -z "$validate" ; then log "[E] '$1' not found in $2 file while getting....!!"; exit 1; fi
	eval $3="'$value'"
}

#Update Properties to File
#$1 -> propertyName $2 -> newPropertyValue $3 -> fileName
updatePropertyToFile(){
	sed -i 's@^'$1'=[^ ]*$@'$1'='$2'@g' $3
	#validate=`sed -i 's/^'$1'=[^ ]*$/'$1'='$2'/g' $3`	#for validation
	validate=$(sed '/^\#/d' $3 | grep "^$1"  | tail -n 1 | cut -d "=" -f2-) # for validation
	#echo 'V1:'$validate
	if test -z "$validate" ; then log "[E] '$1' not found in $3 file while Updating....!!"; exit 1; fi
	log "[I] File $3 Updated successfully : {'$1'}"
}


init_logfiles () {
    for f in $LOGFILES; do
        touch $f
    done
    #log "start date for $0 = `date`"
}

init_variables(){
	curDt=`date '+%Y%m%d%H%M%S'`

	VERSION=`cat ${PWD}/version`

	XAPOLICYMGR_DIR=/usr/lib/xapolicymgr

	if [ "${VERSION}" != "" ]
	then
 		INSTALL_DIR=${XAPOLICYMGR_DIR}-${VERSION}
	else
		INSTALL_DIR=${XAPOLICYMGR_DIR}
 	fi

	WEBAPP_ROOT=${INSTALL_DIR}/ews/webapp
	
	DB_FLAVOR=`echo $DB_FLAVOR | tr '[:lower:]' '[:upper:]'`
	if [ "${DB_FLAVOR}" == "" ]
	then
		DB_FLAVOR="MYSQL"
	fi
	log "[I] DB_FLAVOR=${DB_FLAVOR}"

	getPropertyFromFile 'db_root_user' $PROPFILE db_root_user
	getPropertyFromFile 'db_root_password' $PROPFILE db_user
	getPropertyFromFile 'db_user' $PROPFILE db_user
	getPropertyFromFile 'db_password' $PROPFILE db_password
	getPropertyFromFile 'audit_db_user' $PROPFILE audit_db_user
	getPropertyFromFile 'audit_db_password' $PROPFILE audit_db_password
}

wait_for_tomcat_shutdown() {
	i=1
	touch $TMPFILE
	while [ $i -le 20 ]
	do
		ps -ef | grep catalina.startup.Bootstrap | grep -v grep > $TMPFILE
		if [ $? -eq 1 ]; then
			log "[I] Tomcat stopped"
			i=21
		else 
			log "[I] stopping Tomcat.."
			i=`expr $i + 1`
			sleep 1
		fi 
	done
}

check_db_version() {
    if [ "${DB_FLAVOR}" == "MYSQL" ]
    then
		if is_command ${SQL_COMMAND_INVOKER} ; then
			log "[I] '${SQL_COMMAND_INVOKER}' command found"
		else
			log "[E] '${SQL_COMMAND_INVOKER}' command not found"
		exit 1;
		fi
	fi
	if [ "${DB_FLAVOR}" == "ORACLE" ]
    then
        if is_command ${SQL_COMMAND_INVOKER} ; then
            log "[I] '${SQL_COMMAND_INVOKER}' command found"
        else
            log "[E] '${SQL_COMMAND_INVOKER}' command not found"
        exit 1;
        fi
    fi
}

check_db_connector() {
    if [ "${DB_FLAVOR}" == "MYSQL" ]
	then
		log "[I] Checking MYSQL CONNECTOR FILE : ${SQL_CONNECTOR_JAR}"
		if test -f "$SQL_CONNECTOR_JAR"; then
			log "[I] MYSQL CONNECTOR FILE : $SQL_CONNECTOR_JAR file found"
		else
			log "[E] MYSQL CONNECTOR FILE : $SQL_CONNECTOR_JAR does not exists" ; exit 1;
		fi
	fi
    if [ "${DB_FLAVOR}" == "ORACLE" ]
    then
        log "[I] Checking ORACLE CONNECTOR FILE : ${SQL_CONNECTOR_JAR}"
        if test -f "${SQL_CONNECTOR_JAR}"; then
			log "[I] ORACLE CONNECTOR FILE : ${SQL_CONNECTOR_JAR} file found"
        else
			log "[E] ORACLE CONNECTOR FILE : ${SQL_CONNECTOR_JAR} does not exists" ; exit 1;
		fi
    fi
}
check_java_version() {
	if is_command ${JAVA_BIN} ; then
		log "[I] '${JAVA_BIN}' command found"
	else
		log "[E] '${JAVA_BIN}' command not found"
		exit 1;
	fi

	$JAVA_BIN -version 2>&1 | grep -q $JAVA_VERSION_REQUIRED 
	if [ $? != 0 ] ; then
		log "[E] Java 1.7 is required"
		exit 1;
	fi

	#Check for JAVA_HOME 
	if [ "${JAVA_HOME}" == "" ]
	then
 		log "[E] JAVA_HOME environment property not defined, aborting installation."
 		exit 1
 	fi

	#$JAVA_BIN -version 2>&1 | grep -q "$JAVA_ORACLE"
	#if [ $? != 0 ] ; then
		#log "[E] Oracle Java is required"
		#exit 1;
	#fi
}

sanity_check_files() {

	if test -f $war_file; then
		log "[I] $war_file file found" 
	else
		log "[E] $war_file does not exists" ; exit 1;
    fi
	if [ "${DB_FLAVOR}" == "MYSQL" ]
    then
		if test -f $mysql_core_file; then
			log "[I] $mysql_core_file file found"
		else
			log "[E] $mysql_core_file does not exists" ; exit 1;
		fi
	fi
	if [ "${DB_FLAVOR}" == "ORACLE" ]
    then
        if test -f ${oracle_core_file}; then
			log "[I] ${oracle_core_file} file found"
        else
            log "[E] ${oracle_core_file} does not exists" ; exit 1;
        fi
    fi
}

create_rollback_point() {
    DATE=`date`
    BAK_FILE=$APP-$VERSION.$DATE.bak
    log "Creating backup file : $BAK_FILE"
    cp "$APP" "$BAK_FILE"
}

create_db_user(){
	check_db_user_password
	strError="ERROR"
    if [ "${DB_FLAVOR}" == "MYSQL" ]
    then
		log "[I] Creating ${DB_FLAVOR} user '${db_user}'"
		for thost in '%' localhost
		do
			usercount=`$SQL_COMMAND_INVOKER -B -u "$db_root_user" --password="$db_root_password" -h $DB_HOST --skip-column-names -e "select count(*) from mysql.user where user = '$db_user' and host = '$thost';"`
			if  [ ${usercount} -eq 0 ]
			then
				$SQL_COMMAND_INVOKER -B -u "$db_root_user" --password="$db_root_password" -h $DB_HOST -e "create user '$db_user'@'$thost' identified by '$db_password';"
				log "[I] Creating user '$db_user' for host $thost done"
			fi
			dbquery="REVOKE ALL PRIVILEGES,GRANT OPTION FROM  '$db_user'@'$thost';FLUSH PRIVILEGES;"
			echo "${dbquery}" | $SQL_COMMAND_INVOKER -u "$db_root_user" --password="$db_root_password" -h $DB_HOST
			check_ret_status $? "'$DB_FLAVOR' revoke *.* privileges from user '$db_user'@'$thost' failed"
		done
		log "[I] Creating ${DB_FLAVOR} user '${db_user}' DONE"
	fi
	if [ "${DB_FLAVOR}" == "ORACLE" ]
    then
		#check user exist or not
		result3=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "select UPPER(username) from all_users where UPPER(username)=UPPER('${db_user}');"`
		username=`echo ${db_user} | tr '[:lower:]' '[:upper:]'`
		#if does not contains username so create user
		if test "${result3#*$username}" == "$result3"
		then
			#create user
			result4=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "create user ${db_user} identified by \"${db_password}\";"`
			result3=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "select UPPER(username) from all_users where UPPER(username)=UPPER('${db_user}');"`
			username=`echo ${db_user} | tr '[:lower:]' '[:upper:]'`
			#if user is not created print error message
			if test "${result3#*$username}" == "$result3"
			then
				log "[E] Creating User: ${db_user} Failed";
				log "[E] $result4"
				exit 1
			else
				log "[I] Creating User: ${db_user} Success";
			fi
	    fi
        result5=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "GRANT CREATE SESSION,CREATE PROCEDURE,CREATE TABLE,CREATE VIEW,CREATE SEQUENCE,CREATE PUBLIC SYNONYM,CREATE TRIGGER,UNLIMITED TABLESPACE TO ${db_user} WITH ADMIN OPTION;"`
        if test "${result5#*$strError}" == "$result5"
		then
			log "[I] Granting User: ${db_user} Success";
		else
			log "[E] Granting User: ${db_user} Failed";
			log "[E] $result5"
			exit 1
		fi
		log "[I] Creating $DB_FLAVOR user '${db_user}' DONE"
    fi
}

check_db_admin_password () {
	count=0
	msg=''
	cmdStatus=''
	strError="ERROR"
	if [ "${DB_FLAVOR}" == "MYSQL" ]
    then
		log "[I] Checking ${DB_FLAVOR} $db_root_user password"
		msg=`$SQL_COMMAND_INVOKER -u "$db_root_user" --password="$db_root_password" -h "$DB_HOST" -s -e "select version();" 2>&1`
		cmdStatus=$?
    fi

	if [ "${DB_FLAVOR}" == "ORACLE" ]
    then
		log "[I] Checking ${DB_FLAVOR} $db_root_user password"
		msg=`echo "select 1 from dual;" | $SQL_COMMAND_INVOKER  -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA>&1`
		cmdStatus=$?
    fi
	if test "${msg#*$strError}" != "$msg"
	then
		cmdStatus=1
	else
		cmdStatus=0 # $substring is not in $string
    fi
	while :
	do	
		if  [  $cmdStatus != 0 ]; then
			if [ $count != 0 ]
			then
				if [ "${DB_FLAVOR}" == "MYSQL" ]
				then
					log "[I] COMMAND: mysql -u $db_root_user --password=...... -h $DB_HOST : FAILED with error message:"
			    fi
				if [ "${DB_FLAVOR}" == "ORACLE" ]
	            then
	                log "[I] COMMAND: sqlplus  $db_root_user/...... @$DB_HOST AS SYSDBA : FAILED with error message:"
	            fi
				log "*******************************************${sg}*******************************************"
			fi
			if [ $count -gt 2 ]
			then
				log "[E] Unable to continue as db connectivity fails."
				exit 1
			fi
		    trap 'stty echo; exit 1' 2 3 15
            if [ "${DB_FLAVOR}" == "MYSQL" ]
		    then
				printf "Please enter password for mysql user-id, $db_root_user@${DB_HOST} : "
            fi
			if [ "${DB_FLAVOR}" == "ORACLE" ]
			then
				log="[msg] ${msg}"
				printf "Please enter password for oracle user-id, $db_root_user@${DB_HOST} AS SYSDBA: "
			fi
			stty -echo
			read db_root_password
			stty echo
			printf "\n"
			trap '' 2 3 15
			count=`expr ${count} + 1`
			if [ "${DB_FLAVOR}" == "MYSQL" ]
			then
				msg=`$SQL_COMMAND_INVOKER -u "$db_root_user" --password="$db_root_password" -h "$DB_HOST" -s -e "select version();" 2>&1`
				cmdStatus=$?
			fi
			if [ "${DB_FLAVOR}" == "ORACLE" ]
			then
				msg=`echo "select 1 from dual;" | $SQL_COMMAND_INVOKER  -L -S "${db_root_user}"/"\"${db_root_password}\""@"{$DB_HOST}" AS SYSDBA >&1`
				cmdStatus=$?
			fi
			if test "${msg#*$strError}" != "$msg"
		    then
				cmdStatus=1
			else
				cmdStatus=0 # $substring is not in $string
		    fi
	   	else
			log "[I] Checking DB password DONE"
			break;
		fi
	done
	return 0;
}

check_db_user_password() {
	count=0
	muser=${db_user}@${DB_HOST}
	while [ "${db_password}" = "" ]
	do
		if [ $count -gt 0 ]
		then
			log "[I] You can not have a empty password for user: (${muser})." 
		fi
		if [ ${count} -gt 2 ]
		then
			log "[E] Unable to continue as user, ${muser} does not have a non-empty password."
		fi
		printf "Please enter password for the XASecure schema owner (${muser}): "
		trap 'stty echo; exit 1' 2 3 15
		stty -echo
		read db_password
		stty echo
		printf "\n"
		trap ''  2 3 15
		count=`expr ${count} + 1`
	done
}


check_audit_user_password() {
	count=0
	muser=${audit_db_user}@${DB_HOST}
	while [ "${audit_db_password}" = "" ]
	do
		if [ $count -gt 0 ]
		then
			log "[I] You can not have a empty password for user: (${muser})." 
		fi
		if [ ${count} -gt 2 ]
		then
			log "[E] Unable to continue as user, ${muser} does not have a non-empty password."
		fi
		printf "Please enter password for the XASecure Audit Table owner (${muser}): "
		trap 'stty echo; exit 1' 2 3 15
		stty -echo
		read audit_db_password
		stty echo
		printf "\n"
		trap ''  2 3 15
		count=`expr ${count} + 1`
	done
}

upgrade_db() {
	log "[I] - starting upgradedb ... "
	if [ "${DB_FLAVOR}" == "MYSQL" ]
    then
		DBVERSION_CATALOG_CREATION=db/create_dbversion_catalog.sql
		if [ -f ${DBVERSION_CATALOG_CREATION} ]
		then
			log "[I] Verifying database version catalog table .... "
			${mysqlexec} < ${DBVERSION_CATALOG_CREATION}
			`${SQL_COMMAND_INVOKER} -u "${db_root_user}" --password="${db_root_password}" -h ${DB_HOST} -D ${db_name} < ${DBVERSION_CATALOG_CREATION}`
			check_ret_status $? "Verifying database version catalog table Failed."
		fi

		dt=`date '+%s'`
		tempFile=/tmp/sql_${dt}_$$.sql
		sqlfiles=`ls -1 db/patches/*.sql 2> /dev/null | awk -F/ '{ print $NF }' | awk -F- '{ print $1, $0 }' | sort -k1 -n | awk '{ printf("db/patches/%s\n",$2) ; }'`
		for sql in ${sqlfiles}
		do
			if [ -f ${sql} ]
			then
				bn=`basename ${sql}`
				version=`echo ${bn} | awk -F'-' '{ print $1 }'`
				if [ "${version}" != "" ]
				then
					c=`${SQL_COMMAND_INVOKER} -u "${db_root_user}" --password="${db_root_password}" -h ${DB_HOST} -D ${db_name} -B --skip-column-names -e "select count(id) from x_db_version_h where version = '${version}' and active = 'Y'"`
					check_ret_status $? "DBVerionCheck - ${version} Failed."
					if [ ${c} -eq 0 ]
					then
						cat ${sql} > ${tempFile}
						echo >> ${tempFile}
						echo "insert into x_db_version_h (version, inst_at, inst_by, updated_at, updated_by) values ( '${version}', now(), user(), now(), user()) ;" >> ${tempFile}
						log "[I] - patch [${version}] is being applied."
						`${SQL_COMMAND_INVOKER} -u "${db_root_user}" --password="${db_root_password}" -h ${DB_HOST} -D ${db_name} < ${tempFile}`
						check_ret_status $? "Update patch - ${version} Failed. See sql file : [${tempFile}]"
						rm -f ${tempFile}
					else
						log "[I] - patch [${version}] is already applied. Skipping ..."
					fi
				fi
			fi
		done
	fi
	####
	if [ "${DB_FLAVOR}" == "ORACLE" ]
    then
		strError="ERROR"
		DBVERSION_CATALOG_CREATION=db/oracle/create_dbversion_catalog.sql
		VERSION_TABLE=x_db_version_h
		log "[I] Verifying table $VERSION_TABLE in database $db_name";
		if [ -f ${DBVERSION_CATALOG_CREATION} ]
		then
			result1=`${SQL_COMMAND_INVOKER} -L -S "${db_user}"/"\"${db_password}\""@"${DB_HOST}" <<< "select UPPER(table_name) from all_tables where UPPER(tablespace_name)=UPPER('${db_name}') and UPPER(table_name)=UPPER('${VERSION_TABLE}');"`
			tablename=`echo $VERSION_TABLE | tr '[:lower:]' '[:upper:]'`
			if test "${result1#*$tablename}" == "$result1"	#does not contains tablename so create table
			then
				log "[I] Importing Version Catalog file: $DBVERSION_CATALOG_CREATION..."
				result2=`echo "exit"|${SQL_COMMAND_INVOKER} -L -S "${db_user}"/"\"${db_password}\""@"${DB_HOST}" @$DBVERSION_CATALOG_CREATION`
				if test "${result2#*$strError}" == "$result2"
				then
					log "[I] Importing Version Catalog file : $DBVERSION_CATALOG_CREATION DONE";
				else
					log "[E] Importing Version Catalog file : $DBVERSION_CATALOG_CREATION Failed";
					log "[E] $result2"
				fi
			else
				log "[I] Table $VERSION_TABLE already exists in database ${db_name}"
			fi
		fi

		dt=`date '+%s'`
		tempFile=/tmp/sql_${dt}_$$.sql
		sqlfiles=`ls -1 db/oracle/patches/*.sql 2> /dev/null | awk -F/ '{ print $NF }' | awk -F- '{ print $1, $0 }' | sort -k1 -n | awk '{ printf("db/oracle/patches/%s\n",$2) ; }'`
		for sql in ${sqlfiles}
		do
			if [ -f ${sql} ]
			then
				bn=`basename ${sql}`
				version=`echo ${bn} | awk -F'-' '{ print $1 }'`
				if [ "${version}" != "" ]
				then
					result2=`${SQL_COMMAND_INVOKER} -L -S "${db_user}"/"\"${db_password}\""@"${DB_HOST}" <<< "select version from x_db_version_h where version = '${version}' and active = 'Y';"`
					#does not contains record so insert
					if test "${result2#*$version}" == "$result2"
					then
						cat ${sql} > ${tempFile}
						echo >> ${tempFile}
						echo "insert into x_db_version_h (id,version, inst_at, inst_by, updated_at, updated_by) values ( X_DB_VERSION_H_SEQ.nextval,'${version}', sysdate, '${db_user}', sysdate, '${db_user}') ;" >> ${tempFile}
						log "[I] - patch [${version}] is being applied. $tempFile"
						result3=`echo "exit"|${SQL_COMMAND_INVOKER} -L -S "${db_user}"/"\"${db_password}\""@"${DB_HOST}"  @$tempFile`
						log "[+]$result3"
						if test "${result3#*$strError}" == "$result3"
						then
							log "[I] Update patch - ${version} applied. See sql file : [${tempFile}]"
						else
							log "[E] Update patch - ${version} Failed. See sql file : [${tempFile}]"
						fi
						rm -f ${tempFile}
					elif test "${result2#*$strError}" != "$result2"
					then
						log "[E] - patch [${version}] could not applied. Skipping ..."
						exit 1
					else
						log "[I] - patch [${version}] is already applied. Skipping ..."
					fi
				fi
			fi
		done
	fi
	log "[I] - upgradedb completed."
}

import_db(){
	if [ "${DB_FLAVOR}" == "MYSQL" ]
    then
		log "[I] Verifying Database: ${db_name}";
		existdb=`${SQL_COMMAND_INVOKER} -u "${db_root_user}" --password="${db_root_password}" -h $DB_HOST -B --skip-column-names -e  "show databases like '${db_name}' ;"`
		if [ "${existdb}" = "${db_name}" ]
		then
			log "[I] - database ${db_name} already exists. Ignoring import_db ..."
		else
			log "[I] Creating Database: $db_name";
			$SQL_COMMAND_INVOKER -u "$db_root_user" --password="$db_root_password" -h $DB_HOST -e "create database $db_name"
			check_ret_status $? "Creating database Failed.."
			log "[I] Importing Core Database file: $mysql_core_file "
			$SQL_COMMAND_INVOKER -u "$db_root_user" --password="$db_root_password" -h $DB_HOST $db_name < $mysql_core_file
			check_ret_status $? "Importing Database Failed.."
			if [ -f "${mysql_asset_file}" ]
			then
				$SQL_COMMAND_INVOKER -u "$db_root_user" --password="$db_root_password" -h $DB_HOST ${db_name} < ${mysql_asset_file}
				check_ret_status $? "Reset of DB repositories failed"
			fi
			log "[I] Importing Database file : $mysql_core_file DONE";
		fi
		for thost in '%' localhost
		do
			mysqlquery="GRANT ALL ON $db_name.* TO '$db_user'@'$thost' ;
			GRANT ALL PRIVILEGES ON $db_name.* to '$db_user'@'$thost' WITH GRANT OPTION;
			FLUSH PRIVILEGES;"
			echo "${mysqlquery}" | $SQL_COMMAND_INVOKER -u "$db_root_user" --password="$db_root_password" -h $DB_HOST
			check_ret_status $? "'$db_user' grant privileges on '$db_name' failed"
			log "[I] Granting MYSQL user '$db_user' for host $thost DONE"
		done
	fi
	if [ "${DB_FLAVOR}" == "ORACLE" ]
    then
		log "[I] Importing TABLESPACE: ${db_name}";
		strError="ERROR"
		existdb="false"

		#Verifying Users
		log "[I] Verifying DB User: ${db_user}";
		result3=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "select UPPER(username) from all_users where UPPER(username)=UPPER('${db_user}');"`
		username=`echo ${db_user} | tr '[:lower:]' '[:upper:]'`
		if test "${result3#*$username}" == "$result3"	#does not contains username so create user
		then
			#create user
			result4=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA  <<< "create user ${db_user} identified by \"${db_password}\";"`
			result3=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "select UPPER(username) from all_users where UPPER(username)=UPPER('${db_user}');"`
			username=`echo ${db_user} | tr '[:lower:]' '[:upper:]'`
			if test "${result3#*$username}" == "$result3"	#does not contains username so create user
			then
				log "[E] Creating User: ${db_user} Failed";
				log "[E] ${result4}";
				exit 1
			else
				log "[I] Creating User: ${db_user} Success";
			fi
		else
			log "[I] User: ${db_user} exist";
		fi

		#creating db/tablespace
		result1=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "SELECT DISTINCT UPPER(TABLESPACE_NAME) FROM USER_TABLESPACES where UPPER(TABLESPACE_NAME)=UPPER('${db_name}');"`
		tablespace=`echo ${db_name} | tr '[:lower:]' '[:upper:]'`
		if test "${result1#*$tablespace}" == "$result1" #does not contains tablespace so create tablespace
		then
			log "[I] Creating TABLESPACE: ${db_name}";
			result2=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "create tablespace ${db_name} datafile '${db_name}.dat' size 10M autoextend on;"`
			if test "${result2#*$strError}" == "$result2"
			then
				log "[I] TABLESPACE ${db_name} created.";
				existdb="true"
			else
				log "[E] Creating TABLESPACE: ${db_name} Failed";
				log "[E] $result2";
				exit 1
			fi
		else
			log "[I] TABLESPACE ${db_name} already exists.";
		fi

		#verify table space
		result1a=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "SELECT DISTINCT UPPER(TABLESPACE_NAME) FROM USER_TABLESPACES where UPPER(TABLESPACE_NAME)=UPPER('${db_name}');"`
		tablespace1a=`echo ${db_name} | tr '[:lower:]' '[:upper:]'`
		if test "${result1a#*$tablespace1a}" == "$result1a" #does not contains tablespace so exit
		then
			log "[E] TABLESPACE: ${db_name} Does not exist!!";
			exit 1
		fi

		#verify user
		result3=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "select UPPER(username) from all_users where UPPER(username)=UPPER('${db_user}');"`
		username=`echo ${db_user} | tr '[:lower:]' '[:upper:]'`
		if test "${result3#*$username}" == "$result3"	#does not contains username so exit
		then
			log "[E] User: ${db_user} Does not exist!!";
			exit 1
		fi

		# ASSIGN DEFAULT TABLESPACE ${db_name}
		result8=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA  <<< "alter user ${db_user} identified by \"${db_password}\" DEFAULT TABLESPACE ${db_name};"`

	    #grant user
        result5=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "GRANT CREATE SESSION,CREATE PROCEDURE,CREATE TABLE,CREATE VIEW,CREATE SEQUENCE,CREATE PUBLIC SYNONYM,CREATE TRIGGER,UNLIMITED TABLESPACE TO ${db_user} WITH ADMIN OPTION;"`
        if test "${result5#*$strError}" == "$result5"
		then
			log "[I] Granting User: ${db_user} Success";
		else
			log "[E] Granting User: ${db_user} Failed";
			log "[E] $result5";
			exit 1
		fi

		#if does not contains tables create tables
		if [ "${existdb}" == "true" ]
		then
			log "[I] Importing XA Database file: ${oracle_core_file}..."
			result7=`echo "exit"|${SQL_COMMAND_INVOKER} -L -S "${db_user}"/"\"${db_password}\""@"${DB_HOST}" @${oracle_core_file}`
			if test "${result7#*$strError}" == "$result7"
			then
				log "[I] Importing XA Database file : ${oracle_core_file} DONE";
			else
				log "[E] Importing XA Database file : ${oracle_core_file} Failed";
				log "[E] $result7";
				exit 1
			fi
		else
			log "[I] - database ${db_name} already exists. Ignoring import_db ..."	;
		fi
	fi
}

extract_war () {
	if [ ! -e $war_file ]
	then
		log "[E] $war_file file not found!"
	fi
	log "[I] Extract War file $war_file to $app_home" # 
	if [ -d $app_home ]
	then
		mv ${app_home} ${app_home}_archive_`date '+%s'`
	fi
	mkdir -p $app_home
	unzip -q $war_file -d $app_home 
	check_ret_status $? "Extraction of war file failed....!!"
	log "[I] Extract War file $war_file DONE" # 
}

copy_to_webapps (){
	log "[I] Copying to ${WEBAPP_ROOT} ";
	if [ -f $app_home/WEB-INF/log4j.xml.prod ]
    then
        mv -f $app_home/WEB-INF/log4j.xml.prod $app_home/WEB-INF/log4j.xml
    fi
    cp -rf $app_home/* ${WEBAPP_ROOT}
	check_ret_status $? "Copying to ${WEBAPP_ROOT} failed"
	
	#
	# the jar file, ${INSTALL_DIR}/webapps/ROOT/WEB-INF/lib/unixauthclient-*.jar should be accessed from external to have the parameter to work correctly
	#
	for f in  ${WEBAPP_ROOT}/WEB-INF/lib/unixauthclient-*.jar
    do
		if [ -f ${f} ]
		then
			mkdir -p ${INSTALL_DIR}/xasecure_jaas/
			mv ${f} ${INSTALL_DIR}/xasecure_jaas/
		fi
    done

	log "[I] Copying to ${WEBAPP_ROOT} DONE";
}

copy_db_connector(){
	if [ "${DB_FLAVOR}" == "MYSQL" ]
	then
		log "[I] Copying MYSQL Connector to $app_home/WEB-INF/lib ";
	    cp -f $SQL_CONNECTOR_JAR $app_home/WEB-INF/lib
		check_ret_status $? "Copying MYSQL Connector to $app_home/WEB-INF/lib failed"
		log "[I] Copying MYSQL Connector to $app_home/WEB-INF/lib DONE";
	fi
	if [ "${DB_FLAVOR}" == "ORACLE" ]
    then
        log "[I] Copying ORACLE Connector to $app_home/WEB-INF/lib ";
        cp -f $SQL_CONNECTOR_JAR $app_home/WEB-INF/lib
        check_ret_status $? "Copying ORACLE Connector to $app_home/WEB-INF/lib failed"
        log "[I] Copying ORACLE Connector to $app_home/WEB-INF/lib DONE";
    fi
}

update_properties() {
	newPropertyValue=''
	to_file=$app_home/WEB-INF/classes/xa_system.properties
	if test -f $to_file; then
		log "[I] $to_file file found" 
	else
		log "[E] $to_file does not exists" ; exit 1;
    fi
	if [ "${DB_FLAVOR}" == "MYSQL" ]
	then
		propertyName=jdbc.url
		newPropertyValue="jdbc:log4jdbc:mysql://${DB_HOST}/${db_name}"
		updatePropertyToFile $propertyName $newPropertyValue $to_file

		propertyName=auditDB.jdbc.url
		newPropertyValue="jdbc:log4jdbc:mysql://${DB_HOST}/${audit_db_name}"
		updatePropertyToFile $propertyName $newPropertyValue $to_file

		propertyName=jdbc.dialect
		newPropertyValue="org.eclipse.persistence.platform.database.MySQLPlatform"
		updatePropertyToFile $propertyName $newPropertyValue $to_file

		propertyName=auditDB.jdbc.dialect
		newPropertyValue="org.eclipse.persistence.platform.database.MySQLPlatform"
		updatePropertyToFile $propertyName $newPropertyValue $to_file

		propertyName=jdbc.driver
		newPropertyValue="net.sf.log4jdbc.DriverSpy"
		updatePropertyToFile $propertyName $newPropertyValue $to_file

		propertyName=auditDB.jdbc.driver
		newPropertyValue="net.sf.log4jdbc.DriverSpy"
		updatePropertyToFile $propertyName $newPropertyValue $to_file
	fi
	if [ "${DB_FLAVOR}" == "ORACLE" ]
	then
		propertyName=jdbc.url
		newPropertyValue="jdbc:oracle:thin:\@//${DB_HOST}"
		updatePropertyToFile $propertyName $newPropertyValue $to_file

		propertyName=auditDB.jdbc.url
		newPropertyValue="jdbc:oracle:thin:\@//${DB_HOST}"
		updatePropertyToFile $propertyName $newPropertyValue $to_file

		propertyName=jdbc.dialect
		newPropertyValue="org.eclipse.persistence.platform.database.OraclePlatform"
		updatePropertyToFile $propertyName $newPropertyValue $to_file

		propertyName=auditDB.jdbc.dialect
		newPropertyValue="org.eclipse.persistence.platform.database.OraclePlatform"
		updatePropertyToFile $propertyName $newPropertyValue $to_file

		propertyName=jdbc.driver
		newPropertyValue="oracle.jdbc.OracleDriver"
		updatePropertyToFile $propertyName $newPropertyValue $to_file

		propertyName=auditDB.jdbc.driver
		newPropertyValue="oracle.jdbc.OracleDriver"
		updatePropertyToFile $propertyName $newPropertyValue $to_file
	fi
	propertyName=xa.webapp.url.root
	newPropertyValue="${policymgr_external_url}"
	updatePropertyToFile $propertyName $newPropertyValue $to_file

	propertyName=http.enabled
	newPropertyValue="${policymgr_http_enabled}"
	updatePropertyToFile $propertyName $newPropertyValue $to_file	
	
	propertyName=jdbc.user
	newPropertyValue="${db_user}"
	updatePropertyToFile $propertyName $newPropertyValue $to_file	
	
	propertyName=auditDB.jdbc.user
	newPropertyValue="${audit_db_user}"
	updatePropertyToFile $propertyName $newPropertyValue $to_file
	##########

	keystore="${cred_keystore_filename}"

	echo "Starting configuration for XA DB credentials:"

	db_password_alias=policyDB.jdbc.password
	
   	if [ "${keystore}" != "" ]
   	then
		mkdir -p `dirname "${keystore}"`

   		java -cp "cred/lib/*" com.hortonworks.credentialapi.buildks create "$db_password_alias" -value "$db_password" -provider jceks://file$keystore
   		
   		propertyName=xaDB.jdbc.credential.alias
		newPropertyValue="${db_password_alias}"
		updatePropertyToFile $propertyName $newPropertyValue $to_file
	
		propertyName=xaDB.jdbc.credential.provider.path
		newPropertyValue="${keystore}"
		updatePropertyToFile $propertyName $newPropertyValue $to_file

		propertyName=jdbc.password
		newPropertyValue="_"	
		updatePropertyToFile $propertyName $newPropertyValue $to_file
   	else  	
		propertyName=jdbc.password
		newPropertyValue="${db_password}"	
		updatePropertyToFile $propertyName $newPropertyValue $to_file
	fi	
	
	if test -f $keystore; then
		#echo "$keystore found."
		chown -R ${unix_user}:${unix_group} ${keystore}
	else
		#echo "$keystore not found. so clear text password"
		propertyName=jdbc.password
		newPropertyValue="${db_password}"
		updatePropertyToFile $propertyName $newPropertyValue $to_file
	fi
 
	###########
	audit_db_password_alias=auditDB.jdbc.password

	echo "Starting configuration for Audit DB credentials:"
	
   	if [ "${keystore}" != "" ]
   	then
	   	java -cp "cred/lib/*" com.hortonworks.credentialapi.buildks create "$audit_db_password_alias" -value "$audit_db_password" -provider jceks://file$keystore
	   	
		propertyName=auditDB.jdbc.credential.alias
		newPropertyValue="${audit_db_password_alias}"
		updatePropertyToFile $propertyName $newPropertyValue $to_file	
		
		propertyName=auditDB.jdbc.credential.provider.path
		newPropertyValue="${keystore}"
		updatePropertyToFile $propertyName $newPropertyValue $to_file

		propertyName=auditDB.jdbc.password
		newPropertyValue="_"	
		updatePropertyToFile $propertyName $newPropertyValue $to_file
   	else
		propertyName=auditDB.jdbc.password
		newPropertyValue="${audit_db_password}"	
		updatePropertyToFile $propertyName $newPropertyValue $to_file
	fi	
	
	if test -f $keystore; then
		chown -R ${unix_user}:${unix_group} ${keystore}
		#echo "$keystore found."
	else
		#echo "$keystore not found. so use clear text password"
		propertyName=auditDB.jdbc.password
		newPropertyValue="${audit_db_password}"	
		updatePropertyToFile $propertyName $newPropertyValue $to_file
	fi
	
}

create_audit_db_user(){
	check_audit_user_password
	AUDIT_DB="${audit_db_name}"
	AUDIT_USER="${audit_db_user}"
	AUDIT_PASSWORD="${audit_db_password}"
	strError="ERROR"
	#Verifying Database
	if [ "${DB_FLAVOR}" == "MYSQL" ]
    then
		log "[I] Verifying Database: $AUDIT_DB";
		existdb=`${SQL_COMMAND_INVOKER} -u "$db_root_user" --password="$db_root_password" -h $DB_HOST -B --skip-column-names -e  "show databases like '$AUDIT_DB' ;"`
		if [ "${existdb}" = "$AUDIT_DB" ]
		then
			log "[I] Database $AUDIT_DB already exists."
		else
			log "[I] Creating Database: $audit_db_name";
			$SQL_COMMAND_INVOKER -u "$db_root_user" --password="$db_root_password" -h $DB_HOST -e "create database $AUDIT_DB"
			check_ret_status $? "Creating database $AUDIT_DB Failed.."
		fi
	fi
	if [ "${DB_FLAVOR}" == "ORACLE" ]
    then
		log "[I] Verifying TABLESPACE: $AUDIT_DB";
		result1=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA  <<< "SELECT distinct UPPER(TABLESPACE_NAME) FROM USER_TABLESPACES where UPPER(TABLESPACE_NAME)=UPPER('${AUDIT_DB}');"`
		tablespace=`echo $AUDIT_DB | tr '[:lower:]' '[:upper:]'`
		if test "${result1#*$tablespace}" == "$result1" #does not contains tablespace so create tablespace
		then
			log "[I] Creating TABLESPACE: $AUDIT_DB";
			result2=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "create tablespace $AUDIT_DB datafile '$AUDIT_DB.dat' size 10M autoextend on;"`
			if test "${result2#*$strError}" == "$result2"
			then
				log "[I] TABLESPACE $AUDIT_DB created."
			else
				log "[E] Creating TABLESPACE: $AUDIT_DB Failed";
				log "[E] $result2"
				exit 1
			fi
		else
			log "[I] TABLESPACE $AUDIT_DB already exists."
		fi
	fi

	#Verifying Users
	log "[I] Verifying Audit User: $AUDIT_USER";
	if [ "${DB_FLAVOR}" == "MYSQL" ]
    then
		for thost in '%' localhost
		do
			usercount=`$SQL_COMMAND_INVOKER -B -u "$db_root_user" --password="$db_root_password" -h $DB_HOST --skip-column-names -e "select count(*) from mysql.user where user = '$AUDIT_USER' and host = '$thost';"`
			if  [ ${usercount} -eq 0 ]
			then
				log "[I] Creating ${DB_FLAVOR} user '$AUDIT_USER'@'$thost'"
				$SQL_COMMAND_INVOKER -B -u "$db_root_user" --password="$db_root_password" -h $DB_HOST -e "create user '$AUDIT_USER'@'$thost' identified by '$AUDIT_PASSWORD';"
				check_ret_status $? "${DB_FLAVOR} create user failed"
			fi
			if [ "${AUDIT_USER}" != "${db_user}" ]
			then
				mysqlquery="REVOKE ALL PRIVILEGES,GRANT OPTION FROM '$AUDIT_USER'@'$thost' ;
				FLUSH PRIVILEGES;"
				echo "${mysqlquery}" | $SQL_COMMAND_INVOKER -u "$db_root_user" --password="$db_root_password" -h $DB_HOST
				check_ret_status $? "'$DB_FLAVOR' revoke privileges from user '$AUDIT_USER'@'$thost' failed"
				log "[I] '$DB_FLAVOR' revoke all privileges from user '$AUDIT_USER'@'$thost' DONE"
			fi
		done
	fi
	if [ "${DB_FLAVOR}" == "ORACLE" ]
    then
		result3=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "select UPPER(username) from all_users where UPPER(username)=UPPER('${AUDIT_USER}');"`
		username=`echo $AUDIT_USER | tr '[:lower:]' '[:upper:]'`
		if test "${result3#*$username}" == "$result3"	#does not contains username so create user
		then
			#create user
			result4=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "create user ${AUDIT_USER} identified by \"${AUDIT_PASSWORD}\" DEFAULT TABLESPACE ${AUDIT_DB};"`
			if test "${result4#*$strError}" == "$result4"
		    then
				log "[I] Creating User: ${AUDIT_USER} Success";
			else
				log "[E] Creating User: ${AUDIT_USER} Failed";
				log "[E] $result4"
				exit 1
		    fi
		else
			log "[I] User: ${AUDIT_USER} exist";
		fi
        result5=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "GRANT CREATE SESSION TO ${AUDIT_USER};"`
        if test "${result5#*$strError}" == "$result5"
		then
			log "[I] Granting User: $AUDIT_USER Success";
		else
			log "[E] Granting User: $AUDIT_USER Failed";
			log "[E] $result5"
			exit 1
		fi
    fi

	#Verifying audit table
	AUDIT_TABLE=xa_access_audit
	if [ "${DB_FLAVOR}" == "MYSQL" ]
	then
		log "[I] Verifying table $AUDIT_TABLE in audit database $AUDIT_DB";
		existtbl=`${SQL_COMMAND_INVOKER} -u "$db_root_user" --password="$db_root_password" -D $AUDIT_DB -h $DB_HOST -B --skip-column-names -e  "show tables like '$AUDIT_TABLE' ;"`
		if [ "${existtbl}" != "$AUDIT_TABLE" ]
		then
			log "[I] Importing Audit Database file: $mysql_audit_file..."
			$SQL_COMMAND_INVOKER -u "$db_root_user" --password="$db_root_password" -h $DB_HOST $AUDIT_DB < $mysql_audit_file
			check_ret_status $? "Importing Audit Database Failed.."
			log "[I] Importing Audit Database file : $mysql_audit_file DONE";
		else
			log "[I] Table $AUDIT_TABLE already exists in audit database $AUDIT_DB"
		fi
	fi
	if [ "${DB_FLAVOR}" == "ORACLE" ]
	then
		log "[I] Verifying table $AUDIT_TABLE in TABLESPACE $db_name";
		# ASSIGN DEFAULT TABLESPACE ${db_name}
		result8=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA  <<< "alter user ${AUDIT_USER} identified by \"${AUDIT_PASSWORD}\" DEFAULT TABLESPACE ${AUDIT_DB};"`
		result6=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "select UPPER(table_name) from all_tables where UPPER(tablespace_name)=UPPER('$db_name') and UPPER(table_name)=UPPER('${AUDIT_TABLE}');"`
		tablename=`echo $AUDIT_TABLE | tr '[:lower:]' '[:upper:]'`
		if test "${result6#*$tablename}" == "$result6"	#does not contains tablename so create table
		then
			log "[I] Importing Audit Database file: $oracle_audit_file..."
			result7=`echo "exit"|${SQL_COMMAND_INVOKER} -L -S "${db_user}"/"\"${db_password}\""@"${DB_HOST}" @$oracle_audit_file`
			if test "${result7#*$strError}" == "$result7"
			then
				log "[I] Importing Audit Database file : $oracle_audit_file DONE";
			else
				log "[E] Importing Audit Database file : $oracle_audit_file failed";
				log "[E] $result7"
			fi
		else
			log "[I] Table $AUDIT_TABLE already exists in TABLESPACE $db_name"
		fi
	fi

	#Granting Users
	log "[I] Granting Privileges to User: $AUDIT_USER";
	if [ "${DB_FLAVOR}" == "MYSQL" ]
    then
		for thost in '%' localhost
		do
			mysqlquery="GRANT ALL ON $AUDIT_DB.* TO '$db_user'@'$thost' ;
			GRANT ALL PRIVILEGES ON $AUDIT_DB.* to '$db_user'@'$thost' WITH GRANT OPTION;
			FLUSH PRIVILEGES;"
			echo "${mysqlquery}" | $SQL_COMMAND_INVOKER -u "$db_root_user" --password="$db_root_password" -h $DB_HOST
			check_ret_status $? "'$db_user' grant privileges on '$AUDIT_DB' failed"
			log "[I] Creating MYSQL user '$AUDIT_USER' for host $thost DONE"

			mysqlquery="GRANT INSERT ON $AUDIT_DB.$AUDIT_TABLE TO '$AUDIT_USER'@'$thost' ;
			FLUSH PRIVILEGES;"
			echo "${mysqlquery}" | $SQL_COMMAND_INVOKER -u "$db_root_user" --password="$db_root_password" -h $DB_HOST
			check_ret_status $? "'$DB_FLAVOR' grant INSERT privileges to user '$AUDIT_USER'@'$thost' on $AUDIT_TABLE failed"
			log "[I] '$DB_FLAVOR' grant INSERT privileges to user '$AUDIT_USER'@'$thost' on $AUDIT_TABLE DONE"
		done
	fi
	if [ "${DB_FLAVOR}" == "ORACLE" ]
	then
		if [ "${AUDIT_USER}" != "${db_user}" ]
		then
			result11=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "GRANT SELECT ON ${db_user}.XA_ACCESS_AUDIT_SEQ TO ${AUDIT_USER};"`
			result12=`${SQL_COMMAND_INVOKER} -L -S "${db_root_user}"/"\"${db_root_password}\""@"${DB_HOST}" AS SYSDBA <<< "GRANT INSERT ON ${db_user}.${AUDIT_TABLE} TO ${AUDIT_USER};"`
			if test "${result11#*$strError}" != "$result11"
			then
				log "[E] Granting User: $AUDIT_USER Failed";
				log "[E] $result11";
				exit1
			elif test "${result12#*$strError}" != "$result12"
			then
				log "[E] Granting User: $AUDIT_USER Failed";
				log "[E] $result12";
				exit 1
			else
				log "[I] Granting User: $AUDIT_USER Success";
			fi
		fi
	fi
}

do_unixauth_setup() {

	XASECURE_JAAS_DIR="${INSTALL_DIR}/xasecure_jaas"

	if [ -d "${XASECURE_JAAS_DIR}" ]
	then
		mv "${XASECURE_JAAS_DIR}" "${XASECURE_JAAS_DIR}_archive_`date '+%s'`"
	fi

	mkdir -p ${XASECURE_JAAS_DIR}

	cp ./unixauth-config/*  ${XASECURE_JAAS_DIR}

	cat unixauth-config/unixauth.properties | \
			grep -v '^remoteLoginEnabled=' | \
			grep -v '^authServiceHostName=' | \
			grep -v '^authServicePort=' > ${INSTALL_DIR}/xasecure_jaas/unixauth.properties

	echo "remoteLoginEnabled=${remoteLoginEnabled}"   >> ${INSTALL_DIR}/xasecure_jaas/unixauth.properties
	echo "authServiceHostName=${authServiceHostName}" >> ${INSTALL_DIR}/xasecure_jaas/unixauth.properties
	echo "authServicePort=${authServicePort}"         >> ${INSTALL_DIR}/xasecure_jaas/unixauth.properties

	owner=xasecure
	group=xasecure
	chown -R ${owner}:${group} ${XASECURE_JAAS_DIR}
	chmod -R go-rwx ${XASECURE_JAAS_DIR}
}
do_authentication_setup(){
	log "[I] Starting setup based on user authentication method=$authentication_method";     
	./setup_authentication.sh $authentication_method $app_home

    if [ $authentication_method = "LDAP" ] ; then
    	log "[I] Loading LDAP attributes and properties";
		newPropertyValue=''	
		ldap_file=$app_home/WEB-INF/classes/xa_ldap.properties
		if test -f $ldap_file; then
			log "[I] $ldap_file file found" 
			propertyName=xa_ldap_url
			newPropertyValue="${xa_ldap_url}"
			
			updatePropertyToFile $propertyName $newPropertyValue $ldap_file
			
			propertyName=xa_ldap_userDNpattern
			newPropertyValue="${xa_ldap_userDNpattern}"
			updatePropertyToFile $propertyName $newPropertyValue $ldap_file
			
			propertyName=xa_ldap_groupSearchBase
			newPropertyValue="${xa_ldap_groupSearchBase}"
			updatePropertyToFile $propertyName $newPropertyValue $ldap_file
			
			propertyName=xa_ldap_groupSearchFilter
			newPropertyValue="${xa_ldap_groupSearchFilter}"
			updatePropertyToFile $propertyName $newPropertyValue $ldap_file
			
			propertyName=xa_ldap_groupRoleAttribute
			newPropertyValue="${xa_ldap_groupRoleAttribute}"
			updatePropertyToFile $propertyName $newPropertyValue $ldap_file
			
			propertyName=authentication_method
			newPropertyValue="${authentication_method}"
			updatePropertyToFile $propertyName $newPropertyValue $ldap_file
		else
			log "[E] $ldap_file does not exists" ; exit 1;
		
    	fi
    fi
    if [ $authentication_method = "ACTIVE_DIRECTORY" ] ; then
    	log "[I] Loading ACTIVE DIRECTORY attributes and properties";
		newPropertyValue=''
		ldap_file=$app_home/WEB-INF/classes/xa_ldap.properties
		if test -f $ldap_file; then
			log "[I] $ldap_file file found" 
			propertyName=xa_ldap_ad_url
			newPropertyValue="${xa_ldap_ad_url}"
			updatePropertyToFile $propertyName $newPropertyValue $ldap_file
		
			propertyName=xa_ldap_ad_domain
			newPropertyValue="${xa_ldap_ad_domain}"
			updatePropertyToFile $propertyName $newPropertyValue $ldap_file
			
			propertyName=authentication_method
			newPropertyValue="${authentication_method}"
			updatePropertyToFile $propertyName $newPropertyValue $ldap_file
		else
			log "[E] $ldap_file does not exists" ; exit 1;
		fi
    fi
    if [ $authentication_method = "UNIX" ] ; then
        do_unixauth_setup
    fi
    log "[I] Finished setup based on user authentication method=$authentication_method";  
}

#=====================================================================

setup_unix_user_group(){

	log "[I] Setting up UNIX user : ${unix_user} and group: ${unix_group}";

    groupadd ${unix_group}
    check_ret_status_for_groupadd $? "Creating group ${unix_group} failed"

	id -u ${unix_user} > /dev/null 2>&1

	if [ $? -ne 0 ]
	then
	    log "[I] Creating new user and adding to group";
        useradd ${unix_user} -g ${unix_group} -m
		check_ret_status $? "useradd ${unix_user} failed"
	else
	    log "[I] User already exists, adding it to group";
	    usermod -g ${unix_group} ${unix_user}
	fi

	log "[I] Setting up UNIX user : ${unix_user} and group: ${unix_group} DONE";
}

setup_install_files(){

	log "[I] Setting up installation files and directory";
	if [ -d ${INSTALL_DIR} ]
	then
		mv ${INSTALL_DIR} ${INSTALL_DIR}_${curDt}
	fi

	mkdir -p ${INSTALL_DIR}
	mkdir -p ${INSTALL_DIR}/ews
	mkdir -p ${WEBAPP_ROOT}

	cp -r ews/* ${INSTALL_DIR}/
	mv ${INSTALL_DIR}/lib ${INSTALL_DIR}/ews/
	mv ${INSTALL_DIR}/xapolicymgr.properties ${INSTALL_DIR}/ews/
	mv ${INSTALL_DIR}/xapolicymgr /etc/init.d/xapolicymgr

	cat ews/startpolicymgr.sh | sed -e "s|[ \t]*JAVA_HOME=| JAVA_HOME=${JAVA_HOME}|" > ${INSTALL_DIR}/startpolicymgr.sh

	chmod ug+rx /etc/init.d/xapolicymgr

	if [ -d /etc/rc2.d ]
    then
		RC_DIR=/etc/rc2.d
        log "[I] Creating script S88xapolicymgr/K90xapolicymgr in $RC_DIR directory .... "
		rm -f $RC_DIR/S88xapolicymgr  $RC_DIR/K90xapolicymgr
		ln -s /etc/init.d/xapolicymgr $RC_DIR/S88xapolicymgr
		ln -s /etc/init.d/xapolicymgr $RC_DIR/K90xapolicymgr
    fi

    if [ -d /etc/rc3.d ]
    then
	    RC_DIR=/etc/rc3.d
        log "[I] Creating script S88xapolicymgr/K90xapolicymgr in $RC_DIR directory .... "
		rm -f $RC_DIR/S88xapolicymgr  $RC_DIR/K90xapolicymgr
		ln -s /etc/init.d/xapolicymgr $RC_DIR/S88xapolicymgr
		ln -s /etc/init.d/xapolicymgr $RC_DIR/K90xapolicymgr
    fi

	# SUSE has rc2.d and rc3.d under /etc/rc.d
    if [ -d /etc/rc.d/rc2.d ]
    then
		RC_DIR=/etc/rc.d/rc2.d
        log "[I] Creating script S88xapolicymgr/K90xapolicymgr in $RC_DIR directory .... "
		rm -f $RC_DIR/S88xapolicymgr  $RC_DIR/K90xapolicymgr
		ln -s /etc/init.d/xapolicymgr $RC_DIR/S88xapolicymgr
		ln -s /etc/init.d/xapolicymgr $RC_DIR/K90xapolicymgr
    fi
    if [ -d /etc/rc.d/rc3.d ]
    then
		RC_DIR=/etc/rc.d/rc3.d
        log "[I] Creating script S88xapolicymgr/K90xapolicymgr in $RC_DIR directory .... "
		rm -f $RC_DIR/S88xapolicymgr  $RC_DIR/K90xapolicymgr
		ln -s /etc/init.d/xapolicymgr $RC_DIR/S88xapolicymgr
		ln -s /etc/init.d/xapolicymgr $RC_DIR/K90xapolicymgr
    fi


	if [ -L ${XAPOLICYMGR_DIR} ]
	then 
		rm -f ${XAPOLICYMGR_DIR}
	fi

	ln -s ${INSTALL_DIR} ${XAPOLICYMGR_DIR}

	if [ ! -L /var/log/xapolicymgr ]
	then
		ln -s ${XAPOLICYMGR_DIR}/ews/logs  /var/log/xapolicymgr
	fi
	log "[I] Setting up installation files and directory DONE";

	if [ -d ${INSTALL_DIR}/ ]
	then
		chown -R ${unix_user}:${unix_group} ${INSTALL_DIR}
	fi
}

restart_policymgr(){

	log "[I] Restarting xapolicymgr";
	service xapolicymgr stop 
	service xapolicymgr start
	sleep 30  # To ensure that the root application is initialized fully
	log "[I] Restarting xapolicymgr DONE";

}
execute_java_patches(){
	if [ "${DB_FLAVOR}" == "MYSQL" ]
	then
		dt=`date '+%s'`
		tempFile=/tmp/sql_${dt}_$$.sql
		mysqlexec="${SQL_COMMAND_INVOKER} -u ${db_root_user} --password="${db_root_password}" -h ${DB_HOST} ${db_name}"
		javaFiles=`ls -1 $app_home/WEB-INF/classes/com/xasecure/patch/Patch*.class 2> /dev/null | awk -F/ '{ print $NF }' | awk -F_J '{ print $2, $0 }' | sort -k1 -n | awk '{ printf("%s\n",$2) ; }'`
		for javaPatch in ${javaFiles}
		do
			if test -f "$app_home/WEB-INF/classes/com/xasecure/patch/$javaPatch"; then
				className=$(basename "$javaPatch" .class)
				version=`echo ${className} | awk -F'_' '{ print $2 }'`
				if [ "${version}" != "" ]
				then
					c=`${mysqlexec} -B --skip-column-names -e "select count(id) from x_db_version_h where version = '${version}' and active = 'Y'"`
					check_ret_status $? "DBVerionCheck - ${version} Failed."
					if [ ${c} -eq 0 ]
					then
						log "[I] patch ${javaPatch} is being applied..";
						msg=`java -cp "$app_home/WEB-INF/:$app_home/META-INF/:$app_home/WEB-INF/lib/*:$app_home/WEB-INF/classes/:$app_home/WEB-INF/classes/META-INF/" com.xasecure.patch.${className}`
						check_ret_status $? "Unable to apply patch:$javaPatch"
						touch ${tempFile}
						echo >> ${tempFile}
						echo "insert into x_db_version_h (version, inst_at, inst_by, updated_at, updated_by) values ( '${version}', now(), user(), now(), user()) ;" >> ${tempFile}
						${mysqlexec} < ${tempFile}
						check_ret_status $? "Update patch - ${javaPatch} has failed."
						rm -f ${tempFile}
						log "[I] patch ${javaPatch} has been applied!!";
					else
						log "[I] - patch [${javaPatch}] is already applied. Skipping ..."
					fi
				fi
			fi
		done
	fi
	if [ "${DB_FLAVOR}" == "ORACLE" ]
	then
		dt=`date '+%s'`
		tempFile=/tmp/sql_${dt}_$$.sql
		javaFiles=`ls -1 $app_home/WEB-INF/classes/com/xasecure/patch/Patch*.class 2> /dev/null | awk -F/ '{ print $NF }' | awk -F_J '{ print $2, $0 }' | sort -k1 -n | awk '{ printf("%s\n",$2) ; }'`
		for javaPatch in ${javaFiles}
		do
			if test -f "$app_home/WEB-INF/classes/com/xasecure/patch/$javaPatch"; then
				className=$(basename "$javaPatch" .class)
				version=`echo ${className} | awk -F'_' '{ print $2 }'`
				if [ "${version}" != "" ]
				then
					result2=`${SQL_COMMAND_INVOKER} -L -S "${db_user}"/"\"${db_password}\""@"${DB_HOST}" <<< "select version from x_db_version_h where version = '${version}' and active = 'Y';"`
					#does not contains record so insert
					if test "${result2#*$version}" == "$result2"
					then
						log "[I] patch ${javaPatch} is being applied..";
						msg=`java -cp "$app_home/WEB-INF/:$app_home/META-INF/:$app_home/WEB-INF/lib/*:$app_home/WEB-INF/classes/:$app_home/WEB-INF/classes/META-INF/" com.xasecure.patch.${className}`
						check_ret_status $? "Unable to apply patch:$javaPatch"
						touch ${tempFile}
						echo >> ${tempFile}
						echo "insert into x_db_version_h (id,version, inst_at, inst_by, updated_at, updated_by) values ( X_DB_VERSION_H_SEQ.nextval,'${version}', sysdate, '${db_user}', sysdate, '${db_user}') ;" >> ${tempFile}
						result3=`echo "exit"|${SQL_COMMAND_INVOKER} -L -S "${db_user}"/"\"${db_password}\""@"${DB_HOST}"  @$tempFile`
						if test "${result3#*$strError}" == "$result3"
						then
							log "[I] patch ${javaPatch} has been applied!!";
						else
							log "[E] patch ${javaPatch} has failed."
						fi
						rm -f ${tempFile}
					elif test "${result2#*$strError}" != "$result2"
					then
						log "[E] - patch [${javaPatch}] could not applied. Skipping ..."
						exit 1
					else
						log "[I] - patch [${javaPatch}] is already applied. Skipping ..."
					fi
				fi
			fi
		done
	fi
}
init_logfiles
log " --------- Running XASecure PolicyManager Web Application Install Script --------- "
log "[I] uname=`uname`"
log "[I] hostname=`hostname`"
init_variables
get_distro
check_java_version
check_db_version
check_db_connector
setup_unix_user_group
setup_install_files
sanity_check_files
check_db_admin_password
create_db_user
extract_war
copy_db_connector
import_db
upgrade_db
create_audit_db_user
update_properties
do_authentication_setup
copy_to_webapps
execute_java_patches
restart_policymgr
echo "Installation of XASecure PolicyManager Web Application is completed."
