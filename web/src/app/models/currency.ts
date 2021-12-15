export class Currency {
    static readonly LTC = new Currency('LTC', 'assets/monitor/icon-ltc_tp.svg');
    static readonly BTC = new Currency('BTC', 'assets/monitor/icon-btc_tp.svg');
    static readonly XSN = new Currency('XSN', 'assets/monitor/icon-xsn_tp.svg');
    static readonly USDT = new Currency('USDT', 'assets/monitor/icon-usdt_tp.svg');
    static readonly values = [Currency.XSN, Currency.BTC, Currency.LTC, Currency.USDT];

    private constructor(public name: string, public urlIcon: string) { }
    toString() { return this.name; }

    from(name: string): Currency {
        return Currency.values.find(currency => {
            return currency.name.toUpperCase() === name.toUpperCase();
        });
    }
}
