/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.crawler;

import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.seo.studio.model.CrawlHit;
import com.liferay.seo.studio.service.SEOStudioService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Noor Najjar
 */
@Component
public class DetectImageAltTextCrawler {

	public void detect(
			long seoStudioScanId, long accountEntryId, List<CrawlHit> crawlHits)
		throws Exception {

		Map<String, List<String>> pageURLsByInsightTypeMap =
			_collectAltInsightPages(crawlHits);

		Set<String> pageURLs = new LinkedHashSet<>();

		for (List<String> insightPageURLs : pageURLsByInsightTypeMap.values()) {
			pageURLs.addAll(insightPageURLs);
		}

		if (pageURLs.isEmpty()) {
			if (_log.isInfoEnabled()) {
				_log.info(
					"No image alt text insights were detected for scan " +
						seoStudioScanId);
			}

			return;
		}

		Map<String, Long> pageIdsByURLMap = _ensurePages(
			seoStudioScanId, accountEntryId, new ArrayList<>(pageURLs));

		_writeInsights(
			_ALT_TEXT_TOO_LONG_JSON_OBJECT, seoStudioScanId, accountEntryId,
			pageURLsByInsightTypeMap.get(
				_ALT_TEXT_TOO_LONG_JSON_OBJECT.getString("insightType")),
			pageIdsByURLMap);
		_writeInsights(
			_EMPTY_ALT_ATTRIBUTES_JSON_OBJECT, seoStudioScanId, accountEntryId,
			pageURLsByInsightTypeMap.get(
				_EMPTY_ALT_ATTRIBUTES_JSON_OBJECT.getString("insightType")),
			pageIdsByURLMap);
		_writeInsights(
			_MISSING_ALT_TEXT_JSON_OBJECT, seoStudioScanId, accountEntryId,
			pageURLsByInsightTypeMap.get(
				_MISSING_ALT_TEXT_JSON_OBJECT.getString("insightType")),
			pageIdsByURLMap);
	}

	private Map<String, List<String>> _collectAltInsightPages(
		List<CrawlHit> crawlHits) {

		Set<String> altTextTooLongPageURLs = new LinkedHashSet<>();
		Set<String> emptyAltPageURLs = new LinkedHashSet<>();
		Set<String> missingAltTextPageURLs = new LinkedHashSet<>();

		for (CrawlHit crawlHit : crawlHits) {
			String pageURL = _pageURL(crawlHit);

			if (pageURL == null) {
				continue;
			}

			JSONArray imagesJSONArray = crawlHit.getImages();

			for (int i = 0; i < imagesJSONArray.length(); i++) {
				JSONObject imageJSONObject = imagesJSONArray.getJSONObject(i);

				String alt = imageJSONObject.getString("alt");

				if (!imageJSONObject.optBoolean("altPresent")) {
					missingAltTextPageURLs.add(pageURL);
				}
				else if (alt.isBlank()) {
					emptyAltPageURLs.add(pageURL);
				}

				if (alt.length() > _MAX_ALT_LENGTH) {
					altTextTooLongPageURLs.add(pageURL);
				}
			}
		}

		return HashMapBuilder.<String, List<String>>put(
			"alt_text_too_long", new ArrayList<>(altTextTooLongPageURLs)
		).put(
			"empty_alt_attributes", new ArrayList<>(emptyAltPageURLs)
		).put(
			"missing_alt_text", new ArrayList<>(missingAltTextPageURLs)
		).build();
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
			"description", definitionJSONObject.getString("description")
		).put(
			"externalReferenceCode", externalReferenceCode
		).put(
			"name", definitionJSONObject.getString("name")
		).put(
			"r_accountToSEOStudioInsightTypes_accountEntryId", accountEntryId
		).put(
			"r_seoStudioScanToSEOStudioInsightTypes_seoStudioScanId",
			seoStudioScanId
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

	private Map<String, Long> _ensurePages(
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

	private String _pageURL(CrawlHit crawlHit) {
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

	private void _writeInsights(
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

	private static final JSONObject _ALT_TEXT_TOO_LONG_JSON_OBJECT =
		new JSONObject(
		).put(
			"category", "images"
		).put(
			"description",
			StringBundler.concat(
				"One or more alt attributes exceed 125 characters. Screen ",
				"readers truncate long alt text and excessively long ",
				"descriptions read as keyword stuffing to search engines.")
		).put(
			"insightType", "alt_text_too_long"
		).put(
			"name", "altTextTooLong"
		);

	private static final int _BATCH_SIZE = 100;

	private static final JSONObject _EMPTY_ALT_ATTRIBUTES_JSON_OBJECT =
		new JSONObject(
		).put(
			"category", "images"
		).put(
			"description",
			StringBundler.concat(
				"One or more images use alt=\"\". This is the correct setting ",
				"for purely decorative images, but it is frequently applied ",
				"to meaningful images by mistake - making them invisible to ",
				"both search engines and screen readers.")
		).put(
			"insightType", "empty_alt_attributes"
		).put(
			"name", "emptyAltAttributesOnImages"
		);

	private static final int _MAX_ALT_LENGTH = 125;

	private static final JSONObject _MISSING_ALT_TEXT_JSON_OBJECT =
		new JSONObject(
		).put(
			"category", "images"
		).put(
			"description",
			StringBundler.concat(
				"One or more <img> tags on this page have no alt attribute. ",
				"Alt text is how search engines and assistive technologies ",
				"understand image content; missing alt text hurts ",
				"image-search visibility and accessibility compliance ",
				"simultaneously.")
		).put(
			"insightType", "missing_alt_text"
		).put(
			"name", "missingAltTextOnImages"
		);

	private static final long _PAGE_FETCH_POLL_INTERVAL = 1000;

	private static final long _PAGE_FETCH_TIMEOUT = 60000;

	private static final Log _log = LogFactory.getLog(
		DetectImageAltTextCrawler.class);

	@Autowired
	private SEOStudioService _seoStudioService;

}