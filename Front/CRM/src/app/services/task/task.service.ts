// task.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class TaskService {
  private baseUrl = `${environment.taskBaseUrl}/tasks`;

  constructor(private http: HttpClient) {}

  // Get all tasks
  getAllTasks(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}`);
  }

  // Get tasks by candidate full name
  getTasksByCandidateId(candidateFullName: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/candidate/${encodeURIComponent(candidateFullName)}`);
  }

  // Get tasks by status
  getTasksByStatus(status: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/status/${status}`);
  }

  // Create a new task
  createTask(task: any): Observable<any> {
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    return this.http.post<any>(this.baseUrl, task, { headers });
  }

  // Update an existing task
  updateTask(taskId: number, taskData: any): Observable<any> {
    return this.http.put(`${this.baseUrl}/${taskId}`, taskData);
  }


  // Delete a task
  deleteTask(id: number): Observable<HttpResponse<void>> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`, { observe: 'response' });
  }

  // Mark a task as completed
  // task.service.ts
  markTaskAsCompleted(id: number): Observable<any> {
    return this.http.patch<any>(`${this.baseUrl}/${id}/complete`, {});
  }

  getTaskById(taskId: number): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/${taskId}`);
  }

}
