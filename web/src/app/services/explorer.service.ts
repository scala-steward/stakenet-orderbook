import { Injectable } from '@angular/core';
import { environment } from 'src/environments/environment';
import { HttpClient } from '@angular/common/http';
import { ExplorerAddress } from '../models/explorer-address';
import { BURNED_ADDRESS } from '../models/constants';


@Injectable({
  providedIn: 'root'
})
export class ExplorerService {


  private baseUrl = environment.EXPLORER_URL;

  constructor(private http: HttpClient) { }

  getBurnedAmount(): Promise<ExplorerAddress> {
    const url = `${this.baseUrl}/addresses/${BURNED_ADDRESS}`;
    return this.http.get<ExplorerAddress>(url).toPromise();
  }

  getUSDPrice(currency: string): Promise<any> {
    const url = `${this.baseUrl}/${currency}/prices`;
    return this.http.get<number>(url).toPromise();
  }
}
