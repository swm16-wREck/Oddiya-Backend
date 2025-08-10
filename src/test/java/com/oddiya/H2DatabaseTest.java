package com.oddiya;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class H2DatabaseTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testH2DatabaseConnection() throws Exception {
        // Verify we're using H2
        String url = dataSource.getConnection().getMetaData().getURL();
        assertThat(url).contains("h2:mem");
        
        // Test we can create and query a table
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test_table (id INT PRIMARY KEY, name VARCHAR(255))");
        jdbcTemplate.update("INSERT INTO test_table (id, name) VALUES (?, ?)", 1, "Test H2");
        
        String result = jdbcTemplate.queryForObject(
            "SELECT name FROM test_table WHERE id = ?", 
            String.class, 
            1
        );
        
        assertThat(result).isEqualTo("Test H2");
        
        // Verify the database name
        String dbName = dataSource.getConnection().getCatalog();
        System.out.println("Connected to H2 database: " + dbName);
        System.out.println("Database URL: " + url);
    }
    
    @Test
    void testH2ModePostgreSQL() throws Exception {
        // Test PostgreSQL compatibility mode with supported features
        // Note: H2 doesn't support JSONB, but supports JSON as VARCHAR
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS pg_test (id SERIAL PRIMARY KEY, data VARCHAR(1000))");
        
        // Insert JSON as string (H2 compatible)
        jdbcTemplate.update("INSERT INTO pg_test (data) VALUES (?)", "{\"key\": \"value\"}");
        
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_test", Integer.class);
        assertThat(count).isEqualTo(1);
        
        // Test SERIAL works (PostgreSQL compatibility)
        jdbcTemplate.update("INSERT INTO pg_test (data) VALUES (?)", "{\"key2\": \"value2\"}");
        Integer id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM pg_test", Integer.class);
        assertThat(id).isEqualTo(2);
    }
}