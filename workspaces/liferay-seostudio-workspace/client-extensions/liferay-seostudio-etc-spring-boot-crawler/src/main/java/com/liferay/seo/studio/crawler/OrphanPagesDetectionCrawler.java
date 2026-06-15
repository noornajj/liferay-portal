/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.crawler;

import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.seo.studio.model.CrawlHit;
import com.liferay.seo.studio.service.SEOStudioService;

import java.net.URI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONObject;

import org.springframework.stereotype.Component;

/**
 * @author Brooke Dalton
 */
@Component
public class OrphanPagesDetectionCrawler extends BaseDetectionCrawler {

	@Override
	public void detect(
			long accountEntryId, List<CrawlHit> crawlHits, URI hostname,
			long seoStudioScanId)
		throws Exception {

		Set<String> canonicalURLs = new LinkedHashSet<>();
		Set<String> linkedCanonicalURLs = new HashSet<>();

		for (CrawlHit crawlHit : crawlHits) {
			String canonicalURL = crawlHit.getCanonicalURL();

			if ((canonicalURL == null) || canonicalURL.isBlank()) {
				continue;
			}

			canonicalURLs.add(canonicalURL);

			for (String link : crawlHit.getLinks()) {
				if ((link != null) && !link.isBlank() &&
					!link.equals(canonicalURL)) {

					linkedCanonicalURLs.add(link);
				}
			}
		}

		List<String> orphans = new ArrayList<>();

		String seedURL = SEOStudioService.toDomainURL(hostname);

		for (String canonicalURL : canonicalURLs) {
			if (canonicalURL.equals(seedURL) ||
				linkedCanonicalURLs.contains(canonicalURL)) {

				continue;
			}

			orphans.add(canonicalURL);
		}

		if (ListUtil.isEmpty(orphans)) {
			if (_log.isInfoEnabled()) {
				_log.info(
					"No orphan pages were detected for scan " +
						seoStudioScanId);
			}

			return;
		}

		Map<String, Long> pageIdsByURLMap = ensurePages(
			seoStudioScanId, accountEntryId, orphans);

		writeInsights(
			_ORPHAN_PAGE_JSON_OBJECT, seoStudioScanId, accountEntryId, orphans,
			pageIdsByURLMap);
	}

	private static final JSONObject _ORPHAN_PAGE_JSON_OBJECT = new JSONObject(
	).put(
		"category", "linksAndURLs"
	).put(
		"classification", "informational"
	).put(
		"insightType", "orphan_page"
	).put(
		"name", "orphanPages"
	).put(
		"severity", "high"
	);

	private static final Log _log = LogFactory.getLog(
		OrphanPagesDetectionCrawler.class);

}