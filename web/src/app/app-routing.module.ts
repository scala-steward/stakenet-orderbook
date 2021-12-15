import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { TvChartContainerComponent } from './components/tv-chart-container/tv-chart-container.component';
import { MonitorComponent } from './components/monitor/monitor.component';


const routes: Routes = [
  { path: ':market', component: HomeComponent },
  { path: 'graph/:market', component: TvChartContainerComponent },
  { path: 'monitor/:market', component: MonitorComponent },
  { path: '**', redirectTo: 'XSN_BTC' },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
