import { OrderbookApiService } from 'src/app/services/orderbook-api.service';
import {
  IDatafeedChartApi,
  LibrarySymbolInfo,
  IExternalDatafeed,
  IDatafeedQuotesApi,
  QuotesCallback,
  Bar
} from 'src/assets/charting_library/charting_library.min';
import { transformSatoshis } from 'src/app/helpers/satoshis';
import { TRADING_PAIRS } from 'src/app/models/constants';
import * as moment from 'moment';


const supportedResolutions = ['1', '3', '5', '15', '30', '60', '240', '360', '720', '1D', '1W', '1M'];
const historyProvider = {};
const limitBars = 700;


export class DataFeed {

  config = { supported_resolutions: supportedResolutions };

  getDataFeed(orderbookApiService: OrderbookApiService): IDatafeedChartApi & IExternalDatafeed & IDatafeedQuotesApi {
    return {
      onReady: cb => {
        setTimeout(() => cb(this.config), 0);

      },

      searchSymbols: (userInput, exchange, symbolType, onResultReadyCallback) => {
        onResultReadyCallback(
          TRADING_PAIRS.filter(x => x.indexOf(userInput) >= 0).map(x => {
            return {
              symbol: x,
              full_name: x,
              description: x.replace('_', '/'),
              exchange: x,
              ticker: x,
              type: 'crypto'
            };
          }));
      },
      resolveSymbol: (symbolName, onSymbolResolvedCallback, onResolveErrorCallback) => {

        const splitData = symbolName.split(/[:/]/);
        const symbolStub: LibrarySymbolInfo = {
          name: symbolName,
          full_name: symbolName,
          description: symbolName.replace('/', '_'),
          type: 'crypto',
          session: '24x7',
          timezone: 'Etc/UTC',
          ticker: symbolName,
          exchange: splitData[0],
          listed_exchange: '',
          minmov: 1,
          pricescale: 100000000,
          has_intraday: true,
          intraday_multipliers: ['1', '60'],
          supported_resolutions: supportedResolutions,
          volume_precision: 8,
          data_status: 'streaming',
          format: 'price'
        };

        if (TRADING_PAIRS.find(x => x === symbolStub.description)) {
          setTimeout(() =>
            onSymbolResolvedCallback(symbolStub), 0);

        } else {
          onResolveErrorCallback('Invalid trading pair');
        }

      },
      getBars(symbolInfo, resolution, from, to, onHistoryCallback, onErrorCallback, firstDataRequest) {
        orderbookApiService.getBarsPrices(symbolInfo.description, resolution, from, to, limitBars)
          .then(response => {
            const bars = response.barpricesList.map(el => {
              return {
                time: el.time,
                low: +transformSatoshis(el.low.value),
                high: +transformSatoshis(el.high.value),
                open: +transformSatoshis(el.open.value),
                close: +transformSatoshis(el.close.value),
                volume: el.volume
              };
            });
            if (bars.length) {
              historyProvider[symbolInfo.description] = { lastBar: bars[bars.length - 1] };
              onHistoryCallback(bars, {
                noData: false
              });
            } else {
              onHistoryCallback(bars, {
                noData: true
              });
            }
          }).catch(err => {
            onErrorCallback(err);
          });

      },

      subscribeBars: (symbolInfo, resolution, onRealtimeCallback, subscribeUID, onResetCacheNeededCallback) => {
        orderbookApiService.subscribe(symbolInfo.description);
        const stream = orderbookApiService.subscribeTradingPair(symbolInfo.description);
        stream.forEach(event => {
          const trade = event.toObject().event.ordersmatched.trade;
          const lastBar = historyProvider[symbolInfo.description].lastBar;
          const nextBarTime = this.getNextBarTime(resolution, moment(lastBar.time));

          let newLastBar: Bar;
          const tradePrice = +transformSatoshis(trade.price.value);

          if (moment(trade.executedon).valueOf() >= nextBarTime.valueOf()) {
            newLastBar = {
              time: nextBarTime.valueOf(),
              open: lastBar.close,
              high: lastBar.close,
              low: lastBar.close,
              close: tradePrice,
              volume: 1
            };
          } else {
            if (tradePrice < lastBar.low) {
              lastBar.low = tradePrice;
            } else if (tradePrice > lastBar.high) {
              lastBar.high = tradePrice;
            }

            lastBar.volume += 1;
            lastBar.close = tradePrice;
            newLastBar = lastBar;
          }

          onRealtimeCallback(newLastBar);
          historyProvider[symbolInfo.description].lastBar = newLastBar;

        });
      },
      unsubscribeBars: subscriberUID => {
        const tradingPair = subscriberUID.split('_');
      },
      getServerTime: cb => {

      },

      getQuotes: (symbols: string[], onDataCallback: QuotesCallback, onErrorCallback: (msg: string) => void): void => {

      },
      subscribeQuotes: (symbols: string[], fastSymbols: string[], onRealtimeCallback: QuotesCallback, listenerGUID: string): void => { },
      unsubscribeQuotes: (listenerGUID: string): void => { }
    };

  }

  getNextBarTime(resolution: string, time: moment.Moment): moment.Moment {
    const lastCharacter = resolution.slice(-1);
    const numResolution = resolution.length === 1 ? 1 : +resolution.slice(0, -1);
    if (lastCharacter === 'D') {
      return time.add(numResolution, 'days');
    } else if (lastCharacter === 'W') {
      return time.add(numResolution, 'weeks');
    } else if (lastCharacter === 'M') {
      return time.add(numResolution, 'months');
    } else {
      return time.add(numResolution, 'minutes');
    }
  }

}
