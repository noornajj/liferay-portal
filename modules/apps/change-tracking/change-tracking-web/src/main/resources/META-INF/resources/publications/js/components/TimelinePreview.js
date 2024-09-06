/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import ClayAlert from '@clayui/alert';
import {fetch} from 'frontend-js-web';
import React, {useEffect, useState} from 'react';

export default function TimelinePreview({dataURL, showPreview, spritemap}) {
	const [loading, setLoading] = useState(false);
	const [state, setState] = useState({
		renderData: null,
	});

	useEffect(() => {
		setLoading(true);

		fetch(dataURL)
			.then((response) => response.json())
			.then((json) => {
				if (!json.changeType) {
					setLoading(false);
					setState({
						renderData: {
							errorMessage: Liferay.Language.get(
								'an-unexpected-error-occurred'
							),
						},
					});

					return;
				}

				const newState = {
					renderData: json,
				};

				setState((prevState) => ({...prevState, ...newState}));

				setLoading(false);
			})
			.catch(() => {
				setLoading(false);
				setState({
					renderData: {
						errorMessage: Liferay.Language.get(
							'an-unexpected-error-occurred'
						),
					},
				});
			});
	}, [dataURL]);

	if (showPreview) {
		if (
			Object.prototype.hasOwnProperty.call(
				state.renderData,
				'rightPreview'
			)
		) {
			if (state.renderData.rightPreview) {
				return (
					<div
						dangerouslySetInnerHTML={{
							__html: state.renderData.rightPreview,
						}}
					/>
				);
			}

			return (
				<ClayAlert displayType="info" spritemap={spritemap}>
					{Liferay.Language.get('content-is-empty')}
				</ClayAlert>
			);
		}
		else if (
			Object.prototype.hasOwnProperty.call(
				state.renderData,
				'rightLocalizedPreview'
			)
		) {
			if (state.renderData.rightLocalizedPreview[currentLocale.label]) {
				return (
					<div
						dangerouslySetInnerHTML={{
							__html: state.renderData.rightLocalizedPreview[
								currentLocale.label
							],
						}}
					/>
				);
			}

			return (
				<ClayAlert displayType="info" spritemap={spritemap}>
					{Liferay.Language.get('content-is-empty')}
				</ClayAlert>
			);
		}
		else if (loading) {
			return '';
		}

		return (
			<ClayAlert displayType="danger" spritemap={spritemap}>
				{Liferay.Language.get(
					'unable-to-display-content-due-to-an-unexpected-error'
				)}
			</ClayAlert>
		);
	}
	else {
		return null;
	}
}
