import { Component, OnInit, OnDestroy, AfterViewInit } from '@angular/core';
import {
    widget,
    IChartingLibraryWidget,
    ChartingLibraryWidgetOptions,
    LanguageCode,
    ThemeName,
    IDatafeedChartApi,
    IExternalDatafeed,
    IDatafeedQuotesApi,
    VisibleTimeRange,
    SeriesStyle,
} from 'src/assets/charting_library/charting_library.min';
import { OrderbookApiService } from 'src/app/services/orderbook-api.service';
import { DataFeed } from 'src/app/helpers/api/datafeed';
import { Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { TRADING_PAIRS } from 'src/app/models/constants';
import * as moment from 'moment';

@Component({
    selector: 'app-tv-chart-container',
    templateUrl: './tv-chart-container.component.html',
    styleUrls: ['./tv-chart-container.component.css']
})

export class TvChartContainerComponent implements OnInit, AfterViewInit, OnDestroy {
    private libraryPath: ChartingLibraryWidgetOptions['library_path'] = '/assets/charting_library/';
    private chartsStorageApiVersion: ChartingLibraryWidgetOptions['charts_storage_api_version'] = '1.1';
    private fullscreen: ChartingLibraryWidgetOptions['fullscreen'] = false;
    private autosize: ChartingLibraryWidgetOptions['autosize'] = true;
    private containerId: ChartingLibraryWidgetOptions['container_id'] = 'tv_chart_container';
    private tvWidget: IChartingLibraryWidget | null = null;
    private theme: ThemeName = 'Dark';
    private datafeed: IDatafeedChartApi & IExternalDatafeed & IDatafeedQuotesApi = new DataFeed().getDataFeed(this.orderbookApiService);
    private currentTradingPair;
    private routerObserver: Subscription;
    private tradingPairs: string[] = TRADING_PAIRS;
    private disabledFeatures = ['use_localstorage_for_settings'];
    public height = '50vh';
    private dateRange: VisibleTimeRange = {
        from: moment().subtract(1, 'days').unix(),
        to: moment().unix()
    };


    constructor(
        private orderbookApiService: OrderbookApiService,
        private activatedRoute: ActivatedRoute,
        private router: Router) { }

    ngOnInit(): void {
        this.currentTradingPair = this.activatedRoute.snapshot.params.market;
        this.routerObserver = this.router.events.pipe(filter(event => event instanceof NavigationEnd))
            .subscribe(() => this.onTradingPairChanged());
        if (this.router.url.includes('graph')) {
            this.height = '100vh';
            this.disabledFeatures = ['use_localstorage_for_settings', 'header_fullscreen_button'];
        }
    }

    private onTradingPairChanged() {

        const urlMarket = this.activatedRoute.snapshot.params.market;
        if (this.tradingPairs.find(x => x === urlMarket)) {
            this.currentTradingPair = urlMarket;
            this.tvWidget.setSymbol(this.currentTradingPair.replace('_', '/'), '1H', () => { });
        }
    }

    ngAfterViewInit() {

        const widgetOptions: ChartingLibraryWidgetOptions = {
            symbol: this.currentTradingPair.replace('_', '/'),
            datafeed: this.datafeed,
            interval: '1H',
            container_id: this.containerId,
            library_path: this.libraryPath,
            locale: this.getLanguageFromURL() || 'en',
            disabled_features: this.disabledFeatures,
            enabled_features: [],
            charts_storage_api_version: this.chartsStorageApiVersion,
            fullscreen: false,
            autosize: this.autosize,
            theme: this.theme,
            studies_overrides: {}
        };


        const tvWidget = new widget(widgetOptions);
        this.tvWidget = tvWidget;

        this.tvWidget.onChartReady(() => {
            this.tvWidget.chart().setVisibleRange(this.dateRange);
            this.tvWidget.chart().setChartType(SeriesStyle.HeikenAshi);
            tvWidget.chart().onSymbolChanged().subscribe(this.currentTradingPair, () => {
                const newTradingPair = this.tvWidget.chart().symbol().replace('/', '_');

                if (this.currentTradingPair !== newTradingPair) {
                    if (this.router.url.includes('graph')) {
                        this.router.navigateByUrl(`/graph/${newTradingPair}`);
                    } else {
                        this.router.navigateByUrl(`/${newTradingPair}`);
                    }
                }
            });
        });
    }


    private getLanguageFromURL(): LanguageCode | null {
        const regex = new RegExp('[\\?&]lang=([^&#]*)');
        const results = regex.exec(location.search);

        return results === null ? null : decodeURIComponent(results[1].replace(/\+/g, ' ')) as LanguageCode;
    }

    ngOnDestroy() {

        if (this.routerObserver !== undefined) {
            this.routerObserver.unsubscribe();
        }
    }
}
