import { Component, OnInit, OnDestroy } from '@angular/core';
import { TradingPairService } from 'src/app/services/trading-pair.service';
import { Volume } from 'src/app/models/volume';
import { ExplorerAddress } from 'src/app/models/explorer-address';
import { Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { ExplorerService } from 'src/app/services/explorer.service';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { VOLUME_PERIODS } from 'src/app/models/constants';
import { NotificationService } from 'src/app/services/notification.service';
import { TradingPair } from 'src/app/models/trading-pair';

@Component({
  selector: 'app-monitor',
  templateUrl: './monitor.component.html',
  styleUrls: ['./monitor.component.css']
})

export class MonitorComponent implements OnInit, OnDestroy {
  tradingPair: TradingPair = TradingPair.XSN_BTC;
  monitorPairs = [TradingPair.XSN_BTC, TradingPair.BTC_USDT, TradingPair.LTC_BTC];
  volume: Volume;
  burnedAddress: ExplorerAddress;
  currencyPriceUSD: number;
  private routerObserver: Subscription;
  private monitorInterval;
  private intervalms = 30000;
  animations = {
    volume: false,
    burned: false
  };
  volumePeriods = VOLUME_PERIODS;
  volumePeriod = VOLUME_PERIODS.week;
  numberOfNodes = 0;
  numberOfChannels = 0;
  numberOfTrades = 0;
  menuOpened: boolean = false;

  constructor(
    private tradingPairService: TradingPairService,
    private explorerService: ExplorerService,
    private activatedRoute: ActivatedRoute,
    private router: Router,
    private notificationService: NotificationService) { }

  ngOnInit(): void {
    this.tradingPair = TradingPair.from(this.activatedRoute.snapshot.params.market);
    this.initializeInfo();
    this.monitorInterval = setInterval(() => this.initializeInfo(), this.intervalms);
    this.monitorPairs = this.monitorPairs.filter(elem => this.tradingPair !== elem);

    this.routerObserver = this.router.events.pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => {
        const newTradingPair = TradingPair.from(this.activatedRoute.snapshot.params.market);
        if (newTradingPair !== this.tradingPair) {
          this.tradingPair = newTradingPair;
          this.initializeInfo();
        }
      });
  }

  ngOnDestroy(): void {
    if (this.routerObserver !== undefined) {
      this.routerObserver.unsubscribe();
    }

    if (this.monitorInterval) {
      clearInterval(this.monitorInterval);
    }
  }

  initializeInfo() {
    this.getVolume();
    this.getBurnedAmount();
    this.getUSDPrice('xsn');
    this.getNumberOfTrades();
    this.getNodesInfo();
  }

  async getVolume() {
    this.volume = await this.tradingPairService.getVolume(this.tradingPair, this.volumePeriod.value);
    this.animations.volume = true;
  }

  // TODO: remove empty address amounts when the dex address is ready.
  async getBurnedAmount() {
    this.burnedAddress = {
      address: '',
      received: 0,
      spent: 0,
      available: 0,
    };
    this.animations.burned = true;
  }

  async getUSDPrice(currency: string) {
    if (currency === 'usdt') {
      // the explorer doesn't support usdt but its ~1 anyway
      this.currencyPriceUSD = 1;
    } else {
      const price = await this.explorerService.getUSDPrice(currency);

      if (price) {
        this.currencyPriceUSD = price.usd;
      }
    }
  }

  async getNumberOfTrades() {
    const response = await this.tradingPairService.getNumberOfTrades(this.tradingPair, this.volumePeriod.value);
    if (response.error) {
      this.notificationService.error(response.error);
      this.numberOfTrades = 0;

    } else {
      this.numberOfTrades = response.trades;
    }
  }

  async getNodesInfo() {
    const response = await this.tradingPairService.getNodesInfo(this.tradingPair);
    if (response.error) {
      this.notificationService.error(response.error);
      this.numberOfNodes = 0;
      this.numberOfChannels = 0;
    } else {
      this.numberOfNodes = response.nodes;
      this.numberOfChannels = response.channels;
    }
  }

  calculateBurnedAmount() {
    if (this.currencyPriceUSD && this.burnedAddress) {
      return this.toCurrencyFormat(this.burnedAddress.available * this.currencyPriceUSD);
    } else {
      return 'UNAVAILABLE';
    }
  }

  toCurrencyFormat(amount: number, fixed: number = 2): string {
    return amount.toFixed(fixed).replace(/\d(?=(\d{3})+\.)/g, '$&,');
  }

  onPeriodChanged(periodSelected) {
    this.volumePeriod = periodSelected;
    this.getVolume();
    this.getNumberOfTrades();
    this.getNodesInfo();
  }

  onClickTradingPair() {
    this.menuOpened = !this.menuOpened;
  }
}
