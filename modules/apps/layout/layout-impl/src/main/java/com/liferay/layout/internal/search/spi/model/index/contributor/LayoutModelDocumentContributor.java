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

package com.liferay.layout.internal.search.spi.model.index.contributor;

import com.liferay.exportimport.kernel.lar.ExportImportThreadLocal;
import com.liferay.layout.internal.search.util.LayoutCrawler;
import com.liferay.layout.page.template.model.LayoutPageTemplateStructure;
import com.liferay.layout.page.template.service.LayoutPageTemplateStructureLocalService;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.search.BooleanClause;
import com.liferay.portal.kernel.search.BooleanClauseFactoryUtil;
import com.liferay.portal.kernel.search.BooleanClauseOccur;
import com.liferay.portal.kernel.search.Document;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.Hits;
import com.liferay.portal.kernel.search.Indexer;
import com.liferay.portal.kernel.search.IndexerRegistryUtil;
import com.liferay.portal.kernel.search.Query;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.search.SearchException;
import com.liferay.portal.kernel.service.GroupLocalServiceUtil;
import com.liferay.portal.kernel.service.LayoutLocalServiceUtil;
import com.liferay.portal.kernel.util.Html;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.search.spi.model.index.contributor.ModelDocumentContributor;

import java.util.Locale;
import java.util.Set;

import com.liferay.staging.StagingGroupHelper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Vagner B.C
 */
@Component(
	immediate = true,
	property = "indexer.class.name=com.liferay.portal.kernel.model.Layout",
	service = ModelDocumentContributor.class
)
public class LayoutModelDocumentContributor
	implements ModelDocumentContributor<Layout> {

	public static final String CLASS_NAME = Layout.class.getName();

	@Override
	public void contribute(Document document, Layout layout) {
		if (layout.isSystem() ||
			(layout.getStatus() != WorkflowConstants.STATUS_APPROVED)) {

			return;
		}

		document.addText(
			Field.DEFAULT_LANGUAGE_ID, layout.getDefaultLanguageId());
		document.addLocalizedText(Field.NAME, layout.getNameMap());
		document.addText(
			"privateLayout", String.valueOf(layout.isPrivateLayout()));
		document.addText(Field.TYPE, layout.getType());

		LayoutPageTemplateStructure layoutPageTemplateStructure =
			_layoutPageTemplateStructureLocalService.
				fetchLayoutPageTemplateStructure(
					layout.getGroupId(), layout.getPlid());

		for (String languageId : layout.getAvailableLanguageIds()) {
			Locale locale = LocaleUtil.fromLanguageId(languageId);

			document.addText(
				Field.getLocalizedName(locale, Field.TITLE),
				layout.getName(locale));
		}

		if (layoutPageTemplateStructure == null) {
			return;
		}

		Set<Locale> locales = LanguageUtil.getAvailableLocales(
			layout.getGroupId());

		for (Locale locale : locales) {
			String content = _html.stripHtml(
				_getWrapper(_layoutCrawler.getLayoutContent(layout, locale)));

			content = content.concat(_getStagedContent(layout, locale));

			if (Validator.isNull(content)) {
				continue;
			}

			document.addText(
				Field.getLocalizedName(locale, Field.CONTENT), content);
		}
	}

	private String _getStagedContent(Layout layout, Locale locale) {
		Group group = null;

		Group stagingGroup = null;

		try {
			group = GroupLocalServiceUtil.getGroup(layout.getGroupId());
		}
		catch (PortalException e) {
			e.printStackTrace();
		}

		if (ExportImportThreadLocal.isInitialLayoutStagingInProcess()) {
			stagingGroup = _stagingGroupHelper.fetchLiveGroup(group);
		}
		else if (group.isStagingGroup()) {
			stagingGroup = group;
		}
		else {
			return StringPool.BLANK;
		}

		layout = LayoutLocalServiceUtil.fetchLayoutByUuidAndGroupId(
			layout.getUuid(), stagingGroup.getGroupId(),
			layout.isPrivateLayout());

		SearchContext searchContext = new SearchContext();

		BooleanClause<Query> booleanClause = BooleanClauseFactoryUtil.create(
			Field.ENTRY_CLASS_PK, String.valueOf(layout.getPlid()),
			BooleanClauseOccur.MUST.getName());

		searchContext.setBooleanClauses(new BooleanClause[]

			{
				booleanClause
			});

		searchContext.setCompanyId(stagingGroup.getCompanyId());
		searchContext.setEntryClassNames(new String[]

			{
				Layout.class.getName()
			});
		searchContext.setGroupIds(new long[]

			{
				stagingGroup.getGroupId()
			});

		Indexer<Layout> indexer = IndexerRegistryUtil.getIndexer(Layout.class);

		Hits hits = null;
		try {
			hits = indexer.search(searchContext);
		}
		catch (SearchException e) {
			e.printStackTrace();
		}

		Document[] documents = hits.getDocs();


		if (documents.length != 1) {
			return StringPool.BLANK;
		}

		Document document = documents[0];

		return document.get(Field.getLocalizedName(locale, Field.CONTENT));
	}

	private String _getWrapper(String layoutContent) {
		int wrapperIndex = layoutContent.indexOf(_WRAPPER_ELEMENT);

		if (wrapperIndex == -1) {
			return layoutContent;
		}

		return layoutContent.substring(
			wrapperIndex + _WRAPPER_ELEMENT.length());
	}

	private static final String _WRAPPER_ELEMENT = "id=\"wrapper\">";

	@Reference
	private Html _html;

	@Reference
	private LayoutCrawler _layoutCrawler;

	@Reference
	private LayoutPageTemplateStructureLocalService
		_layoutPageTemplateStructureLocalService;

	@Reference
	private StagingGroupHelper _stagingGroupHelper;

}