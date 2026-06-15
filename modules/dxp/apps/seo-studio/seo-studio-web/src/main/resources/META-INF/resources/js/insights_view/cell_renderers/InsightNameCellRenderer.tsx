/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import React, {useContext} from 'react';

import {InsightsViewContext} from '../InsightsViewContext';

export default function InsightNameCellRenderer({
	itemData,
	value,
}: {
	itemData: {externalReferenceCode: string};
	value: string | {key: string; name: string};
}) {
	const {selectInsight} = useContext(InsightsViewContext);

	const label = value && typeof value === 'object' ? value.name : value;

	return (
		<span
			onClick={(event) => {
				event.stopPropagation();

				selectInsight(itemData.externalReferenceCode);
			}}
			style={{cursor: 'pointer', textDecoration: 'underline'}}
		>
			{label}
		</span>
	);
}
