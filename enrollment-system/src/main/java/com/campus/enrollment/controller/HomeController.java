package com.campus.enrollment.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 显式映射根路径到静态首页，避免部分环境下欢迎页未解析导致根路径无内容。
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}
