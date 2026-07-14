/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.detector;

import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.seo.studio.model.CrawlHit;

import java.net.URI;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONObject;

import org.springframework.stereotype.Component;

/**
 * @author Noor Najjar
 */
@Component
public class MetadataDetector extends BaseDetector {

	@Override
	public void detect(
			long accountEntryId, List<CrawlHit> crawlHits, URI crawlURI,
			long seoStudioScanId)
		throws Exception {

		Set<String> missingMetaDescriptionPageURLs = new LinkedHashSet<>();
		Set<String> missingTitlePageURLs = new LinkedHashSet<>();

		for (CrawlHit crawlHit : crawlHits) {
			String pageURL = _getPageURL(crawlHit);

			if (pageURL == null) {
				continue;
			}

			if (Validator.isNull(crawlHit.getMetaDescription())) {
				missingMetaDescriptionPageURLs.add(pageURL);
			}

			if (Validator.isNull(crawlHit.getTitle())) {
				missingTitlePageURLs.add(pageURL);
			}
		}

		_postInsights(
			accountEntryId, _DESCRIPTION_JSON_OBJECT,
			missingMetaDescriptionPageURLs, seoStudioScanId);
		_postInsights(
			accountEntryId, _TITLE_JSON_OBJECT, missingTitlePageURLs,
			seoStudioScanId);
	}

	private String _getPageURL(CrawlHit crawlHit) {
		String canonicalURL = crawlHit.getCanonicalURL();

		if (Validator.isNotNull(canonicalURL)) {
			return canonicalURL;
		}

		return crawlHit.getURL();
	}

	private void _postInsights(
			long accountEntryId, JSONObject definitionJSONObject,
			Set<String> pageURLs, long seoStudioScanId)
		throws Exception {

		if (pageURLs.isEmpty()) {
			if (_log.isInfoEnabled()) {
				_log.info(
					StringBundler.concat(
						"No ", definitionJSONObject.getString("name"),
						" issues were detected for scan ", seoStudioScanId));
			}

			return;
		}

		List<String> affectedPageURLs = new ArrayList<>(pageURLs);

		postInsights(
			accountEntryId, definitionJSONObject,
			resolvePageIds(accountEntryId, affectedPageURLs, seoStudioScanId),
			affectedPageURLs, seoStudioScanId);
	}

	private static final JSONObject _DESCRIPTION_JSON_OBJECT = new JSONObject(
	).put(
		"category", "metadata"
	).put(
		"classification", "opportunity"
	).put(
		"description",
		StringBundler.concat(
			"One or more pages are missing a meta description or have an ",
			"empty one. Search engines use it for the results snippet; ",
			"without it they auto-generate one, reducing control over how the ",
			"page is presented and its click-through rate.")
	).put(
		"fixHint",
		StringBundler.concat(
			"Add a unique meta description of roughly 150 to 160 characters ",
			"that summarizes the page and includes its primary keywords, so ",
			"search engines show it verbatim in the results snippet.")
	).put(
		"name", "missingMetaDescription"
	).put(
		"severity", "2"
	);

	private static final JSONObject _TITLE_JSON_OBJECT = new JSONObject(
	).put(
		"category", "metadata"
	).put(
		"classification", "problem"
	).put(
		"description",
		StringBundler.concat(
			"One or more pages are missing a <title> tag or have an empty ",
			"one. The title tag is the primary label search engines show in ",
			"results and the strongest on-page signal for what a page is ",
			"about; without it, rankings and click-through suffer.")
	).put(
		"fixHint",
		StringBundler.concat(
			"Add a concise, unique <title> of roughly 50 to 60 characters ",
			"that leads with the page's primary keyword and reflects its ",
			"actual content.")
	).put(
		"name", "missingOrEmptyTitleTag"
	).put(
		"severity", "3"
	);

	private static final Log _log = LogFactory.getLog(MetadataDetector.class);

}