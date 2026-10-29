package com.fishmall.admin.application;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.admin.security.*;
import com.fishmall.user.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class AdminBusinessIntegrationTest {
 @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("fishmall").withUsername("test").withPassword("test");
 @DynamicPropertySource static void configure(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("admin.jwt-secret",()->"01234567890123456789012345678901");r.add("user.jwt-secret",()->"01234567890123456789012345678901");r.add("admin.initial-password",()->"initial-pass-123");}
 @TestConfiguration static class Time { @Bean @Primary Clock businessClock(){return Clock.fixed(Instant.parse("2026-10-28T16:05:00Z"),ZoneOffset.UTC);} }
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired AdminJwtTokenService tokens; @Autowired UserJwtTokenService userTokens; @Autowired ObjectMapper json;
 String auth;
 @BeforeEach void fixture(){ // Disposable Testcontainers schema only.
  for(String t:List.of("message_log","order_status_log","order_price_revision","payment_record","sales_order_item","sales_order","shop_banner"))db.update("delete from "+t);
  auth="Bearer "+tokens.issue(new AdminPrincipal(1L,"admin",0L)).token();
 }
 long order(String status,String refund,String created){String no=UUID.randomUUID().toString();db.update("insert into sales_order(order_no,wx_user_id,status,address_snapshot,estimated_amount_cent,refund_status,created_at) values (?,1,?,'{}',100,?,?)",no,status,refund,created);return db.queryForObject("select id from sales_order where order_no=?",Long.class,no);}
 void payment(long id,String state,long amount,String paid){db.update("insert into payment_record(order_id,out_trade_no,state,amount_cent,paid_at) values (?,?,?,?,?)",id,UUID.randomUUID().toString(),state,amount,paid);}
 @Test void emptyDashboardAndMigratedIndex() throws Exception {
  mvc.perform(get("/api/v1/admin/dashboard").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.date").value("2026-10-29")).andExpect(jsonPath("$.data.todayOrderCount").value(0)).andExpect(jsonPath("$.data.todaySalesAmountCent").value(0)).andExpect(jsonPath("$.data.pendingOrderCount").value(0));
  assertThat(db.queryForObject("select count(*) from information_schema.statistics where table_schema=database() and table_name='payment_record' and index_name='idx_payment_state_paid'",Long.class)).isEqualTo(2);
 }
 @Test void dashboardUsesShanghaiBoundariesGrossReceiptsAndAllTimeTasks() throws Exception {
  order("CREATED","NONE","2026-10-28 23:59:59.999");order("PAID_WAIT_DELIVERY","NONE","2026-10-29 00:00:00");order("DELIVERING","NONE","2026-10-20 12:00:00");
  payment(order("CANCELLED","REQUIRED","2026-10-29 23:59:59.999"),"SUCCESS",1200,"2026-10-29 00:00:00");
  payment(order("CANCELLED","MANUAL_REFUNDED","2026-10-10 12:00:00"),"SUCCESS",800,"2026-10-29 23:59:59.999");
  payment(order("COMPLETED","NONE","2026-10-29 01:00:00"),"SUCCESS",9999,"2026-10-28 23:59:59.999");
  payment(order("CLOSED","NONE","2026-10-30 00:00:00"),"SUCCESS",7777,"2026-10-30 00:00:00");
  payment(order("PENDING_PAYMENT","NONE","2026-10-29 02:00:00"),"FAILED",999,"2026-10-29 01:00:00");
  mvc.perform(get("/api/v1/admin/dashboard").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.todayOrderCount").value(4)).andExpect(jsonPath("$.data.todaySalesAmountCent").value(2000)).andExpect(jsonPath("$.data.pendingOrderCount").value(4)).andExpect(jsonPath("$.data.pendingQuoteCount").value(1)).andExpect(jsonPath("$.data.pendingDeliveryCount").value(1)).andExpect(jsonPath("$.data.pendingCompletionCount").value(1)).andExpect(jsonPath("$.data.pendingRefundCount").value(1));
 }
 Map<String,Object> banner(String type,Long target,String state){var b=new LinkedHashMap<String,Object>();b.put("title","测试轮播");b.put("imageUrl","/uploads/products/12345678-1234-1234-1234-123456789abc.png");b.put("jumpType",type);b.put("jumpTargetId",target);b.put("sort",1);b.put("status",state);return b;}
 long create(Map<String,Object> b) throws Exception {var response=mvc.perform(post("/api/v1/admin/banners").header("Authorization",auth).contentType("application/json").content(json.writeValueAsBytes(b))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return json.readTree(response).path("data").path("id").asLong();}
 @Test void bannersCrudAndSoftDelete() throws Exception {
  var body=banner("NONE",null,"DISABLED");long id=create(body);body.put("title","已更新");body.put("status","ACTIVE");
  mvc.perform(put("/api/v1/admin/banners/"+id).header("Authorization",auth).contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("已更新")).andExpect(jsonPath("$.data.targetAvailable").value(true));
  mvc.perform(get("/api/v1/admin/banners").param("status","ACTIVE").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
  mvc.perform(delete("/api/v1/admin/banners/"+id).header("Authorization",auth)).andExpect(status().isOk());mvc.perform(get("/api/v1/admin/banners/"+id).header("Authorization",auth)).andExpect(status().isNotFound());
  assertThat(db.queryForObject("select deleted from shop_banner where id=?",Integer.class,id)).isEqualTo(1);
 }
 @Test void bannerValidatesInputsAndPagination() throws Exception {
  for(var c:List.of(Map.entry("imageUrl","https://example.com/a.png"),Map.entry("imageUrl","/uploads/products/../../a.png"),Map.entry("jumpType","OTHER"),Map.entry("status","OTHER"),Map.entry("title"," "),Map.entry("sort",-1))){var b=banner("NONE",null,"ACTIVE");b.put(c.getKey(),c.getValue());mvc.perform(post("/api/v1/admin/banners").header("Authorization",auth).contentType("application/json").content(json.writeValueAsBytes(b))).andExpect(status().isBadRequest());}
  for(String type:List.of("CATEGORY","PRODUCT"))mvc.perform(post("/api/v1/admin/banners").header("Authorization",auth).contentType("application/json").content(json.writeValueAsBytes(banner(type,999999L,"ACTIVE")))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/admin/banners").param("page","2147483647").param("pageSize","100").header("Authorization",auth)).andExpect(status().isBadRequest());
 }
 @Test void targetBecomingUnavailableIsVisibleAndCannotBeEnabled() throws Exception {
  String name=UUID.randomUUID().toString();db.update("insert into product_category(name) values (?)",name);long c=db.queryForObject("select id from product_category where name=?",Long.class,name);
  long id=create(banner("CATEGORY",c,"ACTIVE"));db.update("update product_category set status='DISABLED' where id=?",c);
  mvc.perform(get("/api/v1/admin/banners/"+id).header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.targetAvailable").value(false));
  mvc.perform(put("/api/v1/admin/banners/"+id).header("Authorization",auth).contentType("application/json").content(json.writeValueAsBytes(banner("CATEGORY",c,"ACTIVE")))).andExpect(status().isBadRequest());
  mvc.perform(put("/api/v1/admin/banners/"+id).header("Authorization",auth).contentType("application/json").content(json.writeValueAsBytes(banner("CATEGORY",c,"DISABLED")))).andExpect(status().isOk());
 }
 @Test void listAndExcelUseSameRefundFilter() throws Exception {
  long id=order("CANCELLED","REQUIRED","2026-10-29 12:00:00");order("CANCELLED","MANUAL_REFUNDED","2026-10-29 12:00:00");order("CREATED","NONE","2026-10-29 12:00:00");
  mvc.perform(get("/api/v1/admin/orders").param("status","CANCELLED").param("refundStatus","REQUIRED").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value(id));
  byte[] bytes=mvc.perform(get("/api/v1/admin/orders/export").param("startDate","2026-10-29").param("endDate","2026-10-29").param("status","CANCELLED").param("refundStatus","REQUIRED").header("Authorization",auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
  try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))){assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(1);}
  for(String path:List.of("/api/v1/admin/orders","/api/v1/admin/orders/export"))mvc.perform(get(path).param("startDate","2026-10-29").param("endDate","2026-10-29").param("refundStatus","INVALID").header("Authorization",auth)).andExpect(status().isBadRequest());
 }
 @Test void adminEndpointsRejectAnonymousAndUserTokens() throws Exception {
  String user="Bearer "+userTokens.issue(new UserPrincipal(1L)).token();for(String path:List.of("/api/v1/admin/dashboard","/api/v1/admin/orders","/api/v1/admin/orders/export","/api/v1/admin/banners")){mvc.perform(get(path)).andExpect(status().isUnauthorized());mvc.perform(get(path).header("Authorization",user)).andExpect(status().isUnauthorized());}
 }
 @Test void publicBannersFollowSortStatusAndSoftDeletion() throws Exception {
  var a=banner("NONE",null,"ACTIVE");a.put("sort",5);long first=create(a);
  var b=banner("NONE",null,"ACTIVE");b.put("sort",1);long second=create(b);
  create(banner("NONE",null,"DISABLED"));
  mvc.perform(get("/api/v1/banners")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2)).andExpect(jsonPath("$.data[0].id").value(second)).andExpect(jsonPath("$.data[1].id").value(first));
  mvc.perform(delete("/api/v1/admin/banners/"+second).header("Authorization",auth)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/banners")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(first));
 }

}
