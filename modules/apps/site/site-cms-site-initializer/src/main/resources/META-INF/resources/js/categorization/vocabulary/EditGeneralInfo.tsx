/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {Provider, LanguagePicker} from '@clayui/core';
import ClayForm, {ClayCheckbox, ClayInput, ClaySelectWithOption, ClayToggle} from '@clayui/form';
import ClayIcon from '@clayui/icon';
import React, {useState} from 'react';

const VISIBILITY_OPTIONS = [
    Liferay.Language.get('public'),
    Liferay.Language.get('private'),
].map((label) => ({
    label,
    value: label,
}));

export default function EditGeneralInfo () {

    const [isChecked, setIsChecked] = useState(true);
    const [toggled, setToggle] = useState(true);

    const locales = [
        {
            displayName: 'English (United States)',
            id: 'en_US',
            label: 'en-US',
            symbol: 'us-us',
        },
        {
            displayName: 'Español (España)',
            id: 'es_ES',
            label: 'es-ES',
            symbol: 'es-es',
        },
    ];

    return (
        <div className="vertical-nav-content-wrapper">
            <ClayForm.Group className="c-gap-4 d-flex flex-column p-4">
                <div>
                    <div className="form-title">
                        {Liferay.Language.get('basic-info')}
                    </div>

                    <Provider spritemap="/o/admin-theme/images/clay/icons.svg">
                        <div className="p-4" style={{width: 'fit-content'}}>
                            <LanguagePicker
                                locales={locales}
                                small
                            />
                        </div>
                    </Provider>
                </div>

                <div>
                    <label>
                        {Liferay.Language.get('name')}
                        <ClayIcon
                            className="c-ml-1 reference-mark"
                            focusable="false"
                            role="presentation"
                            symbol="asterisk"
                        />
                    </label>
                    <ClayInput
                        id="basicInputText"
                        required
                        type="text"/>
                </div>

                <div>
                    <label>
                        {Liferay.Language.get('description')}
                    </label>
                    <ClayInput
                        component="textarea"
                        type="text"/>
                </div>

                <div>
                    <ClayToggle
                        label={Liferay.Language.get('allow-multiple-categories')}
                        onToggle={setToggle}
                        toggled={toggled}
                    />
                </div>

                <div>
                    <label>
                        {Liferay.Language.get('visibility')}
                    </label>
                    <ClaySelectWithOption
                        options={VISIBILITY_OPTIONS}
                    />
                </div>
            </ClayForm.Group>
            <ClayForm.Group className="c-gap-4 d-flex flex-column p-4">
                <div className="form-title">
                    {Liferay.Language.get('space')}
                </div>

                <div>
                    <label>
                        {Liferay.Language.get('space')}
                        <ClayIcon
                            className="c-ml-1 reference-mark"
                            focusable="false"
                            role="presentation"
                            symbol="asterisk"
                        />
                    </label>
                    <ClaySelectWithOption
                        options={[]}
                    />
                </div>

                <div>
                    <ClayCheckbox
                        checked={isChecked}
                        label={Liferay.Language.get('make-this-vocabulary-available-in-all-spaces')}
                        onChange={() => setIsChecked(!isChecked)}
                    />
                </div>
            </ClayForm.Group>
        </div>
    );
}