/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import com.liferay.client.extension.util.spring.boot3.BaseRestController;

import java.io.IOException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONObject;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author Noor Najjar
 */
@RequestMapping("/process")
@RestController
public class AIRequestProcessorRestController extends BaseRestController {

	public AIRequestProcessorRestController(
		AIBotConfigurationClient aiBotConfigurationClient,
		AIRequestClient aiRequestClient, List<LogParser> logParsers,
		ScanClient scanClient) {

		_aiBotConfigurationClient = aiBotConfigurationClient;
		_aiRequestClient = aiRequestClient;
		_scanClient = scanClient;

		_logParsersByFormatKey = logParsers.stream(
		).collect(
			Collectors.toMap(LogParser::formatKey, Function.identity())
		);
	}

	@PostMapping
	public ResponseEntity<String> post(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam("domainId") long domainId,
			@RequestParam(value = "format", defaultValue = "apache-combined")
				String format,
			@RequestPart("file") MultipartFile file)
		throws IOException {

		String token = jwt.getTokenValue();

		Optional<JSONObject> inFlightScan =
			_scanClient.findInFlightAIRequestProcessorScan(token, domainId);

		if (inFlightScan.isPresent()) {
			throw new ConcurrentScanException(
				inFlightScan.get(
				).getLong("id"));
		}

		LogParser logParser = _logParsersByFormatKey.get(format);

		if (logParser == null) {
			throw new IllegalArgumentException(
				"Unsupported log format: " + format);
		}

		Optional<JSONObject> lastCompletedScan =
			_scanClient.findLastCompletedAIRequestProcessorScan(
				token, domainId);

		Instant lowerBound = lastCompletedScan.map(
			scan -> Instant.parse(scan.getString("requestDate"))
		).orElseGet(
			() -> Instant.now().minus(365, ChronoUnit.DAYS)
		);

		List<String> enabledAgentNames =
			_aiBotConfigurationClient.listEnabledAgentNames(token, domainId);

		Instant scanRequestDate = Instant.now();

		JSONObject scan = _scanClient.createScan(
			token, domainId, _SCAN_NAME, _SCAN_TYPE, scanRequestDate);

		long scanId = scan.getLong("id");

		try {
			AIRequestAggregator aiRequestAggregator =
				new AIRequestAggregator(enabledAgentNames);

			try (Stream<JSONObject> stream = logParser.parse(
					file.getInputStream(), lowerBound)) {

				stream.forEach(aiRequestAggregator::accept);
			}

			Map<AggregationKey, Integer> counts = aiRequestAggregator.build();

			_aiRequestClient.batchCreate(token, scanId, domainId, counts);

			_scanClient.updateScanState(token, scanId, "completed", null);

			_pruneStaleRequests(token, domainId, scanRequestDate);

			JSONObject responseBody = new JSONObject(
			).put(
				"scanId", scanId
			);

			return ResponseEntity.ok(responseBody.toString());
		}
		catch (IOException ioException) {
			_failQuietly(token, scanId, ioException);

			throw ioException;
		}
		catch (RuntimeException runtimeException) {
			_failQuietly(token, scanId, runtimeException);

			throw runtimeException;
		}
	}

	private void _failQuietly(String token, long scanId, Throwable throwable) {
		try {
			_scanClient.updateScanState(
				token, scanId, "failed", throwable.getMessage());
		}
		catch (Exception exception) {
			_log.error(
				"Unable to mark scan " + scanId + " as failed", exception);
		}
	}

	private void _pruneStaleRequests(
		String token, long domainId, Instant scanRequestDate) {

		try {
			LocalDate cutoffDate = scanRequestDate.atZone(
				ZoneOffset.UTC
			).toLocalDate(
			).minusDays(365);

			List<Long> staleIds = _aiRequestClient.findStaleRequestIds(
				token, domainId, cutoffDate);

			for (Long id : staleIds) {
				_aiRequestClient.deleteById(token, id);
			}
		}
		catch (Exception exception) {
			_log.warn(
				"Post-complete prune for domain " + domainId + " failed",
				exception);
		}
	}

	private static final String _SCAN_NAME = "aiRequestProcessor";
	private static final String _SCAN_TYPE = "incremental";

	private static final Log _log = LogFactory.getLog(
		AIRequestProcessorRestController.class);

	private final AIBotConfigurationClient _aiBotConfigurationClient;
	private final AIRequestClient _aiRequestClient;
	private final Map<String, LogParser> _logParsersByFormatKey;
	private final ScanClient _scanClient;

}