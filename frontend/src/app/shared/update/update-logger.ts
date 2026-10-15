import { inject, Injectable } from '@angular/core';
import { SwUpdate} from '@angular/service-worker';

@Injectable({providedIn: 'root'})
export class UpdateLogger {
  private readonly swUpdate = inject(SwUpdate);

  constructor() {
    if(!this.swUpdate.isEnabled) return;

    this.swUpdate.versionUpdates.subscribe((event) => {
      if(event.type === 'VERSION_READY') {
        console.info('Neue Verison verfügbar', event.latestVersion.hash);
      }
    });
  }
}
