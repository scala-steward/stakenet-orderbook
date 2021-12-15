import { Injectable } from '@angular/core';
import { environment } from 'src/environments/environment';
import { HttpClient } from '@angular/common/http';
import { Volume } from '../models/volume';
import { TradingPair } from '../models/trading-pair';

@Injectable({
  providedIn: 'root'
})
export class TradingPairService {
  private baseUrl = environment.API_URL + '/trading-pairs';

  constructor(private http: HttpClient) { }

  getVolume(tradingPair: TradingPair, days: number): Promise<Volume> {
    const url = `${this.baseUrl}/${tradingPair.name}/volume?lastDays=${days}`;
    return this.http.get<Volume>(url).toPromise();
  }

  getNumberOfTrades(tradingPair: TradingPair, days: number): Promise<any> {
    const url = `${this.baseUrl}/${tradingPair.name}/trades-number?lastDays=${days}`;
    return this.http.get<any>(url).toPromise();
  }


  getNodesInfo(tradingPair: TradingPair): Promise<any> {
    const url = `${this.baseUrl}/${tradingPair.name}/nodes-info`;
    return this.http.get<any>(url).toPromise();
  }

}
