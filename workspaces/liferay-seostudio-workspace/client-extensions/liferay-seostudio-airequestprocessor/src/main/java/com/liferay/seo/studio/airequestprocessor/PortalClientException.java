/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import org.springframework.http.HttpStatusCode;

/**
 * @author Noor Najjar
 */
public class PortalClientException extends RuntimeException {

	public PortalClientException(
		HttpStatusCode statusCode, String responseBody, Throwable cause) {

		super(
			"Portal returned " + statusCode.value() + ": " + responseBody,
			cause);

		_responseBody = responseBody;
		_statusCode = statusCode;
	}

	public String getResponseBody() {
		return _responseBody;
	}

	public HttpStatusCode getStatusCode() {
		return _statusCode;
	}

	private final String _responseBody;
	private final HttpStatusCode _statusCode;

}