import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatToolbar } from '@angular/material/toolbar';
import { MatCard, MatCardContent, MatCardTitle } from '@angular/material/card';
import { environment } from '../environments/environment';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, MatToolbar, MatCard, MatCardTitle, MatCardContent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly title = signal(environment.appName);
  protected readonly apiBaseUrl = signal(environment.apiBaseUrl);
}
