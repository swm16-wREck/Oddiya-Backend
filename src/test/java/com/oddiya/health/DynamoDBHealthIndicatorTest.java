package com.oddiya.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.Status;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamoDBHealthIndicatorTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    private DynamoDBHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new DynamoDBHealthIndicator(dynamoDbClient);
    }

    @Test
    void doHealthCheck_AllTablesActive_ShouldReturnUp() {
        // Arrange
        ListTablesResponse listResponse = ListTablesResponse.builder()
            .tableNames(Arrays.asList("Users", "TravelPlans", "Places"))
            .build();
        
        DescribeTableResponse usersResponse = createActiveTableResponse("Users", 100L, 5L);
        DescribeTableResponse plansResponse = createActiveTableResponse("TravelPlans", 250L, 12L);
        DescribeTableResponse placesResponse = createActiveTableResponse("Places", 500L, 0L);
        
        when(dynamoDbClient.listTables(any(ListTablesRequest.class))).thenReturn(listResponse);
        when(dynamoDbClient.describeTable(argThat((DescribeTableRequest req) -> 
            req.tableName().equals("Users")))).thenReturn(usersResponse);
        when(dynamoDbClient.describeTable(argThat((DescribeTableRequest req) -> 
            req.tableName().equals("TravelPlans")))).thenReturn(plansResponse);
        when(dynamoDbClient.describeTable(argThat((DescribeTableRequest req) -> 
            req.tableName().equals("Places")))).thenReturn(placesResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        Map<String, Object> details = health.getDetails();
        assertEquals(3, details.get("tableCount"));
        assertEquals(850L, details.get("totalItemCount")); // 100 + 250 + 500
        assertEquals(17L, details.get("totalSizeBytes")); // 5 + 12 + 0
        assertEquals(3L, details.get("activeTableCount"));
        assertEquals(0L, details.get("inactiveTableCount"));
        assertTrue(details.containsKey("tables"));
        assertTrue(details.containsKey("lastChecked"));
        
        verify(dynamoDbClient).listTables(any(ListTablesRequest.class));
        verify(dynamoDbClient, times(3)).describeTable(any(DescribeTableRequest.class));
    }

    @Test
    void doHealthCheck_SomeTablesCreating_ShouldReturnUp() {
        // Arrange
        ListTablesResponse listResponse = ListTablesResponse.builder()
            .tableNames(Arrays.asList("Users", "NewTable"))
            .build();
        
        DescribeTableResponse usersResponse = createActiveTableResponse("Users", 100L, 5L);
        DescribeTableResponse newTableResponse = createTableResponse("NewTable", 
            TableStatus.CREATING, 0L, 0L);
        
        when(dynamoDbClient.listTables(any(ListTablesRequest.class))).thenReturn(listResponse);
        when(dynamoDbClient.describeTable(argThat((DescribeTableRequest req) -> 
            req.tableName().equals("Users")))).thenReturn(usersResponse);
        when(dynamoDbClient.describeTable(argThat((DescribeTableRequest req) -> 
            req.tableName().equals("NewTable")))).thenReturn(newTableResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        Map<String, Object> details = health.getDetails();
        assertEquals(2, details.get("tableCount"));
        assertEquals(1L, details.get("activeTableCount"));
        assertEquals(1L, details.get("inactiveTableCount"));
    }

    @Test
    void doHealthCheck_AllTablesInactive_ShouldReturnDown() {
        // Arrange
        ListTablesResponse listResponse = ListTablesResponse.builder()
            .tableNames(Arrays.asList("DeletedTable"))
            .build();
        
        DescribeTableResponse deletedResponse = createTableResponse("DeletedTable", 
            TableStatus.DELETING, 0L, 0L);
        
        when(dynamoDbClient.listTables(any(ListTablesRequest.class))).thenReturn(listResponse);
        when(dynamoDbClient.describeTable(any(DescribeTableRequest.class))).thenReturn(deletedResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        
        Map<String, Object> details = health.getDetails();
        assertEquals(1, details.get("tableCount"));
        assertEquals(0L, details.get("activeTableCount"));
        assertEquals(1L, details.get("inactiveTableCount"));
    }

    @Test
    void doHealthCheck_NoTables_ShouldReturnDown() {
        // Arrange
        ListTablesResponse listResponse = ListTablesResponse.builder()
            .tableNames(Arrays.asList())
            .build();
        
        when(dynamoDbClient.listTables(any(ListTablesRequest.class))).thenReturn(listResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("No DynamoDB tables found", health.getDetails().get("error"));
        assertEquals(0, health.getDetails().get("tableCount"));
    }

    @Test
    void doHealthCheck_ListTablesException_ShouldReturnDown() {
        // Arrange
        when(dynamoDbClient.listTables(any(ListTablesRequest.class)))
            .thenThrow(DynamoDbException.builder()
                .message("Access denied")
                .awsErrorDetails(AwsErrorDetails.builder()
                    .errorCode("AccessDeniedException")
                    .errorMessage("Access denied")
                    .build())
                .build());

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().get("error").toString().contains("Access denied"));
        assertEquals("AccessDeniedException", health.getDetails().get("errorCode"));
    }

    @Test
    void doHealthCheck_DescribeTableException_ShouldContinueWithOtherTables() {
        // Arrange
        ListTablesResponse listResponse = ListTablesResponse.builder()
            .tableNames(Arrays.asList("Users", "FailingTable", "Places"))
            .build();
        
        DescribeTableResponse usersResponse = createActiveTableResponse("Users", 100L, 5L);
        DescribeTableResponse placesResponse = createActiveTableResponse("Places", 200L, 3L);
        
        when(dynamoDbClient.listTables(any(ListTablesRequest.class))).thenReturn(listResponse);
        when(dynamoDbClient.describeTable(argThat((DescribeTableRequest req) -> 
            req.tableName().equals("Users")))).thenReturn(usersResponse);
        when(dynamoDbClient.describeTable(argThat((DescribeTableRequest req) -> 
            req.tableName().equals("FailingTable"))))
            .thenThrow(ResourceNotFoundException.builder().message("Table not found").build());
        when(dynamoDbClient.describeTable(argThat((DescribeTableRequest req) -> 
            req.tableName().equals("Places")))).thenReturn(placesResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        Map<String, Object> details = health.getDetails();
        assertEquals(3, details.get("tableCount"));
        assertEquals(2L, details.get("activeTableCount"));
        assertEquals(0L, details.get("inactiveTableCount"));
        assertEquals(1L, details.get("errorCount"));
        
        // Verify specific table details
        @SuppressWarnings("unchecked")
        Map<String, Object> tables = (Map<String, Object>) details.get("tables");
        assertTrue(tables.containsKey("Users"));
        assertTrue(tables.containsKey("Places"));
        assertTrue(tables.containsKey("FailingTable"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> failingTable = (Map<String, Object>) tables.get("FailingTable");
        assertEquals("ERROR", failingTable.get("status"));
        assertTrue(failingTable.get("error").toString().contains("Table not found"));
    }

    @Test
    void doHealthCheck_TableUpdating_ShouldReturnUp() {
        // Arrange
        ListTablesResponse listResponse = ListTablesResponse.builder()
            .tableNames(Arrays.asList("UpdatingTable"))
            .build();
        
        DescribeTableResponse updatingResponse = createTableResponse("UpdatingTable", 
            TableStatus.UPDATING, 150L, 8L);
        
        when(dynamoDbClient.listTables(any(ListTablesRequest.class))).thenReturn(listResponse);
        when(dynamoDbClient.describeTable(any(DescribeTableRequest.class))).thenReturn(updatingResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        Map<String, Object> details = health.getDetails();
        assertEquals(1L, details.get("activeTableCount"));
        assertEquals(0L, details.get("inactiveTableCount"));
    }

    @Test
    void doHealthCheck_MixedTableStates_ShouldProvideDetailedInfo() {
        // Arrange
        ListTablesResponse listResponse = ListTablesResponse.builder()
            .tableNames(Arrays.asList("ActiveTable", "CreatingTable", "UpdatingTable", "DeletingTable"))
            .build();
        
        DescribeTableResponse activeResponse = createActiveTableResponse("ActiveTable", 100L, 5L);
        DescribeTableResponse creatingResponse = createTableResponse("CreatingTable", 
            TableStatus.CREATING, 0L, 0L);
        DescribeTableResponse updatingResponse = createTableResponse("UpdatingTable", 
            TableStatus.UPDATING, 75L, 3L);
        DescribeTableResponse deletingResponse = createTableResponse("DeletingTable", 
            TableStatus.DELETING, 0L, 0L);
        
        when(dynamoDbClient.listTables(any(ListTablesRequest.class))).thenReturn(listResponse);
        when(dynamoDbClient.describeTable(argThat((DescribeTableRequest req) -> 
            req.tableName().equals("ActiveTable")))).thenReturn(activeResponse);
        when(dynamoDbClient.describeTable(argThat((DescribeTableRequest req) -> 
            req.tableName().equals("CreatingTable")))).thenReturn(creatingResponse);
        when(dynamoDbClient.describeTable(argThat((DescribeTableRequest req) -> 
            req.tableName().equals("UpdatingTable")))).thenReturn(updatingResponse);
        when(dynamoDbClient.describeTable(argThat((DescribeTableRequest req) -> 
            req.tableName().equals("DeletingTable")))).thenReturn(deletingResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        Map<String, Object> details = health.getDetails();
        assertEquals(4, details.get("tableCount"));
        assertEquals(175L, details.get("totalItemCount")); // 100 + 0 + 75 + 0
        assertEquals(8L, details.get("totalSizeBytes")); // 5 + 0 + 3 + 0
        assertEquals(2L, details.get("activeTableCount")); // ACTIVE and UPDATING
        assertEquals(2L, details.get("inactiveTableCount")); // CREATING and DELETING
        assertEquals(0L, details.get("errorCount"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> tables = (Map<String, Object>) details.get("tables");
        assertEquals(4, tables.size());
        
        // Verify individual table statuses
        @SuppressWarnings("unchecked")
        Map<String, Object> activeTable = (Map<String, Object>) tables.get("ActiveTable");
        assertEquals("ACTIVE", activeTable.get("status"));
        assertEquals(100L, activeTable.get("itemCount"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> creatingTable = (Map<String, Object>) tables.get("CreatingTable");
        assertEquals("CREATING", creatingTable.get("status"));
        assertEquals(0L, creatingTable.get("itemCount"));
    }

    @Test
    void doHealthCheck_EmptyTableName_ShouldSkipTable() {
        // Arrange
        ListTablesResponse listResponse = ListTablesResponse.builder()
            .tableNames(Arrays.asList("ValidTable", "", "AnotherTable"))
            .build();
        
        DescribeTableResponse validResponse = createActiveTableResponse("ValidTable", 50L, 2L);
        DescribeTableResponse anotherResponse = createActiveTableResponse("AnotherTable", 25L, 1L);
        
        when(dynamoDbClient.listTables(any(ListTablesRequest.class))).thenReturn(listResponse);
        when(dynamoDbClient.describeTable(argThat((DescribeTableRequest req) -> 
            req.tableName().equals("ValidTable")))).thenReturn(validResponse);
        when(dynamoDbClient.describeTable(argThat((DescribeTableRequest req) -> 
            req.tableName().equals("AnotherTable")))).thenReturn(anotherResponse);

        // Act
        Health health = healthIndicator.doHealthCheck();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        
        Map<String, Object> details = health.getDetails();
        assertEquals(3, details.get("tableCount")); // Still counts the empty name in the list
        assertEquals(75L, details.get("totalItemCount")); // Only valid tables counted
        assertEquals(2L, details.get("activeTableCount")); // Only valid active tables
        
        // Verify only valid tables are described
        verify(dynamoDbClient, times(2)).describeTable(any(DescribeTableRequest.class));
    }

    private DescribeTableResponse createActiveTableResponse(String tableName, Long itemCount, Long sizeBytes) {
        return createTableResponse(tableName, TableStatus.ACTIVE, itemCount, sizeBytes);
    }

    private DescribeTableResponse createTableResponse(String tableName, TableStatus status, Long itemCount, Long sizeBytes) {
        return DescribeTableResponse.builder()
            .table(TableDescription.builder()
                .tableName(tableName)
                .tableStatus(status)
                .creationDateTime(Instant.now().minusSeconds(3600)) // Created 1 hour ago
                .itemCount(itemCount)
                .tableSizeBytes(sizeBytes)
                .provisionedThroughput(ProvisionedThroughputDescription.builder()
                    .readCapacityUnits(5L)
                    .writeCapacityUnits(5L)
                    .build())
                .build())
            .build();
    }
}