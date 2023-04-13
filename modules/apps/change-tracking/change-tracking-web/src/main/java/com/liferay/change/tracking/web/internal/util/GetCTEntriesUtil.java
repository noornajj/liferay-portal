/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.change.tracking.web.internal.util;

import com.liferay.change.tracking.closure.CTClosure;
import com.liferay.change.tracking.closure.CTClosureFactory;
import com.liferay.change.tracking.constants.CTConstants;
import com.liferay.change.tracking.model.CTCollection;
import com.liferay.change.tracking.model.CTEntry;
import com.liferay.change.tracking.service.CTCollectionLocalService;
import com.liferay.change.tracking.service.CTEntryLocalService;
import com.liferay.change.tracking.web.internal.display.BasePersistenceRegistry;
import com.liferay.change.tracking.web.internal.display.CTClosureUtil;
import com.liferay.change.tracking.web.internal.display.context.ViewChangesDisplayContext;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.change.tracking.sql.CTSQLModeThreadLocal;
import com.liferay.portal.kernel.dao.orm.ORMException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.model.BaseModel;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.GroupedModel;
import com.liferay.portal.kernel.model.PortletPreferences;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import org.osgi.service.component.annotations.Reference;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * @author Cheryl Tang
 */
public class GetCTEntriesUtil {
	public static JSONArray getJSONArray(long ctCollectionId, int start, int end) throws Exception {
		CTClosure ctClosure = null;

		CTCollection _ctCollection = _ctCollectionLocalService.getCTCollection(ctCollectionId);

		if (_ctCollection.getStatus() != WorkflowConstants.STATUS_APPROVED) {
			try {
				ctClosure = _ctClosureFactory.create(
					_ctCollection.getCtCollectionId());
			}
			catch (Exception exception) {
				_log.error(exception);
			}
		}

		Map<Long, Set<Long>> classNameIdClassPKsMap = new HashMap<>();
		Map<ViewChangesDisplayContext.ModelInfoKey, ViewChangesDisplayContext.ModelInfo> modelInfoMap = new HashMap<>();

		if (ctClosure == null) {
			List<CTEntry> ctEntries =
				_ctEntryLocalService.getCTCollectionCTEntries(
					_ctCollection.getCtCollectionId(), start, end, null);

			int modelKeyCounter = 1;

			for (CTEntry ctEntry : ctEntries) {
				modelInfoMap.put(
					new ViewChangesDisplayContext.ModelInfoKey(
						ctEntry.getModelClassNameId(),
						ctEntry.getModelClassPK()),
					new ViewChangesDisplayContext.ModelInfo(modelKeyCounter++));

				Set<Long> classPKs = classNameIdClassPKsMap.computeIfAbsent(
					ctEntry.getModelClassNameId(), key -> new HashSet<>());

				classPKs.add(ctEntry.getModelClassPK());
			}
		}
		else {
			int[] modelKeyCounterHolder = {1};

			Map<Long, List<Long>> rootPKsMap = ctClosure.getRootPKsMap();

			Queue<Map.Entry<Long, List<Long>>> queue = new LinkedList<>(
				rootPKsMap.entrySet());

			Map.Entry<Long, List<Long>> entry = null;

			while ((entry = queue.poll()) != null) {
				long classNameId = entry.getKey();

				Set<Long> classPKs = classNameIdClassPKsMap.computeIfAbsent(
					classNameId, key -> new HashSet<>());

				classPKs.addAll(entry.getValue());

				for (long classPK : entry.getValue()) {
					ViewChangesDisplayContext.ModelInfoKey modelInfoKey = new ViewChangesDisplayContext.ModelInfoKey(
						classNameId, classPK);

					if (!modelInfoMap.containsKey(modelInfoKey)) {
						modelInfoMap.put(
							modelInfoKey,
							new ViewChangesDisplayContext.ModelInfo(modelKeyCounterHolder[0]++));

						Map<Long, List<Long>> childPKsMap =
							ctClosure.getChildPKsMap(classNameId, classPK);

						if (!childPKsMap.isEmpty()) {
							queue.addAll(childPKsMap.entrySet());
						}
					}
				}
			}
		}

		Map<Long, String> typeNameCacheMap = new HashMap<>();

		for (Map.Entry<Long, Set<Long>> entry :
			classNameIdClassPKsMap.entrySet()) {

			_populateEntryValues(
				modelInfoMap, entry.getKey(), entry.getValue(),
				typeNameCacheMap);
		}

		if (ctClosure != null) {
			long groupClassNameId = _portal.getClassNameId(Group.class);

			for (long groupId :
				classNameIdClassPKsMap.getOrDefault(
					groupClassNameId, Collections.emptySet())) {

				_populateModelInfoGroupIds(
					ctClosure, modelInfoMap, groupClassNameId, groupId);
			}
		}

		Set<Long> rootClassNameIds = _getRootClassNameIds(ctClosure);

		JSONArray changesJSONArray = JSONFactoryUtil.createJSONArray();

		for (ViewChangesDisplayContext.ModelInfo modelInfo : modelInfoMap.values()) {
			if (modelInfo._ctEntry) {
				changesJSONArray.put(modelInfo._modelKey);
			}
		}

		return changesJSONArray;
	}

	private static Set<Long> _getRootClassNameIds(CTClosure ctClosure) {
		if (ctClosure == null) {
			return Collections.emptySet();
		}

		Set<Long> rootClassNameIds = new LinkedHashSet<>();

		for (String className : _ctConfiguration.rootDisplayClassNames()) {
			rootClassNameIds.add(_portal.getClassNameId(className));
		}

		for (String childClassName :
			_ctConfiguration.rootDisplayChildClassNames()) {

			for (long parentClassNameId :
				CTClosureUtil.getParentClassNameIds(
					ctClosure, _portal.getClassNameId(childClassName))) {

				rootClassNameIds.add(parentClassNameId);
			}
		}

		return rootClassNameIds;
	}
	private static <T extends BaseModel<T>> void _populateEntryValues(
		Map<ViewChangesDisplayContext.ModelInfoKey, ViewChangesDisplayContext.ModelInfo> modelInfoMap, long modelClassNameId,
		Set<Long> classPKs, Map<Long, String> typeNameCacheMap)
		throws Exception {

		Map<Serializable, T> baseModelMap = null;
		Map<Serializable, T> ctModelMap = null;

		Map<Serializable, CTEntry> ctEntryMap = new HashMap<>();

		for (CTEntry ctEntry :
			_ctEntryLocalService.getCTEntries(
				_ctCollection.getCtCollectionId(), modelClassNameId)) {

			ctEntryMap.put(ctEntry.getModelClassPK(), ctEntry);
		}

		for (long classPK : classPKs) {
			ViewChangesDisplayContext.ModelInfo modelInfo = modelInfoMap.get(
				new ViewChangesDisplayContext.ModelInfoKey(modelClassNameId, classPK));

			CTEntry ctEntry = ctEntryMap.get(classPK);

			if (ctEntry == null) {
				if (modelClassNameId == _portal.getClassNameId(
					PortletPreferences.class)) {

					continue;
				}

				if (baseModelMap == null) {
					baseModelMap = _basePersistenceRegistry.fetchBaseModelMap(
						modelClassNameId, classPKs);
				}

				T model = baseModelMap.get(classPK);

				if (model == null) {
					if (_log.isWarnEnabled()) {
						_log.warn(
							StringBundler.concat(
								"Missing model from production: {classPK=",
								classPK, ", modelClassNameId=",
								modelClassNameId, "}"));
					}

					continue;
				}

				modelInfo._jsonObject = JSONUtil.put(
					"hideable",
					_ctDisplayRendererRegistry.isHideable(
						model, modelClassNameId)
				).put(
					"modelClassNameId", modelClassNameId
				).put(
					"modelClassPK", classPK
				).put(
					"modelKey", modelInfo._modelKey
				).put(
					"title",
					_getTitle(
						CTConstants.CT_COLLECTION_ID_PRODUCTION,
						CTSQLModeThreadLocal.CTSQLMode.DEFAULT,
						_themeDisplay.getLocale(), model, modelClassNameId,
						typeNameCacheMap)
				);

				modelInfo._site = _isSite(model);
			}
			else {
				long ctCollectionId =
					_ctDisplayRendererRegistry.getCtCollectionId(
						_ctCollection, ctEntry);

				CTSQLModeThreadLocal.CTSQLMode ctSQLMode =
					_ctDisplayRendererRegistry.getCTSQLMode(
						ctCollectionId, ctEntry);

				T model = null;

				try {
					if ((ctCollectionId == _ctCollection.getCtCollectionId()) &&
						(ctSQLMode == CTSQLModeThreadLocal.CTSQLMode.DEFAULT)) {

						if (ctModelMap == null) {
							ctModelMap =
								_ctDisplayRendererRegistry.fetchCTModelMap(
									_ctCollection.getCtCollectionId(),
									CTSQLModeThreadLocal.CTSQLMode.DEFAULT,
									modelClassNameId, classPKs);
						}

						model = ctModelMap.get(classPK);
					}
					else {
						model = _ctDisplayRendererRegistry.fetchCTModel(
							ctCollectionId, ctSQLMode, modelClassNameId,
							classPK);
					}
				}
				catch (SystemException systemException) {
					if (systemException.getCause() instanceof ORMException) {
						if (_ctCollection.getStatus() !=
							WorkflowConstants.STATUS_EXPIRED) {

							_log.error(
								_getMissingModelMessage(
									classPK, modelClassNameId),
								systemException.getCause());
						}
						else if (_log.isDebugEnabled()) {
							_log.debug(
								_getMissingModelMessage(
									classPK, modelClassNameId),
								systemException.getCause());
						}

						continue;
					}

					throw systemException;
				}

				if (model == null) {
					if ((ctEntry.getChangeType() !=
						CTConstants.CT_CHANGE_TYPE_DELETION) &&
						_log.isWarnEnabled()) {

						_log.warn(
							_getMissingModelMessage(classPK, modelClassNameId));
					}

					continue;
				}

				Map<String, Object> modelAttributes =
					model.getModelAttributes();

				Date modifiedDate = ctEntry.getModifiedDate();

				modelInfo._ctEntry = true;

				modelInfo._jsonObject = JSONUtil.put(
					"changeType", ctEntry.getChangeType()
				).put(
					"ctEntryId", ctEntry.getCtEntryId()
				).put(
					"hideable",
					_ctDisplayRendererRegistry.isHideable(
						model, modelClassNameId)
				).put(
					"modelClassNameId", ctEntry.getModelClassNameId()
				).put(
					"modelClassPK", ctEntry.getModelClassPK()
				).put(
					"modelKey", modelInfo._modelKey
				).put(
					"modifiedTime", modifiedDate.getTime()
				).put(
					"timeDescription",
					_language.getTimeDescription(
						_httpServletRequest,
						System.currentTimeMillis() - modifiedDate.getTime(),
						true)
				).put(
					"title",
					_getTitle(
						ctCollectionId, ctSQLMode, _themeDisplay.getLocale(),
						model, modelClassNameId, typeNameCacheMap)
				).put(
					"userId", ctEntry.getUserId()
				).put(
					"workflowStatus", (Integer)modelAttributes.get("status")
				);

				if (model instanceof GroupedModel) {
					GroupedModel groupedModel = (GroupedModel)model;

					modelInfo._jsonObject.put(
						"groupId", groupedModel.getGroupId());
				}

				modelInfo._site = _isSite(model);
			}
		}
	}


	private static void _populateModelInfoGroupIds(
		CTClosure ctClosure, Map<ViewChangesDisplayContext.ModelInfoKey, ViewChangesDisplayContext.ModelInfo> modelInfoMap,
		long groupClassNameId, long groupId) {

		ViewChangesDisplayContext.ModelInfo groupModelInfo = modelInfoMap.get(
			new ViewChangesDisplayContext.ModelInfoKey(groupClassNameId, groupId));

		if (!groupModelInfo._site) {
			return;
		}

		Map<Long, List<Long>> pksMap = ctClosure.getChildPKsMap(
			groupClassNameId, groupId);

		Deque<Map.Entry<Long, ? extends Collection<Long>>> queue =
			new LinkedList<>(pksMap.entrySet());

		Map.Entry<Long, ? extends Collection<Long>> entry = null;

		while ((entry = queue.poll()) != null) {
			long classNameId = entry.getKey();

			for (long classPK : entry.getValue()) {
				ViewChangesDisplayContext.ModelInfo modelInfo = modelInfoMap.get(
					new ViewChangesDisplayContext.ModelInfoKey(classNameId, classPK));

				if (modelInfo._jsonObject != null) {
					modelInfo._jsonObject.put("groupId", groupId);
				}

				Map<Long, ? extends Collection<Long>> childPKsMap =
					ctClosure.getChildPKsMap(classNameId, classPK);

				if (!childPKsMap.isEmpty()) {
					queue.addAll(childPKsMap.entrySet());
				}
			}
		}
	}

	@Reference
	private static CTCollectionLocalService _ctCollectionLocalService;

	@Reference
	private static CTClosureFactory _ctClosureFactory;

	@Reference
	private static Portal _portal;

	@Reference
	private static Log _log;
	@Reference
	private static CTEntryLocalService _ctEntryLocalService;

	@Reference
	private BasePersistenceRegistry _basePersistenceRegistry;
}
