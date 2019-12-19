import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FlexLayoutModule } from '@angular/flex-layout';

import { TppSuccessRoutingModule } from './success-routing.module';
import { TppSuccessComponent } from './success.component';
import { MatButtonModule, MatProgressSpinnerModule } from '@angular/material';
import { ForgerockAlertModule } from '@forgerock/openbanking-ngx-common/components/forgerock-alert';

@NgModule({
  declarations: [TppSuccessComponent],
  imports: [
    CommonModule,
    TppSuccessRoutingModule,
    FlexLayoutModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    ForgerockAlertModule
  ]
})
export class TppSuccessModule {}
