/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import ClayButton from '@clayui/button';
import ClayModal, {useModal} from '@clayui/modal';
import React from 'react';

import {IVocabulary} from "../types/IVocabulary";
import {sub} from "frontend-js-web";

export default function DeleteVocabularyConfirmationModal({
                                                              itemData,
                                                          }: {
    itemData: IVocabulary
                                                          }
) {
    const deleteURL: string | undefined = itemData.actions?.delete?.href;
    const {observer, onOpenChange, open} = useModal();

    const _handleDelete = (deleteURL: string | undefined, itemData: IVocabulary) => {
        const id: string | undefined = itemData.id?.toString();

        if (deleteURL && id) {
            fetch(deleteURL.replace('{id}', id), {method: 'DELETE'})
                .then((response) => {
                    if (response.ok) {
                        Liferay.Util.SessionStorage.setItem(
                            'com.liferay.site.cms.site.initializer.vocabularySuccessMessage',
                            Liferay.Language.get(
                                'your-request-completed-successfully'
                            ),
                            sessionStorage.TYPES.NECESSARY
                        );

                        window.location.reload();
                    }
                    else {
                        Liferay.Util.openToast({
                            message: Liferay.Language.get(
                                'an-unexpected-error-occurred'
                            ),
                            type: 'danger',
                        });
                    }
                })
                .catch(() => {
                    Liferay.Util.openToast({
                        message: Liferay.Language.get(
                            'an-unexpected-error-occurred'
                        ),
                        type: 'danger',
                    });
                });
        }
        else {
            Liferay.Util.openToast({
                message: Liferay.Language.get(
                    'an-unexpected-error-occurred'
                ),
                type: 'danger',
            });
        }
    }

    return <>
        (open && (
        <ClayModal observer={observer}>
            <ClayModal.Header>
                {sub(
                    Liferay.Language.get('delete-x'),
                    '"' + itemData.name + '"'
                )}
            </ClayModal.Header>

            <ClayModal.Body>
                {Liferay.Language.get('delete-vocabulary-confirmation')}
            </ClayModal.Body>

            <ClayModal.Footer
                last={
                    <ClayButton.Group spaced>
                        <ClayButton
                            displayType="secondary"
                            onClick={() => onOpenChange(false)}
                        >
                            {Liferay.Language.get('cancel')}
                        </ClayButton>

                        <ClayButton
                            onClick={async () => {
                                onOpenChange(false);
                                _handleDelete(deleteURL, itemData);
                            }}
                        >
                            {Liferay.Language.get('delete')}
                        </ClayButton>
                    </ClayButton.Group>
                }
            />
        </ClayModal>
        ))
        || <></>
    </>;
}
