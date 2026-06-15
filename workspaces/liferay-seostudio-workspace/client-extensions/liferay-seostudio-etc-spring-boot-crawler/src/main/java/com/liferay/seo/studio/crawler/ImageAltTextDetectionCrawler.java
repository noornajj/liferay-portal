/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.crawler;

import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.seo.studio.model.CrawlHit;

import java.net.URI;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.stereotype.Component;

/**
 * @author Noor Najjar
 */
@Component
public class ImageAltTextDetectionCrawler extends BaseDetectionCrawler {

	@Override
	public void detect(
			long accountEntryId, List<CrawlHit> crawlHits, URI hostname,
			long seoStudioScanId)
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

		Map<String, Long> pageIdsByURLMap = ensurePages(
			seoStudioScanId, accountEntryId, new ArrayList<>(pageURLs));

		writeInsights(
			_ALT_TEXT_TOO_LONG_JSON_OBJECT, seoStudioScanId, accountEntryId,
			pageURLsByInsightTypeMap.get(
				_ALT_TEXT_TOO_LONG_JSON_OBJECT.getString("insightType")),
			pageIdsByURLMap);
		writeInsights(
			_EMPTY_ALT_ATTRIBUTES_JSON_OBJECT, seoStudioScanId, accountEntryId,
			pageURLsByInsightTypeMap.get(
				_EMPTY_ALT_ATTRIBUTES_JSON_OBJECT.getString("insightType")),
			pageIdsByURLMap);
		writeInsights(
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
			String pageURL = getPageURL(crawlHit);

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

	private static final JSONObject _ALT_TEXT_TOO_LONG_JSON_OBJECT =
		new JSONObject(
		).put(
			"category", "images"
		).put(
			"classification", "opportunity"
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
		).put(
			"severity", "low"
		);

	private static final JSONObject _EMPTY_ALT_ATTRIBUTES_JSON_OBJECT =
		new JSONObject(
		).put(
			"category", "images"
		).put(
			"classification", "opportunity"
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
		).put(
			"severity", "medium"
		);

	private static final int _MAX_ALT_LENGTH = 125;

	private static final JSONObject _MISSING_ALT_TEXT_JSON_OBJECT =
		new JSONObject(
		).put(
			"category", "images"
		).put(
			"classification", "problem"
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
		).put(
			"severity", "high"
		);

	private static final Log _log = LogFactory.getLog(
		ImageAltTextDetectionCrawler.class);

}