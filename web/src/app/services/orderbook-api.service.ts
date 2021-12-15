import { Injectable } from '@angular/core';
import {
  GetHistoricTradesResponse, GetOpenOrdersResponse, SubscribeResponse,
  UnsubscribeResponse, CancelOpenOrderResponse, GetBarsPricesResponse
} from '../models/protos/commands_pb';
import { ProtobufFactory } from '../helpers/protobuf-factory';
import { OrderbookService } from './orderbook.service';
import { Command, Event } from '../models/protos/api_pb';
import { NotificationService } from './notification.service';
import { first, map, takeUntil, takeWhile, filter } from 'rxjs/operators';
import { promiseTimeout } from 'src/app/helpers/promise-timeout';
import { Observable } from 'rxjs';
import { OrderPlaced } from '../models/protos/events_pb';


@Injectable({
  providedIn: 'root'
})
export class OrderbookApiService {
  constructor(private orderbookService: OrderbookService, private notificationService: NotificationService) {
  }

  public getHistoricTrading(tradingPair: string): Promise<GetHistoricTradesResponse.AsObject> {
    const message: Command = ProtobufFactory.createGetHistoricTrades(tradingPair);
    this.orderbookService.send(message);

    const orderbookStream = this.getStream(message.getClientmessageid());
    const promise = new Promise<GetHistoricTradesResponse.AsObject>((resolve, reject) => {
      orderbookStream.forEach((event: Event) => {

        const commandResponse = event.toObject().response;

        if (commandResponse.commandfailed) {
          reject(Error(commandResponse.commandfailed.reason));
        } else {
          resolve(commandResponse.gethistorictradesresponse);
        }
      });
    });

    return promiseTimeout(promise);
  }

  public getTradingOrders(tradingPair: string): Promise<GetOpenOrdersResponse.AsObject> {
    const message: Command = ProtobufFactory.createGetOpenOrders(tradingPair);
    this.orderbookService.send(message);

    const orderbookStream = this.getStream(message.getClientmessageid());
    const promise = new Promise<GetOpenOrdersResponse.AsObject>((resolve, reject) => {
      orderbookStream.forEach((event: Event) => {
        const commandResponse = event.toObject().response;
        if (commandResponse.commandfailed) {
          reject(Error(commandResponse.commandfailed.reason));
        } else {
          resolve(commandResponse.getopenordersresponse);
        }
      });
    });

    return promiseTimeout(promise);
  }

  public subscribe(tradingPair: string): Promise<SubscribeResponse.AsObject> {
    const message: Command = ProtobufFactory.createSubscribe(tradingPair);
    this.orderbookService.send(message);

    const orderbookStream = this.getStream(message.getClientmessageid());
    const promise = new Promise<SubscribeResponse.AsObject>((resolve, reject) => {
      orderbookStream.forEach((event: Event) => {
        const commandResponse = event.toObject().response;
        if (commandResponse.commandfailed) {
          reject(Error(commandResponse.commandfailed.reason));
          this.notificationService.error(commandResponse.commandfailed.reason);
        } else {
          resolve(commandResponse.subscriberesponse);
        }
      });
    });

    return promiseTimeout(promise);
  }

  public unsubscribe(tradingPair: string): Promise<UnsubscribeResponse.AsObject> {
    const message: Command = ProtobufFactory.createUnsubscribe(tradingPair);
    this.orderbookService.send(message);

    const orderbookStream = this.getStream(message.getClientmessageid());
    const promise = new Promise<UnsubscribeResponse.AsObject>((resolve, reject) => {
      orderbookStream.forEach((event: Event) => {
        const commandResponse = event.getResponse().toObject();
        if (commandResponse.commandfailed) {
          reject(Error(commandResponse.commandfailed.reason));
          this.notificationService.error(commandResponse.commandfailed.reason);
        } else {
          resolve(commandResponse.unsubscriberesponse);
        }
      });
    });

    return promiseTimeout(promise);
  }

  public cancelOrder(orderid: string): Promise<CancelOpenOrderResponse.AsObject> {
    const message: Command = ProtobufFactory.createCancelOpenOrder(orderid);
    this.orderbookService.send(message);

    const orderbookStream = this.getStream(message.getClientmessageid());
    const promise = new Promise<CancelOpenOrderResponse.AsObject>((resolve, reject) => {
      orderbookStream.forEach((event: Event) => {
        const commandResponse = event.toObject().response;
        if (commandResponse.commandfailed) {
          reject(Error(commandResponse.commandfailed.reason));
          this.notificationService.error(commandResponse.commandfailed.reason);
        } else {
          resolve(commandResponse.cancelorderresponse);
        }
      });
    });

    return promiseTimeout(promise);
  }

  public getBarsPrices(tradingPair: string, resolution: string, from: number, to: number, limit: number):
    Promise<GetBarsPricesResponse.AsObject> {
    const message: Command = ProtobufFactory.createGetBarsPrices(tradingPair, resolution, from, to, limit);
    this.orderbookService.send(message);

    const orderbookStream = this.getStream(message.getClientmessageid());
    const promise = new Promise<GetBarsPricesResponse.AsObject>((resolve, reject) => {
      orderbookStream.forEach((event: Event) => {

        const commandResponse = event.toObject().response;
        if (commandResponse.commandfailed) {
          reject(Error(commandResponse.commandfailed.reason));
          this.notificationService.error(commandResponse.commandfailed.reason);
        } else {
          resolve(commandResponse.getbarspricesresponse);
        }
      });
    });

    return promiseTimeout(promise);
  }

  public subscribeTradingPair(tradingPair: string): Observable<Event> {
    return this.orderbookService.getStream()
      .pipe(filter(event => event.hasEvent() &&
        event.getEvent().hasOrdersmatched() &&
        event.getEvent().getOrdersmatched().getTradingpair() === tradingPair));
  }

  private getStream(commandId: string) {
    return this.orderbookService.getStream()
      .pipe(first(event => event.hasResponse() && event.getResponse().getClientmessageid() === commandId));
  }
}
