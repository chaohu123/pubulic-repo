package com.campus.enrollment.web;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring Boot 集成测试：校验 REST 层与统一响应封装。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("选课接口集成测试")
class EnrollmentIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    /**
     * CSV 导入接口应返回 code=200 且包含处理结果 data。
     */
    @Test
    @DisplayName("POST /process 返回统一封装")
    void process_ok() throws Exception {
        String body = "{\"csv\":\"S000001,C000001,测试课,专业课\"}";
        mockMvc.perform(post("/api/enrollment/process").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.outputCount").value(1));
        log.info("【集成】/process 校验通过");
    }

    /**
     * Excel 导入接口应返回 code=200。
     */
    @Test
    @DisplayName("POST /process-excel")
    void processExcel_ok() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet();
            Row h = sh.createRow(0);
            h.createCell(0).setCellValue("学生ID");
            h.createCell(1).setCellValue("课程ID");
            h.createCell(2).setCellValue("课程名称");
            Row d = sh.createRow(1);
            d.createCell(0).setCellValue("S000010");
            d.createCell(1).setCellValue("C000010");
            d.createCell(2).setCellValue("大学英语");
            d.createCell(3).setCellValue("公共课");
            wb.write(bos);
        }
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "e.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bos.toByteArray());
        mockMvc.perform(multipart("/api/enrollment/process-excel").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.outputCount").value(1));
        log.info("【集成】/process-excel 校验通过");
    }

    /**
     * 样例接口应返回统一封装下的列表数据。
     */
    @Test
    @DisplayName("GET /sample")
    void sample_ok() throws Exception {
        mockMvc.perform(get("/api/enrollment/sample"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        log.info("【集成】/sample 校验通过");
    }

    /**
     * 非法 JSON 请求体应返回 code=400 与“数据格式错误”类提示。
     */
    @Test
    @DisplayName("非法 JSON 触发数据格式错误")
    void process_badJson() throws Exception {
        mockMvc.perform(post("/api/enrollment/process").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        log.info("【集成】非法 JSON 校验通过");
    }

    /**
     * 统一检索：多列 AND。
     */
    @Test
    @DisplayName("POST /search 统一检索")
    void search_unified_ok() throws Exception {
        String json = """
                {"records":[{"studentId":"S000001","courseId":"C000001","courseName":"A","courseType":"专业课"}],
                "studentId":"S000001","courseType":"专业课"}
                """;
        mockMvc.perform(post("/api/enrollment/search").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.matched").value(true));
        log.info("【集成】/search 统一检索校验通过");
    }
}
