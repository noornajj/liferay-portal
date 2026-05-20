/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * @author Noor Najjar
 */
@Component
public class PortalClient {

	public PortalClient(
		@Value("${com.liferay.lxc.dxp.mainDomain}") String mainDomain,
		@Value("${com.liferay.lxc.dxp.server.protocol}") String serverProtocol,
		RestTemplate restTemplate) {

		_baseURL = serverProtocol + "://" + mainDomain;
		_restTemplate = restTemplate;
	}

	public void delete(String token, String path) {
		_exchange(token, path, HttpMethod.DELETE, null);
	}

	public String get(String token, String path) {
		return _exchange(token, path, HttpMethod.GET, null);
	}

	public String patch(String token, String path, String body) {
		return _exchange(token, path, HttpMethod.PATCH, body);
	}

	public String post(String token, String path, String body) {
		return _exchange(token, path, HttpMethod.POST, body);
	}

	private String _exchange(
		String token, String path, HttpMethod method, String body) {

		HttpHeaders httpHeaders = new HttpHeaders();

		httpHeaders.setBearerAuth(token);
		httpHeaders.setContentType(MediaType.APPLICATION_JSON);

		HttpEntity<String> httpEntity = new HttpEntity<>(body, httpHeaders);

		URI uri = URI.create(_baseURL + path);

		try {
			ResponseEntity<String> responseEntity = _restTemplate.exchange(
				uri, method, httpEntity, String.class);

			return responseEntity.getBody();
		}
		catch (RestClientResponseException restClientResponseException) {
			throw new PortalClientException(
				restClientResponseException.getStatusCode(),
				restClientResponseException.getResponseBodyAsString(),
				restClientResponseException);
		}
	}

	private final String _baseURL;
	private final RestTemplate _restTemplate;

}