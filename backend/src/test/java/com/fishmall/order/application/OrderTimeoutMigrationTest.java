package com.fishmall.order.application;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class OrderTimeoutMigrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("fishmall").withUsername("test").withPassword("test");
    @Test void upgradesExistingV7OrdersWithoutChangingBusinessState() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                .locations("classpath:db/migration").target("7").load().migrate();
        try (var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
             var statement=connection.createStatement()) {
            statement.executeUpdate("insert into sales_order(order_no,wx_user_id,status,address_snapshot,estimated_amount_cent) "
                    + "values('upgrade-old',1,'CREATED','{}',100)");
        }
        var flyway=Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                .locations("classpath:db/migration").target("8").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1); flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
             var statement=connection.createStatement()) {
            try (var rows=statement.executeQuery("select status,estimated_amount_cent,timeout_next_attempt_at,timeout_attempt_count from sales_order where order_no='upgrade-old'")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("CREATED");
                assertThat(rows.getLong(2)).isEqualTo(100); assertThat(rows.getTimestamp(3)).isNull(); assertThat(rows.getInt(4)).isZero();
            }
            try(var rows=statement.executeQuery("show index from sales_order where Key_name='idx_order_timeout_retry'")) {
                assertThat(rows.next()).isTrue();
            }
            try(var rows=statement.executeQuery("explain select * from sales_order where id>0 and "
                    + "((status='CREATED' and created_at<=current_timestamp-interval 1 day) or "
                    + "(status in ('QUOTED','PENDING_PAYMENT') and pay_deadline_at<=current_timestamp)) "
                    + "and (timeout_next_attempt_at is null or timeout_next_attempt_at<=current_timestamp) order by id limit 50")) {
                assertThat(rows.next()).isTrue();
                org.slf4j.LoggerFactory.getLogger(getClass()).info("超时扫描查询计划: type={}, key={}, possibleKeys={}",
                        rows.getString("type"),rows.getString("key"),rows.getString("possible_keys"));
            }
        }
        var latest=Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                .locations("classpath:db/migration").target("9").load();
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
        latest.validate();
        assertThat(latest.migrate().migrationsExecuted).isZero();
        try(var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
            var statement=connection.createStatement()) {
            try(var rows=statement.executeQuery("select status,estimated_amount_cent from sales_order where order_no='upgrade-old'")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("CREATED");assertThat(rows.getLong(2)).isEqualTo(100);
            }
            try(var rows=statement.executeQuery("show index from payment_record where Key_name='idx_payment_state_paid'")) {
                assertThat(rows.next()).isTrue();
            }
        }
        try(var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());var statement=connection.createStatement()) {
            statement.executeUpdate("insert into message_log(order_id,template_type,state,request_json) select id,'LEGACY','FAILED','{}' from sales_order where order_no='upgrade-old'");
        }
        var notificationUpgrade=Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                .locations("classpath:db/migration").target("10").load();
        assertThat(notificationUpgrade.migrate().migrationsExecuted).isEqualTo(1);
        notificationUpgrade.validate();
        try(var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());var statement=connection.createStatement();
            var rows=statement.executeQuery("select state,event_key,attempt_count from message_log where template_type='LEGACY'")) {
            assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("FAILED");assertThat(rows.getString(2)).isNull();assertThat(rows.getInt(3)).isZero();
        }
    }
}
