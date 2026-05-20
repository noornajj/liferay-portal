/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * @author Noor Najjar
 */
public class PortalClientTest {

	@BeforeEach
	public void setUp() {
		_restTemplate = new RestTemplate();
		_mockRestServiceServer = MockRestServiceServer.bindTo(
			_restTemplate
		).build();
		_portalClient = new PortalClient(
			"localhost:8080", "http", _restTemplate);
	}

	@Test
	public void testDeleteSendsBearerAuth() {
		_mockRestServiceServer.expect(
			requestTo("http://localhost:8080/o/c/seostudioairequests/42")
		).andExpect(
			method(HttpMethod.DELETE)
		).andExpect(
			header("Authorization", "Bearer test-token")
		).andRespond(
			withSuccess()
		);

		_portalClient.delete("test-token", "/o/c/seostudioairequests/42");

		_mockRestServiceServer.verify();
	}

	@Test
	public void testFourXxResponseThrowsPortalClientException() {
		_mockRestServiceServer.expect(
			requestTo("http://localhost:8080/o/c/seostudioscans/999")
		).andRespond(
			withStatus(HttpStatus.NOT_FOUND).body(
				"{\"error\":\"not found\"}"
			).contentType(
				MediaType.APPLICATION_JSON
			)
		);

		PortalClientException portalClientException = assertThrows(
			PortalClientException.class,
			() -> _portalClient.get("token", "/o/c/seostudioscans/999"));

		assertEquals(
			HttpStatus.NOT_FOUND, portalClientException.getStatusCode());
		assertEquals(
			"{\"error\":\"not found\"}",
			portalClientException.getResponseBody());
	}

	@Test
	public void testGetReturnsResponseBody() {
		_mockRestServiceServer.expect(
			requestTo("http://localhost:8080/o/c/seostudioscans/1")
		).andExpect(
			method(HttpMethod.GET)
		).andExpect(
			header("Authorization", "Bearer test-token")
		).andRespond(
			withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));

		String body = _portalClient.get("test-token", "/o/c/seostudioscans/1");

		assertEquals("{\"id\":1}", body);

		_mockRestServiceServer.verify();
	}

	@Test
	public void testPatchSendsBodyAndJsonContentType() {
		_mockRestServiceServer.expect(
			requestTo("http://localhost:8080/o/c/seostudioscans/1")
		).andExpect(
			method(HttpMethod.PATCH)
		).andExpect(
			header("Content-Type", "application/json")
		).andExpect(
			content().json("{\"state\":\"completed\"}")
		).andRespond(
			withSuccess()
		);

		_portalClient.patch(
			"test-token", "/o/c/seostudioscans/1",
			"{\"state\":\"completed\"}");

		_mockRestServiceServer.verify();
	}

	@Test
	public void testPostSendsBodyAndJsonContentType() {
		_mockRestServiceServer.expect(
			requestTo("http://localhost:8080/o/c/seostudioscans")
		).andExpect(
			method(HttpMethod.POST)
		).andExpect(
			header("Content-Type", "application/json")
		).andExpect(
			content().json("{\"name\":\"aiRequestProcessor\"}")
		).andRespond(
			withSuccess(
				"{\"id\":42}", MediaType.APPLICATION_JSON));

		String body = _portalClient.post(
			"test-token", "/o/c/seostudioscans",
			"{\"name\":\"aiRequestProcessor\"}");

		assertEquals("{\"id\":42}", body);

		_mockRestServiceServer.verify();
	}

	@Test
	public void testServerErrorThrowsPortalClientException() {
		_mockRestServiceServer.expect(
			requestTo("http://localhost:8080/o/c/seostudioscans")
		).andRespond(
			withServerError()
		);

		PortalClientException portalClientException = assertThrows(
			PortalClientException.class,
			() -> _portalClient.get("token", "/o/c/seostudioscans"));

		assertEquals(
			HttpStatus.INTERNAL_SERVER_ERROR,
			portalClientException.getStatusCode());
	}

	private MockRestServiceServer _mockRestServiceServer;
	private PortalClient _portalClient;
	private RestTemplate _restTemplate;

}