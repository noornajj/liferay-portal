/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import ClayForm, {ClayCheckbox, ClayInput, ClaySelectWithOption, ClayToggle} from '@clayui/form';
import ClayIcon from '@clayui/icon';
import ClayTable from '@clayui/table';
import {ClayCheckbox} from '@clayui/form';
import ClayMultiSelect from '@clayui/multi-select';
import React, {useState} from 'react';

import {AssetType} from "../types/AssetType";

export default function EditAssociatedAssetTypes ({assetTypes,} : {
    assetTypes: AssetType[];
}) {
    const [selectedAssetTypes, setSelectedAssetTypes] = useState<AssetType[]>([]);
    const [allAssetTypesSelected, setAllAssetTypesSelected] = useState(false);

    const isChecked = (items: AssetType[], item: AssetType) => {
        return !!items.find((val) => val.value === item.value);
    };

    const onChangeAssetTypes = () => {
        setAllAssetTypesSelected(!allAssetTypesSelected);

        if (allAssetTypesSelected) {
            setSelectedAssetTypes([]);
        }
        else {
            setSelectedAssetTypes(assetTypes);
        }
    };

    const toggleItemChecked = (item: AssetType) => {
        if (!isChecked(selectedAssetTypes, item)) {
            setSelectedAssetTypes([
                ...selectedAssetTypes,
                {
                    label: item.label,
                    value: item.value,
                    restricted: false,
                }
            ]);
        }
        else {
            setSelectedAssetTypes(
                selectedAssetTypes.filter((entry) => item.value !== entry.value)
            );
        }
    };

    const setRequiredOnAssetType = (item) => {
        const updatedSelectedAssetTypes = selectedAssetTypes.map(assetType => {
            if (assetType.value === item.value) {
                return {
                    ...assetType,
                    restricted: !item.restricted,
                };
            }
            else {
                return assetType;
            }
        });

        setSelectedAssetTypes(updatedSelectedAssetTypes);
    };

    return (
        <div className="vertical-nav-content-wrapper">
            <ClayForm.Group className="c-gap-4 d-flex flex-column p-4">
                <div className="form-title">
                    {Liferay.Language.get('associated-asset-types')}
                </div>

                <div>
                    <p className="text-secondary">
                        {Liferay.Language.get('choose-the-asset-types-this-vocabulary-is-associated-with-and-whether-it-is-required')}
                    </p>
                </div>

                <div>
                    <label>
                        {Liferay.Language.get('asset-types')}
                    </label>
                    <ClayMultiSelect
                        disabled={allAssetTypesSelected}
                        items={
                            allAssetTypesSelected
                                ? []
                                : selectedAssetTypes
                        }
                    //    onItemsChange={setSelectedAssetTypes()}
                        sourceItems={assetTypes}
                    >
                        {(item: any) => (
                            <ClayMultiSelect.Item
                                key={item.value}
                                onClick={(event) => {

                                    toggleItemChecked(item);
                                }}
                                textValue={item.label}
                            >
                                <div className="autofit-row autofit-row-center">
                                    <div className="autofit-col mr-3">
                                        <ClayCheckbox
                                            aria-label={item.label}
                                            checked={isChecked(selectedAssetTypes, item)}
                                            className="invisible"
                                            onClick={(event: any) => {

                                                toggleItemChecked(item);
                                            }}
                                        />
                                    </div>

                                    <div className="autofit-col">
                                        <span>{item.label}</span>
                                    </div>
                                </div>
                            </ClayMultiSelect.Item>
                        )}
                    </ClayMultiSelect>
                </div>

                <div>
                    <ClayCheckbox
                        checked={allAssetTypesSelected}
                        label="Make this vocabulary available in all asset types, including those yet to be created"
                        onChange={onChangeAssetTypes}
                    />
                </div>

                <div>
                    <ClayTable striped>
                        <ClayTable.Head>
                            <ClayTable.Row>
                                <ClayTable.Cell className="text-secondary">
                                    {Liferay.Language.get('title')}
                                </ClayTable.Cell>

                                <ClayTable.Cell className="text-secondary">
                                    {Liferay.Language.get('required')}
                                </ClayTable.Cell>
                            </ClayTable.Row>
                        </ClayTable.Head>

                        <ClayTable.Body>
                        {!!selectedAssetTypes && (Object.keys(selectedAssetTypes).map((assetType: AssetType) => (
                            <ClayTable.Row>
                                <ClayTable.Cell>
                                    {assetType.label}
                                </ClayTable.Cell>
                                <ClayTable.Cell>
                                    <ClayToggle
                                        onToggle={setRequiredOnAssetType(assetType)}
                                        toggled={assetType.required}
                                    />
                                </ClayTable.Cell>
                            </ClayTable.Row>
                        )))}
                        </ClayTable.Body>
                    </ClayTable>
                </div>
            </ClayForm.Group>
        </div>
    );
}