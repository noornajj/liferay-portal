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

package com.liferay.exportimport.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.document.library.constants.DLPortletKeys;
import com.liferay.exportimport.kernel.configuration.ExportImportConfigurationParameterMapFactoryUtil;
import com.liferay.exportimport.kernel.lar.ExportImportPathUtil;
import com.liferay.exportimport.kernel.lar.PortletDataContext;
import com.liferay.exportimport.kernel.lar.PortletDataContextFactoryUtil;
import com.liferay.exportimport.kernel.lar.StagedModelDataHandlerUtil;
import com.liferay.exportimport.kernel.lar.UserIdStrategy;
import com.liferay.exportimport.kernel.lifecycle.ExportImportLifecycleManagerUtil;
import com.liferay.exportimport.kernel.lifecycle.constants.ExportImportLifecycleConstants;
import com.liferay.exportimport.test.util.lar.BaseStagedModelDataHandlerTestCase;
import com.liferay.layout.test.util.LayoutTestUtil;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutSetPrototype;
import com.liferay.portal.kernel.model.Portlet;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.ResourcePermission;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.StagedModel;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.portlet.PortletConfigurationListener;
import com.liferay.portal.kernel.portlet.PortletPreferencesFactoryUtil;
import com.liferay.portal.kernel.service.GroupLocalServiceUtil;
import com.liferay.portal.kernel.service.PortletLocalServiceUtil;
import com.liferay.portal.kernel.service.ResourcePermissionLocalServiceUtil;
import com.liferay.portal.kernel.service.RoleLocalServiceUtil;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.util.KeyValuePair;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.Time;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.kernel.zip.ZipReader;
import com.liferay.portal.kernel.zip.ZipReaderFactoryUtil;
import com.liferay.portal.kernel.zip.ZipWriter;
import com.liferay.portal.kernel.zip.ZipWriterFactoryUtil;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portlet.documentlibrary.constants.DLConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.portlet.PortletPreferences;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author Noor Najjar
 */
@RunWith(Arquillian.class)
public class LayoutPortletExportImportTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		UserTestUtil.setUser(TestPropsValues.getUser());

		_companyId = TestPropsValues.getCompanyId();
		_globalGroup = GroupLocalServiceUtil.getCompanyGroup(_companyId);

		_ownerRole = RoleLocalServiceUtil.getRole(_companyId, RoleConstants.OWNER);
	}

	@Test
	public void testGloballyScopedPortletExportImportDoesNotOverrideGlobalSitePermissions()
		throws Exception {

		LayoutSetPrototype exportLayoutSetPrototype =
			LayoutTestUtil.addLayoutSetPrototype(RandomTestUtil.randomString());

		Group exportGroup = exportLayoutSetPrototype.getGroup();

		////////////
		initExport(exportGroup);

		Layout exportLayout = LayoutTestUtil.addTypeContentPublishedLayout(
			exportGroup, RandomTestUtil.randomString(), 0);

		addPortletToLayoutWithGlobalScope(exportLayout);

		ResourcePermissionLocalServiceUtil.addModelResourcePermissions(
			exportGroup.getCompanyId(),
			exportGroup.getGroupId(),
			TestPropsValues.getUserId(), DLConstants.RESOURCE_NAME,
			String.valueOf(exportGroup.getGroupId()), null, null);

		Map<String, List<KeyValuePair>> permissionsMap =
			portletDataContext.getPermissions();

		// Remove owner permissions from global site
		removeOwnerPermissionsFromDLHomeFolderPermissionsInGlobalSite();

		// Do export

		StagedModelDataHandlerUtil.exportStagedModel(
			portletDataContext, exportLayout);

		Group importGroup = GroupTestUtil.addGroup();

		initImport(exportGroup, importGroup);

		ResourcePermissionLocalServiceUtil.addModelResourcePermissions(
			exportGroup.getCompanyId(),
			exportGroup.getGroupId(),
			TestPropsValues.getUserId(), DLConstants.RESOURCE_NAME,
			String.valueOf(exportGroup.getGroupId()), null, null);

		portletDataContext.addPermissions(DLConstants.RESOURCE_NAME,
			exportGroup.getGroupId(), permissionsMap.get(
				DLConstants.RESOURCE_NAME +"#"+String.valueOf(exportGroup.getGroupId())));

		ExportImportLifecycleManagerUtil.fireExportImportLifecycleEvent(
			ExportImportLifecycleConstants.EVENT_LAYOUT_IMPORT_STARTED,
			ExportImportLifecycleConstants.
				PROCESS_FLAG_LAYOUT_IMPORT_IN_PROCESS,
			portletDataContext.getExportImportProcessId(),
			PortletDataContextFactoryUtil.clonePortletDataContext(
				portletDataContext));

		Layout exportedLayout = (Layout)readExportedStagedModel(exportLayout);

		StagedModelDataHandlerUtil.importStagedModel(
			portletDataContext, exportedLayout);

		ExportImportLifecycleManagerUtil.fireExportImportLifecycleEvent(
			ExportImportLifecycleConstants.EVENT_LAYOUT_IMPORT_SUCCEEDED,
			ExportImportLifecycleConstants.
				PROCESS_FLAG_LAYOUT_IMPORT_IN_PROCESS,
			portletDataContext.getExportImportProcessId(),
			PortletDataContextFactoryUtil.clonePortletDataContext(
				portletDataContext));

		// Need to verify OWNER permissions still aren't present

		_resourcePermission =
			ResourcePermissionLocalServiceUtil.getResourcePermission(
				exportLayout.getCompanyId(), DLConstants.RESOURCE_NAME,
				ResourceConstants.SCOPE_INDIVIDUAL,
				String.valueOf(_globalGroup.getGroupId()), _ownerRole.getRoleId());

		Assert.assertEquals(0, _resourcePermission.getActionIds());
		Assert.assertFalse(_resourcePermission.getViewActionId());
	}

	protected void addPortletToLayoutWithGlobalScope(Layout exportLayout)
		throws Exception {
		String portletId = LayoutTestUtil.addPortletToLayout(
			exportLayout, DLPortletKeys.DOCUMENT_LIBRARY);

		Portlet portlet = PortletLocalServiceUtil.getPortletById(portletId);

		PortletPreferences portletPreferences =
			PortletPreferencesFactoryUtil.getLayoutPortletSetup(
				exportLayout, portletId);

		portletPreferences.setValue("lfrScopeType", "company");

		portletPreferences.setValue("lfrScopeLayoutUuid", "");

		String languageId = LanguageUtil.getLanguageId(Locale.getDefault());

		String portletTitle = portletPreferences.getValue(
			"portletSetupTitle_" + languageId, StringPool.BLANK);

		String newPortletTitle = PortalUtil.getNewPortletTitle(
			portletTitle, null, "global");

		portletPreferences.setValue(
			"portletSetupTitle_" + languageId,
			newPortletTitle);

		portletPreferences.setValue(
			"portletSetupUseCustomTitle", Boolean.TRUE.toString());

		portletPreferences.store();

		PortletConfigurationListener portletConfigurationListener =
			portlet.getPortletConfigurationListenerInstance();

		if (portletConfigurationListener != null) {
			portletConfigurationListener.onUpdateScope(
				portletId, portletPreferences);
		}
	}
	protected Date getEndDate() {
		return new Date();
	}

	protected Map<String, String[]> getParameterMap() {
		return ExportImportConfigurationParameterMapFactoryUtil.
				buildParameterMap();
	}

	protected Date getStartDate() {
		return new Date(System.currentTimeMillis() - Time.HOUR);
	}

	protected void initExport(Group exportGroup) throws Exception {
		zipWriter = ZipWriterFactoryUtil.getZipWriter();

		portletDataContext =
			PortletDataContextFactoryUtil.createExportPortletDataContext(
				exportGroup.getCompanyId(), exportGroup.getGroupId(),
				getParameterMap(), getStartDate(), getEndDate(), zipWriter);

		portletDataContext.setExportImportProcessId(
			BaseStagedModelDataHandlerTestCase.class.getName());

		rootElement = SAXReaderUtil.createElement("root");

		portletDataContext.setExportDataRootElement(rootElement);

		missingReferencesElement = rootElement.addElement("missing-references");

		portletDataContext.setMissingReferencesElement(
			missingReferencesElement);

		portletDataContext.addPortletPermissions(DLConstants.RESOURCE_NAME);
	}

	protected void initImport(Group exportGroup, Group importGroup)
		throws Exception {

		userIdStrategy = new TestUserIdStrategy();

		zipReader = ZipReaderFactoryUtil.getZipReader(zipWriter.getFile());

		String xml = zipReader.getEntryAsString("/manifest.xml");

		if (xml == null) {
			Document document = SAXReaderUtil.createDocument();

			Element rootElement = document.addElement("root");

			rootElement.addElement("header");

			zipWriter.addEntry("/manifest.xml", document.asXML());

			zipReader = ZipReaderFactoryUtil.getZipReader(zipWriter.getFile());
		}

		portletDataContext =
			PortletDataContextFactoryUtil.createImportPortletDataContext(
				importGroup.getCompanyId(), importGroup.getGroupId(),
				getParameterMap(), userIdStrategy, zipReader);

		portletDataContext.setExportImportProcessId(
			BaseStagedModelDataHandlerTestCase.class.getName());
		portletDataContext.setImportDataRootElement(rootElement);

		Element missingReferencesElement = rootElement.element(
			"missing-references");

		if (missingReferencesElement == null) {
			missingReferencesElement = rootElement.addElement(
				"missing-references");
		}

		portletDataContext.setMissingReferencesElement(
			missingReferencesElement);

		Group sourceCompanyGroup = GroupLocalServiceUtil.getCompanyGroup(
			exportGroup.getCompanyId());

		portletDataContext.setSourceCompanyGroupId(
			sourceCompanyGroup.getGroupId());

		portletDataContext.setSourceCompanyId(exportGroup.getCompanyId());
		portletDataContext.setSourceGroupId(exportGroup.getGroupId());
	}

	protected StagedModel readExportedStagedModel(StagedModel stagedModel) {
		String stagedModelPath = ExportImportPathUtil.getModelPath(stagedModel);

		return (StagedModel)portletDataContext.getZipEntryAsObject(
			stagedModelPath);
	}

	protected void removeOwnerPermissionsFromDLHomeFolderPermissionsInGlobalSite()
		throws PortalException {

		long globalGroupId = _globalGroup.getGroupId();

		ResourcePermissionLocalServiceUtil.addResourcePermissions(
			_globalGroup.getCompanyId(), globalGroupId, TestPropsValues.getUserId(), DLConstants.RESOURCE_NAME,
			String.valueOf(globalGroupId), false, false, false);

		_resourcePermission =
			ResourcePermissionLocalServiceUtil.getResourcePermission(
				_globalGroup.getCompanyId(), DLConstants.RESOURCE_NAME,
				ResourceConstants.SCOPE_INDIVIDUAL,
				String.valueOf(globalGroupId), _ownerRole.getRoleId());

		_resourcePermission.setActionIds(0);
		_resourcePermission.setViewActionId(false);

		ResourcePermissionLocalServiceUtil.updateResourcePermission(_resourcePermission);

	}

	private long _companyId;
	private Group _globalGroup;

	private Role _ownerRole;

	@DeleteAfterTestRun
	private ResourcePermission _resourcePermission;

	protected Element missingReferencesElement;
	protected PortletDataContext portletDataContext;
	protected Element rootElement;
	protected UserIdStrategy userIdStrategy;
	protected ZipReader zipReader;
	protected ZipWriter zipWriter;

	protected class TestUserIdStrategy implements UserIdStrategy {

		public TestUserIdStrategy() {
			_userId = _initializeUserId();
		}

		public TestUserIdStrategy(User user) {
			_userId = user.getUserId();
		}

		@Override
		public long getUserId(String userUuid) {
			return _userId;
		}

		private long _initializeUserId() {
			try {
				return TestPropsValues.getUserId();
			}
			catch (Exception exception) {
				return 0;
			}
		}

		private final long _userId;

	}

}
