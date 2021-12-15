import { Injectable, OnDestroy } from '@angular/core';
import { Subject, Observable } from 'rxjs';
import { WebSocketService } from './web-socket.service';
import { environment } from 'src/environments/environment';
import { map } from 'rxjs/operators';
import { ProtobufFactory } from '../helpers/protobuf-factory';
import { Event, Command } from '../models/protos/api_pb';


const INTERVAL_FOR_CONNECTION_MS = 60000; // 60 seg

@Injectable({
  providedIn: 'root'
})
export class OrderbookService implements OnDestroy {

  private messages: Subject<any>;
  private intervalForConection;
  private streamEvents: Subject<Event> = new Subject();
  private streamSocketConection: Subject<any> = new Subject();

  constructor(private wsService: WebSocketService) {
    this.streamSocketConection = this.wsService.getOnOpenSubject();
    this.connect();
  }

  public getConectionStream(): Observable<any> {
    return this.streamSocketConection;
  }

  ngOnDestroy(): void {
    clearInterval(this.intervalForConection);
  }

  public getStream(): Observable<Event> {
    return this.streamEvents;
  }

  private handlerrors(error): void {
    this.reconnectSocket();
  }

  private reconnectSocket(): void {
    setTimeout(() => this.connect(), 500);
  }

  private connect() {

    // tslint:disable-next-line: whitespace no-angle-bracket-type-assertion
    this.messages = <Subject<any>>this.wsService
      .connect(environment.SERVER_URL)
      .pipe(map(response => {
        return Event.deserializeBinary(new Uint8Array(response.data));
      }));

    this.messages.subscribe(
      event => this.streamEvents.next(event),
      error => { this.handlerrors(error); },
      () => { this.reconnectSocket(); }
    );

    if (!this.intervalForConection) {
      this.intervalForConection = setInterval(() => {
        this.send(ProtobufFactory.createPing());
      }, INTERVAL_FOR_CONNECTION_MS);
    }
  }

  public send(message: Command) {
    this.messages.next(message.serializeBinary());
  }
}
