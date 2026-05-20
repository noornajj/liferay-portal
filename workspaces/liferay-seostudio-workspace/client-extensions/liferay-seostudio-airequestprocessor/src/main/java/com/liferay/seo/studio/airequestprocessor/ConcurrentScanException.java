/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

/**
 * @author Noor Najjar
 */
public class ConcurrentScanException extends RuntimeException {

	public ConcurrentScanException(long conflictingScanId) {
		super(
			"Another \"aiRequestProcessor\" scan is queued or running " +
				"for this domain (scanId=" + conflictingScanId + ")");

		_conflictingScanId = conflictingScanId;
	}

	public long getConflictingScanId() {
		return _conflictingScanId;
	}

	private final long _conflictingScanId;

}