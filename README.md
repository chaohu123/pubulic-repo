# 高校选课管理系统（enrollment-system）

基于 **Spring Boot 3.2.x** + **JUnit 5** 的选课 CSV / **Excel（xlsx）** 处理演示工程：去重、排序、分类、**统一检索**、导出、分页前端与统一异常响应。

---

## 一、快速运行

```bash
cd enrollment-system
mvn spring-boot:run
```

浏览器访问：`http://localhost:8080/`（地址栏请使用英文半角冒号 `:`）。



## 二、测试说明

### 2.1 运行全部测试

```bash
mvn test
```

### 2.2 测试分层

| 测试类 | 说明 |
|--------|------|
| `EnrollmentServiceTest` | 纯单元测试：CSV/Excel 解析、去重、排序、统一检索、非法参数、**约 1000 条**性能断言（≤1s） |
| `EnrollmentIntegrationTest` | `@SpringBootTest` + `MockMvc`：`/process`、`/process-excel`、`/sample`、`/search` 及非法 JSON |

### 2.3 测试数据（100 / 500 / 1000）

- **运行时生成**：接口 `GET /api/enrollment/mock-csv?size=100|500|1000` 返回含**合法行、重复行、空行、非法格式**的 CSV 文本，用于健壮性自测。  
- **页面加载**：首页按钮「加载测试数据(100/500/1000)」会拉取该文本并自动调用 `/process` 完成统计与展示。

若需将 CSV 保存为本地文件，可在服务启动后执行（示例）：

```bash
curl -o enroll_1000.csv "http://localhost:8080/api/enrollment/mock-csv?size=1000"
```

---

## 三、性能测试结果（参考）

在常见开发机（JDK 17+）上，`mvn test` 日志中可见量级如下（以本机一次运行为例，实际随 CPU 波动）：

| 场景 | 量级说明 |
|------|----------|
| 约 1000 条合法唯一 + 噪声 | `processCsv` 通常在 **数十毫秒** 级完成 |
| 1000 条结果集上的统一检索 | 通常在 **数毫秒～数十毫秒** 级 |

服务内对处理、检索、排序均输出 **SLF4J** 日志，格式示例：`处理耗时：12ms | 合法行=… 输出行=…`。

---

## 四、功能拓展说明

| 能力 | 接口或说明 |
|------|------------|
| 统一 JSON 封装 | 多数接口返回 `{ code, message, data }`，`code=200` 表示成功 |
| 全局异常 | `@RestControllerAdvice`：`BusinessException`、参数校验、`HttpMessageNotReadableException`（**数据格式错误**）、兜底 500 |
| 统一检索（多列 AND + 可选快捷维度） | `POST /api/enrollment/search`，请求体见 `UnifiedSearchRequest` |
| Excel 批量导入 | `POST /api/enrollment/process-excel`（`multipart/form-data`，字段名 `file`，仅 `.xlsx`） |
| 排序切换 | `POST /api/enrollment/sort`，`sortField`: `STUDENT_ID` / `COURSE_ID` / `COURSE_NAME` |
| 导出 CSV | `POST /api/enrollment/export`，`Content-Disposition` 附件下载 |
| 测试 CSV 生成 | `GET /api/enrollment/mock-csv?size=100|500|1000` |

业务规则均在 **`EnrollmentService`**：解析单行扫描、**`HashSet` 复合键去重（保留首次）**、一次排序、单次 Stream 过滤链检索；**Controller 不包含业务判断**。

---

## 五、页面功能（文字说明，相当于截图说明）

1. **导入区**：CSV 文本框、**Excel 文件选择 + 上传**、测试数据按钮。  
2. **统一检索**：学生ID/课程ID/课程名称（子串）+ 课程类型（精确）+ 可选「快捷维度+关键词」，全部为 **AND**。  
3. **加载动画**：请求进行中全屏半透明遮罩 + 旋转指示，按钮禁用。  
4. **统计条**：展示总记录数及公共课/专业课/选修课条数（来自 `ProcessResult`）。  
5. **排序**：选择字段与升/降序后应用到当前列表。  
6. **分页**：表格区域按 **每页 10 条** 分页展示当前视图数据。  
7. **导出**：将**当前视图列表**导出为 `enrollments.csv` 下载。  
8. **清空**：清空主数据与视图并给出 Toast。

---

## 六、项目亮点总结

- **分层清晰**：Controller 只做转发与 HTTP 语义；CSV/Excel 容错、去重、分类、检索、导出均在 Service。  
- **健壮性**：空行、字段缺失、非法学号/课号、课程名为空、文件内重复选课组合均有统计与告警列表（上限截断，避免响应过大）。  
- **性能**：去重使用 **`HashSet` 复合键** + 单次遍历合并分类与去重；检索使用 **Stream 单次过滤链**；关键路径打日志。  
- **可测性**：单元测试覆盖核心业务；`SpringBootTest` 覆盖 REST 与统一响应契约。  
- **可演示**：单页即可完成导入、统计、检索、排序、分页与导出，贴合作业/答辩场景。

---

## 七、相关文件索引

| 路径 | 说明 |
|------|------|
| `docs/AI与人工协作标注说明.md` | **AI 生成与人工优化标注**
| `src/main/java/.../service/EnrollmentService.java` | 核心业务 |
| `src/main/java/.../advice/GlobalExceptionHandler.java` | 统一异常 JSON |
| `src/main/java/.../controller/EnrollmentController.java` | REST 接口 |
| `src/main/resources/static/index.html` | 前端单页 |
| `src/test/java/.../service/EnrollmentServiceTest.java` | 单元测试 |
| `src/test/java/.../web/EnrollmentIntegrationTest.java` | 集成测试 |

---

## 八、API 摘要

- `POST /api/enrollment/process` — body: `{ "csv": "..." }`  
- `POST /api/enrollment/process-excel` — `multipart/form-data`，字段名 `file`，`.xlsx`  
- `POST /api/enrollment/search` — 统一检索，body 见 `UnifiedSearchRequest`（多列 AND + 可选 `quickDimension` / `quickKeyword`）  
- `POST /api/enrollment/sort` — 排序  
- `POST /api/enrollment/export` — 导出文件  
- `GET /api/enrollment/mock-csv?size=100|500|1000` — 测试 CSV 文本  
- `GET /api/enrollment/sample` — 样例 JSON（封装在 `ApiResponse` 内）
