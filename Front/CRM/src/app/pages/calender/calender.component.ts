import { Component, OnInit } from '@angular/core';
import { ScheduleModule } from '@syncfusion/ej2-angular-schedule';
import {
  EventSettingsModel,
  DayService,
  WeekService,
  WorkWeekService,
  MonthService,
  AgendaService,
} from '@syncfusion/ej2-angular-schedule';
import { EventService } from '../../services/event/event.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [ScheduleModule],
  providers: [DayService, WeekService, WorkWeekService, MonthService, AgendaService],
  template: `
    <ejs-schedule
      width="100%"
      height="550px"
      [selectedDate]="selectedDate"
      [eventSettings]="eventSettings"
    ></ejs-schedule>
  `,
  styleUrl: './calender.component.scss',
})
export class CalenderComponent implements OnInit {
  public selectedDate: Date = new Date();
  public eventSettings: EventSettingsModel = { dataSource: [] };

  constructor(private eventService: EventService) {}

  ngOnInit(): void {
    this.fetchEvents();
  }

  fetchEvents(): void {
    // Goes through EventService rather than calling HttpClient directly, so the
    // base URL stays defined in one place. The previous inline
    // http.get('http://localhost:8089/api/events') was the last hardcoded URL
    // left in the application and pointed at the browser's own machine.
    this.eventService.getAllEvents().subscribe(
      (data: any[]) => {
        this.eventSettings = {
          dataSource: (data ?? []).map((event: any) => ({
            Id: event.id,
            Subject: event.name,
            StartTime: new Date(`${event.start}T${event.start_time}`),
            EndTime: new Date(`${event.end}T${event.end_time}`),
          })),
        };
      },
      () => {}
    );
  }
}
