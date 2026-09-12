package top.lingxi.campus.chat.session.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.lingxi.campus.common.annotation.RequireRole;
import top.lingxi.campus.common.result.Result;

import top.lingxi.campus.chat.session.service.IServiceCatalogService;
import top.lingxi.campus.domain.biz.catalog.entity.BizServiceCatalog;

import java.util.List;

/**
 * 服务目录管理接口（AI 意图识别关键词配置）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/service-catalog")
@RequiredArgsConstructor
@Tag(name = "服务目录管理", description = "配置 AI 意图识别触发关键词")
public class ServiceCatalogController {

    private final IServiceCatalogService catalogService;

    @GetMapping
    @Operation(summary = "获取服务目录列表")
    public Result<List<BizServiceCatalog>> listAll() {
        List<BizServiceCatalog> list = catalogService.listAll();
        return Result.success(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取服务目录详情")
    public Result<BizServiceCatalog> getById(@PathVariable Long id) {
        BizServiceCatalog catalog = catalogService.getById(id);
        return Result.success(catalog);
    }

    @PostMapping
    @RequireRole("ADMIN")
    @Operation(summary = "新增服务目录")
    public Result<BizServiceCatalog> create(@RequestBody BizServiceCatalog catalog) {
        BizServiceCatalog created = catalogService.create(catalog);
        return Result.success(created);
    }

    @PutMapping("/{id}")
    @RequireRole("ADMIN")
    @Operation(summary = "更新服务目录")
    public Result<BizServiceCatalog> update(
            @PathVariable Long id,
            @RequestBody BizServiceCatalog catalog) {
        BizServiceCatalog updated = catalogService.update(id, catalog);
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")
    @Operation(summary = "删除服务目录")
    public Result<Void> delete(@PathVariable Long id) {
        catalogService.delete(id);
        return Result.success();
    }

    @PostMapping("/refresh-cache")
    @RequireRole("ADMIN")
    @Operation(summary = "手动刷新关键词缓存", description = "修改关键词后立即使 AI 生效")
    public Result<Void> refreshCache() {
        catalogService.refreshCache();
        return Result.success();
    }
}