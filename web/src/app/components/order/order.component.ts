import { Component, OnInit, Input } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import { Command } from '../../models/protos/api_pb';
import { Order } from '../../models/protos/models_pb';
import { ProtobufFactory, newOrder, newOrderDetails } from '../../helpers/protobuf-factory';
import { OrderbookService } from 'src/app/services/orderbook.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TRADING_PAIRS } from '../../models/constants';


@Component({
  selector: 'app-order',
  templateUrl: './order.component.html',
  styleUrls: ['./order.component.css']
})
export class OrderComponent implements OnInit {

  ordertypes: string[] = [];
  orderSides: string[] = [];
  tradingPairs: string[] = TRADING_PAIRS;
  orderTypeArray = [Order.OrderType.LIMIT, Order.OrderType.MARKET];
  orderFormControl: FormGroup;

  constructor(
    private orderbookService: OrderbookService,
    public activeModal: NgbActiveModal) {

    Object.keys(Order.OrderType).forEach(k =>
      this.ordertypes.push(k)
    );

    Object.keys(Order.OrderSide).forEach(k =>
      this.orderSides.push(k)
    );
  }

  ngOnInit() {
    this.orderFormControl = this.createFormGroup();
  }

  createFormGroup(): FormGroup {
    const satoshiValidation = Validators.pattern('^[1-9][0-9]*$');
    return new FormGroup({
      tradingPair: new FormControl('', [Validators.required]),
      orderType: new FormControl('', [Validators.required]),
      orderSide: new FormControl('', [Validators.required]),
      satoshiFunds: new FormControl('', [Validators.required, satoshiValidation]),
      satoshiPrice: new FormControl('', [Validators.required, satoshiValidation]),
    });
  }

  public onOrderTypeChanged() {
    if (this.orderFormControl.get('orderType').value === Order.OrderType.MARKET.toString()) {
      this.orderFormControl.get('satoshiPrice').setValue('');
      this.orderFormControl.get('satoshiPrice').disable();
    } else {
      this.orderFormControl.get('satoshiPrice').enable();
    }
  }

  public sendNewOrder() {
    if (this.orderFormControl.invalid) {
      return;
    }
    const myOrder = this.orderFormControl.getRawValue();
    const orderDetails = newOrderDetails(myOrder.satoshiFunds, myOrder.satoshiPrice);
    const protoOrder = newOrder(myOrder.tradingPair, myOrder.orderType, myOrder.orderSide, orderDetails);

    const message: Command = ProtobufFactory.createPlaceOrder(protoOrder);
    this.orderbookService.send(message);
    this.activeModal.dismiss();
  }
}
