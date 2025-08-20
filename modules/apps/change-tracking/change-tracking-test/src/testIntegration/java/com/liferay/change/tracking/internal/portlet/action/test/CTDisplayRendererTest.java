/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.change.tracking.spi.display;

import com.liferay.change.tracking.constants.CTActionKeys;
import com.liferay.change.tracking.constants.PublicationRoleConstants;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Noor Najjar
 */
public class CTDisplayRendererTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;


	@Test
	public void testPreviewCTDisplayRendererWithDisplayPageTemplate() {

		_layoutPageTemplateEntryLocalService.addLayoutPageTemplateEntry(
			null, TestPropsValues.getUserId(), _group.getGroupId(),
			displayPageLayoutPageTemplateCollection.
				getLayoutPageTemplateCollectionId(),
			null, 0, 0, RandomTestUtil.randomString(),
			LayoutPageTemplateEntryTypeConstants.DISPLAY_PAGE, 0, true, 0,
			0, 0, WorkflowConstants.STATUS_APPROVED, _serviceContext));
	}
}
