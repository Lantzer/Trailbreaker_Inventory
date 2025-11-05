package com.example.test;

import com.TbInventory.TbInvApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.mockito.Mockito;

/**
 * Fast context loading tests.
 *
 * These tests verify the Spring application context loads correctly with mocked dependencies.
 * They run very quickly and are part of the default test suite.
 *
 * For real database tests, use: mvn -P real-db-test test
 */
@FastTest
@SpringBootTest(classes = TbInvApplication.class)
@Import(TbInvApplicationTests.MockDataSourceConfig.class)
class TbInvApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    //real profile in pom to environment variable for test. 
    private Environment env;

    @Test
    void contextLoads() {
        assertNotNull(dataSource, "DataSource should be available (mock by default)");
    }

    @Test
    void testDatabaseConnection() throws Exception {
        // If a real DB is configured via TEST_DB_URL, the DataSource will be real.
        // By default (no TEST_DB_URL), the MockDataSourceConfig supplies a mock.
        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection);
            assertTrue(connection.isValid(1));
            DatabaseMetaData md = connection.getMetaData();
            System.out.println("DB URL: " + md.getURL());
        }
    }

    @TestConfiguration
    //Mock is only present when property is true or missing (default)
    @ConditionalOnProperty(prefix = "test.datasource", name = "mock", havingValue = "true", matchIfMissing = true)
    public static class MockDataSourceConfig {
    
        @Bean
        @Primary
        public DataSource dataSource() throws Exception {
            DataSource ds = Mockito.mock(DataSource.class);
            Connection conn = Mockito.mock(Connection.class);
            DatabaseMetaData meta = Mockito.mock(DatabaseMetaData.class);
    
            Mockito.when(ds.getConnection()).thenReturn(conn);
            Mockito.when(conn.getMetaData()).thenReturn(meta);
            Mockito.when(conn.isValid(Mockito.anyInt())).thenReturn(true);
            Mockito.when(meta.getURL()).thenReturn("jdbc:mockdb://localhost/test");
    
            return ds;
        }
    }

}