import { Currency } from './currency';

export class TradingPair {
    static readonly XSN_BTC = new TradingPair(Currency.BTC, Currency.XSN);
    static readonly LTC_BTC = new TradingPair(Currency.BTC, Currency.LTC);
    static readonly BTC_USDT = new TradingPair(Currency.USDT, Currency.BTC);
    static readonly values = [TradingPair.XSN_BTC, TradingPair.LTC_BTC, TradingPair.BTC_USDT];

    name: string;
    formName: string;
    private constructor(public principal: Currency, public secondary: Currency) {
        this.name = secondary.name + '_' + principal.name;
        this.formName = secondary.name + '/' + principal.name;
    }

    static from(pairMaybe: string): TradingPair {
        const result = TradingPair.values.find(pair => {
            if (pair.name.toUpperCase() === pairMaybe.toUpperCase()) {
                return pair;
            }
        });
        if (result) {
            return result;
        } else {
            throw new TypeError('Invalid trading pair: ' + pairMaybe);
        }
    }

    public toString(): string {
        return this.name;
    }
}
