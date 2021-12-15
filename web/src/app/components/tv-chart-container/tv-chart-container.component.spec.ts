import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { TvChartContainerComponent } from './tv-chart-container.component';
import { ToastrModule } from 'ngx-toastr';
import { RouterTestingModule } from '@angular/router/testing';
import { HomeComponent } from '../home/home.component';

describe('TvChartContainerComponent', () => {
  let component: TvChartContainerComponent;
  let fixture: ComponentFixture<TvChartContainerComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [TvChartContainerComponent],
      imports: [
        RouterTestingModule.withRoutes([
          { path: ':market', component: HomeComponent },
          { path: 'graph/:market', component: TvChartContainerComponent },
          { path: '**', redirectTo: 'XSN_BTC' }
        ]),
        ToastrModule.forRoot()]
    })
      .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(TvChartContainerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
