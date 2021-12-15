import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { SweetAlert2Module } from '@sweetalert2/ngx-sweetalert2';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ToastrModule } from 'ngx-toastr';
import { HttpClientModule } from '@angular/common/http';


import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { OrderbookService } from './services/orderbook.service';
import { TradingPairService } from './services/trading-pair.service';
import { ExplorerService } from './services/explorer.service';
import { HomeComponent } from './components/home/home.component';
import { OrderComponent } from './components/order/order.component';
import { AngularResizedEventModule } from 'angular-resize-event';
import { TvChartContainerComponent } from './components/tv-chart-container/tv-chart-container.component';
import { MonitorComponent } from './components/monitor/monitor.component';
import { WebSocketService } from './services/web-socket.service';

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    OrderComponent,
    TvChartContainerComponent,
    MonitorComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    BrowserAnimationsModule,
    FormsModule,
    ReactiveFormsModule,
    SweetAlert2Module.forRoot(),
    NgbModule,
    ToastrModule.forRoot(),
    AngularResizedEventModule,
    HttpClientModule
  ],
  providers: [OrderbookService, OrderComponent, TradingPairService, ExplorerService, WebSocketService],
  bootstrap: [AppComponent],
  entryComponents: [OrderComponent]
})
export class AppModule { }
