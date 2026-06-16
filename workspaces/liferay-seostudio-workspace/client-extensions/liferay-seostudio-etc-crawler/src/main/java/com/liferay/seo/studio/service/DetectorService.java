/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.service;

import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.seo.studio.detector.BaseDetector;
import com.liferay.seo.studio.model.CrawlHit;

import java.net.URI;

import java.util.List;

import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Brooke Dalton
 */
@Service
public class DetectorService {

	public String runDetectors(JSONObject scanJSONObject, long seoStudioScanId)
		throws Exception {

		long seoStudioDomainId = scanJSONObject.getLong(
			"r_seoStudioDomainToSEOStudioScans_seoStudioDomainId");

		String domainJSON = _seoStudioService.getDomain(seoStudioDomainId);

		if (Validator.isNull(domainJSON)) {
			return "Unable to find a domain for SEO Studio domain ID " +
				seoStudioDomainId;
		}

		List<CrawlHit> crawlHits = _seoStudioService.getCrawlHits(
			seoStudioDomainId);

		if (ListUtil.isEmpty(crawlHits)) {
			return "Unable to find crawl hits for SEO Studio domain ID " +
				seoStudioDomainId;
		}

		long accountEntryId = scanJSONObject.getLong(
			"r_accountToSEOStudioScans_accountEntryId");

		URI crawlURI = _seoStudioService.toCrawlURI(
			new JSONObject(
				domainJSON
			).getString(
				"hostname"
			));

		for (BaseDetector detector : _detectors) {
			detector.detect(
				accountEntryId, crawlHits, crawlURI, seoStudioScanId);
		}

		return null;
	}

	@Autowired
	private List<BaseDetector> _detectors;

	@Autowired
	private SEOStudioService _seoStudioService;

}