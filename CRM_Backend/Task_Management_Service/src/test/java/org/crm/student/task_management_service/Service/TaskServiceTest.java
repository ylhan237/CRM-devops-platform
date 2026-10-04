package org.crm.student.task_management_service.Service;

import org.crm.student.task_management_service.Repository.TaskRepository;
import org.crm.student.task_management_service.model.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CandidateClient candidateClient;

    @Mock
    private UserClient userClient;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(taskService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(taskService, "apiGatewayUrl", "http://localhost:8060");
        ReflectionTestUtils.setField(taskService, "notificationServiceUrl", "http://localhost:8085");
    }

    @Test
    void createTask_shouldRejectInvalidAssignedUser() {
        Task task = new Task();
        task.setAssignedTo("unknown.user");
        task.setCandidateFullname("no association");

        when(userClient.validateUser("unknown.user", "Bearer token")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> taskService.createTask(task, "Bearer token"));

        assertEquals("Invalid assignedTo: unknown.user", exception.getMessage());
        verify(taskRepository, never()).save(task);
    }

    @Test
    void createTaskPersistsAssignedEmailAndForwardsCallerAuthorization() {
        Task task = new Task();
        task.setAssignedTo("Ada Lovelace");
        task.setCandidateFullname("no association");
        task.setDescription("Review application");

        when(userClient.validateUser("Ada Lovelace", "Bearer admin-token")).thenReturn(true);
        when(restTemplate.exchange(
                eq("http://localhost:8060/api/v1/auth/Ada Lovelace/email"),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok("ada@example.com"));
        when(taskRepository.save(task)).thenAnswer(invocation -> invocation.getArgument(0));

        Task created = taskService.createTask(task, "Bearer admin-token");

        assertEquals("ada@example.com", created.getAssignedToEmail());
        verify(userClient).validateUser("Ada Lovelace", "Bearer admin-token");
        verify(taskRepository).save(argThat(saved -> "ada@example.com".equals(saved.getAssignedToEmail())));
        verify(restTemplate).postForObject(
                eq("http://localhost:8085/api/notifications/task-assignment"),
                any(),
                eq(String.class));
    }

    @Test
    void markTaskAsCompleted_shouldRejectWhenDeadlinePassed() {
        Task task = new Task();
        task.setId(7L);
        task.setDeadline(LocalDate.now().minusDays(1));

        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> taskService.markTaskAsCompleted(7L));

        assertEquals("Cannot mark task as completed; deadline has passed.", exception.getMessage());
        verify(taskRepository, never()).save(task);
    }
}
