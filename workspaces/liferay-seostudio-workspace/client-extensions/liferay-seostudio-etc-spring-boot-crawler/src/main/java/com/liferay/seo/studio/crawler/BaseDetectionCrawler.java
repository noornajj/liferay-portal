/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.crawler;

import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.seo.studio.model.CrawlHit;
import com.liferay.seo.studio.service.SEOStudioService;

import java.net.URI;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Noor Najjar
 */
public abstract class BaseDetectionCrawler {

	public abstract void detect(
			long accountEntryId, List<CrawlHit> crawlHits, URI hostname,
			long seoStudioScanId)
		throws Exception;

	protected static String getPageURL(CrawlHit crawlHit) {
		String canonicalURL = crawlHit.getCanonicalURL();

		if ((canonicalURL != null) && !canonicalURL.isBlank()) {
			return canonicalURL;
		}

		String url = crawlHit.getURL();

		if ((url != null) && !url.isBlank()) {
			return url;
		}

		return null;
	}

	protected Map<String, Long> ensurePages(
			long seoStudioScanId, long accountEntryId, List<String> pageURLs)
		throws Exception {

		Map<String, Long> pageIdsByURLMap = _readPages(seoStudioScanId);

		List<String> missingPageURLs = new ArrayList<>();

		for (String pageURL : pageURLs) {
			if (!pageIdsByURLMap.containsKey(pageURL)) {
				missingPageURLs.add(pageURL);
			}
		}

		if (missingPageURLs.isEmpty()) {
			return pageIdsByURLMap;
		}

		_createPagesBatch(seoStudioScanId, accountEntryId, missingPageURLs);

		long time = System.currentTimeMillis() + _PAGE_FETCH_TIMEOUT;

		while (!pageIdsByURLMap.keySet(
				).containsAll(
					pageURLs
				)) {

			if (System.currentTimeMillis() > time) {
				if (_log.isWarnEnabled()) {
					_log.warn(
						"Timed out waiting for pages to be readable for scan " +
							seoStudioScanId);
				}

				break;
			}

			Thread.sleep(_PAGE_FETCH_POLL_INTERVAL);

			pageIdsByURLMap = _readPages(seoStudioScanId);
		}

		return pageIdsByURLMap;
	}

	protected void writeInsights(
			JSONObject definitionJSONObject, long seoStudioScanId,
			long accountEntryId, List<String> pageURLs,
			Map<String, Long> pageIdsByURLMap)
		throws Exception {

		if (ListUtil.isEmpty(pageURLs)) {
			return;
		}

		long seoStudioInsightTypeId = _createInsightTypeId(
			seoStudioScanId, accountEntryId, definitionJSONObject);

		_createScanInsightsBatch(
			seoStudioScanId, accountEntryId, seoStudioInsightTypeId, pageURLs,
			pageIdsByURLMap);

		if (_log.isInfoEnabled()) {
			_log.info(
				StringBundler.concat(
					"Wrote ", pageURLs.size(), " ",
					definitionJSONObject.getString("insightType"),
					" scan insights for insight type ",
					seoStudioInsightTypeId));
		}
	}

	private long _createInsightTypeId(
		long seoStudioScanId, long accountEntryId,
		JSONObject definitionJSONObject) {

		String externalReferenceCode =
			definitionJSONObject.getString("insightType") + ":" +
				seoStudioScanId;

		JSONObject bodyJSONObject = new JSONObject();

		bodyJSONObject.put(
			"category", definitionJSONObject.getString("category")
		).put(
			"description", definitionJSONObject.optString("description")
		).put(
			"externalReferenceCode", externalReferenceCode
		).put(
			"name", definitionJSONObject.getString("name")
		).put(
			"r_accountToSEOStudioInsightTypes_accountEntryId", accountEntryId
		).put(
			"r_seoStudioScanToSEOStudioInsightTypes_seoStudioScanId",
			seoStudioScanId
		).put(
			"whyItMatters", definitionJSONObject.optString("whyItMatters")
		);

		JSONObject responseJSONObject = new JSONObject(
			_seoStudioService.createInsightType(bodyJSONObject));

		return responseJSONObject.getLong("id");
	}

	private void _createPagesBatch(
			long seoStudioScanId, long accountEntryId, List<String> pageURLs)
		throws Exception {

		for (int i = 0; i < pageURLs.size(); i += _BATCH_SIZE) {
			List<String> chunk = pageURLs.subList(
				i, Math.min(i + _BATCH_SIZE, pageURLs.size()));

			JSONArray pagesJSONArray = new JSONArray();

			for (String pageURL : chunk) {
				pagesJSONArray.put(
					_toPageJSONObject(
						accountEntryId, pageURL, seoStudioScanId));
			}

			_seoStudioService.createPagesBatch(pagesJSONArray);
		}
	}

	private void _createScanInsightsBatch(
			long seoStudioScanId, long accountEntryId,
			long seoStudioInsightTypeId, List<String> pageURLs,
			Map<String, Long> pageIdsByURLMap)
		throws Exception {

		String detectedDate = Instant.now(
		).truncatedTo(
			ChronoUnit.SECONDS
		).toString();

		for (int i = 0; i < pageURLs.size(); i += _BATCH_SIZE) {
			List<String> chunk = pageURLs.subList(
				i, Math.min(i + _BATCH_SIZE, pageURLs.size()));

			JSONArray scanInsightsJSONArray = new JSONArray();

			for (String pageURL : chunk) {
				Long seoStudioPageId = pageIdsByURLMap.get(pageURL);

				if (seoStudioPageId == null) {
					if (_log.isWarnEnabled()) {
						_log.warn(
							"No page was found for URL " + pageURL +
								"; skipping scan insight");
					}

					continue;
				}

				scanInsightsJSONArray.put(
					_toScanInsightJSONObject(
						accountEntryId, detectedDate, seoStudioInsightTypeId,
						seoStudioPageId, seoStudioScanId));
			}

			if (scanInsightsJSONArray.length() == 0) {
				continue;
			}

			_seoStudioService.createScanInsightsBatch(scanInsightsJSONArray);
		}
	}

	private Map<String, Long> _readPages(long seoStudioScanId) {
		Map<String, Long> pageIdsByURLMap = new HashMap<>();

		int page = 1;

		while (true) {
			JSONObject pagesJSONObject = new JSONObject(
				_seoStudioService.fetchPages(seoStudioScanId, 2000, page));

			JSONArray itemsJSONArray = pagesJSONObject.optJSONArray("items");

			if ((itemsJSONArray == null) || (itemsJSONArray.length() == 0)) {
				break;
			}

			for (Object itemObject : itemsJSONArray) {
				JSONObject itemJSONObject = (JSONObject)itemObject;

				pageIdsByURLMap.put(
					itemJSONObject.getString("pageURL"),
					itemJSONObject.getLong("id"));
			}

			page++;
		}

		return pageIdsByURLMap;
	}

	private JSONObject _toPageJSONObject(
		long accountEntryId, String pageURL, long seoStudioScanId) {

		JSONObject pageJSONObject = new JSONObject();

		pageJSONObject.put(
			"pageURL", pageURL
		).put(
			"r_accountToSEOStudioPages_accountEntryId", accountEntryId
		).put(
			"r_seoStudioScanToSEOStudioPages_seoStudioScanId", seoStudioScanId
		);

		return pageJSONObject;
	}

	private JSONObject _toScanInsightJSONObject(
		long accountEntryId, String detectedDate, long seoStudioInsightTypeId,
		long seoStudioPageId, long seoStudioScanId) {

		JSONObject scanInsightJSONObject = new JSONObject();

		scanInsightJSONObject.put(
			"detectedDate", detectedDate
		).put(
			"r_accountToSEOStudioScanInsights_accountEntryId", accountEntryId
		).put(
			"r_seoStudioInsightTypeToScanInsights_seoStudioInsightTypeId",
			seoStudioInsightTypeId
		).put(
			"r_seoStudioPageToSEOStudioScanInsights_seoStudioPageId",
			seoStudioPageId
		).put(
			"r_seoStudioScanToSEOStudioScanInsights_seoStudioScanId",
			seoStudioScanId
		);

		return scanInsightJSONObject;
	}

	private static final int _BATCH_SIZE = 100;

	private static final long _PAGE_FETCH_POLL_INTERVAL = 1000;

	private static final long _PAGE_FETCH_TIMEOUT = 60000;

	private static final Log _log = LogFactory.getLog(
		BaseDetectionCrawler.class);

	@Autowired
	private SEOStudioService _seoStudioService;

}