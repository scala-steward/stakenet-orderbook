import { Component, OnInit, AfterViewInit, OnDestroy } from '@angular/core';
import { Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { FormControl } from '@angular/forms';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import * as moment from 'moment';
import { Subscription } from 'rxjs';
import { Big } from 'big.js';

import { OrderbookService } from 'src/app/services/orderbook.service';
import { OrderComponent } from 'src/app/components/order/order.component';

import { Order, Trade } from 'src/app/models/protos/models_pb';
import { Event } from 'src/app/models/protos/api_pb';
import { SubscribeResponse } from 'src/app/models/protos/commands_pb';
import { OrderPlaced, OrdersMatched, SwapSuccess, OrderCanceled } from 'src/app/models/protos/events_pb';


import { TRADING_PAIRS } from 'src/app/models/constants';
import { GroupedOrders, Entry } from 'src/app/helpers/grouped-orders';
import { transformSatoshis, calculateValue, calculateAmount } from 'src/app/helpers/satoshis';
import { OrderbookApiService } from 'src/app/services/orderbook-api.service';
import { NotificationService } from 'src/app/services/notification.service';
import { filter } from 'rxjs/operators';
import { newOrderDetails } from 'src/app/helpers/protobuf-factory';



@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})

export class HomeComponent implements OnInit, AfterViewInit, OnDestroy {

  title = 'Orderbook';
  public tradingPairs: string[] = TRADING_PAIRS;
  public buyOrdersGrouped: GroupedOrders;
  public sellOrdersGrouped: GroupedOrders;
  public historicTradesList: Trade.AsObject[] = [];
  private myOrdersPlaced: Array<Order.AsObject> = [];
  public tradingPair: FormControl;
  private orderSides: string[] = [];
  public NUMBER_OF_ROWS = 20;
  private orderbookEventsSubscription: Subscription;
  public transformSatoshis = transformSatoshis;
  public calculateValue = calculateValue;
  public calculateAmount = calculateAmount;
  private routerObserver: Subscription;
  private openConectionObserver: Subscription;

  constructor(
    private orderbookService: OrderbookService,
    private modalService: NgbModal,
    private router: Router,
    private activatedRoute: ActivatedRoute,
    private orderbookApiService: OrderbookApiService,
    private notificationService: NotificationService) {

    this.buyOrdersGrouped = new GroupedOrders(true);
    this.sellOrdersGrouped = new GroupedOrders(false);
  }

  ngOnInit(): void {
    let urlMarket = this.activatedRoute.snapshot.params.market;
    if (!TRADING_PAIRS.find(x => x === urlMarket)) {
      urlMarket = TRADING_PAIRS[0];
      this.router.navigateByUrl(`/${urlMarket}`);
    }

    this.tradingPair = new FormControl(urlMarket);
    Object.keys(Order.OrderSide).forEach(k =>
      this.orderSides.push(k)
    );

    this.routerObserver = this.router.events.pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => {
        const newTradingPair = this.activatedRoute.snapshot.params.market;
        this.tradingPair.setValue(newTradingPair, { emitEvent: false });

        this.onTradingPairChanged({ target: { value: newTradingPair } });
      });


    this.openConectionObserver = this.orderbookService.getConectionStream().subscribe(
      _ => {
        this.requestInitialData();
      }
    );
  }

  ngAfterViewInit(): void {
    this.orderbookEventsSubscription = this.orderbookService.getStream().subscribe(
      (message) => {
        if (message.hasEvent()) {
          this.handleServerEvents(message.getEvent());
        }
      }
    );
  }

  ngOnDestroy(): void {
    if (this.orderbookEventsSubscription !== undefined) {
      this.orderbookEventsSubscription.unsubscribe();
    }

    if (this.routerObserver !== undefined) {
      this.routerObserver.unsubscribe();
    }

    if (this.openConectionObserver !== undefined) {
      this.openConectionObserver.unsubscribe();
    }
  }

  private requestInitialData() {
    this.orderbookApiService.getHistoricTrading(this.tradingPair.value).then(response => {
      this.fillHistoricTrades(response.tradesList);
    }).catch(reason => {
      this.notificationService.error(reason);
    });

    this.orderbookApiService.subscribe(this.tradingPair.value).then(response => {
      this.tradingOrdersRetrieved(response);
    }).catch(reason => {
      this.notificationService.error(reason);
    });
  }

  public handleServerEvents(event: Event.ServerEvent): void {
    const myEvent = event.toObject();
    switch (event.getValueCase()) {
      case Event.ServerEvent.ValueCase.MYMATCHEDORDERCANCELED:
        break;

      case Event.ServerEvent.ValueCase.MYORDERMATCHED:
        break;

      case Event.ServerEvent.ValueCase.NEWORDERMESSAGE:
        break;

      case Event.ServerEvent.ValueCase.ORDERCANCELED:
        this.cancelOrder(myEvent.ordercanceled);
        break;

      case Event.ServerEvent.ValueCase.ORDERPLACED:
        this.orderPlaced(myEvent.orderplaced);
        break;

      case Event.ServerEvent.ValueCase.ORDERSMATCHED:
        this.ordersMatched(myEvent.ordersmatched);
        break;

      case Event.ServerEvent.ValueCase.SWAPSUCCESS:
        this.swapSuccess(myEvent.swapsuccess);
        break;

      case Event.ServerEvent.ValueCase.VALUE_NOT_SET:
        break;
    }
  }

  public myOrdersFiltered(): Order.AsObject[] {
    return this.myOrdersPlaced.filter(
      value => value.tradingpair === this.tradingPair.value
    );
  }

  public onTradingPairChanged(event) {
    const tradingPair = event.target.value;
    this.requestInitialData();
    TRADING_PAIRS.forEach(t => {
      if (t !== tradingPair) {
        this.orderbookApiService.unsubscribe(t);
      }
    });
    this.router.navigateByUrl(`/${tradingPair}`);
  }

  public newOrderComponent() {
    this.modalService.open(OrderComponent);
  }

  /*Events Received From Server */
  private myOrderPlaced(order: Order.AsObject) {
    this.myOrdersPlaced.push(order);
  }

  private tradingOrdersRetrieved(ordersRetrieved: SubscribeResponse.AsObject) {
    this.buyOrdersGrouped.values = [];
    this.sellOrdersGrouped.values = [];
    if (ordersRetrieved.tradingpair === this.tradingPair.value) {

      this.buyOrdersGrouped.values = ordersRetrieved.summarybidsList.map((order) => {
        return new Entry(order.price.value, order.amount.value);
      });


      this.sellOrdersGrouped.values = ordersRetrieved.summaryasksList.map((order) => {
        return new Entry(order.price.value, order.amount.value);
      });
    }
  }

  private orderPlaced(orderPlaced: OrderPlaced.AsObject) {
    if (orderPlaced.order.tradingpair === this.tradingPair.value) {
      if (orderPlaced.order.side === Order.OrderSide.BUY) {
        this.buyOrdersGrouped.add(orderPlaced.order.details);
      } else {
        this.sellOrdersGrouped.add(orderPlaced.order.details);
      }
    }
  }

  private cancelOrder(value: OrderCanceled.AsObject) {
    if (value.order.side === Order.OrderSide.BUY) {
      this.buyOrdersGrouped.remove(value.order.details.price, value.order.details.funds);
    } else {
      this.sellOrdersGrouped.remove(value.order.details.price, value.order.details.funds);
    }
  }

  private ordersMatched(orderMatched: OrdersMatched.AsObject) {
    if (orderMatched.trade.tradingpair === this.tradingPair.value) {
      if (orderMatched.trade.executingorderside.toLowerCase() === 'sell') {
        this.buyOrdersGrouped.remove(orderMatched.trade.price, orderMatched.trade.existingorderfunds);
      } else {
        this.sellOrdersGrouped.remove(orderMatched.trade.price, orderMatched.trade.existingorderfunds);
      }
    }
  }

  private swapSuccess(swapSuccess: SwapSuccess.AsObject) {
    if (swapSuccess.trade.tradingpair === this.tradingPair.value) {
      this.historicTradesList.unshift(swapSuccess.trade);
    }
  }

  private fillHistoricTrades(tradesList: Trade.AsObject[]) {
    const filtered = tradesList.filter(x => x.tradingpair === this.tradingPair.value);
    this.historicTradesList = filtered.slice(0, this.NUMBER_OF_ROWS);
  }

  // Functions for UI
  public getLastPrice() {
    if (this.historicTradesList.length > 0) {
      return this.getCurrency(1) + ' ' + transformSatoshis(this.historicTradesList[0].price.value);
    }
  }

  public getCurrency(index: number): string {
    let value = '';
    const currencies: string[] = this.tradingPair.value.split('_');
    if (currencies.length === 2) {
      value = currencies[index];
    }
    return value;
  }

  public getBidSum(index: number) {
    const sum = this.bidSumAux(index, this.buyOrdersGrouped);
    return sum.toFixed(8);
  }

  public bidSumAux(index: number, data: GroupedOrders): Big {
    const value = calculateAmount(data.values[index]);
    if (index === 0) {
      return value;
    }
    return value.plus(this.bidSumAux(index - 1, data));
  }

  public getAskSum(index: number) {
    const sum = this.askSumAux(index, this.sellOrdersGrouped);
    return sum.toFixed(8);
  }

  public askSumAux(index: number, data: GroupedOrders): Big {
    const value = Big(transformSatoshis(data.values[index].amount));

    if (index === 0) {
      return value;
    }
    return value.plus(this.askSumAux(index - 1, data));
  }

  public getExecutedTime(executionDate: number) {
    return moment(executionDate).format('HH:mm:ss');
  }

}
