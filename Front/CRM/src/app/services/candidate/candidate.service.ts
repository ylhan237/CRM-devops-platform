import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import {Candidate} from "../../models/candidate";
import { environment } from '../../../environments/environment';


@Injectable({
  providedIn: 'root'
})
export class CandidateService {

  private apiUrl = `${environment.apiBaseUrl}/api/candidates`;

  constructor(private http: HttpClient) { }

  getCandidates(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}`);
  }

}
