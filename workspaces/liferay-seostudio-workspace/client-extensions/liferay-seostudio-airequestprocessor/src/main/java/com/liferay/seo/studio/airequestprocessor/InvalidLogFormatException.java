/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import java.io.IOException;

/**
 * @author Noor Najjar
 */
public class InvalidLogFormatException extends IOException {

	public InvalidLogFormatException(String line, String reason) {
		super("Invalid log line (" + reason + "): " + line);

		_line = line;
	}

	public String getLine() {
		return _line;
	}

	private final String _line;

}