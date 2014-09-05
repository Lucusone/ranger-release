package com.xasecure.biz;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.xasecure.common.GUIDUtil;
import com.xasecure.common.XACommonEnums;
import com.xasecure.common.XAConstants;
import com.xasecure.common.ContextUtil;
import com.xasecure.common.PropertiesUtil;
import com.xasecure.common.RESTErrorUtil;
import com.xasecure.common.StringUtil;
import com.xasecure.common.UserSessionBase;
import com.xasecure.common.db.BaseDao;
import com.xasecure.entity.XXDBBase;
import com.xasecure.entity.XXPortalUser;
import com.xasecure.service.AbstractBaseResourceService;
import com.xasecure.view.VXDataObject;
import com.xasecure.view.VXString;
import com.xasecure.view.VXStringList;
import com.xasecure.view.VXPortalUser;

import java.util.Random;

import com.xasecure.view.VXResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;

import com.xasecure.common.AppConstants;
import com.xasecure.db.XADaoManager;
import com.xasecure.entity.XXAsset;
import com.xasecure.entity.XXGroup;
import com.xasecure.entity.XXPermMap;
import com.xasecure.entity.XXResource;
import com.xasecure.entity.XXTrxLog;
import com.xasecure.entity.XXUser;
import com.xasecure.view.VXResource;

@Component
public class XABizUtil {
	static final Logger logger = Logger.getLogger(XABizUtil.class);

	@Autowired
	RESTErrorUtil restErrorUtil;

	@Autowired
	XADaoManager daoManager;

	@Autowired
	StringUtil stringUtil;

	@Autowired
	UserMgr userMgr;

	Set<Class<?>> groupEditableClasses;
	private Class<?>[] groupEditableClassesList = {};

	Map<String, Integer> classTypeMappings = new HashMap<String, Integer>();
	private int maxFirstNameLength;
	int maxDisplayNameLength = 150;
	boolean defaultAutoApprove = true;
	boolean showBlockedContent = true;
	public final String EMPTY_CONTENT_DISPLAY_NAME = "...";
	boolean enableResourceAccessControl;
	private Random random;
	private static final String PATH_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrst0123456789-_.";
	private static char[] PATH_CHAR_SET = PATH_CHARS.toCharArray();
	private static int PATH_CHAR_SET_LEN = PATH_CHAR_SET.length;

	public XABizUtil() {
		maxFirstNameLength = Integer.parseInt(PropertiesUtil.getProperty(
				"xa.user.firstname.maxlength", "16"));
		maxDisplayNameLength = PropertiesUtil.getIntProperty(
				"xa.bookmark.name.maxlen", maxDisplayNameLength);
		showBlockedContent = PropertiesUtil.getBooleanProperty(
				"xa.content.show_blocked", showBlockedContent);
		defaultAutoApprove = PropertiesUtil.getBooleanProperty(
				"xa.mod.default", defaultAutoApprove);

		groupEditableClasses = new HashSet<Class<?>>(
				Arrays.asList(groupEditableClassesList));
		enableResourceAccessControl = PropertiesUtil.getBooleanProperty(
				"xa.resource.accessControl.enabled", true);
		random = new Random();
	}

	public <T extends XXDBBase> List<? extends XXDBBase> getParentObjects(T object) {
		List<XXDBBase> parentObjectList = null;
		// if (checkParentAcess.contains(object.getMyClassType())) {
		// parentObjectList = new ArrayList<MBase>();
		// }
		return parentObjectList;
	}

	public int getClassType(Class<?> klass) {
		String className = klass.getName();
		// See if this mapping is already in the database
		Integer classType = classTypeMappings.get(className);
		if (classType == null) {
			// Instantiate the class and call the getClassType method
			if (XXDBBase.class.isAssignableFrom(klass)) {
				try {
					XXDBBase gjObj = (XXDBBase) klass.newInstance();
					classType = gjObj.getMyClassType();
					classTypeMappings.put(className, classType);
				} catch (Throwable ex) {
					logger.error("Error instantiating object for class "
							+ className, ex);
				}
			}
		}
		if (classType == null) {
			return XACommonEnums.CLASS_TYPE_NONE;
		} else {
			return classType;
		}
	}

	// Access control methods
	public void checkSystemAdminAccess() {
		UserSessionBase currentUserSession = ContextUtil
				.getCurrentUserSession();
		if (currentUserSession != null && currentUserSession.isUserAdmin()) {
			return;
		}
		throw restErrorUtil
				.create403RESTException("Only System Administrators can add accounts");
	}

	/**
	 * @param contentType
	 * @return
	 */
	public int getMimeTypeInt(String contentType) {
		if (contentType.equalsIgnoreCase("JPEG")
				|| contentType.equalsIgnoreCase("JPG")
				|| contentType.endsWith("jpg") || contentType.endsWith("jpeg")) {
			return XAConstants.MIME_JPEG;
		}
		if (contentType.equalsIgnoreCase("PNG") || contentType.endsWith("png")) {
			return XAConstants.MIME_PNG;
		}
		return XAConstants.MIME_UNKNOWN;
	}

	/**
	 * @param mimeType
	 * @return
	 */
	public String getMimeType(int mimeType) {
		switch (mimeType) {
		case XAConstants.MIME_JPEG:
			return "jpg";
		case XAConstants.MIME_PNG:
			return "png";
		}
		return "";
	}

	/**
	 * @param contentType
	 * @return
	 */
	public String getImageExtension(String contentType) {
		if (contentType.toLowerCase().endsWith("jpg")
				|| contentType.toLowerCase().endsWith("jpeg")) {
			return "jpg";
		} else if (contentType.toLowerCase().endsWith("png")) {
			return "png";
		}
		return "";
	}

	/**
	 * @param file
	 * @return
	 */
	public String getFileNameWithoutExtension(File file) {
		if (file != null) {
			String fileName = file.getName();
			if (fileName.indexOf(".") > 0) {
				return fileName.substring(0, fileName.indexOf("."));
			}
			return fileName;

		}
		return null;
	}

	public String getDisplayNameForClassName(XXDBBase obj) {
		String classTypeDisplayName = XAConstants.getLabelFor_ClassTypes(obj
				.getMyClassType());
		if (classTypeDisplayName == null) {
			logger.error(
					"Error get name for class type. obj=" + obj.toString(),
					new Throwable());
		}
		return classTypeDisplayName;
	}

	public String getDisplayName(XXDBBase obj) {
		if (obj != null) {
			return handleGetDisplayName(obj.getMyDisplayValue());
		} else {
			return handleGetDisplayName(null);
		}
	}

	/**
	 * @param displayValue
	 * @return
	 */
	private String handleGetDisplayName(String displayValue) {
		if (displayValue == null || displayValue.trim().isEmpty()) {
			return EMPTY_CONTENT_DISPLAY_NAME;
		}

		if (displayValue.length() > maxDisplayNameLength) {
			displayValue = displayValue.substring(0, maxDisplayNameLength - 3)
					.concat("...");
		}
		return displayValue;
	}

	/**
	 * @param userProfile
	 * @return
	 */
	public String generatePublicName(VXPortalUser userProfile, XXPortalUser gjUser) {
		return generatePublicName(userProfile.getFirstName(),
				userProfile.getLastName());
	}

	public String generatePublicName(String firstName, String lastName) {
		String publicName = null;
		String fName = firstName;
		if (firstName.length() > maxFirstNameLength) {
			fName = firstName.substring(0, maxFirstNameLength - (1 + 3))
					+ "...";
		}
		if (lastName != null && lastName.length() > 0) {
			publicName = fName + " " + lastName.substring(0, 1) + ".";
		}
		return publicName;
	}

	public void updateCloneReferences(XXDBBase obj) {
		if (obj == null) {
			return;
		}
	}

	public Long getForUserId(XXDBBase resource) {
		return null;
	}

	public XXDBBase getMObject(int objClassType, Long objId) {
		XXDBBase obj = null;

		if (objId != null) {
			BaseDao<?> dao = daoManager.getDaoForClassType(objClassType);

			if (dao != null) {
				obj = (XXDBBase) dao.getById(objId);
			}
		}

		return obj;
	}

	public XXDBBase getMObject(VXDataObject vXDataObject) {
		if (vXDataObject != null) {
			return getMObject(vXDataObject.getMyClassType(), vXDataObject.getId());
		}
		return null;
	}

	public VXDataObject getVObject(int objClassType, Long objId) {
		if (objId == null) {
			return null;
		}
		if (objClassType == XAConstants.CLASS_TYPE_USER_PROFILE) {
			return userMgr.mapXXPortalUserVXPortalUser(daoManager.getXXPortalUser().getById(
					objId));
		}
		try {
			AbstractBaseResourceService<?, ?> myService = AbstractBaseResourceService
					.getService(objClassType);
			if (myService != null) {
				return myService.readResource(objId);
			}
		} catch (Throwable t) {
			logger.error("Error reading resource. objectClassType="
					+ objClassType + ", objectId=" + objId, t);
		}
		return null;
	}

	public void deleteReferencedObjects(XXDBBase obj) {

		if (obj == null) {
			return;
		}
		if (obj.getMyClassType() == XAConstants.CLASS_TYPE_NONE) {
			return;
		}

	}

	/**
	 * @param obj
	 */
	void deleteObjects(List<XXDBBase> objs) {

	}

	void deleteObject(XXDBBase obj) {
		AbstractBaseResourceService<?, ?> myService = AbstractBaseResourceService
				.getService(obj.getMyClassType());
		if (myService != null) {
			myService.deleteResource(obj.getId());
		} else {
			logger.error("Service not found for obj=" + obj, new Throwable());
		}
	}

	public <T extends XXDBBase> Class<? extends XXDBBase> getContextObject(
			int objectClassType, Long objectId) {
		return null;
	}

	public VXStringList mapStringListToVStringList(List<String> stringList) {
		if (stringList == null) {
			return null;
		}

		List<VXString> vStringList = new ArrayList<VXString>();
		for (String str : stringList) {
			VXString vXString = new VXString();
			vXString.setValue(str);
			vStringList.add(vXString);
		}

		return new VXStringList(vStringList);
	}

	/**
	 * return response object if users is having permission on given resource
	 * 
	 * @param vXResource
	 * @param permission
	 * @return
	 */
	public VXResponse hasPermission(VXResource vXResource, int permission) {

		VXResponse vXResponse = new VXResponse();
		if (!enableResourceAccessControl) {
			logger.debug("Resource Access Control is disabled !!!");
			return vXResponse;
		}

		if (vXResource == null) {
			vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
			vXResponse.setMsgDesc("Please provide valid policy.");
			return vXResponse;
		}

		String resourceNames = vXResource.getName();
		if (stringUtil.isEmpty(resourceNames)) {
			vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
			vXResponse.setMsgDesc("Please provide valid policy.");
			return vXResponse;
		}

		if (isAdmin()) {
			return vXResponse;
		}

		Long xUserId = getXUserId();
		Long assetId = vXResource.getAssetId();
		List<XXResource> xResourceList = daoManager.getXXResource()
				.findByAssetIdAndResourceStatus(assetId,
						AppConstants.STATUS_ENABLED);

		XXAsset xAsset = daoManager.getXXAsset().getById(assetId);
		int assetType = xAsset.getAssetType();

		vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
		vXResponse.setMsgDesc("Permission Denied !");

		if (assetType == AppConstants.ASSET_HIVE) {
			String[] requestResNameList = resourceNames.split(",");
			if (stringUtil.isEmpty(vXResource.getUdfs())) {
				int reqTableType = vXResource.getTableType();
				int reqColumnType = vXResource.getColumnType();
				for (String resourceName : requestResNameList) {
					boolean matchFound = matchHivePolicy(resourceName,
							xResourceList, xUserId, permission, reqTableType,
							reqColumnType, false);
					if (!matchFound) {
						vXResponse.setMsgDesc("You're not permitted to perform "
								+ "the action for resource path : "
								+ resourceName);
						vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
						return vXResponse;
					}
				}
			} else {
				for (String resourceName : requestResNameList) {
					boolean matchFound = matchHivePolicy(resourceName,
							xResourceList, xUserId, permission);
					if (!matchFound) {
						vXResponse.setMsgDesc("You're not permitted to perform "
								+ "the action for resource path : "
								+ resourceName);
						vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
						return vXResponse;
					}
				}
			}
			vXResponse.setStatusCode(VXResponse.STATUS_SUCCESS);
			return vXResponse;
		} else if (assetType == AppConstants.ASSET_HBASE) {
			String[] requestResNameList = resourceNames.split(",");
			for (String resourceName : requestResNameList) {
				boolean matchFound = matchHbasePolicy(resourceName,
						xResourceList, vXResponse, xUserId, permission);
				if (!matchFound) {
					vXResponse.setMsgDesc("You're not permitted to perform "
							+ "the action for resource path : " + resourceName);
					vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
					return vXResponse;
				}
			}
			vXResponse.setStatusCode(VXResponse.STATUS_SUCCESS);
			return vXResponse;
		} else if (assetType == AppConstants.ASSET_HDFS) {
			String[] requestResNameList = resourceNames.split(",");
			for (String resourceName : requestResNameList) {
				boolean matchFound = matchHdfsPolicy(resourceName,
						xResourceList, xUserId, permission);
				if (!matchFound) {
					vXResponse.setMsgDesc("You're not permitted to perform "
							+ "the action for resource path : " + resourceName);
					vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
					return vXResponse;
				}
			}
			vXResponse.setStatusCode(VXResponse.STATUS_SUCCESS);
			return vXResponse;
		} else if (assetType == AppConstants.ASSET_KNOX) {
				String[] requestResNameList = resourceNames.split(",");
				for (String resourceName : requestResNameList) {
					boolean matchFound = matchKnoxPolicy(resourceName,
							xResourceList, vXResponse, xUserId, permission);
					if (!matchFound) {
						vXResponse.setMsgDesc("You're not permitted to perform "
								+ "the action for resource path : " + resourceName);
						vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
						return vXResponse;
					}
				}
				vXResponse.setStatusCode(VXResponse.STATUS_SUCCESS);
				return vXResponse;	
        } else if (assetType == AppConstants.ASSET_STORM) {
            String[] requestResNameList = resourceNames.split(",");
            for (String resourceName : requestResNameList) {
                boolean matchFound = matchStormPolicy(resourceName,
                        xResourceList, vXResponse, xUserId, permission);
                if (!matchFound) {
                    vXResponse.setMsgDesc("You're not permitted to perform "
                            + "the action for resource path : " + resourceName);
                    vXResponse.setStatusCode(VXResponse.STATUS_ERROR);
                    return vXResponse;
                }
            }
            vXResponse.setStatusCode(VXResponse.STATUS_SUCCESS);
            return vXResponse;
        }
		return vXResponse;
	}

	/**
	 * return true id current logged in session is owned by admin
	 * 
	 * @return
	 */
	public boolean isAdmin() {
		UserSessionBase currentUserSession = ContextUtil
				.getCurrentUserSession();
		if (currentUserSession == null) {
			logger.debug("Unable to find session.");
			return false;
		}

		if (currentUserSession.isUserAdmin()) {
			return true;
		}
		return false;
	}

	/**
	 * returns current user's userID from active user sessions
	 * 
	 * @return
	 */
	public Long getXUserId() {

		UserSessionBase currentUserSession = ContextUtil
				.getCurrentUserSession();
		if (currentUserSession == null) {
			logger.debug("Unable to find session.");
			return null;
		}

		XXPortalUser user = daoManager.getXXPortalUser().getById(
				currentUserSession.getUserId());
		if (user == null) {
			logger.debug("XXPortalUser not found with logged in user id : "
					+ currentUserSession.getUserId());
			return null;
		}

		XXUser xUser = daoManager.getXXUser().findByUserName(user.getLoginId());
		if (xUser == null) {
			logger.debug("XXPortalUser not found for user id :" + user.getId()
					+ " with name " + user.getFirstName());
			return null;
		}

		return xUser.getId();
	}

	/**
	 * returns true if user is having required permission on given Hdfs resource
	 * 
	 * @param resourceName
	 * @param xResourceList
	 * @param xUserId
	 * @param permission
	 * @return
	 */
	private boolean matchHdfsPolicy(String resourceName,
			List<XXResource> xResourceList, Long xUserId, int permission) {
		boolean matchFound = false;
		resourceName = replaceMetaChars(resourceName);

		for (XXResource xResource : xResourceList) {
			if (xResource.getResourceStatus() != AppConstants.STATUS_ENABLED) {
				continue;
			}
			Long resourceId = xResource.getId();
			matchFound = checkUsrPermForPolicy(xUserId, permission, resourceId);
			if (matchFound) {
				matchFound = false;
				String resource = xResource.getName();
				String[] dbResourceNameList = resource.split(",");
				for (String dbResourceName : dbResourceNameList) {
					if (comparePathsForExactMatch(resourceName, dbResourceName)) {
						matchFound = true;
					} else {
						if (xResource.getIsRecursive() == AppConstants.BOOL_TRUE) {
							matchFound = isRecursiveWildCardMatch(resourceName,
									dbResourceName);
						} else {
							matchFound = nonRecursiveWildCardMatch(
									resourceName, dbResourceName);
						}
					}
					if (matchFound) {
						break;
					}
				}
				if (matchFound) {
					break;
				}
			}
		}
		return matchFound;
	}

	/**
	 * returns true if user is having required permission on given Hbase
	 * resource
	 * 
	 * @param resourceName
	 * @param xResourceList
	 * @param vXResponse
	 * @param xUserId
	 * @param permission
	 * @return
	 */
	public boolean matchHbasePolicy(String resourceName,
			List<XXResource> xResourceList, VXResponse vXResponse, Long xUserId,
			int permission) {
		if(stringUtil.isEmpty(resourceName)){
			return false;
		}
		if(xResourceList==null){
			return false;
		}
		if(xUserId==null){
			return false;
		}		
		String[] splittedResources = stringUtil.split(resourceName,
				File.separator);
		int numberOfResources = splittedResources.length;
		if (numberOfResources < 1 || numberOfResources > 3) {
			logger.debug("Invalid policy name : " + resourceName);
			return false;
		}

		boolean policyMatched = false;
		// check all resources whether Hbase policy is enabled in any resource
		// of provided resource list
		for (XXResource xResource : xResourceList) {
			if (xResource.getResourceStatus() != AppConstants.STATUS_ENABLED) {
				continue;
			}
			Long resourceId = xResource.getId();
			boolean hasPermission = checkUsrPermForPolicy(xUserId, permission,
					resourceId);
			// if permission is enabled then load Tables,column family and
			// columns list from resource
			if (hasPermission) {
				String[] xTables = (xResource.getTables() == null || xResource
						.getTables().equalsIgnoreCase("")) ? null : stringUtil
						.split(xResource.getTables(), ",");
				String[] xColumnFamilies = (xResource.getColumnFamilies() == null || xResource
						.getColumnFamilies().equalsIgnoreCase("")) ? null
						: stringUtil.split(xResource.getColumnFamilies(), ",");
				String[] xColumns = (xResource.getColumns() == null || xResource
						.getColumns().equalsIgnoreCase("")) ? null : stringUtil
						.split(xResource.getColumns(), ",");

				boolean matchFound = false;

				for (int index = 0; index < numberOfResources; index++) {
					matchFound = false;
					// check whether given table resource matches with any
					// existing table resource
					if (index == 0) {
						if(xTables!=null){
						for (String xTable : xTables) {
							if (matchPath(splittedResources[index], xTable)) {
								matchFound = true;
								continue;
							}
						}
						}
						if(!matchFound) {
							break;
						}
					} // check whether given column family resource matches with
						// any existing column family resource
					else if (index == 1) {
						if(xColumnFamilies!=null){
						for (String xColumnFamily : xColumnFamilies) {
							if (matchPath(splittedResources[index],
									xColumnFamily)) {
								matchFound = true;
								continue;
							}
						}
						}
						if(!matchFound) {
							break;
						}
					}// check whether given column resource matches with any
						// existing column resource
					else if (index == 2) {
						if(xColumns!=null){
						for (String xColumn : xColumns) {
							if (matchPath(splittedResources[index], xColumn)) {
								matchFound = true;
								continue;
							}
						}
						}
						if(!matchFound) {
							break;
						}
					}
				}
				if (matchFound) {
					policyMatched = true;
					break;
				}
			}
		}
		return policyMatched;
	}

	public boolean matchHivePolicy(String resourceName,
			List<XXResource> xResourceList, Long xUserId, int permission) {
		return matchHivePolicy(resourceName, xResourceList, xUserId,
				permission, 0, 0, true);
	}

	/**
	 * returns true if user is having required permission on given Hive resource
	 * 
	 * @param resourceName
	 * @param xResourceList
	 * @param xUserId
	 * @param permission
	 * @param reqTableType
	 * @param reqColumnType
	 * @param isUdfPolicy
	 * @return
	 */
	public boolean matchHivePolicy(String resourceName,
			List<XXResource> xResourceList, Long xUserId, int permission,
			int reqTableType, int reqColumnType, boolean isUdfPolicy) {
		if(stringUtil.isEmpty(resourceName)){
			return false;
		}
		if(xResourceList==null){
			return false;
		}
		if(xUserId==null){
			return false;
		}
		String[] splittedResources = stringUtil.split(resourceName,
				File.separator);// get list of resources
		int numberOfResources = splittedResources.length;
		if (numberOfResources < 1 || numberOfResources > 3) {
			logger.debug("Invalid policy name : " + resourceName);
			return false;
		}

		boolean policyMatched = false;
		for (XXResource xResource : xResourceList) {
			if (xResource.getResourceStatus() != AppConstants.STATUS_ENABLED) {
				continue;
			}
			Long resourceId = xResource.getId();
			boolean hasPermission = checkUsrPermForPolicy(xUserId, permission,
					resourceId);

			if (hasPermission) {
				// get database list from resource list
				String[] xDatabases = stringUtil.split(
						xResource.getDatabases(), ",");
				// get table list from resource list
				String[] xTables = (xResource.getTables() == null || xResource
						.getTables().equalsIgnoreCase("")) ? null : stringUtil
						.split(xResource.getTables(), ",");
				// get UDF list from resource list
				String[] xUdfs = (xResource.getUdfs() == null || xResource
						.getUdfs().equalsIgnoreCase("")) ? null : stringUtil
						.split(xResource.getUdfs(), ",");
				// get column list from resource list
				String[] xColumns = (xResource.getColumns() == null || xResource
						.getColumns().equalsIgnoreCase("")) ? null : stringUtil
						.split(xResource.getColumns(), ",");

				boolean matchFound = false;
				// check whether given database resource available in database
				// list
				for (String xDatabase : xDatabases) {
					if (matchPath(splittedResources[0], xDatabase)) {
						matchFound = true;
					}
				}
				if (!matchFound) {
					continue;
				}
				// check whether given UDF resource available in UDF list
				if (isUdfPolicy) {
					if (xUdfs != null) {
						for (String xUdf : xUdfs) {
							if (matchPath(splittedResources[1], xUdf)) {
								policyMatched = true;
								break;
							}
						}
					} else {
						continue;
					}
				}

				int dbTableType = xResource.getTableType();
				int dbColumnType = xResource.getColumnType();
				// true if database table type and column type is include
				boolean isXResourceInc = XABizUtil.areAllEqual(
						AppConstants.POLICY_INCLUSION, dbTableType,
						dbColumnType);
				// true if requested table type and requested column type is
				// include
				boolean isReqResourceInc = XABizUtil.areAllEqual(
						AppConstants.POLICY_INCLUSION, reqTableType,
						reqColumnType);

				if (numberOfResources < 2) {
					continue;
				}

				if (isReqResourceInc) {
					if (isXResourceInc) { // True and True
						matchFound = false;
						if(xTables!=null){
							for (String xTable : xTables) {
								if (matchPath(splittedResources[1], xTable)) {
									matchFound = true;
								}
							}
						}
						if (!matchFound) {
							continue;
						}

						if (xColumns == null) {
							policyMatched = true;
							break;
						} else {
							if (numberOfResources < 3) {
								continue;
							}
						}

						matchFound = false;
						for (String xColumn : xColumns) {
							if (matchPath(splittedResources[2], xColumn)) {
								policyMatched = true;
								break;
							}
						}
						if (!matchFound) {
							continue;
						}
					} else { // only condition 2 is true

						if (dbTableType == AppConstants.POLICY_EXCLUSION) {
							for (String xTable : xTables) {
								if (matchPath(splittedResources[1], xTable)) {
									continue;
								}
							}
						} else {
							matchFound = false;
							for (String xTable : xTables) {
								if (matchPath(splittedResources[1], xTable)) {
									matchFound = true;
								}
							}
							if (!matchFound) {
								continue;
							}
						}

						if (xColumns == null) {
							return true;
						} else {
							if (numberOfResources < 3) {
								return false;
							}
						}

						if (dbColumnType == AppConstants.POLICY_EXCLUSION) {
							for (String xColumn : xColumns) {
								if (matchPath(splittedResources[2], xColumn)) {
									continue;
								}
							}
						} else {
							matchFound = false;
							for (String xColumn : xColumns) {
								if (matchPath(splittedResources[2], xColumn)) {
									matchFound = true;
								}
							}
							if (!matchFound) {
								continue;
							}
						}
					}
				} else {
					// Only admin is allowed to create exclude policies.
					boolean isAdmin = ContextUtil.getCurrentUserSession()
							.isUserAdmin();
					return isAdmin;
				}

				// if (isXResourceInc && isReqResourceInc) { // True and True
				// matchFound = false;
				// for (String xTable : xTables) {
				// if (matchPath(splittedResources[1], xTable)) {
				// matchFound = true;
				// }
				// }
				// if (!matchFound) {
				// continue;
				// }
				//
				// if (xColumns == null) {
				// policyMatched = true;
				// break;
				// } else {
				// if (numberOfResources < 3) {
				// continue;
				// }
				// }
				//
				// matchFound = false;
				// for (String xColumn : xColumns) {
				// if (matchPath(splittedResources[2], xColumn)) {
				// policyMatched = true;
				// break;
				// }
				// }
				// if (!matchFound) {
				// continue;
				// }
				// } else if (isXResourceInc) { // only condition 1 is true
				//
				// if (reqTableType == AppConstants.POLICY_EXCLUSION) {
				// matchFound = false;
				// for (String xTable : xTables) {
				// if (xTable.equals("*")) {
				// matchFound = true;
				// }
				// }
				// if (!matchFound) {
				// continue;
				// }
				//
				// } else {
				// matchFound = false;
				// for (String xTable : xTables) {
				// if (matchPath(splittedResources[1], xTable)) {
				// matchFound = true;
				// }
				// }
				// if (!matchFound) {
				// continue;
				// }
				// }
				//
				// if (xColumns == null) {
				// policyMatched = true;
				// break;
				// } else {
				// if (numberOfResources < 3) {
				// continue;
				// }
				// }
				//
				// if (reqColumnType == AppConstants.POLICY_EXCLUSION) {
				// matchFound = false;
				// for (String xColumn : xColumns) {
				// if (xColumn.equals("*")) {
				// matchFound = true;
				// }
				// }
				// if (!matchFound) {
				// continue;
				// }
				//
				// } else {
				// matchFound = false;
				// for (String xColumn : xColumns) {
				// if (matchPath(splittedResources[2], xColumn)) {
				// matchFound = true;
				// }
				// }
				// if (!matchFound) {
				// continue;
				// }
				// }
				//
				// } else if (isReqResourceInc) { // only condition 2 is true
				//
				// if (dbTableType == AppConstants.POLICY_EXCLUSION) {
				// for (String xTable : xTables) {
				// if (matchPath(splittedResources[1], xTable)) {
				// continue;
				// }
				// }
				// } else {
				// matchFound = false;
				// for (String xTable : xTables) {
				// if (matchPath(splittedResources[1], xTable)) {
				// matchFound = true;
				// }
				// }
				// if (!matchFound) {
				// continue;
				// }
				// }
				//
				// if (xColumns == null) {
				// return true;
				// } else {
				// if (numberOfResources < 3) {
				// return false;
				// }
				// }
				//
				// if (dbColumnType == AppConstants.POLICY_EXCLUSION) {
				// for (String xColumn : xColumns) {
				// if (matchPath(splittedResources[2], xColumn)) {
				// continue;
				// }
				// }
				// } else {
				// matchFound = false;
				// for (String xColumn : xColumns) {
				// if (matchPath(splittedResources[2], xColumn)) {
				// matchFound = true;
				// }
				// }
				// if (!matchFound) {
				// continue;
				// }
				// }

				// } else { //else cases
				// if (dbTableType == AppConstants.POLICY_EXCLUSION) {
				// if (reqTableType == AppConstants.POLICY_EXCLUSION) {
				// for (String xTable : xTables) {
				// if (matchPath(splittedResources[1], xTable)) {
				// continue;
				// }
				// }
				// } else {
				// for (String xTable : xTables) {
				// if (matchPath(splittedResources[1], xTable)) {
				// continue;
				// }
				// }
				// }
				// } else {
				// if (reqTableType == AppConstants.POLICY_EXCLUSION) {
				// matchFound = false;
				// for (String xTable : xTables) {
				// if (xTable.equals("*")) {
				// matchFound = true;
				// }
				// }
				// if (!matchFound) {
				// return false;
				// }
				// } else {
				// for (String xTable : xTables) {
				// if (matchPath(splittedResources[1], xTable)) {
				// continue;
				// }
				// }
				// }
				// }
				//
				// if (xColumns == null) {
				// return true;
				// } else {
				// if (numberOfResources < 3) {
				// return false;
				// }
				// }
				//
				// if (dbColumnType == AppConstants.POLICY_EXCLUSION) {
				// if (reqColumnType == AppConstants.POLICY_EXCLUSION) {
				// for (String xColumn : xColumns) {
				// if (matchPath(splittedResources[2], xColumn)) {
				// continue;
				// }
				// }
				// } else {
				// for (String xColumn : xColumns) {
				// if (matchPath(splittedResources[2], xColumn)) {
				// continue;
				// }
				// }
				// }
				// } else {
				// if (reqColumnType == AppConstants.POLICY_EXCLUSION) {
				// matchFound = false;
				// for (String xColumn : xColumns) {
				// if (xColumn.equals("*")) {
				// matchFound = true;
				// }
				// }
				// if (!matchFound) {
				// return false;
				// }
				// } else {
				// for (String xColumn : xColumns) {
				// if (matchPath(splittedResources[2], xColumn)) {
				// continue;
				// }
				// }
				// }
				// }
				//
				// }
			}
		}
		return policyMatched;
	}
	/**
	 * returns true if user is having required permission on given Hbase
	 * resource
	 * 
	 * @param resourceName
	 * @param xResourceList
	 * @param vXResponse
	 * @param xUserId
	 * @param permission
	 * @return
	 */
	private boolean matchKnoxPolicy(String resourceName,
			List<XXResource> xResourceList, VXResponse vXResponse, Long xUserId,
			int permission) {

		String[] splittedResources = stringUtil.split(resourceName,
				File.separator);
		int numberOfResources = splittedResources.length;
		if (numberOfResources < 1 || numberOfResources > 3) {
			logger.debug("Invalid policy name : " + resourceName);
			return false;
		}

		boolean policyMatched = false;
		// check all resources whether Knox policy is enabled in any resource
		// of provided resource list
		for (XXResource xResource : xResourceList) {
			if (xResource.getResourceStatus() != AppConstants.STATUS_ENABLED) {
				continue;
			}
			Long resourceId = xResource.getId();
			boolean hasPermission = checkUsrPermForPolicy(xUserId, permission,
					resourceId);
			// if permission is enabled then load Topologies,services list from resource
			if (hasPermission) {
				String[] xTopologies = (xResource.getTopologies() == null || xResource
						.getTopologies().equalsIgnoreCase("")) ? null : stringUtil
						.split(xResource.getTopologies(), ",");
				String[] xServices = (xResource.getServices() == null || xResource
						.getServices().equalsIgnoreCase("")) ? null
						: stringUtil.split(xResource.getServices(), ",");

				boolean matchFound = false;

				for (int index = 0; index < numberOfResources; index++) {
					matchFound = false;
					// check whether given table resource matches with any
					// existing topology resource
					if (index == 0) {
						if(xTopologies!=null){
						for (String xTopology : xTopologies) {
							if (matchPath(splittedResources[index], xTopology)) {
								matchFound = true;
								continue;
							}
						}
						}
						if(!matchFound) {
							break;
						}
					} // check whether given service resource matches with
						// any existing service resource
					else if (index == 1) {
						if(xServices!=null){
						for (String xService : xServices) {
							if (matchPath(splittedResources[index],
									xService)) {
								matchFound = true;
								continue;
							}
						}
						}
						if(!matchFound) {
							break;
						}
					}
				}
				if (matchFound) {
					policyMatched = true;
					break;
				}
			}
		}
		return policyMatched;
	}

 	/**
 	 * returns true if user is having required permission on given STORM
 	 * resource
 	 * 
 	 * @param resourceName
 	 * @param xResourceList
 	 * @param vXResponse
 	 * @param xUserId
 	 * @param permission
 	 * @return
 	 */
 	private boolean matchStormPolicy(String resourceName,
 			List<XXResource> xResourceList, VXResponse vXResponse, Long xUserId,
 			int permission) {
 
 		String[] splittedResources = stringUtil.split(resourceName,
 				File.separator);
 		int numberOfResources = splittedResources.length;
 		if (numberOfResources < 1 || numberOfResources > 3) {
 			logger.debug("Invalid policy name : " + resourceName);
 			return false;
 		}
 
 		boolean policyMatched = false;
 		// check all resources whether Knox policy is enabled in any resource
 		// of provided resource list
 		for (XXResource xResource : xResourceList) {
 			if (xResource.getResourceStatus() != AppConstants.STATUS_ENABLED) {
 				continue;
 			}
 			Long resourceId = xResource.getId();
 			boolean hasPermission = checkUsrPermForPolicy(xUserId, permission,
 					resourceId);
 			// if permission is enabled then load Topologies,services list from resource
 			if (hasPermission) {
 				String[] xTopologies = (xResource.getTopologies() == null || xResource
 						.getTopologies().equalsIgnoreCase("")) ? null : stringUtil
 						.split(xResource.getTopologies(), ",");
 				/*String[] xServices = (xResource.getServices() == null || xResource
 						.getServices().equalsIgnoreCase("")) ? null
 						: stringUtil.split(xResource.getServices(), ",");*/
 
 				boolean matchFound = false;
 
 				for (int index = 0; index < numberOfResources; index++) {
 					matchFound = false;
 					// check whether given table resource matches with any
 					// existing topology resource
 					if (index == 0) {
 						if(xTopologies!=null){
 						for (String xTopology : xTopologies) {
 							if (matchPath(splittedResources[index], xTopology)) {
 								matchFound = true;
 								continue;
 							}
 						}
 						}
 					} // check whether given service resource matches with
 						// any existing service resource
 					/*else if (index == 1) {
 						if(xServices!=null){
 						for (String xService : xServices) {
 							if (matchPath(splittedResources[index],
 									xService)) {
 								matchFound = true;
 								continue;
 							}
 						}
 						}
 					}*/
 				}
 				if (matchFound) {
 					policyMatched = true;
 					break;
 				}
 			}
 		}
 		return policyMatched;
 	}

	/**
	 * returns path without meta characters
	 * 
	 * @param path
	 * @return
	 */
	public String replaceMetaChars(String path) {
		if (path == null || path.isEmpty()) {
			return path;
		}

		if (path.contains("*")) {
			String replacement = getRandomString(5, 60);
			path = path.replaceAll("\\*", replacement);
		}
		if (path.contains("?")) {
			String replacement = getRandomString(1, 1);
			path = path.replaceAll("\\?", replacement);
		}
		return path;
	}

	/**
	 * returns random String of given length range
	 * 
	 * @param minLen
	 * @param maxLen
	 * @return
	 */
	private String getRandomString(int minLen, int maxLen) {
		StringBuilder sb = new StringBuilder();
		int len = getRandomInt(minLen, maxLen);
		for (int i = 0; i < len; i++) {
			int charIdx = random.nextInt(PATH_CHAR_SET_LEN);
			sb.append(PATH_CHAR_SET[charIdx]);
		}
		return sb.toString();
	}

	/**
	 * return random integer number for given range
	 * 
	 * @param min
	 * @param max
	 * @return
	 */
	private int getRandomInt(int min, int max) {
		if (min == max) {
			return min;
		} else {
			int interval = max - min;
			int randomNum = random.nextInt();
			return ((Math.abs(randomNum) % interval) + min);
		}
	}

	/**
	 * returns true if given userID is having specified permission on specified
	 * resource
	 * 
	 * @param xUserId
	 * @param permission
	 * @param resourceId
	 * @return
	 */
	private boolean checkUsrPermForPolicy(Long xUserId, int permission,
			Long resourceId) {
		// this snippet load user groups and permission map list from DB
		List<XXGroup> userGroups = new ArrayList<XXGroup>();
		List<XXPermMap> permMapList = new ArrayList<XXPermMap>();
		userGroups = daoManager.getXXGroup().findByUserId(xUserId);
		permMapList = daoManager.getXXPermMap().findByResourceId(resourceId);
		boolean matchFound = false;
		for (XXPermMap permMap : permMapList) {
			if (permMap.getPermType() == permission) {
				// check whether permission is group permission and permission
				// is enabled for group to which user belong
				if (permMap.getPermFor() == AppConstants.XA_PERM_FOR_GROUP
						&& isGroupInList(permMap.getGroupId(), userGroups)) {
					matchFound = true;
				} // check whether permission is user permission and enabled to
					// user
				else if (permMap.getPermFor() == AppConstants.XA_PERM_FOR_USER
						&& permMap.getUserId().equals(xUserId)) {
					matchFound = true;
				}
			}
			if (matchFound) {
				break;
			}
		}
		return matchFound;
	}

	/**
	 * returns true is given group id is in given group list
	 * 
	 * @param groupId
	 * @param xGroupList
	 * @return
	 */
	public boolean isGroupInList(Long groupId, List<XXGroup> xGroupList) {
		for (XXGroup xGroup : xGroupList) {
			if (xGroup.getId().equals(groupId)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * returns true if given path matches in same level or sub directories with
	 * given wild card pattern
	 * 
	 * @param pathToCheck
	 * @param wildcardPath
	 * @return
	 */
	public boolean isRecursiveWildCardMatch(String pathToCheck,
			String wildcardPath) {
		if (pathToCheck != null) {
			if (wildcardPath != null && wildcardPath.equals(File.separator)) {
				return true;
			}
			StringBuilder sb = new StringBuilder();
			for (String p : pathToCheck.split(File.separator)) {
				sb.append(p);
				boolean matchFound = FilenameUtils.wildcardMatch(sb.toString(),
						wildcardPath);
				if (matchFound) {
					return true;
				}
				sb.append(File.separator);
			}
			sb = null;
		}
		return false;
	}

	/**
	 * return List<Integer>
	 * 
	 * List of all possible parent return type for some specific resourceType
	 * 
	 * @param resourceType
	 *            , assetType
	 * 
	 */
	public List<Integer> getResorceTypeParentHirearchy(int resourceType,
			int assetType) {
		List<Integer> resourceTypeList = new ArrayList<Integer>();

		if (assetType == AppConstants.ASSET_HDFS) {
			resourceTypeList.add(AppConstants.RESOURCE_PATH);
		} else if (assetType == AppConstants.ASSET_HIVE) {
			resourceTypeList.add(AppConstants.RESOURCE_DB);
			if (resourceType == AppConstants.RESOURCE_TABLE) {
				resourceTypeList.add(AppConstants.RESOURCE_TABLE);
			} else if (resourceType == AppConstants.RESOURCE_UDF) {
				resourceTypeList.add(AppConstants.RESOURCE_UDF);
			} else if (resourceType == AppConstants.RESOURCE_COLUMN) {
				resourceTypeList.add(AppConstants.RESOURCE_TABLE);
				resourceTypeList.add(AppConstants.RESOURCE_COLUMN);
			}
		} else if (assetType == AppConstants.ASSET_HBASE) {
			resourceTypeList.add(AppConstants.RESOURCE_TABLE);
			if (resourceType == AppConstants.RESOURCE_COL_FAM) {
				resourceTypeList.add(AppConstants.RESOURCE_COL_FAM);
			} else if (resourceType == AppConstants.RESOURCE_COLUMN) {
				resourceTypeList.add(AppConstants.RESOURCE_COL_FAM);
				resourceTypeList.add(AppConstants.RESOURCE_COLUMN);
			}
		}

		return resourceTypeList;
	}

	/**
	 * return true if both path matches exactly, wild card matching is not
	 * checked
	 * 
	 * @param path1
	 * @param path2
	 * @return
	 */
	public boolean comparePathsForExactMatch(String path1, String path2) {
		String pathSeparator = File.separator;
		if (!path1.endsWith(pathSeparator)) {
			path1 = path1.concat(pathSeparator);
		}
		if (!path2.endsWith(pathSeparator)) {
			path2 = path2.concat(pathSeparator);
		}
		return path1.equalsIgnoreCase(path2);
	}

	/**
	 * return true if both path matches at same level path, this function does
	 * not match sub directories
	 * 
	 * @param pathToCheck
	 * @param wildcardPath
	 * @return
	 */
	public boolean nonRecursiveWildCardMatch(String pathToCheck,
			String wildcardPath) {
		if (pathToCheck != null && wildcardPath != null) {

			List<String> pathToCheckArray = new ArrayList<String>();
			List<String> wildcardPathArray = new ArrayList<String>();

			for (String p : pathToCheck.split(File.separator)) {
				pathToCheckArray.add(p);
			}
			for (String w : wildcardPath.split(File.separator)) {
				wildcardPathArray.add(w);
			}

			if (pathToCheckArray.size() == wildcardPathArray.size()) {
				boolean match = false;
				for (int index = 0; index < pathToCheckArray.size(); index++) {
					match = matchPath(pathToCheckArray.get(index),
							wildcardPathArray.get(index));
					if (!match)
						return match;
				}
				return match;
			}
		}
		return false;
	}

	/**
	 * returns true if first and second path are same
	 * 
	 * @param pathToCheckFragment
	 * @param wildCardPathFragment
	 * @return
	 */
	private boolean matchPath(String pathToCheckFragment,
			String wildCardPathFragment) {
		if (pathToCheckFragment.contains("*")
				|| pathToCheckFragment.contains("?")) {
			pathToCheckFragment = replaceMetaChars(pathToCheckFragment);

			if (wildCardPathFragment.contains("*")
					|| wildCardPathFragment.contains("?")) {
				return FilenameUtils.wildcardMatch(pathToCheckFragment,
						wildCardPathFragment, IOCase.SENSITIVE);
			} else {
				return false;
			}
		} else {
			if (wildCardPathFragment.contains("*")
					|| wildCardPathFragment.contains("?")) {
				return FilenameUtils.wildcardMatch(pathToCheckFragment,
						wildCardPathFragment, IOCase.SENSITIVE);
			} else {
				return pathToCheckFragment.trim().equals(
						wildCardPathFragment.trim());
			}
		}
	}

	/**
	 * This method returns true if first parameter value is equal to others
	 * argument value passed
	 * 
	 * @param checkValue
	 * @param otherValues
	 * @return
	 */
	public static boolean areAllEqual(int checkValue, int... otherValues) {
		for (int value : otherValues) {
			if (value != checkValue) {
				return false;
			}
		}
		return true;
	}

	public void createTrxLog(List<XXTrxLog> trxLogList) {
		if (trxLogList == null) {
			return;
		}

		UserSessionBase usb = ContextUtil.getCurrentUserSession();
		Long authSessionId = null;
		if (usb != null) {
			authSessionId = ContextUtil.getCurrentUserSession().getSessionId();
		}
		Long trxId = GUIDUtil.genLong();

		for (XXTrxLog xTrxLog : trxLogList) {
			xTrxLog.setTransactionId(trxId.toString());
			if (authSessionId != null) {
				xTrxLog.setSessionId("" + authSessionId);
			}
			xTrxLog.setSessionType("Spring Authenticated Session");
			xTrxLog.setRequestId(trxId.toString());
			daoManager.getXXTrxLog().create(xTrxLog);
		}
	}
}
