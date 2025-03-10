/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import ClayForm, {ClayCheckbox, ClayInput, ClaySelectWithOption, ClayToggle} from '@clayui/form';
import ClayIcon from '@clayui/icon';
import React, {useState} from 'react';

export default function EditPermissions () {
    return (
        <div className="vertical-nav-content-wrapper">
            <ClayForm.Group className="c-gap-4 d-flex flex-column p-4">
                <div className="form-title">
                    {Liferay.Language.get('permissions')}
                </div>

                <div>
                    <label>
                        {Liferay.Language.get('viewable-by')}
                    </label>
                    <ClaySelectWithOption
                        options={[]}
                    />
                </div>
            </ClayForm.Group>
        </div>
    );
}