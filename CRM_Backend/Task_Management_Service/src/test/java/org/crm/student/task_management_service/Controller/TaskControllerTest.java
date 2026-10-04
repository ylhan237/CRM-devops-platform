package org.crm.student.task_management_service.Controller;

import org.crm.student.task_management_service.Service.TaskService;
import org.crm.student.task_management_service.model.Task;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskControllerTest {

    private final TaskService taskService = mock(TaskService.class);
    private final TaskController taskController = new TaskController(taskService);

    @Test
    void createTaskForwardsAuthorizationToService() {
        Task task = new Task();
        when(taskService.createTask(task, "Bearer token")).thenReturn(task);

        ResponseEntity<Task> response = taskController.createTask("Bearer token", task);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(task, response.getBody());
        verify(taskService).createTask(task, "Bearer token");
    }

    @Test
    void candidateTaskRouteReturnsTheCandidatesTasks() {
        List<Task> tasks = List.of(new Task());
        when(taskService.getTasksByCandidateId("Ada Lovelace")).thenReturn(tasks);

        ResponseEntity<List<Task>> response = taskController.getTasksByCandidateId("Ada Lovelace");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(tasks, response.getBody());
        verify(taskService).getTasksByCandidateId("Ada Lovelace");
    }

    @Test
    void updateTaskForwardsAuthorizationToService() {
        Task task = new Task();
        when(taskService.updateTask(7L, task, "Bearer token")).thenReturn(java.util.Optional.of(task));

        ResponseEntity<Task> response = taskController.updateTask("Bearer token", 7L, task);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(task, response.getBody());
        verify(taskService).updateTask(7L, task, "Bearer token");
    }
}
