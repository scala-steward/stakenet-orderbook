import { Injectable, OnDestroy } from '@angular/core';
import { Observable, Observer, Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class WebSocketService {

  private subject: Subject<any> = new Subject();
  private onOpenSubject: Subject<any> = new Subject();
  private ws: any;
  private maxAttemps = 5;

  constructor() { }

  public connect(url: string): Subject<any> {

    if (!this.ws || this.ws.readyState === WebSocket.CLOSED) {
      this.subject = this.create(url);
    }
    return this.subject;
  }

  public getOnOpenSubject() {
    return this.onOpenSubject;
  }

  private create(url: string): Subject<any> {
    const clientVersion = '100'; // required version to connect to the server
    const subject = new Subject<any>();

    this.ws = new WebSocket(url, clientVersion);
    this.ws.binaryType = 'arraybuffer';
    this.ws.onopen = () => this.onOpenSubject.next('CONNECTED');

    const observable = new Observable(
      (obs: Observer<any>) => {
        this.ws.onmessage = obs.next.bind(obs);
        this.ws.onerror = obs.error.bind(obs);
        this.ws.onclose = obs.complete.bind(obs);
      }
    );

    subject.subscribe({
      next: (message: any) => {
        if (!message.origin) {
          this.sendMessage(message, 1);
        }
      }
    });

    observable.subscribe(subject);

    return subject;
  }


  private sendMessage(message: any, attemp: number) {
    if (attemp < this.maxAttemps) {
      if (this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(message);
      } else {
        setTimeout(() => {
          this.sendMessage(message, attemp + 1);
        }, attemp * 2000);
      }
    } else {
      console.log('cannot connect to web socket service');
    }
  }

  public close() {
    this.ws.close();
    this.subject = null;
  }
}
