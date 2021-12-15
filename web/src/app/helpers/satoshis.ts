import { Big } from 'big.js';
import { SATOSHIS_SCALE } from '../models/constants';
import { Entry } from './grouped-orders';

export const transformSatoshis = (value: string | number) => {
    return Big(value).div(Big(SATOSHIS_SCALE));
};

export const calculateValue = (entry: Entry) => {
    return (Big(entry.amount).div(Big(SATOSHIS_SCALE))).times(Big(entry.price).div(Big(SATOSHIS_SCALE)));
};


export const calculateAmount = (entry: Entry) => {
    return (Big(entry.amount).div(Big(SATOSHIS_SCALE))).div(Big(entry.price).div(Big(SATOSHIS_SCALE)));
};
