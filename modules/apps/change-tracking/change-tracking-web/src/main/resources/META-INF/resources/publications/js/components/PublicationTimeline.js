/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

import React from 'react';

import WorkflowStatusLabel from './WorkflowStatusLabel';

const PublicationTimeline = ({timelineItems}) => {
	if (timelineItems && timelineItems.length > 0) {
		return (
			<div className="publication-timeline">
				{timelineItems.map((timelineItem) => (
					<div key={timelineItem.id}>
						<div>
							{timelineItem.name}

							<WorkflowStatusLabel
								workflowStatus={timelineItem.status}
							/>

							<span className="timeline-increment-text">
								{timelineItem.date}
							</span>
						</div>

						<div>{timelineItem.description}</div>
					</div>
				))}
			</div>
		);
	}

	return (
		<div className="publication-timeline timeline">
			{Liferay.Language.get(
				'no-publications-were-found'
			)}
		</div>
	);
};

export default PublicationTimeline;
