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

package com.liferay.portal.kernel.service;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutSetPrototype;

import java.util.Locale;
import java.util.Map;

/**
 * @author Noor Najjar
 */
public interface LayoutLocalServiceHelper {

	public String getFriendlyURL(
			long groupId, boolean privateLayout, long layoutId, String name,
			String friendlyURL)
		throws PortalException;

	public String getFriendlyURL(
			long groupId, boolean privateLayout, long layoutId, String name,
			String friendlyURL, String languageId)
		throws PortalException;

	public String getFriendlyURL(String friendlyURL);

	public Map<Locale, String> getFriendlyURLMap(
			long groupId, boolean privateLayout, long layoutId, String name,
			Map<Locale, String> friendlyURLMap)
		throws PortalException;

	public int getNextPriority(
		long groupId, boolean privateLayout, long parentLayoutId,
		String sourcePrototypeLayoutUuid, int defaultPriority);

	public long getParentLayoutId(
		long groupId, boolean privateLayout, long parentLayoutId);

	public long getUniquePlid();

	public boolean hasLayoutSetPrototypeLayout(
			LayoutSetPrototype layoutSetPrototype, String layoutUuid)
		throws PortalException;

	public void validate(
			long groupId, boolean privateLayout, long layoutId,
			long parentLayoutId, String name, String type, boolean hidden,
			Map<Locale, String> friendlyURLMap, ServiceContext serviceContext)
		throws PortalException;

	public void validateFirstLayout(Layout layout) throws PortalException;

	public void validateFirstLayout(String type) throws PortalException;

	public void validateFriendlyURL(
			long groupId, boolean privateLayout, long layoutId,
			String friendlyURL)
		throws PortalException;

	public void validateFriendlyURL(
			long groupId, boolean privateLayout, long layoutId,
			String friendlyURL, String languageId)
		throws PortalException;

	public void validateFriendlyURLs(
			long groupId, boolean privateLayout, long layoutId,
			Map<Locale, String> friendlyURLMap)
		throws PortalException;

	public void validateName(String name) throws PortalException;

	public void validateName(String name, String languageId)
		throws PortalException;

	public void validateParentLayoutId(
			long groupId, boolean privateLayout, long layoutId,
			long parentLayoutId)
		throws PortalException;

}